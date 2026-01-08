package com.example.cadencecoach.presentation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.util.UUID

/**
 * MainActivity
 * ------------
 * This is the main entry point of the Wear OS app.
 * It controls:
 * - cadence measurement
 * - baseline calculation
 * - audio feedback
 * - CSV logging
 * - basic UI state
 */
class MainActivity : ComponentActivity() {

    // Measures steps and calculates cadence (steps per minute)
    private lateinit var cadenceSensor: CadenceSensor

    // Plays the beat and feedback sounds
    private lateinit var audioManager: AudioManager

    // Logs all data to a CSV file
    private lateinit var csvLogger: CsvLogger

    // Handler used to run repeated tasks with a delay (on main thread)
    private val handler = Handler(Looper.getMainLooper())

    // --- Runtime (non-UI) state ---

    // The baseline cadence calculated during calibration
    private var baselineCadence = 0

    // Last measured cadence value
    private var lastCadence = 0

    // Keeps track of the last cadence state (UP / DOWN / OK)
    private var lastCadenceState = "INIT"

    // Unique ID for this run session
    private var sessionId = ""

    // Timestamp when the run started
    private var sessionStartTime = 0L

    // --- UI state (Compose state) ---

    // True when baseline has been calculated
    private var baselineCalculated by mutableStateOf(false)

    // True once the user presses START
    private var started by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent the watch screen from turning off during the run
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize helper classes
        cadenceSensor = CadenceSensor(this)
        audioManager = AudioManager(this)
        csvLogger = CsvLogger(this)

        // Generate a unique session ID for this run
        sessionId = UUID.randomUUID().toString()

        // Set the UI using Jetpack Compose
        setContent {
            CadenceCoachUI(
                baselineCalculated = baselineCalculated,
                started = started,
                onStart = { startRun() }
            )
        }
    }

    /**
     * Called when the user presses START
     * Starts cadence measurement and baseline calibration
     */
    private fun startRun() {
        // Prevent starting twice
        if (started) return

        started = true

        // Start reading cadence from sensors
        cadenceSensor.start()

        // Store the time when the run starts
        sessionStartTime = System.currentTimeMillis()

        // --- BASELINE PHASE (30 seconds) ---
        handler.postDelayed({

            // Read cadence after 30s and store it as baseline
            baselineCadence = cadenceSensor.cadence
            baselineCalculated = true
            lastCadence = baselineCadence

            // Start playing the beat at baseline cadence
            audioManager.startBeat(baselineCadence)

            // Log baseline information to CSV
            csvLogger.log(
                sessionId,
                elapsedTimeSec = 0,
                cadence = baselineCadence,
                baselineCadence = baselineCadence,
                cadenceState = "BASELINE_SET"
            )

            // Start continuous cadence monitoring
            startMonitoring()

        }, 30_000) // 30 seconds baseline duration
    }

    /**
     * Continuously checks cadence every 2 seconds
     * Compares it to baseline and plays feedback if needed
     */
    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {

                // Current time and elapsed time since start
                val now = System.currentTimeMillis()
                val elapsedSec = (now - sessionStartTime) / 1000

                // Current cadence value
                val currentCadence = cadenceSensor.cadence

                // Define acceptable cadence range (+/- 5%)
                val lower = (baselineCadence * 0.95).toInt()
                val upper = (baselineCadence * 1.05).toInt()

                // Determine current cadence state
                val newState = when {
                    currentCadence < lower -> "DOWN"
                    currentCadence > upper -> "UP"
                    else -> "OK"
                }

                // Only play feedback if state changed
                if (newState != lastCadenceState) {
                    when (newState) {
                        "DOWN" -> audioManager.playCadenceDown()
                        "UP" -> audioManager.playCadenceUp()
                        "OK" -> audioManager.playCadenceRecovered()
                    }
                    lastCadenceState = newState
                }

                // Log current data to CSV
                csvLogger.log(
                    sessionId,
                    elapsedSec,
                    currentCadence,
                    baselineCadence,
                    lastCadenceState
                )

                // Store last cadence for next comparison
                lastCadence = currentCadence

                // Run again after 2 seconds
                handler.postDelayed(this, 2_000)
            }
        })
    }

    /**
     * Cleanup when the app is closed
     */
    override fun onDestroy() {
        super.onDestroy()
        cadenceSensor.stop()
        audioManager.stopBeat()
        handler.removeCallbacksAndMessages(null)
    }
}

/* ---------------- UI ---------------- */

/**
 * Simple Wear OS UI
 * Shows status text and a START button
 */
@Composable
fun CadenceCoachUI(
    baselineCalculated: Boolean,
    started: Boolean,
    onStart: () -> Unit
) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1C2D)), // Dark blue background
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // Status text depending on app state
                Text(
                    text = when {
                        !started -> "Press START"
                        !baselineCalculated -> "Measuring cadence…"
                        else -> "Cadence Coach Active"
                    },
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // START button (only visible before starting)
                if (!started) {
                    Box(
                        modifier = Modifier
                            .background(Color.DarkGray)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .clickable { onStart() }
                    ) {
                        Text(
                            text = "START",
                            color = Color.Cyan
                        )
                    }
                }
            }
        }
    }
}