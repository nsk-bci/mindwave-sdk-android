package com.neurosky.sdk.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.parser.ThinkGearParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val _dataFlow = MutableSharedFlow<BrainWaveData>(extraBufferCapacity = 64)
    override val dataFlow: Flow<BrainWaveData> = _dataFlow

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var readJob: Job? = null

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
            readJob = scope.launch { readLoop() }
        } catch (e: Exception) {
            _stateFlow.value = ConnectionState.ERROR
            socket?.close()
            socket = null
        }
    }

    private suspend fun readLoop() {
        val inputStream = socket?.inputStream ?: return
        val buffer = ByteArray(1024)
        try {
            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) break
                for (i in 0 until bytesRead) {
                    val data = parser.parseByte(buffer[i])
                    if (data != null) _dataFlow.tryEmit(data)
                }
            }
        } catch (e: Exception) {
            _stateFlow.value = ConnectionState.ERROR
        } finally {
            _stateFlow.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        readJob?.cancel()
        readJob = null
        socket?.close()
        socket = null
        _stateFlow.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendCommand(cmd: Byte): Unit = withContext(Dispatchers.IO) {
        socket?.outputStream?.write(byteArrayOf(cmd))
    }
}
