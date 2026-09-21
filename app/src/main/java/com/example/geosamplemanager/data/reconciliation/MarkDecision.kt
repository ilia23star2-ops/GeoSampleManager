package com.example.geosamplemanager.data.reconciliation

import kotlin.math.round

/**
 * Доменные правила отметки пробы.
 *
 * Единая точка для UI и голосового помощника. Не зависит от Android,
 * Compose, SampleRow, VoiceSession — только чистые данные и решения.
 *
 * Вызывающий маппит свои данные в [MarkContext], получает [MarkDecision],
 * и сам решает, как реагировать:
 *   • UI  → открывает нужный диалог.
 *   • ГП  → говорит фразу и играет звук.
 *
 * FIX 5.8.9d-3a: новый файл. Правило одно — реакции разные.
 */

/**
 * Решение о том, что делать при попытке отметить пробу.
 *
 * Все ветки несут [ordinal] и [sampleNumber] — чтобы рендерер фразы
 * (ГП) и заголовок диалога (UI) могли построить ответ без повторного
 * обращения к состоянию.
 */
sealed interface MarkDecision {

    /** Порядковый номер пробы в скважине (1..30). */
    val ordinal: Int

    /** Номер пробы (sample_number). Пример: «NV152401». */
    val sampleNumber: String

    /**
     * Проба уже отмечена (галка стоит).
     * UI: диалог «Снять / Отложить / Пропустить».
     * ГП: «Уже отмечена. Снять, отложить или пропустить?»
     */
    data class AlreadyFound(
        override val ordinal: Int,
        override val sampleNumber: String
    ) : MarkDecision

    /**
     * У пробы ошибка импорта.
     * UI: диалог «Принять / Редактировать».
     * ГП: «Ошибка импорта. Проверьте на экране.»
     */
    data class ImportError(
        override val ordinal: Int,
        override val sampleNumber: String
    ) : MarkDecision

    /**
     * Проба отложена.
     * UI: диалог «Отметить / Снять отложенную».
     * ГП: «Отложена. Отметить или снять?»
     */
    data class Postponed(
        override val ordinal: Int,
        override val sampleNumber: String
    ) : MarkDecision

    /**
     * Весовой контроль — нужно ввести вес.
     * UI: WeightDialog(isControl = true).
     * ГП: «Первая — весовой контроль. Вес?»
     */
    data class NeedsControlWeight(
        override val ordinal: Int,
        override val sampleNumber: String
    ) : MarkDecision

    /**
     * Холостая — нужно ввести вес вручную.
     * UI: WeightDialog(isControl = false).
     * ГП: «Первая — холостая. Вес?»
     */
    data class NeedsBlankWeight(
        override val ordinal: Int,
        override val sampleNumber: String
    ) : MarkDecision

    /**
     * Отметить с автоматически подобранным весом
     * (холостая + FIXED/AVERAGE).
     * UI: setBlankWeightAndMarkFound.
     * ГП: отметить + «Первая. Вес два и шесть.»
     */
    data class MarkWithWeight(
        override val ordinal: Int,
        override val sampleNumber: String,
        val weight: Double
    ) : MarkDecision

    /**
     * Обычная отметка. Всё в порядке.
     * UI: setFound(true).
     * ГП: отметить + «Первая отмечена.»
     */
    data class CanMark(
        override val ordinal: Int,
        override val sampleNumber: String
    ) : MarkDecision
}

/**
 * Политика холостых на наряд.
 *
 * Локальная копия `ui.screens.BlankWeightMode` — чтобы домен не зависел
 * от UI-слоя. Маппинг делается на границе (UI/ГП).
 */
enum class BlankWeightPolicy {
    /** Единый вес на наряд. */
    FIXED,

    /** Среднее соседних проб. */
    AVERAGE,

    /** Вручную — всегда спрашивать. */
    MANUAL
}

/**
 * Плоский контекст попытки отметки.
 *
 * Без rowId (вызывающий сам знает строку), без submittedWeight
 * (вес валидируется отдельно, см. [validateWeight]).
 */
data class MarkContext(
    val hasImportError: Boolean,
    val found: Boolean,
    val postponed: Boolean,
    val weightControl: Boolean,
    val controlWeight: Double?,
    val isBlank: Boolean,
    val weight: Double?,
    val ordinal: Int,
    val sampleNumber: String,
    val blankWeightMode: BlankWeightPolicy,
    val fixedWeight: Double?,
    val averageWeight: Double?
)

