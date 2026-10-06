package com.example.geosamplemanager.data.stats

/**
 * FIX 5.10-stat-errors-a:
 * Правила авто-повышения событий в уровень «Внимание» (warn).
 *
 * Реализовано (SHADOW_STATS §4.7):
 *  - 5+ неудачных поисков подряд → warn;
 *  - 3+ одинаковых ошибки подряд → warn.
 *
 * Не реализовано в этой пачке (отдельный заход):
 *  - «краш + удаление БД за 5 мин» — требует связки сессий
 *    и db-операций.
 *
 * Чистая логика без Android и БД — тестируется в JVM.
 * Счётчики сбрасываются при срабатывании правила и при reset()
 * (новая сессия).
 *
 * Семантика счётчиков:
 *  - «подряд» — сброс на любом несоответствии;
 *  - после срабатывания счётчик сбрасывается, чтобы не спамить
 *    warn на каждое следующее неудачное событие.
 */
object AutoWarnRules {

    /** Порог неудачных поисков подряд. */
    internal const val NOT_FOUND_THRESHOLD = 5

    /** Порог одинаковых ошибок подряд. */
    internal const val REPEAT_ERROR_THRESHOLD = 3

    private var consecutiveNotFound: Int = 0
    private var lastErrorKey: String? = null
    private var errorRepeatCount: Int = 0

    /**
     * Успешный поиск сбрасывает счётчик неудач. Вызывается
     * из места, где известно, что поиск дал результат.
     */
    fun onSearchSuccess() {
        consecutiveNotFound = 0
    }

    /**
     * Неудачный поиск. Возвращает true, когда порог
     * [NOT_FOUND_THRESHOLD] достигнут. После срабатывания счётчик
     * сбрасывается, чтобы не спамить warn на каждый следующий
     * неудачный поиск.
     */
    fun onSearchNotFound(): Boolean {
        consecutiveNotFound++
        if (consecutiveNotFound >= NOT_FOUND_THRESHOLD) {
            consecutiveNotFound = 0
            return true
        }
        return false
    }

    /**
     * Ошибка. Возвращает true, когда одна и та же ошибка
     * (category + summary) встретилась [REPEAT_ERROR_THRESHOLD]
     * раз подряд.
     *
     * Счётчик сбрасывается на «1», если пришла другая ошибка,
     * и в «0» — после срабатывания.
     */
    fun onError(category: String, summary: String): Boolean {
        val key = "$category|$summary"
        if (key == lastErrorKey) {
            errorRepeatCount++
        } else {
            lastErrorKey = key
            errorRepeatCount = 1
        }
        if (errorRepeatCount >= REPEAT_ERROR_THRESHOLD) {
            lastErrorKey = null
            errorRepeatCount = 0
            return true
        }
        return false
    }

    /**
     * Полный сброс. Вызывается при старте новой сессии.
     */
    fun reset() {
        consecutiveNotFound = 0
        lastErrorKey = null
        errorRepeatCount = 0
    }
}