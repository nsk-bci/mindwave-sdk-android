package com.neurosky.sample

import com.neurosky.sdk.model.BrainWaveData
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 뇌파 데이터를 CSV 파일에 기록하는 유틸리티. IO 스레드에서 호출해야 한다. */
class CsvRecorder(file: File) {

    private val writer: BufferedWriter = BufferedWriter(FileWriter(file))
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    init {
        writer.write(
            "timestamp,attention,meditation,signalQuality," +
            "delta,theta,lowAlpha,highAlpha,lowBeta,highBeta,lowGamma,midGamma," +
            "poor_signal_flag"
        )
        writer.newLine()
        writer.flush()
    }

    fun appendRow(data: BrainWaveData) {
        val time = timeFormat.format(Date(data.timestamp))
        val poorFlag = if (data.poorSignal > 50) 1 else 0
        writer.write(
            "$time,${data.attention},${data.meditation},${data.signalQuality}," +
            "${data.delta},${data.theta},${data.lowAlpha},${data.highAlpha}," +
            "${data.lowBeta},${data.highBeta},${data.lowGamma},${data.midGamma}," +
            "$poorFlag"
        )
        writer.newLine()
        writer.flush()
    }

    fun close() {
        runCatching { writer.close() }
    }
}
