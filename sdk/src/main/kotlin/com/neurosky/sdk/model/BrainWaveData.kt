package com.neurosky.sdk.model

data class BrainWaveData(
    val timestamp: Long = System.currentTimeMillis(),
    val poorSignal: Int = 0,       // 0=완벽, 200=무신호
    val attention: Int = 0,        // 0~100
    val meditation: Int = 0,       // 0~100
    val delta: Int = 0,
    val theta: Int = 0,
    val lowAlpha: Int = 0,
    val highAlpha: Int = 0,
    val lowBeta: Int = 0,
    val highBeta: Int = 0,
    val lowGamma: Int = 0,
    val midGamma: Int = 0,
    val rawEeg: List<Int> = emptyList(),  // 10샘플/패킷, 512Hz
    val eyeBlink: Int = 0,
) {
    val signalQuality: SignalQuality
        get() = when {
            poorSignal == 200 -> SignalQuality.NO_SIGNAL
            poorSignal > 50   -> SignalQuality.POOR
            poorSignal > 0    -> SignalQuality.FAIR
            else              -> SignalQuality.GOOD
        }
}

enum class SignalQuality { NO_SIGNAL, POOR, FAIR, GOOD }
