package com.example.geosamplemanager.ui.screens

import com.example.geosamplemanager.data.excel.ParsedSample

/**
 * Вспомогательные функции для отображения проб.
 */
object SampleDisplay {

    /** Человеческое название типа пробы. */
    fun typeDisplay(sample: ParsedSample): String {
        if (sample.status == "blank") return "Холостая"
        if (sample.status == "control") return "ВК"
        return when (sample.sampleType) {
            "auger" -> "Шнековая"
            "channel" -> "Бороздовая"
            "cobra" -> "Кобра"
            "duplicate" -> "Дубликат"
            else -> sample.sampleType.ifEmpty { "—" }
        }
    }

    /** Определяем, что в наборе преобладает: скважины или выработки. */
    fun isWorkings(samples: List<ParsedSample>): Boolean {
        if (samples.isEmpty()) return false
        val channels = samples.count { it.sampleType == "channel" && it.status != "blank" }
        val others = samples.count { it.sampleType != "channel" && it.status != "blank" }
        return channels > others
    }

    /** Заголовок для колонки скважины/выработки. */
    fun wellColumnTitle(samples: List<ParsedSample>): String =
        if (isWorkings(samples)) "Выработка" else "Скважина"

    /** Заголовок для счётчика. */
    fun wellCounterLabel(samples: List<ParsedSample>): String =
        if (isWorkings(samples)) "Выработок" else "Скважин"

    /** Краткая строка для веса. */
    fun weightDisplay(weight: Double?): String = weight?.toString() ?: "—"
}