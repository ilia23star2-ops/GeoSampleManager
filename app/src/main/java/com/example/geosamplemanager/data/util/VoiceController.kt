package com.example.geosamplemanager.data.util

import android.content.Context
import android.util.Log
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.Recognizer.EndpointerMode
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Управление оффлайн-распознаванием Vosk.
 *
 * Ключевые настройки этой версии (5.8.6-5e):
 * - EndpointerMode.VERY_LONG — максимальная терпимость к паузам.
 * - setEndpointerDelays — кастомные задержки вместо дефолтных:
 *   tStartMax = 5.0 с (максимум ожидания начала речи),
 *   tEnd     = 3.0 с (пауза после речи до финализации),
 *   tMax     = 50.0 с (абсолютный максимум длины фразы).
 *
 * Почему: дефолтный endpoint Vosk обрывает длинные номера
 * (KPD1090031, «15 24 01 2 ноля 5») на середине.
 */
class VoiceController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onPartial: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onTimeout: () -> Unit = {}
) : RecognitionListener {

    companion object {
        private const val TAG = "VoiceController"

        // Настройки endpoint (5.8.6-5e)
        private const val ENDPOINTER_T_START_MAX = 5.0f
        private const val ENDPOINTER_T_END = 3.0f
        private const val ENDPOINTER_T_MAX = 50.0f

        // Anti-echo: окно подавления после TTS (5.8.6-2)
        private const val RESUME_DELAY_MS = 800L
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var isListening = AtomicBoolean(false)
    private var suppressUntil = 0L

    /**
     * Инициализация модели Vosk и запуск распознавания.
     */
    fun start() {
        if (isListening.getAndSet(true)) {
            Log.w(TAG, "start() вызван повторно — уже слушаем")
            return
        }

        try {
            StorageService.unpack(
                context,
                "vosk-model-small-ru-0.22",
                "model",
                { model ->
                    this.model = model
                    startRecognition(model)
                },
                { exception ->
                    isListening.set(false)
                    onError("Ошибка загрузки модели: ${exception.message}")
                }
            )
        } catch (e: Exception) {
            isListening.set(false)
            onError("Ошибка инициализации: ${e.message}")
        }
    }

    private fun startRecognition(model: Model) {
        try {
            val recognizer = Recognizer(model, 16000.0f).apply {
                // 5.8.6-5e: endpoint tuning
                setEndpointerMode(EndpointerMode.VERY_LONG)
                setEndpointerDelays(
                    ENDPOINTER_T_START_MAX,
                    ENDPOINTER_T_END,
                    ENDPOINTER_T_MAX
                )
                setMaxAlternatives(1)
                setWords(true)
                setPartialWords(true)
            }

            speechService = SpeechService(recognizer, 16000.0f).apply {
                startListening(this@VoiceController)
            }

            Log.i(TAG, "Vosk запущен: VERY_LONG, delays=($ENDPOINTER_T_START_MAX, $ENDPOINTER_T_END, $ENDPOINTER_T_MAX)")
        } catch (e: Exception) {
            isListening.set(false)
            onError("Ошибка запуска распознавания: ${e.message}")
        }
    }

    /**
     * Остановка распознавания.
     */
    fun stop() {
        if (!isListening.getAndSet(false)) return

        try {
            speechService?.stop()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка остановки SpeechService", e)
        } finally {
            speechService = null
            model?.close()
            model = null
        }
    }

    /**
     * Пауза распознавания (например, на время TTS).
     */
    fun pause() {
        speechService?.stop()
        Log.d(TAG, "Vosk на паузе")
    }

    /**
     * Возобновление распознавания после TTS с anti-echo задержкой.
     */
    fun resumeWithEchoSuppression(phraseLength: Int) {
        val dynamicDelay = RESUME_DELAY_MS + (phraseLength * 15L).coerceAtMost(1200L)
        suppressUntil = System.currentTimeMillis() + dynamicDelay

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isListening.get()) {
                speechService?.startListening(this)
                Log.d(TAG, "Vosk возобновлён после TTS (задержка ${dynamicDelay}мс)")
            }
        }, dynamicDelay)
    }

    // ==================== RecognitionListener ====================

    override fun onResult(hypothesis: String?) {
        if (isSuppressed()) return
        hypothesis?.let { onResult(it) }
    }

    override fun onPartialResult(hypothesis: String?) {
        if (isSuppressed()) return
        hypothesis?.let { onPartial(it) }
    }

    override fun onFinalResult(hypothesis: String?) {
        if (isSuppressed()) return
        hypothesis?.let { onResult(it) }
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Ошибка распознавания", exception)
        onError(exception?.message ?: "Неизвестная ошибка Vosk")
    }

    override fun onTimeout() {
        Log.w(TAG, "Таймаут распознавания")
        onTimeout()
    }

    private fun isSuppressed(): Boolean {
        return System.currentTimeMillis() < suppressUntil
    }
}
