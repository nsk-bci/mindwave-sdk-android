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
 * 테스트 전용 시뮬레이터 Transport. 1초마다 BrainWaveData를 생성한다.
 *
 * internal — SDK 모듈의 단위 테스트에서만 쓴다. 고정 시드 난수를 쓰므로 같은 시드와 모드는
 * 항상 같은 값 순서를 만든다.
 */
internal class SimulatorTransport(seed: Long = DEFAULT_SEED) : Transport {

    enum class Mode { RANDOM, FOCUSED, RELAXED, POOR_SIGNAL }

    private var mode = Mode.RANDOM
    private val random = Random(seed)
    private val _stateFlow = MutableStateFlow(ConnectionState.DISCONNECTED)

    /**
     * 현재 연결 상태를 나타내는 [Flow].
     *
     * [Transport] 인터페이스에 정의된 `stateFlow`이며, `connectionState`가 아님에 주의.
     * `connectionState`는 [com.neurosky.sdk.NeuroSkySdk]가 내부 트랜스포트의 이
     * `stateFlow`를 구독해 외부로 노출하는 [kotlinx.coroutines.flow.StateFlow] 래퍼다.
     *
     * [SimulatorTransport]를 직접 사용할 때는 반드시 이 프로퍼티를 구독할 것.
     *
     * ```kotlin
     * // 올바른 사용
     * simulatorTransport.stateFlow.collect { state -> ... }
     *
     * // 잘못된 사용 — SimulatorTransport에는 connectionState가 없다
     * // simulatorTransport.connectionState  // 컴파일 에러
     * ```
     */
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

    /** 테스트에서 1초 지연 없이 값을 뽑을 수 있도록 internal. */
    internal fun generateData(): BrainWaveData = when (mode) {
        Mode.FOCUSED -> BrainWaveData(
            poorSignal  = 0,
            attention   = random.nextInt(70, 100),
            meditation  = random.nextInt(40, 60),
            delta       = random.nextInt(10_000, 50_000),
            theta       = random.nextInt(5_000, 20_000),
            lowAlpha    = random.nextInt(3_000, 10_000),
            highAlpha   = random.nextInt(3_000, 10_000),
            lowBeta     = random.nextInt(15_000, 40_000),
            highBeta    = random.nextInt(10_000, 30_000),
            lowGamma    = random.nextInt(5_000, 15_000),
            midGamma    = random.nextInt(5_000, 15_000),
            rawEeg      = List(10) { random.nextInt(-2048, 2048) }
        )
        Mode.RELAXED -> BrainWaveData(
            poorSignal  = 0,
            attention   = random.nextInt(20, 50),
            meditation  = random.nextInt(70, 100),
            delta       = random.nextInt(20_000, 80_000),
            theta       = random.nextInt(15_000, 40_000),
            lowAlpha    = random.nextInt(10_000, 30_000),
            highAlpha   = random.nextInt(10_000, 30_000),
            lowBeta     = random.nextInt(3_000, 10_000),
            highBeta    = random.nextInt(3_000, 10_000),
            lowGamma    = random.nextInt(2_000, 8_000),
            midGamma    = random.nextInt(2_000, 8_000),
            rawEeg      = List(10) { random.nextInt(-1024, 1024) }
        )
        Mode.POOR_SIGNAL -> BrainWaveData(
            poorSignal  = random.nextInt(150, 200),
            attention   = 0,
            meditation  = 0,
            rawEeg      = List(10) { random.nextInt(-4096, 4096) }
        )
        Mode.RANDOM -> BrainWaveData(
            poorSignal  = random.nextInt(0, 30),
            attention   = random.nextInt(0, 100),
            meditation  = random.nextInt(0, 100),
            delta       = random.nextInt(0, 100_000),
            theta       = random.nextInt(0, 100_000),
            lowAlpha    = random.nextInt(0, 50_000),
            highAlpha   = random.nextInt(0, 50_000),
            lowBeta     = random.nextInt(0, 50_000),
            highBeta    = random.nextInt(0, 50_000),
            lowGamma    = random.nextInt(0, 30_000),
            midGamma    = random.nextInt(0, 30_000),
            rawEeg      = List(10) { random.nextInt(-2048, 2048) }
        )
    }

    companion object {
        const val DEFAULT_SEED = 7L
    }
}
