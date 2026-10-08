package com.neurosky.sdk.parser

import com.neurosky.sdk.NeuroSkyUUID
import com.neurosky.sdk.model.BlinkEvent
import com.neurosky.sdk.model.BrainWaveData
import java.util.UUID

/**
 * NeuroSky ThinkGear 패킷 파서.
 *
 * [parse]에 BLE characteristic UUID와 raw bytes를 전달한다.
 *
 * RawEEG 샘플은 [BlinkDetector]로도 흘려보내, 깜빡임이 검출될 때마다 [onBlink]로 [BlinkEvent]를 넘긴다.
 * 신호가 확인되지 않았거나(0xEA 미수신) poorSignal이 [maxPoorSignal]을 넘는 동안에는 검출하지 않는다 —
 * 전극 접촉 불량에서 생기는 큰 진폭 변화를 깜빡임으로 오인하지 않기 위해서다.
 */
class ThinkGearParser(
    private val blinkDetector: BlinkDetector = BlinkDetector(),
    private val maxPoorSignal: Int = DEFAULT_MAX_POOR_SIGNAL,
    private val onBlink: (BlinkEvent) -> Unit = {},
) {

    // 현재 누적 중인 BrainWaveData (패킷 여러 개에 걸쳐 조립)
    private var current = BrainWaveData()

    // 0xEA 패킷으로 신호 품질을 한 번이라도 받았는지 (받기 전에는 깜빡임 검출 안 함)
    private var signalKnown = false
    private var blinkSequence = 0

    /** 새 연결을 위해 누적 상태(데이터, 깜빡임 횟수, 검출기)를 초기화한다. */
    fun reset() {
        current = BrainWaveData()
        signalKnown = false
        blinkSequence = 0
        blinkDetector.reset()
    }

    fun parse(uuid: UUID, bytes: ByteArray): BrainWaveData? {
        return when (uuid) {
            NeuroSkyUUID.ESENSE   -> parseEsense(bytes)
            NeuroSkyUUID.RAW_EEG  -> parseRawEeg(bytes)
            else                  -> null
        }
    }

    /**
     * 0xEA 패킷 — Attention / Meditation / PoorSignal
     * 0xEB 패킷 — Delta, Theta, LowAlpha, HighAlpha
     * 0xEC 패킷 — LowBeta, HighBeta, LowGamma, MidGamma
     */
    private fun parseEsense(bytes: ByteArray): BrainWaveData? {
        if (bytes.size < 3) return null
        return when (bytes[2].toInt() and 0xFF) {
            0xEA -> {
                if (bytes.size < 11) return null
                current = current.copy(
                    poorSignal = bytes[6].toInt() and 0xFF,
                    attention  = bytes[8].toInt() and 0xFF,
                    meditation = bytes[10].toInt() and 0xFF,
                    timestamp  = System.currentTimeMillis()
                )
                signalKnown = true
                current
            }
            0xEB -> {
                if (bytes.size < 20) return null
                current = current.copy(
                    delta      = read3Bytes(bytes, 5),
                    theta      = read3Bytes(bytes, 9),
                    lowAlpha   = read3Bytes(bytes, 13),
                    highAlpha  = read3Bytes(bytes, 17)
                )
                null // 0xEC까지 기다렸다가 방출
            }
            0xEC -> {
                if (bytes.size < 20) return null
                current = current.copy(
                    lowBeta    = read3Bytes(bytes, 5),
                    highBeta   = read3Bytes(bytes, 9),
                    lowGamma   = read3Bytes(bytes, 13),
                    midGamma   = read3Bytes(bytes, 17),
                    timestamp  = System.currentTimeMillis()
                )
                current
            }
            else -> null
        }
    }

    /**
     * RawEEG 특성 (039afff4)
     * 20바이트 → 2바이트씩 → 10개 샘플
     */
    private fun parseRawEeg(bytes: ByteArray): BrainWaveData? {
        if (bytes.size < 20) return null
        val samples = (0 until 10).map { i ->
            val offset = i * 2
            var raw = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
            if (raw >= 32768) raw -= 65536
            raw
        }
        current = current.copy(rawEeg = samples, timestamp = System.currentTimeMillis())
        detectBlink(samples)
        return current
    }

    private fun detectBlink(samples: List<Int>) {
        if (!signalKnown || current.poorSignal > maxPoorSignal) {
            blinkDetector.reset()  // 신호가 돌아오면 워밍업부터 다시
            return
        }
        val strength = blinkDetector.process(samples)
        if (strength > 0) onBlink(BlinkEvent(current.timestamp, strength, ++blinkSequence))
    }

    // ── 공통 유틸 ─────────────────────────────────────────────────────────────

    private fun read3Bytes(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               (bytes[offset + 2].toInt() and 0xFF)
    }

    companion object {
        /** 이 값을 넘는 poorSignal(= POOR, NO_SIGNAL)에서는 깜빡임을 검출하지 않는다. */
        const val DEFAULT_MAX_POOR_SIGNAL = 50
    }
}
