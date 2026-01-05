package com.example.cadencecoach.presentation
import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
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

    //Time between beats(ms)
    private var beatIntervalsMs = 600L

    //Runnable that keeps repeating the beat sound
    private val beatRunnable = object: Runnable{
        override fun run(){
            beatPlayer?.start()
            handler.postDelayed(this,beatIntervalsMs)
        }
    }

    //Start playing a beat matching the given cadence
    fun startBeat(cadence: Int){
        stopBeat()
        //convert cadence (steps/min) into milliseconds between beats
        beatIntervalsMs = (60000 / cadence).toLong()
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
        MediaPlayer.create(context, R.raw.cadence_up).start()
    }

    //Play feedback when cadence is decreasing
    fun playCadenceDown(){
        MediaPlayer.create(context, R.raw.cadence_down).start()
    }
}