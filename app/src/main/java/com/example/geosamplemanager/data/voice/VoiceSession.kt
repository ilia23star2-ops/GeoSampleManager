package com.example.geosamplemanager.data.voice

/**
 * Контекст голосовой сессии.
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
     * true — ГП только что спросил «Вес?» и ждёт ответа.
     * Следующая фраза будет распарсена как вес, а не как поиск.
     */
    var awaitingWeight: Boolean = false

    /**
     * true — ГП только что ответил «Найден в нескольких нарядах».
     * Следующая фраза обрабатывается только как «продолжить» / «стоп» / «пауза».
     * Остальные команды игнорируются с подсказкой.
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
        awaitingWeight = false
        awaitingContinue = false
    }

    fun advanceToNext() {
        currentQuery = null
        currentWellNumber = null
        lastMarkedRowId = null
        lastMarkedSampleNumber = null
        isAutoMode = false
        awaitingWeight = false
        awaitingContinue = false
    }
}