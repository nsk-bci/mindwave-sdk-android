package com.neurosky.sdk.transport

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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

    private val pendingDescriptors = ArrayDeque<BluetoothGattDescriptor>()
    private var handshakeSent = false
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

            pendingDescriptors.clear()
            handshakeSent = false

            listOf(NeuroSkyUUID.ESENSE, NeuroSkyUUID.RAW_EEG).forEach { uuid ->
                val char = service.getCharacteristic(uuid) ?: return@forEach
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(NeuroSkyUUID.CCCD) ?: return@forEach
                pendingDescriptors.addLast(descriptor)
            }

            if (pendingDescriptors.isEmpty()) {
                sendHandshake(gatt, NeuroSkyCommand.START_ESENSE)
            } else {
                writeNextDescriptor(gatt)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log("Descriptor write FAILED (status=$status) — continuing anyway")
            }
            if (pendingDescriptors.isNotEmpty()) {
                writeNextDescriptor(gatt)
            } else if (!handshakeSent) {
                handshakeSent = true
                sendHandshake(gatt, NeuroSkyCommand.START_ESENSE)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (characteristic.uuid == NeuroSkyUUID.HANDSHAKE) {
                // 핸드셰이크 실패해도 데이터가 오는 경우가 있으므로 CONNECTED로 전환
                _stateFlow.value = ConnectionState.CONNECTED
            }
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
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _stateFlow.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendCommand(cmd: Byte) {
        gatt?.let { sendHandshake(it, cmd) }
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt) {
        val descriptor = pendingDescriptors.removeFirstOrNull() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun sendHandshake(gatt: BluetoothGatt, cmd: Byte) {
        val service = gatt.services.firstOrNull { svc ->
            svc.characteristics.any { it.uuid == NeuroSkyUUID.HANDSHAKE }
        } ?: run {
            log("HANDSHAKE char not found — setting CONNECTED anyway")
            _stateFlow.value = ConnectionState.CONNECTED
            return
        }
        val char = service.getCharacteristic(NeuroSkyUUID.HANDSHAKE) ?: run {
            _stateFlow.value = ConnectionState.CONNECTED
            return
        }

        val packet = ByteArray(20) { 0x00 }
        packet[0] = 0x77
        packet[1] = 0x01
        packet[2] = cmd
        val sum = packet.slice(1..18).fold(0) { acc, b -> acc + (b.toInt() and 0xFF) }
        packet[19] = ((sum xor 0xFF) and 0xFF).toByte()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = packet
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
    }
}
