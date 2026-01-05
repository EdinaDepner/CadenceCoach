package com.example.cadencecoach.presentation
import android.os.Bundle
import androidx.activity.ComponentActivity
import android.os.Handler
import android.os.Looper

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