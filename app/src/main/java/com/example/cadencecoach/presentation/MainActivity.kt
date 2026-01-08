package com.example.cadencecoach.presentation

// --- Imports ---
// Android core libraries for managing the Activity, Bundles (state), and Handlers (timing).
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager

// Activity libraries for modern Android development (Compose integration).
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

// Compose Foundation libraries for layouts (Box, Column, Row), gestures (clickable), and drawing (background).
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape

// Compose Runtime libraries to manage state (mutableStateOf) that triggers UI updates.
import androidx.compose.runtime.*

// Compose UI libraries for alignment, styling, colors, and fonts.
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Wear OS specific Material Design components (Button, Text).
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

// Utility for generating unique IDs for the CSV logs.
import java.util.UUID

/**
 * MainActivity
 * ============
 * The main entry point for the application.
 * It inherits from ComponentActivity to support Jetpack Compose on Wear OS.
 *
 * Responsibilities:
 * 1. Initialize helper classes (Sensor, Audio, Logger).
 * 2. Manage the application state (Running, Stopped, Baseline Calculation).
 * 3. Run the main loop that checks cadence and triggers audio feedback.
 * 4. Render the UI using Jetpack Compose.
 */
class MainActivity : ComponentActivity() {

    // --- Helper Classes ---

    // CadenceSensor: Handles the hardware step detector logic.
    // 'lateinit' means we promise to initialize it in onCreate().
    private lateinit var cadenceSensor: CadenceSensor

    // AudioManager: Handles playing the beat and feedback sounds (Up/Down/Recovered).
    private lateinit var audioManager: AudioManager

    // CsvLogger: Handles writing data rows to the internal storage CSV file.
    private lateinit var csvLogger: CsvLogger

    // --- Timing Handler ---

    // Create a Handler attached to the Main Looper (UI thread).
    // We use this to schedule code to run in the future (e.g., "Check cadence in 2 seconds").
    private val handler = Handler(Looper.getMainLooper())

    // --- Run Logic Variables ---

    // Stores the target cadence (Steps Per Minute) calculated during the first 30 seconds.
    // Init to 0 until calculated.
    private var baselineCadence = 0

    // Memorizes the last feedback state ("INIT", "UP", "DOWN", "OK").
    // We use this to prevent the watch from spamming "Cadence Up" every 2 seconds if you are already UP.
    private var lastCadenceState = "INIT"

    // A unique string (UUID) for the current run.
    // This allows us to group rows in the CSV file by specific run sessions.
    private var sessionId = ""

    // The timestamp (in milliseconds) when the START button was pressed.
    // Used to calculate "Elapsed Time" for the logs.
    private var sessionStartTime = 0L

    // Tracks the exact time the user returned to the "OK" zone.
    // If 0L, it means the user is currently NOT stable (deviating).
    private var stableStartTime = 0L

    // Configuration: How many milliseconds the user must stay "OK" before hearing "Recovered".
    private val recoveryDelayMs = 5_000L

    // --- UI State Variables (Compose) ---
    // These use 'mutableStateOf'. When we change these variables, the Screen automatically redraws.

    // Becomes true 30 seconds after start, indicating baseline is set and feedback is active.
    private var baselineCalculated by mutableStateOf(false)

    // Becomes true when user presses START, false when they press STOP.
    private var started by mutableStateOf(false)

    // --- Participant ID Variables ---

    // The integer ID for the person currently doing the experiment. Defaults to 1.
    private var participantId by mutableStateOf(1)

    // Determines which screen to show:
    // False = Show "Participant ID Selector" screen.
    // True = Show "Main Run" screen.
    private var isIdConfirmed by mutableStateOf(false)

