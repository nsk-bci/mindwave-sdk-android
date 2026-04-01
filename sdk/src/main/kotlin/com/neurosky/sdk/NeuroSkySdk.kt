package com.neurosky.sdk

import android.content.Context
import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.transport.BleTransport
import com.neurosky.sdk.transport.BtClassicTransport
import com.neurosky.sdk.transport.ConnectionState
import com.neurosky.sdk.transport.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * NeuroSky MindWave SDK 진입점.
 *
 * BLE를 우선 시도하고, 5초 내 연결 실패 시 BT Classic으로 자동 폴백한다.
 *
 * ```kotlin
 * val sdk = NeuroSkySdk(context)
 *
 * lifecycleScope.launch {
 *     sdk.connect("MindWave Mobile")
 *     sdk.dataFlow.collect { data ->
 *         println("Attention: ${data.attention}")
 *     }
 * }
 * ```
 */
class NeuroSkySdk(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bleTransport = BleTransport(context)
    private val btTransport  = BtClassicTransport()

    private var activeTransport: Transport = bleTransport

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    val dataFlow: Flow<BrainWaveData> get() = activeTransport.dataFlow

    /**
     * 디바이스에 연결한다.
     * @param deviceAddress BLE MAC 주소 또는 "MindWave Mobile" 디바이스 이름
     */
    suspend fun connect(deviceAddress: String) {
        activeTransport = bleTransport
        bleTransport.connect(deviceAddress)

        // BLE 연결 타임아웃 — 5초 내 CONNECTED 미달성 시 BT Classic 폴백
        val bleConnected = waitForConnected(bleTransport, timeoutMs = 5_000L)
        if (!bleConnected) {
            bleTransport.disconnect()
            activeTransport = btTransport
            btTransport.connect(deviceAddress)
        }

        scope.launch {
            activeTransport.stateFlow.collect { state ->
                _connectionState.value = state
            }
        }
    }

    suspend fun disconnect() {
        activeTransport.disconnect()
    }

    suspend fun sendCommand(cmd: Byte) {
        activeTransport.sendCommand(cmd)
    }

    private suspend fun waitForConnected(transport: Transport, timeoutMs: Long): Boolean {
        return withTimeoutOrNull(timeoutMs) {
            transport.stateFlow.first { it == ConnectionState.CONNECTED }
        } != null
    }
}
