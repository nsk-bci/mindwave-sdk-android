package com.neurosky.sdk.parser

/**
 * Raw EEG 스트림에서 눈 깜빡임을 검출한다.
 *
 * 깜빡임은 raw EEG에 짧고 큰 진폭 변화로 나타난다. 최근 [windowSize] 샘플(512Hz 기준 약 200ms)의
 * peak-to-peak 진폭이 [threshold] 이상이면 깜빡임으로 판정하고, [cooldownMs] 동안은 중복 검출하지 않는다.
 * 스트림 시작 직후(또는 [RESTART_GAP_MS] 이상 끊겼다가 재개된 직후) [warmupMs] 동안은
 * 안정화 과도 신호를 깜빡임으로 오인하지 않도록 검출을 멈춘다.
 *
 * 임계값 단위는 BLE raw EEG 값([com.neurosky.sdk.model.BrainWaveData.rawEeg])과 같다.
 * 웹 튜토리얼의 600은 Web Bluetooth raw 단위(네이티브의 약 1/5)라 그대로 쓰면 오검출이 많다.
 * [DEFAULT_THRESHOLD]는 실기기 측정 전의 잠정값이다.
 */
class BlinkDetector(
    val windowSize: Int = 100,
    val threshold: Int = DEFAULT_THRESHOLD,
    val cooldownMs: Long = 600,
    val warmupMs: Long = 500,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    init {
        require(windowSize >= MIN_SAMPLES) { "windowSize must be >= $MIN_SAMPLES" }
    }

    private val window = IntArray(windowSize)
    private var count = 0
    private var head = 0
    private var armedAt = 0L
    private var lastSampleAt = 0L
    private var lastBlinkAt = NEVER

    /**
     * 새 raw 샘플을 넣고 깜빡임 여부를 판정한다.
     *
     * @return 깜빡임이 검출되면 윈도우의 peak-to-peak 진폭, 아니면 0
     */
    fun process(samples: List<Int>): Int {
        if (samples.isEmpty()) return 0
        val now = clock()
        if (count == 0 || now - lastSampleAt > RESTART_GAP_MS) arm(now)
        lastSampleAt = now

        for (s in samples) {
            window[head] = s
            head = (head + 1) % windowSize
            if (count < windowSize) count++
        }

        if (count < MIN_SAMPLES) return 0
        if (now - armedAt < warmupMs) return 0
        if (now - lastBlinkAt <= cooldownMs) return 0

        var min = Int.MAX_VALUE
        var max = Int.MIN_VALUE
        for (i in 0 until count) {
            val v = window[i]
            if (v < min) min = v
            if (v > max) max = v
        }
        val peakToPeak = max - min
        if (peakToPeak < threshold) return 0

        lastBlinkAt = now
        return peakToPeak
    }

    /** 상태를 초기화한다. 다음 샘플부터 워밍업이 다시 시작된다. */
    fun reset() {
        count = 0
        head = 0
    }

    private fun arm(now: Long) {
        reset()
        armedAt = now
        lastBlinkAt = NEVER
    }

    companion object {
        /**
         * 잠정 임계값 — 웹 튜토리얼 값(600)을 네이티브 단위로 환산한 추정치.
         * 실기기 측정(의도적 깜빡임 30회 + 무깜빡임 1분)으로 확정하기 전까지 릴리즈하지 않는다.
         */
        const val DEFAULT_THRESHOLD = 3_000

        /** 이 시간 이상 샘플이 끊기면 스트림 재시작으로 보고 워밍업을 다시 적용한다. */
        const val RESTART_GAP_MS = 1_000L
        private const val MIN_SAMPLES = 8
        private const val NEVER = Long.MIN_VALUE / 2
    }
}
