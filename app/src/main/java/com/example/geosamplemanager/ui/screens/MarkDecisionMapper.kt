package com.example.geosamplemanager.ui.screens

import com.example.geosamplemanager.data.reconciliation.BlankWeightPolicy
import com.example.geosamplemanager.data.reconciliation.MarkContext

/**
 * FIX 5.8.9d-3b: мост между UI-моделями и доменным ядром отметки.
 *
 * Единственное место, где UI-типы (SampleRow, BlankWeightMode,
 * BlankWeightSettings, ReconciliationState) превращаются в плоский
 * MarkContext. UI и ГП используют эту же функцию — значит, одинаковые
 * ситуации дают одинаковые решения.
 *
 * Никакой логики принятия решения — только маппинг.
 */
fun toMarkContext(state: ReconciliationState, row: SampleRow): MarkContext {
    val group = state.groupById(row.groupId)
    val settings = state.currentBlankWeight

    val policy = when (settings.mode) {
        BlankWeightMode.FIXED -> BlankWeightPolicy.FIXED
        BlankWeightMode.AVERAGE -> BlankWeightPolicy.AVERAGE
        BlankWeightMode.MANUAL -> BlankWeightPolicy.MANUAL
    }

    // Средний вес соседей считаем только для холостой без веса — как
    // в старом onToggleFound. Для остальных проб не тратим время.
    val averageWeight = if (group != null && row.isBlank && row.weight == null) {
        calculateAverageNeighborWeight(group, row.id)
    } else {
        null
    }

    return MarkContext(
        hasImportError = row.hasImportError,
        found = row.found,
        postponed = row.postponed,
        weightControl = row.weightControl,
        controlWeight = row.controlWeight,
        isBlank = row.isBlank,
        weight = row.weight,
        ordinal = row.numberInWell,
        sampleNumber = row.sampleNumber,
        blankWeightMode = policy,
        fixedWeight = settings.fixedValue,
        averageWeight = averageWeight
    )
}
