package com.neurosky.sdk.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.parser.ThinkGearParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BtClassicTransport : Transport {

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }

    private val _stateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val stateFlow: Flow<ConnectionState> = _stateFlow

    private val parser = ThinkGearParser()
    private var socket: BluetoothSocket? = null
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    override val dataFlow: Flow<BrainWaveData> = callbackFlow {
        val inputStream = socket?.inputStream
        if (inputStream == null) {
            close()
            awaitClose { socket?.close(); socket = null }
            return@callbackFlow
        }

        val buffer = ByteArray(1024)

        // Blocking IO를 별도 코루틴에서 실행 — awaitClose까지 도달 가능
        val readJob = launch(Dispatchers.IO) {
            try {
                while (true) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead <= 0) break
                    // BT Classic은 ThinkGear Serial 프로토콜 스트림 파싱
                    for (i in 0 until bytesRead) {
                        val data = parser.parseByte(buffer[i])
                        if (data != null) trySend(data)
                    }
                }
            } catch (e: Exception) {
                _stateFlow.value = ConnectionState.ERROR
            } finally {
                _stateFlow.value = ConnectionState.DISCONNECTED
                close()
            }
        }

        awaitClose {
            readJob.cancel()
            socket?.close()
            socket = null
        }
    }

    override suspend fun connect(deviceAddress: String) = withContext(Dispatchers.IO) {
        _stateFlow.value = ConnectionState.CONNECTING
        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress) ?: run {
                _stateFlow.value = ConnectionState.ERROR
                return@withContext
            }
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            bluetoothAdapter?.cancelDiscovery()
            socket?.connect()
            _stateFlow.value = ConnectionState.CONNECTED
        } catch (e: Exception) {
            _stateFlow.value = ConnectionState.ERROR
            socket?.close()
            socket = null
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
        _stateFlow.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendCommand(cmd: Byte): Unit = withContext(Dispatchers.IO) {
        socket?.outputStream?.write(byteArrayOf(cmd))
    }
}
