package com.example.geosamplemanager.data.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Звуковая обратная связь: бип через ToneGenerator.
 *
 * doubleUp() — двойной восходящий бип (§9.2 VOICE.md).
 * error()    — короткий низкий сигнал.
 */
class VoiceFeedback(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())

    fun doubleUp() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            handler.postDelayed({
                try { tg.startTone(ToneGenerator.TONE_PROP_ACK, 150) }
                catch (_: Exception) {}
                handler.postDelayed({
                    try { tg.release() } catch (_: Exception) {}
                }, 300)
            }, 180)
        } catch (_: Exception) {
            // На части устройств ToneGenerator недоступен — молча.
        }
    }

    fun error() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 200)
            handler.postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 300)
        } catch (_: Exception) {}
    }

    fun release() {
        try { handler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
    }
}