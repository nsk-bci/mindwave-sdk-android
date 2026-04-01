package com.neurosky.sdk.simulator

import com.neurosky.sdk.model.BrainWaveData
import com.neurosky.sdk.transport.ConnectionState
import com.neurosky.sdk.transport.Transport
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * 실기기 없이 개발할 때 사용하는 시뮬레이터 Transport.
 * 1초마다 BrainWaveData를 자동 생성한다.
 */
class SimulatorTransport : Transport {

    enum class Mode { RANDOM, FOCUSED, RELAXED, POOR_SIGNAL }

    private var mode = Mode.RANDOM
    private val _stateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val stateFlow: Flow<ConnectionState> = _stateFlow

    fun setMode(newMode: Mode) { mode = newMode }

    override val dataFlow: Flow<BrainWaveData> = flow {
        while (true) {
            emit(generateData())
            delay(1000L)
        }
    }

    override suspend fun connect(deviceAddress: String) {
        _stateFlow.value = ConnectionState.CONNECTING
        delay(500L)
        _stateFlow.value = ConnectionState.CONNECTED
    }

    override suspend fun disconnect() {
        _stateFlow.value = ConnectionState.DISCONNECTED
    }

    override suspend fun sendCommand(cmd: Byte) {
        // 시뮬레이터에서는 명령을 무시
    }

    private fun generateData(): BrainWaveData = when (mode) {
        Mode.FOCUSED -> BrainWaveData(
            poorSignal  = 0,
            attention   = Random.nextInt(70, 100),
            meditation  = Random.nextInt(40, 60),
            delta       = Random.nextInt(10_000, 50_000),
            theta       = Random.nextInt(5_000, 20_000),
            lowAlpha    = Random.nextInt(3_000, 10_000),
            highAlpha   = Random.nextInt(3_000, 10_000),
            lowBeta     = Random.nextInt(15_000, 40_000),
            highBeta    = Random.nextInt(10_000, 30_000),
            lowGamma    = Random.nextInt(5_000, 15_000),
            midGamma    = Random.nextInt(5_000, 15_000),
            rawEeg      = List(10) { Random.nextInt(-2048, 2048) }
        )
        Mode.RELAXED -> BrainWaveData(
            poorSignal  = 0,
            attention   = Random.nextInt(20, 50),
            meditation  = Random.nextInt(70, 100),
            delta       = Random.nextInt(20_000, 80_000),
            theta       = Random.nextInt(15_000, 40_000),
            lowAlpha    = Random.nextInt(10_000, 30_000),
            highAlpha   = Random.nextInt(10_000, 30_000),
            lowBeta     = Random.nextInt(3_000, 10_000),
            highBeta    = Random.nextInt(3_000, 10_000),
            lowGamma    = Random.nextInt(2_000, 8_000),
            midGamma    = Random.nextInt(2_000, 8_000),
            rawEeg      = List(10) { Random.nextInt(-1024, 1024) }
        )
        Mode.POOR_SIGNAL -> BrainWaveData(
            poorSignal  = Random.nextInt(150, 200),
            attention   = 0,
            meditation  = 0,
            rawEeg      = List(10) { Random.nextInt(-4096, 4096) }
        )
        Mode.RANDOM -> BrainWaveData(
            poorSignal  = Random.nextInt(0, 30),
            attention   = Random.nextInt(0, 100),
            meditation  = Random.nextInt(0, 100),
            delta       = Random.nextInt(0, 100_000),
            theta       = Random.nextInt(0, 100_000),
            lowAlpha    = Random.nextInt(0, 50_000),
            highAlpha   = Random.nextInt(0, 50_000),
            lowBeta     = Random.nextInt(0, 50_000),
            highBeta    = Random.nextInt(0, 50_000),
            lowGamma    = Random.nextInt(0, 30_000),
            midGamma    = Random.nextInt(0, 30_000),
            rawEeg      = List(10) { Random.nextInt(-2048, 2048) }
        )
    }
}
