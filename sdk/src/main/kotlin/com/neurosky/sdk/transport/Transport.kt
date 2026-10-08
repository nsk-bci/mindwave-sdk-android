package com.neurosky.sdk.transport

import com.neurosky.sdk.model.BlinkEvent
import com.neurosky.sdk.model.BrainWaveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

interface Transport {
    val dataFlow: Flow<BrainWaveData>
    val stateFlow: Flow<ConnectionState>

    /** 눈 깜빡임 이벤트. 깜빡임을 검출하지 않는 구현(시뮬레이터 등)은 아무것도 방출하지 않는다. */
    val blinkFlow: Flow<BlinkEvent> get() = emptyFlow()
    suspend fun connect(deviceAddress: String)
    suspend fun disconnect()
    suspend fun sendCommand(cmd: Byte)
}

enum class ConnectionState {
    DISCONNECTED, SCANNING, CONNECTING, CONNECTED, ERROR
}
