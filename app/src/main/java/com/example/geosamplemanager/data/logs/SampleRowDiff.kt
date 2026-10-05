package com.example.geosamplemanager.data.logs

import com.example.geosamplemanager.ui.screens.SampleRow

/**
 * FIX 5.9-logs-4b:
 * Формирует фразу с изменениями между старой и новой
 * версией пробы — для журнала. Пример:
 *
 *   «номер с «03» на «05», вес с 2.5 на 3, статус с «Обычная» на «Холостая»»
 *
 * Чистая функция. Никаких зависимостей от Android.
 *
 * Возвращает null, если ничего не изменилось — вызывающий
 * код пропускает запись в журнал.
 */
object SampleRowDiff {

    /**
     * Список изменений одной строкой. Пусто — если ничего
     * не изменилось, тогда возвращаем null.
     */
    fun diff(old: SampleRow, new: SampleRow): String? {
        val parts = mutableListOf<String>()

        if (old.sampleNumber != new.sampleNumber) {
            parts += "номер с «${old.sampleNumber}» на «${new.sampleNumber}»"
        }
        if (old.wellNumber != new.wellNumber) {
            parts += "скважина с «${old.wellNumber}» на «${new.wellNumber}»"
        }
        if (old.intervalFrom != new.intervalFrom) {
            parts += "интервал от «${formatStr(old.intervalFrom)}» " +
                    "на «${formatStr(new.intervalFrom)}»"
        }
        if (old.intervalTo != new.intervalTo) {
            parts += "интервал до «${formatStr(old.intervalTo)}» " +
                    "на «${formatStr(new.intervalTo)}»"
        }
        if (old.weight != new.weight) {
            parts += "вес с ${formatWeight(old.weight)} на ${formatWeight(new.weight)}"
        }
        if (old.controlWeight != new.controlWeight) {
            parts += "вес ВК с ${formatWeight(old.controlWeight)} " +
                    "на ${formatWeight(new.controlWeight)}"
        }
        if (old.type.dbCode != new.type.dbCode) {
            parts += "тип с «${typeLabel(old.type.dbCode)}» " +
                    "на «${typeLabel(new.type.dbCode)}»"
        }
        if (old.status.dbCode != new.status.dbCode) {
            parts += "статус с «${statusLabel(old.status.dbCode)}» " +
                    "на «${statusLabel(new.status.dbCode)}»"
        }
        if (old.characteristic != new.characteristic) {
            parts += "характеристика с «${formatStr(old.characteristic)}» " +
                    "на «${formatStr(new.characteristic)}»"
        }
        if (old.found != new.found) {
            parts += if (new.found) "отметка установлена" else "отметка снята"
        }
        if (old.postponed != new.postponed) {
            parts += if (new.postponed) "проба отложена" else "отложенность снята"
        }
        if (old.weightControl != new.weightControl) {
            parts += if (new.weightControl) "ВК установлен" else "ВК снят"
        }

        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    // ================================================================
    // Форматирование значений
    // ================================================================

    private fun formatStr(s: String): String =
        if (s.isBlank() || s == "—") "(пусто)" else s

    private fun formatWeight(w: Double?): String = when {
        w == null -> "(пусто)"
        w % 1.0 == 0.0 -> w.toInt().toString()
        else -> w.toString().replace('.', ',')
    }

    private fun typeLabel(code: String): String = when (code) {
        "auger" -> "Шнековая"
        "channel" -> "Бороздовая"
        "cobra" -> "Кобра"
        "duplicate" -> "Дубликат"
        else -> code
    }

    private fun statusLabel(code: String): String = when (code) {
        "normal" -> "Обычная"
        "blank" -> "Холостая"
        "control" -> "Весовой контроль"
        else -> code
    }
}