package com.example.geosamplemanager.data.stats

/**
 * FIX 5.10-stat-activity-b:
 * Чистая логика переходов фаз наряда. Не зависит от Android —
 * тестируется в JVM.
 *
 * Решения:
 *  - после поиска: остаёмся в текущей фазе, но если были в
 *    «Сверке» и накопилось `threshold` событий без отметок —
 *    возвращаемся в «Поиск»;
 *  - после отметки: всегда «Сверка»;
 *  - статус: done / half_done / in_progress.
 */
object OrderWorkPhaseLogic {

    /**
     * Решение после события поиска.
     * Возврат в «Поиск» — только если была «Сверка» и накопилось
     * `threshold` событий без отметок.
     */
    fun decideAfterSearch(
        currentPhase: OrderWorkPhase,
        eventsSinceLastMark: Int,
        threshold: Int
    ): OrderWorkPhase {
        if (currentPhase == OrderWorkPhase.VERIFY &&
            eventsSinceLastMark >= threshold
        ) {
            return OrderWorkPhase.SEARCH
        }
        return currentPhase
    }

    /**
     * Решение после отметки. Отметка всегда переводит наряд
     * в «Сверку» — даже если он уже был в «Сверке».
     */
    fun decideAfterMark(currentPhase: OrderWorkPhase): OrderWorkPhase {
        return OrderWorkPhase.VERIFY
    }

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
}