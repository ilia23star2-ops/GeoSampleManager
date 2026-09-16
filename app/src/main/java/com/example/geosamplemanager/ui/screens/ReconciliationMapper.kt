package com.example.geosamplemanager.ui.screens

import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity

/**
 * Мапперы между сущностями БД и UI-моделями экрана «Сверка и поиск».
 *
 * Никакой бизнес-логики — только перевод полей.
 * Никаких Composable.
 */

// ====================================================================
// sample_type / status: строки БД ↔ enum
// ====================================================================

private fun sampleTypeFromDb(code: String?): SampleType =
    SampleType.values().firstOrNull { it.dbCode == code } ?: SampleType.AUGER

private fun statusFromDb(code: String?): SampleStatus =
    SampleStatus.values().firstOrNull { it.dbCode == code } ?: SampleStatus.NORMAL

// ====================================================================
// Entity → UI
// ====================================================================

/**
 * SampleEntity → SampleRow.
 *
 * FIX 5.8.9bug-3-fix-2: serialNumber берётся из БД как есть.
 * Раньше UI показывал индекс в отфильтрованном списке, из-за чего
 * п/п «прыгал» при фильтрации. Теперь это стабильный порядковый
 * номер в наряде.
 *
 * numberInWell вычисляется из sampleNumber и wellNumber:
 * отбрасываем префикс, равный wellNumber, парсим остаток как Int.
 * Если распарсить не удалось — 0.
 */
fun SampleEntity.toRow(groupId: String): SampleRow {
    val numberInWell = extractNumberInWell(sampleNumber, wellNumber)
    return SampleRow(
        id = id.toString(),
        groupId = groupId,
        serialNumber = serialNumber,
        wellNumber = wellNumber,
        sampleNumber = sampleNumber,
        numberInWell = numberInWell,
        intervalFrom = intervalFrom?.toString() ?: "—",
        intervalTo = intervalTo?.toString() ?: "—",
        weight = weight,
        controlWeight = controlWeight,
        type = sampleTypeFromDb(sampleType),
        status = statusFromDb(status),
        characteristic = materialDesc ?: "—",
        found = found,
        postponed = postponed,
        weightControl = weightControl,
        hasNote = hasNote,
        hasPhoto = hasPhoto,
        hasImportError = isImportError()
    )
}

/**
 * Проверка: проба помечена как «ошибка» — только по типу/полю.
 * Сейчас — совпадение полного номера с номером скважины.
 */
private fun SampleEntity.isImportError(): Boolean {
    return sampleNumber == wellNumber && sampleNumber.isNotEmpty()
}

/**
 * Извлекает порядковый номер пробы в скважине.
 */
fun extractNumberInWell(sampleNumber: String, wellNumber: String): Int {
    if (wellNumber.isEmpty() || !sampleNumber.startsWith(wellNumber)) return 0
    val suffix = sampleNumber.substring(wellNumber.length)
    return suffix.toIntOrNull() ?: 0
}

// ====================================================================
// UI → Entity
// ====================================================================

/**
 * Обновляет поля существующей SampleEntity на основе правок из SampleRow.
 * id, orderId, serialNumber — не трогаются.
 */
fun SampleEntity.applyRow(row: SampleRow): SampleEntity {
    return copy(
        sampleNumber = row.sampleNumber,
        wellNumber = row.wellNumber,
        intervalFrom = row.intervalFrom.toDoubleOrNull(),
        intervalTo = row.intervalTo.toDoubleOrNull(),
        weight = row.weight,
        controlWeight = row.controlWeight,
        sampleType = row.type.dbCode,
        status = row.status.dbCode,
        materialDesc = row.characteristic.takeIf { it != "—" },
        found = row.found,
        postponed = row.postponed,
        weightControl = row.weightControl,
        hasNote = row.hasNote,
        hasPhoto = row.hasPhoto
    )
}

// ====================================================================
// Группировка: соберём SampleGroup из пробы + метаданные наряда/участка
// ====================================================================

fun buildSampleGroup(
    order: OrderEntity,
    area: AreaEntity?,
    samples: List<SampleEntity>
): SampleGroup {
    val groupId = order.id.toString()
    return SampleGroup(
        id = groupId,
        areaTitle = area?.areaName ?: "Без участка",
        orderTitle = "Наряд №${order.orderNumber}",
        subtitle = "",
        rows = samples.map { it.toRow(groupId) }
    )
}