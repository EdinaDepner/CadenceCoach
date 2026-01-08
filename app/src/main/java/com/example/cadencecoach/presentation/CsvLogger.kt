package com.example.cadencecoach.presentation

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class CsvLogger(private val context: Context) {

    private val fileName = "cadence_log.csv"
    private val file: File = File(context.filesDir, fileName)

    init {
        // Write header if file does not exist yet
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
        FileWriter(file, true).use { writer ->
            writer.append(line).append("\n")
        }
    }
}