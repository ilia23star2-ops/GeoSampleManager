package com.example.geosamplemanager.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Синглтон TTS для всего приложения.
 *
 * TTS инициализируется за 2–3 секунды. Если делать это при каждом
 * открытии диалога ГП, пользователь ждёт. Поэтому инициализируем
 * один раз в GeoSampleApp.onCreate() — тогда к моменту первого
 * «скажи» TTS уже готов.
 */
object VoiceTtsHolder {

    private const val TAG = "VoiceTtsHolder"

    private var tts: TextToSpeech? = null
    private var ready = false

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    val result = tts?.setLanguage(Locale("ru", "RU"))
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        Log.w(TAG, "Русский язык TTS не поддерживается")
                    } else {
                        ready = true
                        Log.i(TAG, "TTS готов")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "TTS: ошибка настройки языка", e)
                }
            } else {
                Log.w(TAG, "TTS init failed: status=$status")
            }
        }
    }

    fun speak(text: String) {
        if (!ready) {
            Log.d(TAG, "speak: TTS не готов, пропускаю")
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "v_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.w(TAG, "speak failed", e)
        }
    }

    fun isReady(): Boolean = ready

    /**
     * Полное освобождение. Вызывать не нужно — TTS живёт вместе с
     * процессом приложения. Оставлено для тестов.
     */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ready = false
    }
}