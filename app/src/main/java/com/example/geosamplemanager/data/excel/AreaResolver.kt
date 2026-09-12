package com.example.geosamplemanager.data.excel

import com.example.geosamplemanager.data.settings.ImportSettings
import java.util.Locale

/**
 * Определение участка по префиксам в номерах скважин.
 * Пример: KPD1090031 → префикс KPD → участок «Коптеловский» (из настроек).
 */
object AreaResolver {

    /**
     * Возвращает название участка или null, если не удалось определить.
     */
    fun resolve(wellNumbers: Collection<String>, settings: ImportSettings): String? {
        if (wellNumbers.isEmpty()) return null

        // 1) Считаем частоты префиксов
        val prefixCounts = mutableMapOf<String, Int>()
        for (well in wellNumbers) {
            val prefix = extractPrefix(well) ?: continue
            prefixCounts[prefix] = (prefixCounts[prefix] ?: 0) + 1
        }
        if (prefixCounts.isEmpty()) return null

        // 2) Для каждого участка считаем, сколько его префиксов встретилось
        var bestArea: String? = null
        var bestScore = 0
        for ((area, prefixes) in settings.areaPrefixes) {
            var score = 0
            for (p in prefixes) {
                score += prefixCounts[p.uppercase(Locale.ROOT)] ?: 0
            }
            if (score > bestScore) {
                bestScore = score
                bestArea = area
            }
        }
        return bestArea
    }

    /**
     * Извлекает буквенный префикс из номера скважины.
     * KPD1090031 → KPD
     * NV1524 → NV
     * 12345 → null
     */
    fun extractPrefix(wellNumber: String): String? {
        val trimmed = wellNumber.trim()
        if (trimmed.isEmpty()) return null
        val m = Regex("^([A-Za-zА-Яа-я]{1,6})").find(trimmed) ?: return null
        return m.groupValues[1].uppercase(Locale.ROOT)
    }
}