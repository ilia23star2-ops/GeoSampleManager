package com.example.geosamplemanager.data.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * FIX 5.9-settings-sound / -3-fix-2:
 * Утилита для теста TTS из настроек — «Проверить озвучку».
 *
 * Играет короткую фразу с указанной громкостью и скоростью.
 * Не трогает основной VoiceController (не глушит микрофон, не
 * работает с Vosk). Отдельный TextToSpeech, создаётся и
 * уничтожается по запросу.
 *
 * FIX 5.9-tts-audio-mode:
 *  - setAudioAttributes(USAGE_MEDIA, CONTENT_TYPE_SPEECH);
 *  - KEY_PARAM_STREAM = STREAM_MUSIC.
 *  Это выравнивает поведение с VoiceController и делает тест
 *  «Проверить» консистентным с работой ГП в сверке.
 */
object SoundTestUtil {

    private const val TAG = "SoundTestUtil"

    private const val TEST_PHRASE =
        "Проба отмечена. Вес два и пять. Наряд двадцать семь."

    fun speakTest(
        context: Context,
        volume: TtsVolume,
        speed: Float = VoiceSettings.DEFAULT_TTS_SPEED,
        onDone: (() -> Unit)? = null
    ) {
        if (volume == TtsVolume.OFF) {
            onDone?.invoke()
            return
        }

        val appContext = context.applicationContext
        val audioManager =
            appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        var tts: TextToSpeech? = null
        tts = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "speakTest: init failed status=$status")
                try { tts?.shutdown() } catch (_: Exception) {}
                onDone?.invoke()
                return@TextToSpeech
            }

            try {
                tts?.language = Locale("ru", "RU")

                // FIX 5.9-tts-audio-mode: явные audio-атрибуты.
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
                tts?.setAudioAttributes(attrs)

                tts?.setSpeechRate(speed)

                val params = android.os.Bundle().apply {
                    putFloat(
                        TextToSpeech.Engine.KEY_PARAM_VOLUME,
                        ttsVolumeFloat(volume)
                    )
                    putInt(
                        TextToSpeech.Engine.KEY_PARAM_STREAM,
                        AudioManager.STREAM_MUSIC
                    )
                }

                Log.e(
                    TAG,
                    "speakTest: vol=$volume (x${ttsVolumeFloat(volume)}), " +
                            "rate=$speed, audioMode=${audioManager.mode}, " +
                            "streamVol=${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}"
                )

                tts?.setOnUtteranceProgressListener(
                    object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) {
                            try { tts?.shutdown() } catch (_: Exception) {}
                            onDone?.invoke()
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            try { tts?.shutdown() } catch (_: Exception) {}
                            onDone?.invoke()
                        }
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            try { tts?.shutdown() } catch (_: Exception) {}
                            onDone?.invoke()
                        }
                    }
                )

                tts?.speak(
                    TEST_PHRASE,
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    "test_${System.currentTimeMillis()}"
                )
            } catch (e: Exception) {
                Log.w(TAG, "speakTest: ошибка", e)
                try { tts?.shutdown() } catch (_: Exception) {}
                onDone?.invoke()
            }
        }
    }

    fun playAllSignals(context: Context, settings: VoiceSettings) {
        val feedback = VoiceFeedback(context)
        feedback.applySettings(settings)
        feedback.soundOk()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            feedback.soundAttention()
        }, 400L)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            feedback.soundError()
        }, 2500L)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            feedback.release()
        }, 4000L)
    }

    private fun ttsVolumeFloat(volume: TtsVolume): Float = when (volume) {
        TtsVolume.OFF -> 0.0f
        TtsVolume.QUIET -> 0.4f
        TtsVolume.NORMAL -> 0.8f
        TtsVolume.LOUD -> 1.0f
    }
}