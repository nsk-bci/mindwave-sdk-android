package com.neurosky.sdk

import java.util.UUID

object NeuroSkyUUID {
    // MindWave Mobile BLE Characteristics
    val ESENSE     = UUID.fromString("039afff8-2c94-11e3-9e06-0002a5d5c51b")
    val HANDSHAKE  = UUID.fromString("039affa0-2c94-11e3-9e06-0002a5d5c51b")
    val RAW_EEG    = UUID.fromString("039afff4-2c94-11e3-9e06-0002a5d5c51b")
    val CCCD       = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // BT Classic SPP
    val SPP        = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

    // Device Info (표준 BLE UUID)
    val MANUFACTURER   = UUID.fromString("00002a29-0000-1000-8000-00805f9b34fb")
    val MODEL_NUMBER   = UUID.fromString("00002a24-0000-1000-8000-00805f9b34fb")
    val SERIAL_NUMBER  = UUID.fromString("00002a25-0000-1000-8000-00805f9b34fb")
    val HW_REVISION    = UUID.fromString("00002a27-0000-1000-8000-00805f9b34fb")
    val FW_REVISION    = UUID.fromString("00002a26-0000-1000-8000-00805f9b34fb")
    val SW_REVISION    = UUID.fromString("00002a28-0000-1000-8000-00805f9b34fb")
}

object NeuroSkyCommand {
    const val START_RAW_EEG : Byte = 0x15.toByte()
    const val STOP_RAW_EEG  : Byte = 0x16.toByte()
    const val START_ESENSE  : Byte = 0x17.toByte()
    const val STOP_ESENSE   : Byte = 0x18.toByte()
    const val NOTCH_50HZ    : Byte = 0x1B.toByte()  // 중국/유럽
    const val NOTCH_60HZ    : Byte = 0x1C.toByte()  // 한국/미국
}
