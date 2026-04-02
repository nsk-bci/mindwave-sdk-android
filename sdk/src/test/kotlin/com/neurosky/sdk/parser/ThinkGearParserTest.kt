package com.neurosky.sdk.parser

import com.neurosky.sdk.NeuroSkyUUID
import com.neurosky.sdk.model.SignalQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class ThinkGearParserTest {

    private val parser = ThinkGearParser()

    // ── BLE Mode: ESense (0xEA) ──────────────────────────────────────────────

    @Test
    fun parseEsense_0xEA_returnsAttentionMeditationPoorSignal() {
        val bytes = ByteArray(11)
        bytes[0]  = 0xEA.toByte()
        bytes[6]  = 10    // PoorSignal
        bytes[8]  = 75    // Attention
        bytes[10] = 55    // Meditation

        val result = parser.parse(NeuroSkyUUID.ESENSE, bytes)

        assertNotNull(result)
        assertEquals(10, result!!.poorSignal)
        assertEquals(75, result.attention)
        assertEquals(55, result.meditation)
    }

    @Test
    fun parseEsense_0xEA_tooShort_returnsNull() {
        val bytes = ByteArray(5)
        bytes[0] = 0xEA.toByte()

        val result = parser.parse(NeuroSkyUUID.ESENSE, bytes)

        assertNull(result)
    }

    // ── BLE Mode: ESense (0xEB) ──────────────────────────────────────────────

    @Test
    fun parseEsense_0xEB_returnsDeltaThetaAlpha() {
        val bytes = ByteArray(20)
        bytes[0] = 0xEB.toByte()
        // Delta at bytes[5~7] = 256
        bytes[5] = 0x00; bytes[6] = 0x01; bytes[7] = 0x00
        // Theta at bytes[9~11] = 512
        bytes[9] = 0x00; bytes[10] = 0x02; bytes[11] = 0x00
        // LowAlpha at bytes[13~15] = 768
        bytes[13] = 0x00; bytes[14] = 0x03; bytes[15] = 0x00
        // HighAlpha at bytes[17~19] = 1024
        bytes[17] = 0x00; bytes[18] = 0x04; bytes[19] = 0x00

        // 0xEB alone returns null — emit waits for 0xEC
        val eb = parser.parse(NeuroSkyUUID.ESENSE, bytes)
        assertNull(eb)

        // Trigger 0xEC to flush
        val ec = ByteArray(20).also { it[0] = 0xEC.toByte() }
        val result = parser.parse(NeuroSkyUUID.ESENSE, ec)

        assertNotNull(result)
        assertEquals(256,  result!!.delta)
        assertEquals(512,  result.theta)
        assertEquals(768,  result.lowAlpha)
        assertEquals(1024, result.highAlpha)
    }

    // ── BLE Mode: ESense (0xEC) ──────────────────────────────────────────────

    @Test
    fun parseEsense_0xEC_returnsBetaGamma() {
        val bytes = ByteArray(20)
        bytes[0]  = 0xEC.toByte()
        bytes[5]  = 0x00; bytes[6]  = 0x05; bytes[7]  = 0x00  // LowBeta  = 1280
        bytes[9]  = 0x00; bytes[10] = 0x06; bytes[11] = 0x00  // HighBeta = 1536
        bytes[13] = 0x00; bytes[14] = 0x07; bytes[15] = 0x00  // LowGamma = 1792
        bytes[17] = 0x00; bytes[18] = 0x08; bytes[19] = 0x00  // MidGamma = 2048

        val result = parser.parse(NeuroSkyUUID.ESENSE, bytes)

        assertNotNull(result)
        assertEquals(1280, result!!.lowBeta)
        assertEquals(1536, result.highBeta)
        assertEquals(1792, result.lowGamma)
        assertEquals(2048, result.midGamma)
    }

    // ── BLE Mode: RawEEG ─────────────────────────────────────────────────────

    @Test
    fun parseRawEeg_returns10Samples() {
        val bytes = ByteArray(20)
        // Sample 0: 0x01 0x00 = 256
        bytes[0] = 0x01; bytes[1] = 0x00

        val result = parser.parse(NeuroSkyUUID.RAW_EEG, bytes)

        assertNotNull(result)
        assertEquals(10,  result!!.rawEeg.size)
        assertEquals(256, result.rawEeg[0])
    }

    @Test
    fun parseRawEeg_signedConversion_negativeValue() {
        val bytes = ByteArray(20)
        // 0x80 0x01 = 32769 → > 32768 → 32769 - 65536 = -32767
        bytes[0] = 0x80.toByte(); bytes[1] = 0x01

        val result = parser.parse(NeuroSkyUUID.RAW_EEG, bytes)

        assertNotNull(result)
        assertEquals(-32767, result!!.rawEeg[0])
    }

    @Test
    fun parseRawEeg_tooShort_returnsNull() {
        val result = parser.parse(NeuroSkyUUID.RAW_EEG, ByteArray(10))
        assertNull(result)
    }

    // ── BLE Mode: Unknown UUID ────────────────────────────────────────────────

    @Test
    fun parse_unknownUuid_returnsNull() {
        val result = parser.parse(UUID.randomUUID(), ByteArray(20))
        assertNull(result)
    }

    // ── BT Classic Mode ──────────────────────────────────────────────────────

    @Test
    fun parseByte_validPacket_returnsAttentionMeditation() {
        val attention: Byte  = 80
        val meditation: Byte = 60
        val payload = byteArrayOf(0x04, attention, 0x05, meditation)
        val checksum = ((payload.sumOf { it.toInt() and 0xFF } and 0xFF) xor 0xFF) and 0xFF

        val packet = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(),
            payload.size.toByte(),
            0x04, attention,
            0x05, meditation,
            checksum.toByte()
        )

        val freshParser = ThinkGearParser()
        var result = null as com.neurosky.sdk.model.BrainWaveData?
        for (b in packet) result = freshParser.parseByte(b) ?: result

        assertNotNull(result)
        assertEquals(80, result!!.attention)
        assertEquals(60, result.meditation)
    }

    @Test
    fun parseByte_invalidChecksum_returnsNull() {
        val packet = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(),
            2,
            0x04, 80,
            0xFF.toByte()  // wrong checksum
        )

        val freshParser = ThinkGearParser()
        var result = null as com.neurosky.sdk.model.BrainWaveData?
        for (b in packet) result = freshParser.parseByte(b) ?: result

        assertNull(result)
    }

    @Test
    fun parseByte_poorSignalCode_returnsPoorSignal() {
        val poorSignal: Byte = 150.toByte()
        val payload = byteArrayOf(0x02, poorSignal)
        val checksum = ((payload.sumOf { it.toInt() and 0xFF } and 0xFF) xor 0xFF) and 0xFF

        val packet = byteArrayOf(
            0xAA.toByte(), 0xAA.toByte(),
            payload.size.toByte(),
            0x02, poorSignal,
            checksum.toByte()
        )

        val freshParser = ThinkGearParser()
        var result = null as com.neurosky.sdk.model.BrainWaveData?
        for (b in packet) result = freshParser.parseByte(b) ?: result

        assertNotNull(result)
        assertEquals(150, result!!.poorSignal)
    }

    // ── SignalQuality ─────────────────────────────────────────────────────────

    @Test
    fun signalQuality_200_isNoSignal() {
        val bytes = ByteArray(11).also { it[0] = 0xEA.toByte(); it[6] = 200.toByte() }
        assertEquals(SignalQuality.NO_SIGNAL, parser.parse(NeuroSkyUUID.ESENSE, bytes)!!.signalQuality)
    }

    @Test
    fun signalQuality_100_isPoor() {
        val bytes = ByteArray(11).also { it[0] = 0xEA.toByte(); it[6] = 100 }
        assertEquals(SignalQuality.POOR, parser.parse(NeuroSkyUUID.ESENSE, bytes)!!.signalQuality)
    }

    @Test
    fun signalQuality_25_isFair() {
        val bytes = ByteArray(11).also { it[0] = 0xEA.toByte(); it[6] = 25 }
        assertEquals(SignalQuality.FAIR, parser.parse(NeuroSkyUUID.ESENSE, bytes)!!.signalQuality)
    }

    @Test
    fun signalQuality_0_isGood() {
        val bytes = ByteArray(11).also { it[0] = 0xEA.toByte(); it[6] = 0 }
        assertEquals(SignalQuality.GOOD, parser.parse(NeuroSkyUUID.ESENSE, bytes)!!.signalQuality)
    }
}
