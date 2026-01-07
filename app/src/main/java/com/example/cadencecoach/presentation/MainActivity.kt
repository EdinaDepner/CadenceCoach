package com.example.cadencecoach.presentation

import android.os.Bundle
import android.os.Handler
import android.os.Looper

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

/**
 * This is the entry point of the WearOs app
 * We dont show a user interface
 * the app runs in the background and
 * - measures cadence
 * - sets a baseline
 * - plays beats and feedback sounds
 */
class MainActivity : ComponentActivity() {
    //Object that measures steps and calculates cadence
    private lateinit var cadenceSensor: CadenceSensor

    //Object that plays beat and feedback sounds
    private lateinit var audioManager: AudioManager

    //Handler lets us run code after a delay or repeatedly
    private val handler = Handler(Looper.getMainLooper())

    //Baseline cadence (calculated after 1 min)
    private var baselineCadence = 0

    //Flag to check if baseline is ready
    private var baselineCalculated = false
    //Tracks last cadence state to avoid repeated feedback
    private var lastCadenceState = "INIT" //DOWN UP OK
    private var lastCadence = 0
    private var lastStateChangeTime = 0L

    //Detect recovery stability
    private var stableStartTime = 0L
    private val recoveryTimeMs = 5_000L //5 seconds

    private var lastMovementTime = 0L
    private val stopThresholdCadence = 15 //steps per minute
    private val stopTimeoutMs = 5_000L //5 seconds


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // keep screen awake
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        //Initialize sensor and audio helpers
        cadenceSensor = CadenceSensor(this)
        audioManager = AudioManager(this)

        //Start measuring steps immediately
        cadenceSensor.start()

        //UI
        setContent {
            CadenceCoachUI(
                text = if (!baselineCalculated)
                    "Measuring cadence"
                else
                    "Cadence Coach active"
            )
        }

        /**
         * first step is the baseline measurement
         * wait 60s, after that assume that the cadence reached is the runner's natural cadence
         */
        handler.postDelayed({
            baselineCadence = cadenceSensor.cadence
            baselineCalculated = true
            lastCadence = baselineCadence

            //Start playing the beatat the baseline cadence
            audioManager.startBeat(baselineCadence)
            startCadenceMonitoring()
        }, 30_000) //after 30seconds
    }
        /**
         * the second step is to continuously monitor the cadence
         * every 10 seconds we compare the current cadence to baseline
         * and give audio feedback if it is too low or too high
         */

        private fun startCadenceMonitoring(){
        handler.post(object: Runnable {
            override fun run(){
                val currentCadence = cadenceSensor.cadence
                val now = System.currentTimeMillis()
                val delta = currentCadence - lastCadence
                val dropDelta = -5 //sudden drop
                val riseDelta = 5 //sudden rise

                if(currentCadence >= stopThresholdCadence) {
                    lastMovementTime = now
                }

                if(lastMovementTime != 0L && now - lastMovementTime >= stopTimeoutMs){
                    audioManager.stopBeat()
                    lastCadenceState = "STOP"
                    stableStartTime = 0L
                }

                if(baselineCalculated && currentCadence >= stopThresholdCadence)
                {
                    //Allow small tolerance (+-5%) speeding up happens fast, slowing down happens gradually
                    val lowerThreshold = (baselineCadence * 0.95).toInt()
                    val upperThreshold = (baselineCadence * 1.05).toInt()

                when {
                    //too fast
                    currentCadence > upperThreshold || delta >= riseDelta -> {
                        //Runner is speeding up
                        if(lastCadenceState != "UP") {
                            audioManager.playCadenceUp()
                            lastCadenceState = "UP"
                            stableStartTime = 0L
                        }
                    }

                    //TOO SLOW
                    currentCadence < lowerThreshold || delta <= dropDelta -> {
                        //Runner is slowing down
                        if(lastCadenceState != "DOWN") {
                            audioManager.playCadenceDown()
                            lastCadenceState = "DOWN"
                            stableStartTime = 0L
                        }

                    }

                    else -> {

                        if( stableStartTime == 0L)
                        {
                            stableStartTime = now
                        }
                        if(now - stableStartTime >= recoveryTimeMs && lastCadenceState != "OK")
                        {
                            audioManager.playCadenceRecovered()
                            lastCadenceState = "OK"
                        }
                    }
                }
                }
                lastCadence = currentCadence
            //Run this check again after 2 seconds
            handler.postDelayed(this, 2_000)
        }
    })
}

    /**
     * Clean up when the app is closed
     */
    override fun onDestroy() {
        super.onDestroy()
        cadenceSensor.stop()
        audioManager.stopBeat()
        handler.removeCallbacksAndMessages(null)
    }
}

@Composable
fun CadenceCoachUI(text:String){
    MaterialTheme{
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B1C2D)), //DARK BLUE
            contentAlignment = Alignment.Center
        ){
            Text(text = text,
                textAlign = TextAlign.Center,
                color = Color.White
            )
        }
    }

}