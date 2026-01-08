package com.example.cadencecoach.presentation

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign

import androidx.wear.compose.material.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

import java.util.UUID

/**
 * Entry point of the Wear OS app
 * - measures cadence
 * - sets baseline
 * - plays beats and feedback
 * - logs everything to CSV
 */
class MainActivity : ComponentActivity() {

    // Cadence + audio
    private lateinit var cadenceSensor: CadenceSensor
    private lateinit var audioManager: AudioManager

    // CSV logger
    private lateinit var csvLogger: CsvLogger

    // Session info
    private val sessionId = UUID.randomUUID().toString()
    private var sessionStartTime = 0L

    // Handler
    private val handler = Handler(Looper.getMainLooper())

    // Baseline
    private var baselineCadence = 0
    private var baselineCalculated = false

    // Cadence state tracking
    private var lastCadenceState = "INIT" // UP / DOWN / OK / STOP
    private var lastCadence = 0
    private var stableStartTime = 0L

    // Recovery logic
    private val recoveryTimeMs = 5_000L

    // Stop detection
    private var lastMovementTime = 0L
    private val stopThresholdCadence = 15
    private val stopTimeoutMs = 5_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen awake
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Init helpers
        cadenceSensor = CadenceSensor(this)
        audioManager = AudioManager(this)
        csvLogger = CsvLogger(this)

        // Session start
        sessionStartTime = System.currentTimeMillis()

        cadenceSensor.start()

        // UI
        setContent {
            CadenceCoachUI(
                text = if (!baselineCalculated)
                    "Measuring cadence..."
                else
                    "Cadence Coach active"
            )
        }

        /**
         * BASELINE MEASUREMENT (30s)
         */
        handler.postDelayed({
            baselineCadence = cadenceSensor.cadence
            baselineCalculated = true
            lastCadence = baselineCadence

            audioManager.startBeat(baselineCadence)
            startCadenceMonitoring()

        }, 30_000)
    }

    /**
     * CONTINUOUS MONITORING
     */
    private fun startCadenceMonitoring() {
        handler.post(object : Runnable {
            override fun run() {

                val currentCadence = cadenceSensor.cadence
                val now = System.currentTimeMillis()
                val elapsedSec = (now - sessionStartTime) / 1000

                val delta = currentCadence - lastCadence
                val dropDelta = -5
                val riseDelta = 5

                // Detect movement
                if (currentCadence >= stopThresholdCadence) {
                    lastMovementTime = now
                }

                // Detect stop
                if (lastMovementTime != 0L && now - lastMovementTime >= stopTimeoutMs) {
                    audioManager.stopBeat()
                    lastCadenceState = "STOP"
                    stableStartTime = 0L
                }

                if (baselineCalculated && currentCadence >= stopThresholdCadence) {

                    val lowerThreshold = (baselineCadence * 0.95).toInt()
                    val upperThreshold = (baselineCadence * 1.05).toInt()

                    when {
                        // SPEEDING UP
                        currentCadence > upperThreshold || delta >= riseDelta -> {
                            if (lastCadenceState != "UP") {
                                audioManager.playCadenceUp()
                                lastCadenceState = "UP"
                                stableStartTime = 0L
                            }
                        }

                        // SLOWING DOWN
                        currentCadence < lowerThreshold || delta <= dropDelta -> {
                            if (lastCadenceState != "DOWN") {
                                audioManager.playCadenceDown()
                                lastCadenceState = "DOWN"
                                stableStartTime = 0L
                            }
                        }

                        // STABLE → RECOVERED
                        else -> {
                            if (stableStartTime == 0L) {
                                stableStartTime = now
                            }
                            if (now - stableStartTime >= recoveryTimeMs &&
                                lastCadenceState != "OK"
                            ) {
                                audioManager.playCadenceRecovered()
                                lastCadenceState = "OK"
                            }
                        }
                    }
                }

                // -------- CSV LOGGING (EVERY TICK) --------
                csvLogger.log(
                    sessionId = sessionId,
                    elapsedTimeSec = elapsedSec,
                    cadence = currentCadence,
                    baselineCadence = baselineCadence,
                    cadenceState = lastCadenceState
                )
                // -----------------------------------------

                lastCadence = currentCadence

                handler.postDelayed(this, 2_000)
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        cadenceSensor.stop()
        audioManager.stopBeat()
        handler.removeCallbacksAndMessages(null)
    }
}

/**
 * SIMPLE UI
 */
@Composable
fun CadenceCoachUI(text: String) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1C2D)), // dark blue
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                textAlign = TextAlign.Center,
                color = Color.White
            )
        }
    }
}