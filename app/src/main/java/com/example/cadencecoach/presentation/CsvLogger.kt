package com.example.cadencecoach.presentation

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class CsvLogger(context: Context) {

    private val file = File(context.filesDir, "cadence_log.csv")

    init {
        if (!file.exists()) {
            file.createNewFile()
            writeLine(
                "session_id,timestamp,elapsed_time_sec," +
                        "cadence_spm,baseline_cadence,cadence_state"
            )
        }
    }

    fun log(
        sessionId: String,
        elapsedTimeSec: Long,
        cadence: Int,
        baselineCadence: Int,
        cadenceState: String
    ) {
        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.US
        ).format(Date())

        writeLine(
            "$sessionId,$timestamp,$elapsedTimeSec," +
                    "$cadence,$baselineCadence,$cadenceState"
        )
    }

    private fun writeLine(line: String) {
        FileWriter(file, true).use {
            it.append(line).append("\n")
        }
    }
}