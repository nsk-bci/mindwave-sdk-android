package com.neurosky.sdk.transport

import com.neurosky.sdk.model.BrainWaveData
import kotlinx.coroutines.flow.Flow

interface Transport {
    val dataFlow: Flow<BrainWaveData>
    val stateFlow: Flow<ConnectionState>
    suspend fun connect(deviceAddress: String)
    suspend fun disconnect()
    suspend fun sendCommand(cmd: Byte)
}

enum class ConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR
}
