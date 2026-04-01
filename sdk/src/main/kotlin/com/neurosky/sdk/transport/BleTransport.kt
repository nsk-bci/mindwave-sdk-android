package com.neurosky.sdk.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.neurosky.sdk.NeuroSkyCommand
import com.neurosky.sdk.NeuroSkyUUID
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.parser.ThinkGearParser
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow

class BleTransport(private val context: Context) : Transport {

    private val _stateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val stateFlow: Flow<ConnectionState> = _stateFlow

    private val parser = ThinkGearParser()
    private var gatt: BluetoothGatt? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // callbackFlow 채널 참조 — gattCallback에서 데이터 전송에 사용
    private var dataChannel: SendChannel<BrainWaveData>? = null

    // Descriptor write는 직렬로 처리해야 함 (BLE 스택 제약)
    private val pendingDescriptors = ArrayDeque<BluetoothGattDescriptor>()
    private var handshakeSent = false

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _stateFlow.value = ConnectionState.CONNECTING
                gatt.discoverServices()
            } else {
                _stateFlow.value = ConnectionState.DISCONNECTED
                dataChannel?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.services.firstOrNull { svc ->
                svc.characteristics.any { it.uuid == NeuroSkyUUID.ESENSE }
            } ?: return

            pendingDescriptors.clear()
            handshakeSent = false

            // Notification 활성화 대상을 큐에 적재 — onDescriptorWrite에서 순차 처리
            listOf(NeuroSkyUUID.ESENSE, NeuroSkyUUID.RAW_EEG).forEach { uuid ->
                val char = service.getCharacteristic(uuid) ?: return@forEach
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(NeuroSkyUUID.CCCD) ?: return@forEach
                pendingDescriptors.addLast(descriptor)
            }

            writeNextDescriptor(gatt)
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (pendingDescriptors.isNotEmpty()) {
                // 남은 descriptor가 있으면 계속 처리
                writeNextDescriptor(gatt)
            } else if (!handshakeSent) {
                // 모든 descriptor write 완료 후 핸드셰이크를 1회만 전송
                handshakeSent = true
                sendHandshake(gatt, NeuroSkyCommand.START_ESENSE)
                _stateFlow.value = ConnectionState.CONNECTED
            }
        }

        // API 32 이하
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = parser.parse(characteristic.uuid, characteristic.value)
            if (data != null) dataChannel?.trySend(data)
        }

        // API 33+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            val data = parser.parse(characteristic.uuid, value)
            if (data != null) dataChannel?.trySend(data)
        }
    }

    override val dataFlow: Flow<BrainWaveData> = callbackFlow {
        dataChannel = channel
        awaitClose {
            dataChannel = null
            gatt?.close()
            gatt = null
        }
    }

    override suspend fun connect(deviceAddress: String) {
        _stateFlow.value = ConnectionState.SCANNING
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: run {
            _stateFlow.value = ConnectionState.ERROR
            return
        }
        _stateFlow.value = ConnectionState.CONNECTING
        gatt = device.connectGatt(context, false, gattCallback)
    }

    override suspend fun disconnect() {
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
        } ?: return
        val char = service.getCharacteristic(NeuroSkyUUID.HANDSHAKE) ?: return

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