    /**
     * onCreate
     * --------
     * Called by Android when the app is launched.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Important for fitness apps: Keeps the watch screen ON so the user can always see status.
        // Without this, the watch would go to sleep after a few seconds.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Initialize our helper objects passing 'this' (context) so they can access system services.
        cadenceSensor = CadenceSensor(this)
        audioManager = AudioManager(this)
        csvLogger = CsvLogger(this)

        // Generate a random Session ID so we have one ready immediately.
        sessionId = UUID.randomUUID().toString()

        // Initialize the UI using Jetpack Compose.
        setContent {
            // Call our UI Composable function defined at the bottom of the file.
            CadenceCoachUI(
                // Pass current state values to the UI
                participantId = participantId,
                isIdConfirmed = isIdConfirmed,
                baselineCalculated = baselineCalculated,
                started = started,

                // Pass functions (callbacks) that the UI can call when buttons are clicked.

                // When +/- is clicked, update the participantId variable.
                onIdChange = { newId -> participantId = newId },

                // When Confirm is clicked, set isIdConfirmed to true (switches screens).
                onIdConfirm = { isIdConfirmed = true },

                // When Start is clicked, run the startRun() logic.
                onStart = { startRun() },

                // When Stop is clicked, run the stopRun() logic.
                onStop = { stopRun() }
            )
        }
    }

    /**
     * startRun
     * --------
     * Logic executed when the user presses the "START" button.
     */
    private fun startRun() {
        // Re-apply the flag to keep screen on (defensive coding).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // If already started, do nothing (prevents double-clicks).
        if (started) return

        // Set state to true -> UI updates to show "STOP" button.
        started = true

        // Tell the sensor class to start listening for steps.
        cadenceSensor.start()

        // Record the current system time as the start time.
        sessionStartTime = System.currentTimeMillis()

        // --- Schedule Baseline Logic ---
        // We use handler.postDelayed to run this block of code 30,000ms (30s) in the future.
        handler.postDelayed({

            // Check if the user pressed STOP during the 30s wait. If so, cancel baseline.
            if (!started) return@postDelayed

            // 30s have passed. Read the current cadence and lock it as the baseline.
            baselineCadence = cadenceSensor.cadence

            // Update state -> UI text changes from "Measuring..." to "Active".
            baselineCalculated = true

            // Reset state trackers.
            lastCadenceState = "OK"

            // Start the metronome beat at the calculated baseline pace.
            audioManager.startBeat(baselineCadence)

            // Log a special row to the CSV indicating the baseline was just set.
            csvLogger.log(
                participantId,      // The user ID selected earlier
                sessionId,          // The current run ID
                0,                  // Elapsed time (marked as 0 for the "main" part of run)
                baselineCadence,    // Current cadence
                baselineCadence,    // Target cadence
                "BASELINE_SET"      // Event tag
            )

            // Immediately trigger the monitoring loop to start checking performance.
            startMonitoring()

        }, 30_000) // Delay value: 30,000 milliseconds
    }

    /**
     * stopRun
     * -------
     * Logic executed when the user presses "STOP".
     */
    private fun stopRun() {
        // Update flags to stop the UI and logic loops.
        started = false
        baselineCalculated = false

        // Reset the ID confirmation so the app goes back to the "Select ID" screen for the next person.
        isIdConfirmed = false

        // Reset logic variables.
        lastCadenceState = "INIT"
        stableStartTime = 0L

        // Stop the hardware sensor to save battery.
        cadenceSensor.stop()

        // Stop the audio beat.
        audioManager.stopBeat()

        // Important: Remove any pending tasks.
        // If the user stops at 10s, this cancels the 30s baseline timer so it doesn't fire later.
        handler.removeCallbacksAndMessages(null)

        // Generate a fresh Session ID for the next run.
        sessionId = UUID.randomUUID().toString()
    }

    /**
     * startMonitoring
     * ---------------
     * The core loop. It runs a task, then schedules itself to run again in 2 seconds.
     */
    private fun startMonitoring() {
        handler.post(object : Runnable {
            override fun run() {
                // If the run was stopped, stop this loop.
                if (!started) return

                // Get current time.
                val now = System.currentTimeMillis()

                // Calculate elapsed seconds since START was pressed.
                val elapsedSec = (now - sessionStartTime) / 1000

                // Get the latest cadence value from our sensor class.
                val currentCadence = cadenceSensor.cadence

                // Calculate the "Allowed Zone".
                // Lower limit is 95% of baseline.
                val lower = (baselineCadence * 0.95).toInt()
                // Upper limit is 105% of baseline.
                val upper = (baselineCadence * 1.05).toInt()

                // specific logic to determine the current state (DOWN, UP, or OK).
                val detectedState = when {
                    currentCadence < lower -> "DOWN" // Too slow
                    currentCadence > upper -> "UP"   // Too fast
                    else -> "OK"                     // Just right
                }

                // --- Audio Logic Block ---
                when (detectedState) {

                    // Case: User is running too fast or too slow.
                    "UP", "DOWN" -> {
                        // Check if this is a NEW state (e.g., changed from OK to UP).
                        if (detectedState != lastCadenceState) {

                            // Play the appropriate correction sound.
                            if (detectedState == "UP") audioManager.playCadenceUp()
                            else audioManager.playCadenceDown()

                            // Remember this state so we don't play the sound again next loop.
                            lastCadenceState = detectedState
                        }

                        // User is deviating, so reset the "Stability Timer" to 0.
                        stableStartTime = 0L
                    }

                    // Case: User is running at the correct speed.
                    "OK" -> {
                        // We only care about "recovery" if they were previously WRONG (UP or DOWN).
                        if ((lastCadenceState == "UP" || lastCadenceState == "DOWN")) {

                            // If the timer hasn't started yet, mark the current time.
                            if (stableStartTime == 0L) {
                                stableStartTime = now
                            }
                            // If timer IS started, check if enough time (recoveryDelayMs) has passed.
                            else if (now - stableStartTime >= recoveryDelayMs) {

                                // Success! User held the pace for 5 seconds.
                                // Play the "Good Job / Recovered" sound.
                                audioManager.playCadenceRecovered()

                                // Update state to OK so we stop checking recovery.
                                lastCadenceState = "OK"

                                // Reset timer.
                                stableStartTime = 0L
                            }
                        }
                    }
                }

                // Write the current data point to the CSV file.
                csvLogger.log(
                    participantId,
                    sessionId,
                    elapsedSec,
                    currentCadence,
                    baselineCadence,
                    lastCadenceState
                )

                // Recursion-like loop: Schedule this same code to run again in 2000ms (2 seconds).
                handler.postDelayed(this, 2_000)
            }
        })
    }

