package com.example.geosamplemanager.data.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.bluetooth.BluetoothController
import com.example.geosamplemanager.data.bluetooth.BluetoothSettings
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
 * FIX 5.8.6-2: скорость TTS 1.10, увеличенная задержка возобновления.
 * FIX 5.8.6-5c: time-based debounce 600 мс для дубликатов.
 * FIX 5.8.11-voice-24-debounce: буфер + динамический таймер для
 *   склейки фраз при обрыве Vosk по короткой паузе.
 * FIX 5.8.11-e4-fix-2: RESUME_DELAY_MS уменьшен до 250 мс.
 *
 * FIX 5.9-settings-bt:
 *  - принимает BluetoothSettings и BluetoothController;
 *  - при startListening с включённым BT переключает звук в SCO;
 *  - метод applyBluetoothSettings() — смена на лету, пересоздаёт
 *    SpeechService, чтобы AudioRecord взял новый канал;
 *  - в destroy() выключает SCO.
 */
class VoiceController(
    private val context: Context,
    private val callback: VoiceCallback,
    private var btSettings: BluetoothSettings = BluetoothSettings(),
    private val btController: BluetoothController? = null
) {

    private val app = context.applicationContext as GeoSampleApp

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var listening = false

    private var lastFinalText: String = ""
    private var lastFinalAt: Long = 0L

    private var suppressUntil: Long = 0L

    private var pendingText: String? = null

    private var pendingRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

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
            lastFinalAt = 0L
            suppressUntil = 0L
            pendingText = null
            cancelPendingTimer()

            // FIX 5.9-settings-bt: перед стартом — включить SCO,
            // если выбрана BT-гарнитура.
            applyBluetoothAtStart()

            val service = speechService ?: createService(model)

            listening = true
            callback.onReady()

            service.startListening(listener)

            Log.e(TAG, "startListening: старт (грамматика=${app.voiceUseGrammar}, " +
                    "bt=${btSettings.enabled && btSettings.deviceAddress != null})")
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

    /**
     * FIX 5.9-settings-bt:
     * Полная пересборка SpeechService — нужна, когда меняется
     * источник микрофона (BT ↔ встроенный). AudioRecord
     * внутри Vosk создаётся один раз и не подхватывает смену
     * канала на лету.
     */
    private fun recreateSpeechService() {
        try {
            speechService?.stop()
            speechService?.shutdown()
            recognizer?.close()
        } catch (_: Exception) {
        }
        speechService = null
        recognizer = null
        listening = false

        // Запускаем заново — теперь с новым микрофоном.
        startListening()
    }

    fun stopListening() {
        if (!listening) return

        try {
            speechService?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stopListening ошибка", e)
        }

        pendingText = null
        cancelPendingTimer()

        listening = false
    }

    // ================================================================
    // FIX 5.9-settings-bt: Bluetooth
    // ================================================================

    /**
     * Включить SCO, если в настройках выбрана BT-гарнитура.
     */
    private fun applyBluetoothAtStart() {
        val useBt = btSettings.enabled && btSettings.deviceAddress != null
        if (!useBt) {
            btController?.stopSco()
            return
        }
        btController?.startSco()
    }

    /**
     * Сменить BT-настройки на лету. Если активно слушаем и
     * источник микрофона изменился — пересоздаём SpeechService.
     */
    fun applyBluetoothSettings(newSettings: BluetoothSettings) {
        if (newSettings == btSettings) return

        val prevUseBt = btSettings.enabled && btSettings.deviceAddress != null
        val newUseBt = newSettings.enabled && newSettings.deviceAddress != null
        val sameDevice = btSettings.deviceAddress == newSettings.deviceAddress

        btSettings = newSettings

        if (prevUseBt == newUseBt && sameDevice) return

        // Переключаем SCO.
        if (newUseBt) btController?.startSco() else btController?.stopSco()

        // Пересоздаём микрофонный канал, если активно слушаем.
        if (listening) {
            recreateSpeechService()
        }
    }

    private val listener = object : RecognitionListener {

        override fun onPartialResult(hypothesis: String?) {
            val text = extractText(hypothesis, "partial")
            if (text.isEmpty()) return

            if (!pendingText.isNullOrBlank()) {
                restartPendingTimer()
            }

            callback.onPartial(text)
        }

        override fun onResult(hypothesis: String?) {
            val text = extractText(hypothesis, "text")
            if (text.isEmpty()) return

            if (isEcho(text)) return
            if (isDuplicate(text)) return

            acceptFinal(text)

            Log.e(TAG, "RESULT: «$text»")

            if (isInstantCommand(text)) {
                Log.e(TAG, "RESULT: мгновенная команда → flush сразу")
                pendingText = null
                cancelPendingTimer()
                deliverResult(text)
                return
            }

            val merged = if (pendingText.isNullOrBlank()) text
            else "${pendingText} $text"
            pendingText = merged
            Log.e(TAG, "RESULT: буфер = «$merged»")
            restartPendingTimer()
        }

        override fun onFinalResult(hypothesis: String?) {
            val text = extractText(hypothesis, "text")
            if (text.isEmpty()) return

            if (isEcho(text)) return
            if (isDuplicate(text)) return

            acceptFinal(text)

            Log.e(TAG, "FINAL: «$text»")

            val merged = if (pendingText.isNullOrBlank()) text
            else "${pendingText} $text"
            pendingText = merged
            flushPending()
        }

        override fun onError(e: Exception?) {
            listening = false
            pendingText = null
            cancelPendingTimer()
            Log.e(TAG, "VOSK onError: ${e?.message}", e)
            callback.onError("Ошибка распознавания: ${e?.message ?: "неизвестная"}")
        }

        override fun onTimeout() {
            listening = false
            pendingText = null
            cancelPendingTimer()
            Log.e(TAG, "VOSK onTimeout")
            callback.onError("Тишина в микрофоне")
        }
    }

    // ================================================================
    // Буфер + таймер
    // ================================================================

    private fun isInstantCommand(text: String): Boolean {
        val norm = text.lowercase()
            .trim('.', ',', '!', '?', ';', ':')
            .trim()
        return norm in INSTANT_COMMANDS
    }

    private fun restartPendingTimer() {
        cancelPendingTimer()
        val r = Runnable {
            Log.e(TAG, "pending timer сработал (${DEBOUNCE_MS} мс)")
            flushPending()
        }
        pendingRunnable = r
        handler.postDelayed(r, DEBOUNCE_MS)
    }

    private fun cancelPendingTimer() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }

    private fun flushPending() {
        cancelPendingTimer()
        val text = pendingText ?: return
        pendingText = null
        if (text.isBlank()) return
        Log.e(TAG, "flush: «$text» → callback.onResult")
        deliverResult(text)
    }

    private fun deliverResult(text: String) {
        try {
            callback.onResult(text)
            Log.e(TAG, "callback.onResult отработал")
        } catch (e: Exception) {
            Log.e(TAG, "callback.onResult УПАЛ", e)
        }
    }

    private fun isEcho(text: String): Boolean {
        if (System.currentTimeMillis() >= suppressUntil) return false
        Log.e(TAG, "VOSK echo suppressed: «$text»")
        return true
    }

    private fun isDuplicate(text: String): Boolean {
        val now = System.currentTimeMillis()
        if (text == lastFinalText && now - lastFinalAt < DUPLICATE_WINDOW_MS) {
            Log.e(TAG, "VOSK duplicate suppressed: «$text»")
            return true
        }
        return false
    }

    private fun acceptFinal(text: String) {
        lastFinalText = text
        lastFinalAt = System.currentTimeMillis()
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
        lastFinalAt = 0L
        suppressUntil = 0L
        pendingText = null
        cancelPendingTimer()

        // FIX 5.9-settings-bt: выключить SCO при закрытии диалога.
        try {
            btController?.stopSco()
        } catch (_: Exception) {
        }

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
        private const val RESUME_DELAY_MS = 250L

        private const val SPEECH_BASE_MS = 600L
        private const val SPEECH_CHAR_MS = 70L

        private const val DUPLICATE_WINDOW_MS = 600L
        private const val DEBOUNCE_MS = 1200L

        private val INSTANT_COMMANDS: Set<String> = setOf(
            "стоп", "хатит", "хватит", "пауза", "паузу",
            "продолжить", "продолжай",
            "отмена", "отменить", "назад", "верни", "вперёд", "вперед",
            "следующая", "далее", "следующую", "следующий", "дальше",
            "поиск", "режим поиск", "сортировка", "режим сортировка",
            "режим сортировки",
            "помощь", "команды", "команда",
            "сколько осталось",
            "показать отложенные", "отложенные",
            "показать найденные", "найденные",
            "снять все", "сбросить все", "очистить все", "все",
            "отметь все", "отметить все", "отметьте все",
            "снять последнюю", "последнюю снять",
            "снять отложенную", "снять отложенную пробу",
            "отметь", "отметить", "отметьте",
            "снять", "сними", "убрать", "убери",
            "удали", "удалить",
            "отложить", "отложи", "перенести", "перенеси",
            "подтверждаю", "подтвердить",
            "отменяю", "пропустить", "пропусти",
            "эту", "ее", "её", "найденную", "найденное",
            "отметь эту", "отметить эту",
            "отметь ее", "отметить ее",
            "отметь её", "отметить её"
        )
    }
}