/**
 * Анализ: что делать при попытке отметить пробу.
 *
 * Порядок проверок строгий, первый матч побеждает:
 *   found → importError → postponed → весовые правила → CanMark.
 */
fun analyzeMark(ctx: MarkContext): MarkDecision {
    // 1. Уже отмечена — важнее ошибки: пользователь хочет снять.
    if (ctx.found) {
        return MarkDecision.AlreadyFound(ctx.ordinal, ctx.sampleNumber)
    }

    // 2. Ошибка импорта — отдельный сценарий, разбирается вручную.
    if (ctx.hasImportError) {
        return MarkDecision.ImportError(ctx.ordinal, ctx.sampleNumber)
    }

    // 3. Отложена — пользователь решает: отметить или снять.
    if (ctx.postponed) {
        return MarkDecision.Postponed(ctx.ordinal, ctx.sampleNumber)
    }

    // 4. Весовой контроль без веса — спросить вес.
    if (ctx.weightControl && ctx.controlWeight == null) {
        return MarkDecision.NeedsControlWeight(ctx.ordinal, ctx.sampleNumber)
    }

    // 5. Холостая без веса — по настройкам наряда.
    if (ctx.isBlank && ctx.weight == null) {
        return when (ctx.blankWeightMode) {
            BlankWeightPolicy.FIXED -> {
                val w = ctx.fixedWeight
                if (w != null && w > 0.0) {
                    MarkDecision.MarkWithWeight(ctx.ordinal, ctx.sampleNumber, w)
                } else {
                    MarkDecision.NeedsBlankWeight(ctx.ordinal, ctx.sampleNumber)
                }
            }
            BlankWeightPolicy.AVERAGE -> {
                val w = ctx.averageWeight
                if (w != null && w > 0.0) {
                    MarkDecision.MarkWithWeight(ctx.ordinal, ctx.sampleNumber, w)
                } else {
                    MarkDecision.NeedsBlankWeight(ctx.ordinal, ctx.sampleNumber)
                }
            }
            BlankWeightPolicy.MANUAL -> {
                MarkDecision.NeedsBlankWeight(ctx.ordinal, ctx.sampleNumber)
            }
        }
    }

    // 6. Всё остальное — обычная отметка.
    return MarkDecision.CanMark(ctx.ordinal, ctx.sampleNumber)
}

// ====================================================================
// Валидация веса
// ====================================================================

/** Дефолтный максимум веса в канонических единицах (кг). */
const val DEFAULT_MAX_WEIGHT: Double = 100.0

/**
 * Результат валидации введённого веса.
 */
sealed interface WeightValidation {

    /**
     * Вес валиден. [value] — уже округлено до сотых.
     */
    data class Ok(val value: Double) : WeightValidation

    /**
     * Вес невалиден. [reason] — причина для рендеринга.
     */
    data class Invalid(val reason: WeightInvalidReason) : WeightValidation
}

/**
 * Причины невалидного веса. Для рендера текста UI/ГП.
 */
enum class WeightInvalidReason {
    /** Значение null — не введено. */
    NULL,

    /** NaN или Infinity. */
    NOT_FINITE,

    /** Меньше или равно нулю. */
    NOT_POSITIVE,

    /** Больше допустимого максимума. */
    TOO_LARGE
}

/**
 * Проверка веса.
 *
 * Правила:
 *   • value != null;
 *   • value.isFinite();
 *   • value > 0;
 *   • value <= maxWeight (включительно);
 *   • округление до сотых молча.
 *
 * Возвращает [WeightValidation.Ok] с округлённым значением
 * или [WeightValidation.Invalid] с причиной.
 */
fun validateWeight(
    value: Double?,
    maxWeight: Double = DEFAULT_MAX_WEIGHT
): WeightValidation {
    if (value == null) {
        return WeightValidation.Invalid(WeightInvalidReason.NULL)
    }
    if (!value.isFinite()) {
        return WeightValidation.Invalid(WeightInvalidReason.NOT_FINITE)
    }
    if (value <= 0.0) {
        return WeightValidation.Invalid(WeightInvalidReason.NOT_POSITIVE)
    }
    if (value > maxWeight) {
        return WeightValidation.Invalid(WeightInvalidReason.TOO_LARGE)
    }
    val rounded = round(value * 100.0) / 100.0
    if (rounded <= 0.0) {
        return WeightValidation.Invalid(WeightInvalidReason.NOT_POSITIVE)
    }
    return WeightValidation.Ok(rounded)
}