    /**
     * onDestroy
     * ---------
     * Called when the app is completely closed/killed by the OS.
     * We must clean up resources here to prevent memory leaks or battery drain.
     */
    override fun onDestroy() {
        super.onDestroy()
        cadenceSensor.stop()
        audioManager.stopBeat()
        handler.removeCallbacksAndMessages(null)
    }
}

/* ---------------- UI SECTION ---------------- */

/**
 * CadenceCoachUI (Composable Function)
 * ------------------------------------
 * Defines the visual layout of the app.
 * * Parameters:
 * - participantId: The current ID number to display.
 * - isIdConfirmed: Boolean flag to switch between ID screen and Run screen.
 * - baselineCalculated: Boolean flag to change text from "Measuring" to "Active".
 * - started: Boolean flag to toggle START/STOP buttons.
 * - onIdChange, onIdConfirm, onStart, onStop: Functions to call when buttons are clicked.
 */
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
    // Wrap everything in MaterialTheme for standard Wear OS styling.
    MaterialTheme {

        // Box is a container that allows stacking. We use it here to center content on screen.
        Box(
            modifier = Modifier
                .fillMaxSize() // Take up the whole screen
                .background(Color(0xFF0B1C2D)), // Set background color to Dark Blue
            contentAlignment = Alignment.Center // Force content to the middle
        ) {

            // Column stacks items vertically (top to bottom).
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                // --- LOGIC: WHICH SCREEN TO SHOW? ---

                // SCREEN 1: Participant ID Selection
                // Only shown if ID is NOT confirmed yet.
                if (!isIdConfirmed) {

                    // Small Label
                    Text("Participant ID", color = Color.Gray, fontSize = 12.sp)

                    // Spacer adds vertical gap
                    Spacer(modifier = Modifier.height(8.dp))

                    // Row stacks items horizontally: [-] [ ID ] [+]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {

                        // Decrease Button (-)
                        Button(
                            // Logic: Decrease ID but stop at 1.
                            onClick = { if (participantId > 1) onIdChange(participantId - 1) },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
                            modifier = Modifier.size(40.dp) // Make button round and small
                        ) {
                            Text("-", color = Color.White)
                        }

                        // Spacer for gap between button and number
                        Spacer(modifier = Modifier.width(16.dp))

                        // The ID Number Display
                        Text(
                            text = "$participantId",
                            fontSize = 30.sp, // Large font
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )

                        // Spacer
                        Spacer(modifier = Modifier.width(16.dp))

                        // Increase Button (+)
                        Button(
                            // Logic: Increase ID.
                            onClick = { onIdChange(participantId + 1) },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Text("+", color = Color.White)
                        }
                    }

                    // Spacer
                    Spacer(modifier = Modifier.height(16.dp))

                    // CONFIRM Button
                    // A custom styled Box acting as a button.
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF005085), CircleShape) // Blue rounded shape
                            .padding(horizontal = 20.dp, vertical = 10.dp) // Internal padding
                            .clickable { onIdConfirm() } // Makes it tappable
                    ) {
                        Text("CONFIRM", color = Color.White)
                    }
                }

                // SCREEN 2: Main Run Interface
                // Shown only AFTER ID is confirmed.
                else {

                    // Dynamic Status Text
                    Text(
                        text = when {
                            // If not running -> Show ID and instruction
                            !started -> "ID: $participantId\nPress START"
                            // If running but < 30s -> Show Measuring
                            !baselineCalculated -> "Measuring cadence…"
                            // If running > 30s -> Show Active
                            else -> "Cadence Coach Active"
                        },
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )

                    // Spacer
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dynamic Button: Shows START or STOP depending on state.
                    if (!started) {
                        // START Button Design
                        Box(
                            modifier = Modifier
                                .background(Color.DarkGray)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { onStart() }
                        ) {
                            Text("START", color = Color.Cyan)
                        }
                    } else {
                        // STOP Button Design
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