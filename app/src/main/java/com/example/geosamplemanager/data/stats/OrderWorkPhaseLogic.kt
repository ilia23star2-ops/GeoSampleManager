package com.example.geosamplemanager.data.stats

/**
 * FIX 5.10-stat-activity-b:
 * Чистая логика фаз наряда. Не зависит от Android —
 * тестируется в JVM.
 *
 * FIX 5.10-stat-activity-b-2:
 *  - accumulate() — разложение миллисекунд на (накопленные секунды,
 *    сдвиг phaseStartedAt). Исправляет баг «0 сек при быстрой работе».
 *
 * FIX 5.10-stat-activity-b-2 (смена модели фаз):
 *  - фазу определяет ПОСЛЕДНЕЕ событие: поиск → SEARCH,
 *    отметка → VERIFY;
 *  - убраны decideAfterSearch и decideAfterMark — порог «5 событий
 *    без отметок» больше не используется;
 *  - правило «интервал идёт в фазу, которой начался» реализовано
 *    прямо в OrderWorkTracker и в computePhases.
 */
object OrderWorkPhaseLogic {

    /**
     * FIX 5.10-stat-activity-b-2:
     * Результат разложения прошедшего времени.
     */
    data class AccumulationResult(
        /** Сколько целых секунд накопить в текущую фазу. */
        val seconds: Int,
        /**
         * На сколько миллисекунд сдвинуть phaseStartedAt:
         *  - ≥ 0 — обычный случай, сдвиг = seconds * 1000;
         *  - −1 — маркер простоя: вызывающая сторона должна
         *    сбросить phaseStartedAt на `now`.
         */
        val advanceMs: Long
    )

    /**
     * Статус наряда.
     *  - done: все пробы отмечены;
     *  - half_done: сверка началась, но не завершена;
     *  - in_progress: только поиск.
     *
     * При totalSamples = 0 «done» не ставим — нечего «завершать».
     */
    fun computeStatus(
        totalSamples: Int,
        foundSamples: Int,
        hasVerifyActivity: Boolean
    ): OrderWorkStatus {
        if (totalSamples > 0 && foundSamples >= totalSamples) {
            return OrderWorkStatus.DONE
        }
        if (hasVerifyActivity) {
            return OrderWorkStatus.HALF_DONE
        }
        return OrderWorkStatus.IN_PROGRESS
    }

    /**
     * FIX 5.10-stat-activity-b-2:
     * Разложение миллисекунд на (секунды, сдвиг).
     *
     * Правила:
     *  - elapsedMs ≤ 0 → (0, 0) — ничего не двигаем;
     *  - elapsedMs ≥ idleGapMs → (0, −1) — простой, нужно
     *    сбросить точку отсчёта на `now`;
     *  - иначе → (elapsedMs / 1000, seconds * 1000) — накапливаем
     *    целые секунды, остаток (< 1000 мс) переносится на
     *    следующее событие.
     */
    fun accumulate(
        elapsedMs: Long,
        idleGapMs: Long
    ): AccumulationResult {
        if (elapsedMs <= 0) return AccumulationResult(0, 0)
        if (elapsedMs >= idleGapMs) return AccumulationResult(0, -1)
        val sec = (elapsedMs / 1000L).toInt()
        return AccumulationResult(sec, sec * 1000L)
    }
}