package com.example.geosamplemanager.data.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Звуковая обратная связь.
 *
 * Три звука:
 *   soundOk()        — подтверждение действия (короткий высокий).
 *   soundAttention() — нужно внимание (два ровных средних).
 *   soundError()     — ошибка (низкий длинный).
 *
 * Без звука: успешный поиск, IDLE, пауза, старт/конец сессии.
 *
 * Гвардия от серии (правила К3, К5 из DECISIONS §13.5):
 *   • Один и тот же звук подряд в пределах «окна» — глушится.
 *   • OK         — окно 300 мс.
 *   • ATTENTION  — окно 2000 мс (первая запрещённая команда играет,
 *                  повторные — нет).
 *   • ERROR      — окно 1500 мс.
 *   Разные типы звуков не глушат друг друга.
 */
class VoiceFeedback(private val context: Context) {

    private enum class Kind { OK, ATTENTION, ERROR }

    private val handler = Handler(Looper.getMainLooper())

    private var lastKind: Kind? = null
    private var lastAtMs: Long = 0L

    // ================================================================
    // Публичный API
    // ================================================================

    /** Подтверждение: проба отмечена, вес принят, отмена/повтор, next. */
    fun soundOk() {
        if (!canPlay(Kind.OK)) return
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            // Короткий высокий «принято».
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 100)
            handler.postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 200)
        } catch (_: Exception) {
            // На части устройств ToneGenerator недоступен — молча.
        }
    }

    /** Внимание: другой наряд/участок/несколько, проба уже отмечена, нужен вес. */
    fun soundAttention() {
        if (!canPlay(Kind.ATTENTION)) return
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            // Два ровных средних бипа.
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 100)
            handler.postDelayed({
                try { tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 100) }
                catch (_: Exception) {}
                handler.postDelayed({
                    try { tg.release() } catch (_: Exception) {}
                }, 200)
            }, 150)
        } catch (_: Exception) {}
    }

    /** Ошибка: не найдено, ошибка поиска, проба не существует. */
    fun soundError() {
        if (!canPlay(Kind.ERROR)) return
        try {
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            // Низкий длинный.
            tg.startTone(ToneGenerator.TONE_PROP_NACK, 300)
            handler.postDelayed({
                try { tg.release() } catch (_: Exception) {}
            }, 400)
        } catch (_: Exception) {}
    }

    /** Освободить ресурсы. */
    fun release() {
        try { handler.removeCallbacksAndMessages(null) } catch (_: Exception) {}
    }

    // ================================================================
    // Гвардия от серии
    // ================================================================

    private fun canPlay(kind: Kind): Boolean {
        val now = System.currentTimeMillis()
        val windowMs = when (kind) {
            Kind.OK -> 300L
            Kind.ATTENTION -> 2000L
            Kind.ERROR -> 1500L
        }
        if (lastKind == kind && now - lastAtMs < windowMs) {
            // Тот же звук слишком быстро — глушим.
            return false
        }
        lastKind = kind
        lastAtMs = now
        return true
    }
}