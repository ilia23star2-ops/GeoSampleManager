package com.example.geosamplemanager.data.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Режим голосовой сессии.
 *
 *   SEARCH — поиск со статистикой и отметками (по умолчанию).
 *   SORT   — сортировка: только «X — наряд Y», без отметок.
 */
enum class VoiceSessionMode {
    SEARCH,
    SORT
}

/**
 * FIX 5.8.9d-3c2b1: тип ожидаемого выбора для проблемной пробы.
 *
 * ALREADY_FOUND — проба уже отмечена.
 * POSTPONED     — проба отложена.
 */
enum class PendingMarkChoiceType {
    ALREADY_FOUND,
    POSTPONED
}

/**
 * FIX 5.8.9d-3c2b1: контекст ожидаемого выбора.
 *
 * Храним ordinal и sampleNumber, чтобы рендерер и ViewModel могли
 * построить одинаковый ответ для UI и ГП.
 */
data class PendingMarkChoice(
    val type: PendingMarkChoiceType,
    val ordinal: Int,
    val sampleNumber: String
)

/**
 * Контекст голосовой сессии.
 *
 * FIX 5.8.9f-2b: поле `mode` переведено на Compose mutableStateOf.
 * Теперь чип SEARCH/SORT рядом с кнопкой 🎤 автоматически
 * перерисовывается при переключении режима — и голосом, и тапом.
 *
 * FIX 5.8.9d-2a: добавлены currentSampleNumber / currentSampleOrdinal —
 * контекст «какая проба сейчас на экране». Нужен для команды «отметь»
 * (MarkCurrent) без порядкового номера.
 *
 * FIX 5.8.9d-3c2b1: добавлено состояние pendingMarkChoice для ситуаций,
 * когда ГП должен спросить: «снять / отложить / пропустить?».
 */
class VoiceSession {

    var currentOrderTitle: String? = null
    var currentOrderId: Long? = null
    var currentAreaTitle: String? = null
    var currentQuery: String? = null
    var currentWellNumber: String? = null

    /**
     * Номер пробы (sample_number), найденной последним поиском.
     * null — последний поиск нашёл скважину, а не пробу.
     */
    var currentSampleNumber: String? = null

    /**
     * Порядковый номер пробы внутри скважины (numberInWell).
     * Используется для ответа «Третья отмечена» в 5.8.9d-2b.
     */
    var currentSampleOrdinal: Int? = null

    var lastMarkedRowId: String? = null
    var lastMarkedSampleNumber: String? = null
    var isAutoMode: Boolean = false
    var isPaused: Boolean = false

    /**
     * Текущий режим. По умолчанию — SEARCH.
     * Сбрасывается при clear() и advanceToNext().
     *
     * Compose State — чтобы UI читал значение реактивно.
     */
    var mode: VoiceSessionMode by mutableStateOf(VoiceSessionMode.SEARCH)

    /**
     * true — ГП только что спросил «Вес?» и ждёт ответа.
     */
    var awaitingWeight: Boolean = false

    /**
     * true — ГП только что ответил «Найден в нескольких нарядах».
     */
    var awaitingContinue: Boolean = false

    /**
     * FIX 5.8.9d-3c2b1:
     * true — ГП ждёт выбор действия для уже отмеченной / отложенной пробы.
     *
     * Compose State — чтобы UI/диалог мог реактивно показать подсказку.
     */
    var pendingMarkChoice: PendingMarkChoice? by mutableStateOf<PendingMarkChoice?>(null)

    /**
     * FIX 5.8.9d-3c2b1:
     * Было ли сессионное ожидание выбора запущено поверх ручной паузы.
     * Нужно, чтобы после выбора корректно восстановить isPaused.
     */
    private var pausedBeforePending: Boolean = false

    val hasContext: Boolean
        get() = currentOrderId != null && currentQuery != null

    /**
     * FIX 5.8.9d-3c2b1:
     * Запустить ожидание выбора и автоматически поставить сессию на паузу.
     *
     * Пауза нужна, чтобы ГП не продолжал слушать фоновые команды,
     * пока пользователь не ответил на уточняющий вопрос.
     */
    fun startPendingMarkChoice(
        type: PendingMarkChoiceType,
        ordinal: Int,
        sampleNumber: String
    ) {
        if (pendingMarkChoice == null) {
            pausedBeforePending = isPaused
        }

        pendingMarkChoice = PendingMarkChoice(
            type = type,
            ordinal = ordinal,
            sampleNumber = sampleNumber
        )

        awaitingWeight = false
        awaitingContinue = false
        isPaused = true
    }

    /**
     * FIX 5.8.9d-3c2b1:
     * Очистить ожидание выбора и восстановить прежний флаг паузы.
     */
    fun clearPendingMarkChoice() {
        if (pendingMarkChoice == null) {
            return
        }

        pendingMarkChoice = null
        isPaused = pausedBeforePending
        pausedBeforePending = false
    }

    fun clear() {
        currentOrderTitle = null
        currentOrderId = null
        currentAreaTitle = null
        currentQuery = null
        currentWellNumber = null
        currentSampleNumber = null
        currentSampleOrdinal = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        isPaused = false
        mode = VoiceSessionMode.SEARCH
        awaitingWeight = false
        awaitingContinue = false
        pendingMarkChoice = null
        pausedBeforePending = false
    }

    fun advanceToNext() {
        currentQuery = null
        currentWellNumber = null
        currentSampleNumber = null
        currentSampleOrdinal = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        mode = VoiceSessionMode.SEARCH
        awaitingWeight = false
        awaitingContinue = false
        pendingMarkChoice = null
        pausedBeforePending = false
        isPaused = false
    }
}
