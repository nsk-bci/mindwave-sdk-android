package com.neurosky.sdk.parser

import com.neurosky.sdk.NeuroSkyUUID
import com.neurosky.sdk.model.BrainWaveData
import java.util.UUID

/**
 * NeuroSky ThinkGear 패킷 파서.
 *
 * BLE 모드: [parse]에 characteristic UUID와 raw bytes를 전달.
 * BT Classic 모드: [parseByte]에 스트림 바이트를 1바이트씩 전달.
 */
class ThinkGearParser {

    // 현재 누적 중인 BrainWaveData (패킷 여러 개에 걸쳐 조립)
    private var current = BrainWaveData()

    // ── BLE 모드 ──────────────────────────────────────────────────────────────

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
        if (bytes.isEmpty()) return null
        return when (bytes[0].toInt() and 0xFF) {
            0xEA -> {
                if (bytes.size < 11) return null
                current = current.copy(
                    poorSignal = bytes[6].toInt() and 0xFF,
                    attention  = bytes[8].toInt() and 0xFF,
                    meditation = bytes[10].toInt() and 0xFF,
                    timestamp  = System.currentTimeMillis()
                )
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
            if (raw > 32768) raw -= 65536
            raw
        }
        current = current.copy(rawEeg = samples, timestamp = System.currentTimeMillis())
        return current
    }

    // ── BT Classic 모드 (ThinkGear Serial 프로토콜) ────────────────────────

    private enum class SerialState { SYNC1, SYNC2, PLENGTH, PAYLOAD, CHECKSUM }
    private var serialState = SerialState.SYNC1
    private var payloadLength = 0
    private val payloadBuffer = mutableListOf<Byte>()
    private var checksum = 0

    /**
     * ThinkGear Serial 프로토콜 스트림을 1바이트씩 처리.
     * 완전한 패킷이 조립되면 BrainWaveData를 반환.
     */
    fun parseByte(byte: Byte): BrainWaveData? {
        val b = byte.toInt() and 0xFF
        return when (serialState) {
            SerialState.SYNC1 -> {
                if (b == 0xAA) serialState = SerialState.SYNC2
                null
            }
            SerialState.SYNC2 -> {
                serialState = if (b == 0xAA) SerialState.PLENGTH else SerialState.SYNC1
                null
            }
            SerialState.PLENGTH -> {
                if (b == 0xAA) return null  // 연속 SYNC, 다시 PLENGTH 대기
                payloadLength = b
                payloadBuffer.clear()
                checksum = 0
                serialState = SerialState.PAYLOAD
                null
            }
            SerialState.PAYLOAD -> {
                payloadBuffer.add(byte)
                checksum = (checksum + b) and 0xFF
                if (payloadBuffer.size >= payloadLength) serialState = SerialState.CHECKSUM
                null
            }
            SerialState.CHECKSUM -> {
                serialState = SerialState.SYNC1
                val expected = (checksum xor 0xFF) and 0xFF
                if (b == expected) parseSerialPayload(payloadBuffer.toByteArray()) else null
            }
        }
    }

    private fun parseSerialPayload(payload: ByteArray): BrainWaveData? {
        var i = 0
        while (i < payload.size) {
            val code = payload[i++].toInt() and 0xFF
            when (code) {
                0x02 -> { // PoorSignal
                    current = current.copy(poorSignal = payload[i++].toInt() and 0xFF)
                }
                0x04 -> { // Attention
                    current = current.copy(attention = payload[i++].toInt() and 0xFF)
                }
                0x05 -> { // Meditation
                    current = current.copy(meditation = payload[i++].toInt() and 0xFF)
                }
                0x16 -> { // Blink strength
                    current = current.copy(eyeBlink = payload[i++].toInt() and 0xFF)
                }
                0x80 -> { // Raw EEG (2바이트)
                    val len = payload[i++].toInt() and 0xFF
                    if (i + len <= payload.size) {
                        var raw = ((payload[i].toInt() and 0xFF) shl 8) or (payload[i + 1].toInt() and 0xFF)
                        if (raw > 32768) raw -= 65536
                        current = current.copy(rawEeg = listOf(raw))
                        i += len
                    }
                }
                0x83 -> { // EEG Power (24바이트)
                    val len = payload[i++].toInt() and 0xFF
                    if (i + len <= payload.size && len >= 24) {
                        current = current.copy(
                            delta    = read3Bytes(payload, i),
                            theta    = read3Bytes(payload, i + 3),
                            lowAlpha = read3Bytes(payload, i + 6),
                            highAlpha= read3Bytes(payload, i + 9),
                            lowBeta  = read3Bytes(payload, i + 12),
                            highBeta = read3Bytes(payload, i + 15),
                            lowGamma = read3Bytes(payload, i + 18),
                            midGamma = read3Bytes(payload, i + 21)
                        )
                        i += len
                    }
                }
                else -> i++ // 알 수 없는 코드, 1바이트 skip
            }
        }
        return current.copy(timestamp = System.currentTimeMillis())
    }

    // ── 공통 유틸 ─────────────────────────────────────────────────────────────

    private fun read3Bytes(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 >= bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
               ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
               (bytes[offset + 2].toInt() and 0xFF)
    }
}
