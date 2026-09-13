package com.example.geosamplemanager.data.voice

/**
 * Сегментация голосового потока по паузам (§8 VOICE.md).
 *
 * Vosk отдаёт непрерывный поток текста. Сегментатор буферизует куски,
 * склеивает их пробелом, а когда пауза между кусками превышает порог —
 * выпускает накопленную фразу через onPhrase.
 */
class VoiceSegmenter(
    private val pauseMs: Long = 800L,
    private val onPhrase: (String) -> Unit
) {

    private val buffer = StringBuilder()
    private var lastTs: Long = 0L

    /**
     * Пришёл кусок распознанного текста в момент nowMs.
     */
    fun onChunk(text: String, nowMs: Long) {
        if (buffer.isNotEmpty() && lastTs > 0L && (nowMs - lastTs) > pauseMs) {
            emit()
        }
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(trimmed)
        }
        lastTs = nowMs
    }

    /**
     * Периодический тик — если пауза затянулась, фраза выпускается без
     * ожидания следующего куска. Обычно вызывается из корутины.
     */
    fun tick(nowMs: Long) {
        if (buffer.isNotEmpty() && lastTs > 0L && (nowMs - lastTs) > pauseMs) {
            emit()
        }
    }

    /**
     * Принудительно завершить текущую фразу. Вызывается при закрытии
     * сессии, чтобы не потерять хвост.
     */
    fun flush() {
        if (buffer.isNotEmpty()) emit()
    }

    /** Полный сброс без emit. */
    fun reset() {
        buffer.clear()
        lastTs = 0L
    }

    private fun emit() {
        val phrase = buffer.toString().trim()
        buffer.clear()
        lastTs = 0L
        if (phrase.isNotEmpty()) onPhrase(phrase)
    }
}