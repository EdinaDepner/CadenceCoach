package com.example.cadencecoach.presentation
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import kotlin.random.Random
/**
 * Uses the STEP_DETECTOR sensor to count steps
 * Cadence = steps per minute
 * Test_mode = true -> fake cadence (emulator)
 * Test_mode = false -> real cadence(watch)
 */
class CadenceSensor(context: Context): SensorEventListener {

    //Access the Android's sensor system
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    //Step detector sensor (fires once per step)
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    //Nr of steps detected
    private var stepCount = 0

    //Time when measurement started
    private var startTime = 0L

    //Publicly readable cadence value
    var cadence = 0
        private set

    /**
     * Start listening for steps - THIS NEEDS TO BE COMMENTED FOR TESTING
     */

    fun start() {
        stepCount = 0
        startTime = System.currentTimeMillis()
        sensorManager.registerListener(
            this,
            stepSensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    /**
     * Stop listening for steps
     */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /**
     * Called everytime a step is deetected
     */




    //TESTING ONLY ON VIRTUAL WATCH - UNCOMMENT THIS PART WHEN TESTING, COMMENT OUT THE ONE ABOVE
//    private val handler = Handler(Looper.getMainLooper())
//    fun start() {
//        handler.post(object : Runnable {
//            override fun run() {
//                //Fake realistic running cadence
//                cadence = (150..170).random()
//                handler.postDelayed(this, 5_000)
//            }
//        })
//    }
//
//    fun stop() {
//        handler.removeCallbacksAndMessages(null)
//    }

    //END OF COMMENT - COMMENT THE ABOVE CODE WHEN FINISHED TESTING AND UNCOMMENT THE REAL WATCH CODE

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            stepCount++
            val elapsedTimeMs = System.currentTimeMillis() - startTime
            val elapsedMinutes = elapsedTimeMs / 60_000f

            if (elapsedMinutes > 0) {
                cadence = (stepCount / elapsedMinutes).toInt()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //not needed
    }
}