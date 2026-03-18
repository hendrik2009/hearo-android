package com.example.hearo

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Plays UI sounds: tap (switch9.ogg) for clicks, seek-repeat (rollover6.ogg) for each seek-hold repetition. Uses media volume. */
class TapSound(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    @Volatile
    private var tapSoundId: Int = 0

    @Volatile
    private var tapLoaded: Boolean = false

    @Volatile
    private var seekRepeatSoundId: Int = 0

    @Volatile
    private var seekRepeatLoaded: Boolean = false

    init {
        try {
            tapSoundId = soundPool.load(appContext, R.raw.switch9, 1)
            seekRepeatSoundId = soundPool.load(appContext, R.raw.rollover6, 1)
            soundPool.setOnLoadCompleteListener { _, id, status ->
                if (status == 0) {
                    when (id) {
                        tapSoundId -> tapLoaded = true
                        seekRepeatSoundId -> seekRepeatLoaded = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("TapSound", "Failed to load sounds: ${e.message}")
        }
    }

    /** Tap/click feedback (e.g. first press on seek). */
    fun play() {
        if (tapSoundId == 0) return
        if (tapLoaded) {
            soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f)
        } else {
            mainHandler.postDelayed({ if (tapLoaded) soundPool.play(tapSoundId, 1f, 1f, 1, 0, 1f) }, 50)
        }
    }

    /** Seek-hold repeat feedback (played on each repetition while holding seek). */
    fun playSeekRepeat() {
        if (seekRepeatSoundId == 0) return
        if (seekRepeatLoaded) {
            soundPool.play(seekRepeatSoundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
