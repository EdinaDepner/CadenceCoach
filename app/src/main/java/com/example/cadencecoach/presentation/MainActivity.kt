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

    //UI state
    private val uiText = mutableStateOf("Measuring baseline...")

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        //Initialize sensor and audio helpers
        cadenceSensor= CadenceSensor(this)
        audioManager = AudioManager(this)

        //Start measuring steps immediately
        cadenceSensor.start()

        /**
         * first step is the baseline measurement
         * wait 60s, after that assume that the cadence reached is the runner's natural cadence
         */
        handler.postDelayed({
            baselineCadence = cadenceSensor.cadence
            baselineCalculated = true
            uiText.value="Cadence coaching active"

            //Start playing the beatat the baseline cadence
            audioManager.startBeat(baselineCadence)
        },60_000) //60seconds

        /**
         * the second step is to continuously monitor the cadence
         * every 10 seconds we compare the current cadence to baseline
         * and give audio feedback if it is too low or too high
         */
        handler.postDelayed(object: Runnable {
            override fun run(){
                if(baselineCalculated)
                {
                    val currentCadence = cadenceSensor.cadence

                    //Allow small tolerance (+-5%)
                    val lowerThreshold = (baselineCadence * 0.95).toInt()
                    val upperThreshold = (baselineCadence * 1.05).toInt()

                    when{
                        currentCadence < lowerThreshold -> {
                            //Runner is slowing down
                            audioManager.playCadenceDown()
                        }

                        currentCadence > upperThreshold -> {
                            //Runner is speeding up
                            audioManager.playCadenceUp()
                        }
                        //if cadence within range, no extra feedback for now
                    }
                }
                //Run this check again after 10 seconds
                handler.postDelayed(this, 10_000)
            }
        }, 10_000)
        //ui
        setContent {
            CadenceCoachUI(uiText.value)
        }
    }

    /**
     * Clean up when the app is closed
     */
    override fun onDestroy() {
        super.onDestroy()
        cadenceSensor.stop()
        audioManager.stopBeat()
    }
}

@Composable
fun CadenceCoachUI(text:String){
    MaterialTheme{
        Box(modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ){
            Text(text = text,
                textAlign = TextAlign.Center
            )
        }
    }

}