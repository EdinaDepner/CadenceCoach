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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var cadenceSensor: CadenceSensor
    private lateinit var audioManager: AudioManager
    private lateinit var csvLogger: CsvLogger

    private val handler = Handler(Looper.getMainLooper())

    // Run Logic State
    private var baselineCadence = 0
    private var lastCadenceState = "INIT"
    private var sessionId = ""
    private var sessionStartTime = 0L
    private var stableStartTime = 0L
    private val recoveryDelayMs = 5_000L

    // UI State
    private var baselineCalculated by mutableStateOf(false)
    private var started by mutableStateOf(false)

    // NEW: Participant ID State
    private var participantId by mutableStateOf(1)
    private var isIdConfirmed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        cadenceSensor = CadenceSensor(this)
        audioManager = AudioManager(this)
        csvLogger = CsvLogger(this)
        sessionId = UUID.randomUUID().toString()

        setContent {
            CadenceCoachUI(
                participantId = participantId,
                isIdConfirmed = isIdConfirmed,
                baselineCalculated = baselineCalculated,
                started = started,
                onIdChange = { newId -> participantId = newId },
                onIdConfirm = { isIdConfirmed = true },
                onStart = { startRun() },
                onStop = { stopRun() }
            )
        }
    }

    private fun startRun() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (started) return

        started = true
        cadenceSensor.start()
        sessionStartTime = System.currentTimeMillis()

        // BASELINE (30 seconds)
        handler.postDelayed({
            if (!started) return@postDelayed
            baselineCadence = cadenceSensor.cadence
            baselineCalculated = true
            lastCadenceState = "OK"
            audioManager.startBeat(baselineCadence)

            // Log Baseline Set
            csvLogger.log(
                participantId, // Pass ID
                sessionId,
                0,
                baselineCadence,
                baselineCadence,
                "BASELINE_SET"
            )

            startMonitoring()
        }, 30_000)
    }

    private fun stopRun() {
        started = false
        baselineCalculated = false
        isIdConfirmed = false // Reset so we can enter a new ID for the next person
        lastCadenceState = "INIT"
        stableStartTime = 0L

        cadenceSensor.stop()
        audioManager.stopBeat()
        handler.removeCallbacksAndMessages(null)
        sessionId = UUID.randomUUID().toString()
    }

    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                if (!started) return

                val now = System.currentTimeMillis()
                val elapsedSec = (now - sessionStartTime) / 1000
                val currentCadence = cadenceSensor.cadence
                val lower = (baselineCadence * 0.95).toInt()
                val upper = (baselineCadence * 1.05).toInt()

                val detectedState = when {
                    currentCadence < lower -> "DOWN"
                    currentCadence > upper -> "UP"
                    else -> "OK"
                }

                // Audio Logic
                when (detectedState) {
                    "UP", "DOWN" -> {
                        if (detectedState != lastCadenceState) {
                            if (detectedState == "UP") audioManager.playCadenceUp()
                            else audioManager.playCadenceDown()
                            lastCadenceState = detectedState
                        }
                        stableStartTime = 0L
                    }
                    "OK" -> {
                        if ((lastCadenceState == "UP" || lastCadenceState == "DOWN")) {
                            if (stableStartTime == 0L) {
                                stableStartTime = now
                            } else if (now - stableStartTime >= recoveryDelayMs) {
                                audioManager.playCadenceRecovered()
                                lastCadenceState = "OK"
                                stableStartTime = 0L
                            }
                        }
                    }
                }

                // Log with Participant ID
                csvLogger.log(
                    participantId,
                    sessionId,
                    elapsedSec,
                    currentCadence,
                    baselineCadence,
                    lastCadenceState
                )

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

/* ---------------- UPDATED UI ---------------- */

@Composable
fun CadenceCoachUI(
    participantId: Int,
    isIdConfirmed: Boolean,
    baselineCalculated: Boolean,
    started: Boolean,
    onIdChange: (Int) -> Unit,
    onIdConfirm: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1C2D)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // SCENE 1: Participant ID Selection
                if (!isIdConfirmed) {
                    Text("Participant ID", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Decrease Button
                        Button(
                            onClick = { if (participantId > 1) onIdChange(participantId - 1) },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("-", color = Color.White)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // ID Display
                        Text(
                            text = "$participantId",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        // Increase Button
                        Button(
                            onClick = { onIdChange(participantId + 1) },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("+", color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Confirm Button
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF005085), CircleShape)
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .clickable { onIdConfirm() }
                    ) {
                        Text("CONFIRM", color = Color.White)
                    }
                }

                // SCENE 2: Main Run UI (Only shown after ID is confirmed)
                else {
                    Text(
                        text = when {
                            !started -> "ID: $participantId\nPress START"
                            !baselineCalculated -> "Measuring cadence…"
                            else -> "Cadence Coach Active"
                        },
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!started) {
                        Box(
                            modifier = Modifier
                                .background(Color.DarkGray)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { onStart() }
                        ) {
                            Text("START", color = Color.Cyan)
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color.DarkGray)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { onStop() }
                        ) {
                            Text("STOP", color = Color.Red)
                        }
                    }
                }
            }
        }
    }
}