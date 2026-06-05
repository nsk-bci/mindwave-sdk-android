package com.neurosky.sdk.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.neurosky.sdk.NeuroSkyCommand
import com.neurosky.sdk.NeuroSkyUUID
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.parser.ThinkGearParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class BleTransport(private val context: Context) : Transport {

    private val _stateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val stateFlow: Flow<ConnectionState> = _stateFlow

    private val parser = ThinkGearParser()
    private var gatt: BluetoothGatt? = null
    private val bluetoothAdapter =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val mainHandler = Handler(Looper.getMainLooper())
    private val _dataFlow = MutableSharedFlow<BrainWaveData>(extraBufferCapacity = 64)

    /** UI 로그 콜백 — NeuroSkySdk.setLogger()로 주입 */
    var logger: ((String) -> Unit)? = null

    // 연결 시퀀스(알림 활성 + 핸드셰이크)를 콜백 스레드에서 코루틴으로 돌리기 위한 스코프.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 모든 GATT write(디스크립터/명령)를 하나의 직렬 큐로 통과시킨다. Android는 연결당 한 번에
    // ATT 연산 1개만 허용하므로, 콜백을 기다리지 않은 연속 write는 조용히 유실된다(이전 버전의
    // 거짓 성공 원인: 연결 직후 START_RAW_EEG 직후의 노치 write 등).
    private val writeQueue = GattWriteQueue { msg -> log(msg) }

    private var lastDeviceAddress: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    private fun log(msg: String) {
        logger?.invoke(msg)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                    retryCount = 0
                    log("GATT connected → discoverServices")
                    _stateFlow.value = ConnectionState.CONNECTING
                    gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    log("GATT disconnected (status=$status)")
                    gatt.close()
                    this@BleTransport.gatt = null
                    writeQueue.cancelAll() // 진행 중 write 대기 해제
                    if (status == 133 && retryCount < maxRetries) {
                        retryCount++
                        log("GATT error 133 — retry $retryCount/$maxRetries in 600ms")
                        val addr = lastDeviceAddress
                        if (addr != null) {
                            mainHandler.postDelayed({ attemptConnectGatt(addr) }, 600L)
                        } else {
                            _stateFlow.value = ConnectionState.ERROR
                        }
                    } else {
                        _stateFlow.value = if (status != BluetoothGatt.GATT_SUCCESS)
                            ConnectionState.ERROR else ConnectionState.DISCONNECTED
                    }
                }
                else -> {
                    _stateFlow.value = ConnectionState.ERROR
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("onServicesDiscovered FAILED status=$status")
                _stateFlow.value = ConnectionState.ERROR
                return
            }
            val service = gatt.services.firstOrNull { svc ->
                svc.characteristics.any { it.uuid == NeuroSkyUUID.ESENSE }
            } ?: run {
                log("ERROR: No service with ESENSE char found")
                _stateFlow.value = ConnectionState.ERROR
                return
            }
            // 알림 활성(CCCD write)과 핸드셰이크를 직렬 큐로 한 단계씩 진행.
            runConnectSequence(gatt, service)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            writeQueue.onResult(status)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // 핸드셰이크 write가 성공하면 CONNECTED로 전환(실패 시는 runConnectSequence가 처리).
            if (characteristic.uuid == NeuroSkyUUID.HANDSHAKE && status == BluetoothGatt.GATT_SUCCESS) {
                _stateFlow.value = ConnectionState.CONNECTED
            }
            writeQueue.onResult(status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = parser.parse(characteristic.uuid, characteristic.value ?: return)
            if (data != null) _dataFlow.tryEmit(data)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val data = parser.parse(characteristic.uuid, value)
            if (data != null) _dataFlow.tryEmit(data)
        }
    }

    override val dataFlow: Flow<BrainWaveData> = _dataFlow

    override suspend fun connect(deviceAddress: String) {
        log("connect() → $deviceAddress")
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        retryCount = 0
        lastDeviceAddress = deviceAddress
        _stateFlow.value = ConnectionState.CONNECTING
        attemptConnectGatt(deviceAddress)
    }

    private fun attemptConnectGatt(deviceAddress: String) {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: run {
            _stateFlow.value = ConnectionState.ERROR
            return
        }
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(context, false, gattCallback)
        }
    }

    override suspend fun disconnect() {
        lastDeviceAddress = null
        retryCount = 0
        writeQueue.cancelAll()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _stateFlow.value = ConnectionState.DISCONNECTED
    }

    /**
     * 명령을 헤드셋에 전송하고 **onCharacteristicWrite(성공)까지 suspend** 한다.
     * 큐가 직렬화·재시도하며, 끝내 실패하면 예외를 던진다(거짓 성공 방지).
     */
    override suspend fun sendCommand(cmd: Byte) {
        val g = gatt ?: throw IllegalStateException("sendCommand: not connected (gatt == null)")
        val char = findHandshakeCharacteristic(g)
            ?: throw IllegalStateException("sendCommand: HANDSHAKE characteristic not found")
        writeQueue.write(g, char, buildCommandPacket(cmd), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        log("command 0x%02x applied".format(cmd.toInt() and 0xFF))
    }

    /** 알림 활성(CCCD) → 핸드셰이크를 직렬 큐로 한 단계씩 진행. */
    private fun runConnectSequence(gatt: BluetoothGatt, service: BluetoothGattService) {
        scope.launch {
            try {
                listOf(NeuroSkyUUID.ESENSE, NeuroSkyUUID.RAW_EEG).forEach { uuid ->
                    val char = service.getCharacteristic(uuid) ?: return@forEach
                    gatt.setCharacteristicNotification(char, true)
                    val cccd = char.getDescriptor(NeuroSkyUUID.CCCD) ?: return@forEach
                    writeQueue.write(gatt, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                }
                sendHandshake(gatt, NeuroSkyCommand.START_ESENSE)
                _stateFlow.value = ConnectionState.CONNECTED
            } catch (t: Throwable) {
                // 핸드셰이크가 실패해도 데이터가 오는 경우가 있어 CONNECTED로 두되, 연결이 이미
                // 끊긴 경우(gatt == null)에는 상태를 덮어쓰지 않는다.
                if (this@BleTransport.gatt != null) {
                    log("connect sequence failed (${t.message}) — setting CONNECTED anyway")
                    _stateFlow.value = ConnectionState.CONNECTED
                }
            }
        }
    }

    /** 핸드셰이크 명령을 큐로 전송하고 완료까지 suspend. */
    private suspend fun sendHandshake(gatt: BluetoothGatt, cmd: Byte) {
        val char = findHandshakeCharacteristic(gatt) ?: run {
            log("HANDSHAKE char not found — setting CONNECTED anyway")
            _stateFlow.value = ConnectionState.CONNECTED
            return
        }
        writeQueue.write(gatt, char, buildCommandPacket(cmd), BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
    }

    private fun findHandshakeCharacteristic(gatt: BluetoothGatt): BluetoothGattCharacteristic? =
        gatt.services
            .firstOrNull { svc -> svc.characteristics.any { it.uuid == NeuroSkyUUID.HANDSHAKE } }
            ?.getCharacteristic(NeuroSkyUUID.HANDSHAKE)

    /** ThinkGear 명령 패킷(20바이트, [0]=0x77 [1]=0x01 [2]=cmd [19]=checksum). */
    private fun buildCommandPacket(cmd: Byte): ByteArray {
        val packet = ByteArray(20) { 0x00 }
        packet[0] = 0x77
        packet[1] = 0x01
        packet[2] = cmd
        val sum = packet.slice(1..18).fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        packet[19] = ((sum xor 0xFF) and 0xFF).toByte()
        return packet
    }
}
