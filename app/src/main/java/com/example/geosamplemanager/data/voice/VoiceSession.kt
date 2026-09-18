package com.example.geosamplemanager.data.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Режим голосовой сессии.
 *
 *   SEARCH — поиск со статистикой и отметками (по умолчанию).
 *   SORT   — сортировка: только «X — наряд Y», без отметок.
 *
 * FIX 5.8.9f-1a-fix-1: имя изменено с VoiceMode на VoiceSessionMode —
 * чтобы не конфликтовать с VoiceMode из VoiceSettings.kt
 * (там режим пользователя: NOVICE / EXPERIENCED).
 */
enum class VoiceSessionMode {
    SEARCH,
    SORT
}

/**
 * Контекст голосовой сессии.
 *
 * FIX 5.8.9f-2b: поле `mode` переведено на Compose mutableStateOf.
 * Теперь чип SEARCH/SORT рядом с кнопкой 🎤 автоматически
 * перерисовывается при переключении режима — и голосом, и тапом.
 */
class VoiceSession {

    var currentOrderTitle: String? = null
    var currentOrderId: Long? = null
    var currentAreaTitle: String? = null
    var currentQuery: String? = null
    var currentWellNumber: String? = null
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

    val hasContext: Boolean
        get() = currentOrderId != null && currentQuery != null

    fun clear() {
        currentOrderTitle = null
        currentOrderId = null
        currentAreaTitle = null
        currentQuery = null
        currentWellNumber = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        isPaused = false
        mode = VoiceSessionMode.SEARCH
        awaitingWeight = false
        awaitingContinue = false
    }

    fun advanceToNext() {
        currentQuery = null
        currentWellNumber = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        mode = VoiceSessionMode.SEARCH
        awaitingWeight = false
        awaitingContinue = false
    }
}
