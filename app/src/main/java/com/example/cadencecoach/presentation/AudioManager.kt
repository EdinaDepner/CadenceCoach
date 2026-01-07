package com.example.cadencecoach.presentation
import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.cadencecoach.R

/**
 * Plays a repeating beat based on cadence
 * short feedback sounds(cadence up/down)
 */
class AudioManager (private val context: Context){
    //Handler for scheduling beat playback
    private val handler = Handler(Looper.getMainLooper())

    //MediaPlayer for the beat
    private var beatPlayer: MediaPlayer? = null
    private var feedbackPlayer: MediaPlayer? = null

    //Time between beats(ms)
    private var beatIntervalsMs = 600L //placeholder just for initializing

    //Runnable that keeps repeating the beat sound
    private val beatRunnable = object: Runnable{
        override fun run(){
            beatPlayer?.let {
                it.seekTo(0) //replat cleanly
                it.start()
            }
            handler.postDelayed(this,beatIntervalsMs)
        }
    }

    //Start playing a beat matching the given cadence
    fun startBeat(cadence: Int){
        stopBeat()
        if(cadence <= 0) return //safety check

        //convert cadence (steps/min) into milliseconds between beats
        beatIntervalsMs = (60_000 / cadence).toLong() //real cadence is calculated here
        beatPlayer = MediaPlayer.create(context, R.raw.beat)
        handler.post(beatRunnable)
    }

    //stop the beat sound
    fun stopBeat(){
        handler.removeCallbacks(beatRunnable)
        beatPlayer?.release()
        beatPlayer = null
    }

    //Play feedback when cadence is increasing
    fun playCadenceUp(){
        playFeedback(R.raw.cadence_up)
    }

    //Play feedback when cadence is decreasing
    fun playCadenceDown(){
        playFeedback(R.raw.cadence_down)
    }

    fun playCadenceRecovered(){
        playFeedback(R.raw.cadence_recovered)
    }

    private fun playFeedback(resId: Int)
    {
        feedbackPlayer?.release()
        feedbackPlayer = MediaPlayer.create(context,resId)
        feedbackPlayer?.start()
    }
}