package com.example.cadencecoach.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class CadenceSensor(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private var lastStepTimeMs: Long = 0
    private var _smoothedCadence = 0f // Use Float for better precision during smoothing

    // Return the rounded integer for the app to use
    val cadence: Int
        get() {
            // If no step for 2.5 seconds, assume stopped
            if (System.currentTimeMillis() - lastStepTimeMs > 2500) {
                return 0
            }
            return _smoothedCadence.toInt()
        }

    fun start() {
        lastStepTimeMs = System.currentTimeMillis()
        _smoothedCadence = 0f
        sensorManager.registerListener(
            this,
            stepSensor,
            SensorManager.SENSOR_DELAY_FASTEST
        )
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        _smoothedCadence = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_DETECTOR) {
            val now = System.currentTimeMillis()
            val timeDiffMs = now - lastStepTimeMs

            // Filter unrealistic noise (< 200ms means > 300 SPM)
            if (timeDiffMs > 200) {

                // Calculate instant rate for this specific step
                val currentStepRate = (60_000 / timeDiffMs).toFloat()

                if (_smoothedCadence == 0f) {
                    // First step: just accept the value
                    _smoothedCadence = currentStepRate
                } else {
                    // SMOOTHING ALGORITHM:
                    // 60% weight on history (stability)
                    // 40% weight on new step (responsiveness)
                    // This prevents one "bad" step from ruining your recovery timer.
                    _smoothedCadence = (_smoothedCadence * 0.6f) + (currentStepRate * 0.4f)
                }

                lastStepTimeMs = now
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}