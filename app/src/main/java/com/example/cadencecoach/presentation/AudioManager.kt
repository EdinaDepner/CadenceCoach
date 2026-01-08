package com.example.cadencecoach.presentation

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.example.cadencecoach.R

/**
 * AudioManager
 * ------------
 * This class is responsible for all audio output in the app.
 * It plays:
 * - a repeating beat sound based on cadence
 * - short feedback sounds (cadence up / down / recovered)
 */
class AudioManager(private val context: Context) {

    // Handler used to schedule repeated playback on the main thread
    private val handler = Handler(Looper.getMainLooper())

    // MediaPlayer for the continuous beat sound
    private var beatPlayer: MediaPlayer? = null

    // MediaPlayer for short feedback sounds
    private var feedbackPlayer: MediaPlayer? = null

    // Time between beats in milliseconds (calculated from cadence)
    private var intervalMs = 600L

    /**
     * Runnable that repeatedly plays the beat sound.
     * After each beat, it schedules itself again after intervalMs.
     */
    private val beatRunnable = object : Runnable {
        override fun run() {

            // If the beat player exists, restart the sound from the beginning
            beatPlayer?.let {
                it.seekTo(0)
                it.start()
            }

            // Schedule the next beat
            handler.postDelayed(this, intervalMs)
        }
    }

    /**
     * Starts playing the beat sound at a tempo matching the given cadence.
     * Cadence is in steps per minute.
     */
    fun startBeat(cadence: Int) {

        // Stop any existing beat first
        stopBeat()

        // Safety check to avoid division by zero or invalid cadence
        if (cadence <= 0) return

        // Convert cadence (steps per minute) to time between beats in milliseconds
        intervalMs = (60_000 / cadence).toLong()

        // Create the MediaPlayer for the beat sound
        beatPlayer = MediaPlayer.create(context, R.raw.beat)

        // Start the repeating beat
        handler.post(beatRunnable)
    }

    /**
     * Stops the beat sound and cleans up resources.
     */
    fun stopBeat() {
        handler.removeCallbacks(beatRunnable)
        beatPlayer?.release()
        beatPlayer = null
    }

    /**
     * Plays feedback when cadence is higher than baseline.
     */
    fun playCadenceUp() = playFeedback(R.raw.cadence_up)

    /**
     * Plays feedback when cadence is lower than baseline.
     */
    fun playCadenceDown() = playFeedback(R.raw.cadence_down)

    /**
     * Plays feedback when cadence has returned to a stable range.
     */
    fun playCadenceRecovered() = playFeedback(R.raw.cadence_recovered)

    /**
     * Helper function that plays a short feedback sound.
     * It stops any previous feedback sound before playing a new one.
     */
    private fun playFeedback(resId: Int) {

        // Release any previously playing feedback sound
        feedbackPlayer?.release()

        // Create and start the new feedback sound
        feedbackPlayer = MediaPlayer.create(context, resId)
        feedbackPlayer?.start()
    }
}