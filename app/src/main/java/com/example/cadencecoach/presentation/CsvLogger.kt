package com.example.cadencecoach.presentation

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class CsvLogger(context: Context) {

    // Changed filename to ensure we don't mix old data format with new format
    private val file = File(context.filesDir, "cadence_log_participants.csv")

    init {
        if (!file.exists()) {
            file.createNewFile()
            // Updated Header with new columns
            writeLine(
                "participant_id,session_id,timestamp,elapsed_time_sec," +
                        "cadence_spm,baseline_cadence,cadence_deviation,cadence_state"
            )
        }
    }

    fun log(
        participantId: Int,
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

        // Calculate change in cadence (Deviation)
        // e.g., if Baseline is 160 and Current is 165, deviation is +5
        val deviation = cadence - baselineCadence

        writeLine(
            "$participantId,$sessionId,$timestamp,$elapsedTimeSec," +
                    "$cadence,$baselineCadence,$deviation,$cadenceState"
        )
    }

    private fun writeLine(line: String) {
        FileWriter(file, true).use {
            it.append(line).append("\n")
        }
    }
}