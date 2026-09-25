package com.example.geosamplemanager.ui.screens

import com.example.geosamplemanager.data.voice.WeightQueueItem
import com.example.geosamplemanager.data.voice.WeightQueueKind

/**
 * FIX 5.8.11-e4-weight-queue:
 * Построение очереди веса для массовой отметки.
 *
 * Логика:
 *  1. Обычные пробы и пробы с уже заполненным весом отметятся сразу.
 *  2. Холостая без веса → нужен weight.
 *  3. ВК без controlWeight → нужен controlWeight.
 *  4. Порядок очереди — как в списке (сверху вниз).
 *
 * Пропускаем:
 *  - уже отмеченные (found == true);
 *  - с ошибкой импорта (hasImportError);
 *  - не холостые и не ВК (обычные — отметятся сразу).
 */
fun buildWeightQueue(rows: List<SampleRow>): List<WeightQueueItem> {
    val result = mutableListOf<WeightQueueItem>()

    for (row in rows) {
        if (row.found) continue
        if (row.hasImportError) continue

        when {
            row.weightControl && row.controlWeight == null -> {
                result.add(
                    WeightQueueItem(
                        sampleNumber = row.sampleNumber,
                        ordinal = row.numberInWell,
                        kind = WeightQueueKind.WEIGHT_CONTROL
                    )
                )
            }

            row.isBlank && row.weight == null -> {
                result.add(
                    WeightQueueItem(
                        sampleNumber = row.sampleNumber,
                        ordinal = row.numberInWell,
                        kind = WeightQueueKind.BLANK
                    )
                )
            }
        }
    }

    return result
}
