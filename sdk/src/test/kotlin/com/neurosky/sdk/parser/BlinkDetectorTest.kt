package com.neurosky.sdk.parser

import com.neurosky.sdk.NeuroSkyUUID
import com.neurosky.sdk.model.BlinkEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkDetectorTest {

    private var now = 10_000L

    // 잠정 기본값과 무관하게 검출 로직만 검증하도록 임계값을 고정한다.
    private val detector = BlinkDetector(threshold = THRESHOLD, clock = { now })

    private val flat = List(10) { 0 }
    private fun spike(amplitude: Int) = List(10) { i -> if (i == 5) amplitude else 0 }

    /** 워밍업이 끝날 때까지 평탄한 신호를 ~20ms 간격(BLE 패킷 주기)으로 넣는다. */
    private fun warmUp() {
        detector.process(flat)
        repeat(30) { now += 20; detector.process(flat) }
    }

    // ── BlinkDetector ────────────────────────────────────────────────────────

    @Test
    fun detectsBlink_whenPeakToPeakReachesThreshold() {
        warmUp()
        now += 20
        assertEquals(THRESHOLD, detector.process(spike(THRESHOLD)))
    }

    @Test
    fun ignoresDeflection_belowThreshold() {
        warmUp()
        now += 20
        assertEquals(0, detector.process(spike(THRESHOLD - 1)))
    }

    @Test
    fun suppressesDetection_duringWarmup() {
        detector.process(flat)
        now += 400
        assertEquals(0, detector.process(spike(THRESHOLD * 5)))
    }

    @Test
    fun suppressesRepeatedDetection_withinCooldown() {
        warmUp()
        now += 20
        assertEquals(1_200, detector.process(spike(1_200)))
        now += 600
        assertEquals(0, detector.process(spike(1_200)))
        now += 1
        assertEquals(1_200, detector.process(spike(1_200)))
    }

    @Test
    fun peakToPeak_coversWholeWindow_notOnlyLatestPacket() {
        warmUp()
        now += 20
        detector.process(List(10) { -600 })
        now += 20
        assertEquals(1_100, detector.process(List(10) { 500 }))
    }

    @Test
    fun rearmsWarmup_afterStreamGap() {
        warmUp()
        now += BlinkDetector.RESTART_GAP_MS + 1
        assertEquals(0, detector.process(spike(THRESHOLD * 5)))
    }

    // ── ThinkGearParser → BlinkEvent ─────────────────────────────────────────

    private val events = mutableListOf<BlinkEvent>()
    private val parser = ThinkGearParser(detector, onBlink = { events += it })

    private fun rawPacket(samples: List<Int>) = ByteArray(20).also { b ->
        samples.forEachIndexed { i, v ->
            b[i * 2] = (v shr 8).toByte()
            b[i * 2 + 1] = v.toByte()
        }
    }

    private fun esense(poorSignal: Int) = ByteArray(11).also {
        it[2] = 0xEA.toByte()
        it[6] = poorSignal.toByte()
    }

    private fun feedRaw(samples: List<Int>) {
        now += 20
        parser.parse(NeuroSkyUUID.RAW_EEG, rawPacket(samples))
    }

    private fun warmUpParser() = repeat(31) { feedRaw(flat) }

    @Test
    fun parser_emitsBlinkEvent_withStrengthAndSequence() {
        parser.parse(NeuroSkyUUID.ESENSE, esense(poorSignal = 0))
        warmUpParser()

        feedRaw(spike(1_500))
        repeat(10) { feedRaw(flat) }  // 첫 스파이크를 윈도우(100샘플)에서 밀어낸다
        now += 400                    // 쿨다운(600ms) 경과
        feedRaw(spike(1_300))

        assertEquals(listOf(1, 2), events.map { it.sequence })
        assertEquals(listOf(1_500, 1_300), events.map { it.strength })
    }

    @Test
    fun parser_doesNotDetect_beforeSignalQualityIsKnown() {
        warmUpParser()
        feedRaw(spike(THRESHOLD * 5))
        assertTrue(events.isEmpty())
    }

    @Test
    fun parser_doesNotDetect_whileSignalIsPoor() {
        parser.parse(NeuroSkyUUID.ESENSE, esense(poorSignal = 200))
        warmUpParser()
        feedRaw(spike(THRESHOLD * 5))
        assertTrue(events.isEmpty())
    }

    @Test
    fun parser_rearmsWarmup_whenSignalRecovers() {
        parser.parse(NeuroSkyUUID.ESENSE, esense(poorSignal = 200))
        warmUpParser()
        parser.parse(NeuroSkyUUID.ESENSE, esense(poorSignal = 0))

        feedRaw(spike(THRESHOLD * 5))  // 신호 회복 직후 = 워밍업 중
        assertTrue(events.isEmpty())

        warmUpParser()
        feedRaw(spike(1_500))
        assertEquals(1, events.size)
    }

    @Test
    fun parser_reset_restartsSequence() {
        parser.parse(NeuroSkyUUID.ESENSE, esense(poorSignal = 0))
        warmUpParser()
        feedRaw(spike(1_500))

        parser.reset()
        parser.parse(NeuroSkyUUID.ESENSE, esense(poorSignal = 0))
        warmUpParser()
        feedRaw(spike(1_500))

        assertEquals(listOf(1, 1), events.map { it.sequence })
    }

    private companion object {
        const val THRESHOLD = 1_000
    }
}
