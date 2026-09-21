package com.example.geosamplemanager.data.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.voice.VoiceCallback
import com.example.geosamplemanager.data.voice.VoiceGrammar
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.util.Locale

/**
 * Обёртка над Vosk + TTS.
 *
 * ВАЖНО: во время озвучки микрофон глушится — иначе Vosk слышит сам себя.
 *
 * FIX 5.8.6-2:
 * - скорость TTS 1.10;
 * - увеличена задержка возобновления Vosk после речи до 800 мс;
 * - добавлено окно подавления эха: результаты Vosk, пришедшие
 *   сразу после speak(), игнорируются;
 * - исправлены пустые catch-блоки.
 */
class VoiceController(
    private val context: Context,
    private val callback: VoiceCallback
) {

    private val app = context.applicationContext as GeoSampleApp

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false
    private var lastFinalText: String = ""

    /**
     * Время, до которого результаты Vosk считаются эхом TTS.
     */
    private var suppressUntil: Long = 0L

    init {
        initTts()
    }

    // ================================================================
    // TTS
    // ================================================================

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    tts?.language = Locale("ru", "RU")
                    tts?.setSpeechRate(DEFAULT_SPEECH_RATE)

                    ttsReady = true
                    Log.e(TAG, "TTS готов, скорость=$DEFAULT_SPEECH_RATE")
                } catch (e: Exception) {
                    Log.e(TAG, "TTS: ошибка языка", e)
                }

                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.e(TAG, "TTS onStart → пауза Vosk")
                        pauseVosk()
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.e(TAG, "TTS onDone → возобновляю Vosk через ${RESUME_DELAY_MS} мс")
                        resumeVoskDelayed(RESUME_DELAY_MS)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "TTS onError → возобновляю Vosk")
                        resumeVoskDelayed(RESUME_DELAY_MS)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        Log.e(TAG, "TTS onError($errorCode) → возобновляю Vosk")
                        resumeVoskDelayed(RESUME_DELAY_MS)
                    }
                })
            } else {
                Log.e(TAG, "TTS init failed: status=$status")
            }
        }
    }

    fun speak(text: String) {
        if (!ttsReady) {
            Log.d(TAG, "speak: TTS не готов, пропускаю")
            return
        }

        val clean = text.trim()
        if (clean.isEmpty()) return

        suppressUntil = System.currentTimeMillis() + estimateSpeechMs(clean)

        try {
            tts?.speak(
                clean,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "v_${System.currentTimeMillis()}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "speak failed", e)
        }
    }

    private fun estimateSpeechMs(text: String): Long {
        return SPEECH_BASE_MS + text.length * SPEECH_CHAR_MS
    }

    private fun pauseVosk() {
        try {
            speechService?.setPause(true)
            Log.e(TAG, "Vosk: приостановлен")
        } catch (e: Exception) {
            Log.w(TAG, "pause failed", e)
        }
    }

    private fun resumeVoskDelayed(delayMs: Long) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                speechService?.setPause(false)
                Log.e(TAG, "Vosk: возобновлён")
            } catch (e: Exception) {
                Log.w(TAG, "resume failed", e)
            }
        }, delayMs)
    }

    // ================================================================
    // РАСПОЗНАВАНИЕ
    // ================================================================

    fun startListening() {
        if (listening) {
            Log.d(TAG, "startListening: уже слушаю")
            return
        }

        val model = app.voiceModel
        if (model == null) {
            Log.e(TAG, "startListening: МОДЕЛЬ НЕ ЗАГРУЖЕНА")
            callback.onError("Модель Vosk не загружена")
            return
        }

        try {
            lastFinalText = ""
            suppressUntil = 0L

            val service = speechService ?: createService(model)

            listening = true
            callback.onReady()

            service.startListening(listener)

            Log.e(TAG, "startListening: старт (грамматика=${app.voiceUseGrammar})")
        } catch (e: Exception) {
            listening = false
            Log.e(TAG, "startListening: ИСКЛЮЧЕНИЕ", e)
            callback.onError("Не удалось запустить: ${e.message}")
        }
    }

    private fun createService(model: Model): SpeechService {
        val rec = if (app.voiceUseGrammar) {
            try {
                val grammar = VoiceGrammar.build()
                Log.e(TAG, "createService: с грамматикой (${grammar.length} байт)")
                Recognizer(model, SAMPLE_RATE, grammar)
            } catch (e: Exception) {
                Log.e(TAG, "createService: грамматика упала, без неё", e)
                Recognizer(model, SAMPLE_RATE)
            }
        } else {
            Log.e(TAG, "createService: без грамматики")
            Recognizer(model, SAMPLE_RATE)
        }

        recognizer = rec

        val service = SpeechService(rec, SAMPLE_RATE)
        speechService = service

        return service
    }

    fun stopListening() {
        if (!listening) return

        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening ошибка", e)
        }

        listening = false
    }

    private val listener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            val text = extractText(hypothesis, "partial")
            if (text.isEmpty()) return

            callback.onPartial(text)
        }

        override fun onResult(hypothesis: String?) {
            val text = extractText(hypothesis, "text")
            if (text.isEmpty()) return

            if (isEcho(text)) return

            if (text == lastFinalText) return

            lastFinalText = text

            Log.e(TAG, "RESULT: «$text» → вызываю callback.onResult")

            try {
                callback.onResult(text)
                Log.e(TAG, "RESULT: callback.onResult отработал")
            } catch (e: Exception) {
                Log.e(TAG, "RESULT: callback.onResult УПАЛ", e)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = extractText(hypothesis, "text")
            if (text.isEmpty()) return

            if (isEcho(text)) return

            if (text == lastFinalText) return

            lastFinalText = text

            Log.e(TAG, "FINAL: «$text» → вызываю callback.onResult")

            try {
                callback.onResult(text)
            } catch (e: Exception) {
                Log.e(TAG, "FINAL: callback.onResult УПАЛ", e)
            }
        }

        override fun onError(e: Exception?) {
            listening = false
            Log.e(TAG, "VOSK onError: ${e?.message}", e)
            callback.onError("Ошибка распознавания: ${e?.message ?: "неизвестная"}")
        }

        override fun onTimeout() {
            listening = false
            Log.e(TAG, "VOSK onTimeout")
            callback.onError("Тишина в микрофоне")
        }
    }

    private fun isEcho(text: String): Boolean {
        if (System.currentTimeMillis() >= suppressUntil) return false

        Log.e(TAG, "VOSK echo suppressed: «$text»")
        return true
    }

    fun destroy() {
        Log.e(TAG, "destroy")

        try {
            speechService?.stop()
        } catch (_: Exception) {
        }

        try {
            speechService?.shutdown()
        } catch (_: Exception) {
        }

        try {
            recognizer?.close()
        } catch (_: Exception) {
        }

        speechService = null
        recognizer = null
        listening = false
        lastFinalText = ""
        suppressUntil = 0L

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }

        tts = null
        ttsReady = false
    }

    private fun extractText(json: String?, field: String): String {
        if (json.isNullOrEmpty()) return ""

        return try {
            JSONObject(json).optString(field, "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val TAG = "VoiceController"
        private const val SAMPLE_RATE = 16000.0f

        private const val DEFAULT_SPEECH_RATE = 1.10f
        private const val RESUME_DELAY_MS = 800L

        private const val SPEECH_BASE_MS = 600L
        private const val SPEECH_CHAR_MS = 70L
    }
}
