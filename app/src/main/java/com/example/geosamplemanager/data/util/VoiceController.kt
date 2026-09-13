package com.example.geosamplemanager.data.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Обёртка над SpeechRecognizer + TextToSpeech.
 *
 * ВАЖНО:
 *  • Создавать и уничтожать ТОЛЬКО на главном потоке (требование SpeechRecognizer).
 *  • destroy() обязателен — иначе утечка ресурсов распознавателя и TTS.
 *  • TTS инициализируется асинхронно. Если speak() вызвать до готовности —
 *    ничего не произойдёт (не падаем).
 *
 * Все колбэки приходят на главном потоке.
 */
class VoiceController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onReady: () -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        initRecognizer()
        initTts()
    }

    private fun initRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Распознавание речи недоступно на этом устройстве")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onReady()
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    onError(describeError(error))
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    onResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (text.isNotEmpty()) onPartialResult(text)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun initTts() {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("ru", "RU")
                ttsReady = true
            }
        }
    }

    /** Начать слушать. Запускать только после того, как пользователь дал разрешение. */
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            onError("Не удалось запустить микрофон: ${e.message}")
        }
    }

    /** Остановить слушание. */
    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
    }

    /** Озвучить текст. Если TTS ещё не готов — молча игнорируем. */
    fun speak(text: String) {
        if (!ttsReady) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_${System.currentTimeMillis()}")
        } catch (_: Exception) {}
    }

    /** Освободить ресурсы. ОБЯЗАТЕЛЬНО вызывать в onDispose. */
    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        ttsReady = false
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Ошибка аудио"
        SpeechRecognizer.ERROR_CLIENT -> "Ошибка клиента"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения на микрофон"
        SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Тайм-аут сети"
        SpeechRecognizer.ERROR_NO_MATCH -> "Не расслышал"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Распознаватель занят"
        SpeechRecognizer.ERROR_SERVER -> "Ошибка сервера распознавания"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Тишина в микрофоне"
        else -> "Ошибка распознавания ($error)"
    }
}