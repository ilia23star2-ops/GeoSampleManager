package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FIX 5.9-stats-compare:
 * Диалог сравнения двух объектов.
 * Объект — участок или наряд; можно сравнивать любые комбинации.
 * Метрики: Всего, Найдено, Не найдено, Холостые, ВК, Отложено, Ошибки.
 *
 * FIX 5.9-stats-compare-2:
 *  - Поля выбора открывают пикер (TargetPickerDialog).
 *  - В пикере: поиск сверху, дерево участок → наряд.
 *  - У наряда — цветной кружок статуса и название
 *    (Пустой / Не начат / В работе / Проверить / Готов).
 *  - Один и тот же объект нельзя выбрать дважды.
 */

// ====================================================================
// ЦЕЛИ
// ====================================================================

sealed interface CompareTarget {
    val id: String
    val displayTitle: String
    val subtitle: String

    data class Order(
        override val id: String,
        override val displayTitle: String,
        override val subtitle: String,
        val status: OrderStatus
    ) : CompareTarget

    data class Area(
        override val id: String,
        override val displayTitle: String,
        override val subtitle: String
    ) : CompareTarget
}

/** Только объекты верхнего уровня: участки и наряды (для пикера). */
data class AreaGroup(
    val areaId: Long,
    val areaName: String,
    val areaOrderCount: Int,
    val orders: List<CompareTarget.Order>
)

fun buildAreaGroups(data: StatsData): List<AreaGroup> {
    return data.areas.map { area ->
        AreaGroup(
            areaId = area.areaId,
            areaName = area.areaName,
            areaOrderCount = area.orders.size,
            orders = area.orders.map { order ->
                CompareTarget.Order(
                    id = "order_${order.orderId}",
                    displayTitle = "Наряд №${order.orderNumber}",
                    subtitle = area.areaName,
                    status = computeOrderStatus(order.stats)
                )
            }
        )
    }
}

fun areaTarget(area: AreaGroup): CompareTarget.Area =
    CompareTarget.Area(
        id = "area_${area.areaId}",
        displayTitle = area.areaName,
        subtitle = "${area.areaOrderCount} наряд(ов)"
    )

fun statsForTarget(data: StatsData, target: CompareTarget): GroupStats {
    return when (target) {
        is CompareTarget.Area ->
            data.areas.firstOrNull { "area_${it.areaId}" == target.id }?.stats
                ?: GroupStats()
        is CompareTarget.Order ->
            data.areas.asSequence()
                .flatMap { it.orders.asSequence() }
                .firstOrNull { "order_${it.orderId}" == target.id }
                ?.stats
                ?: GroupStats()
    }
}

// ====================================================================
// ГЛАВНЫЙ ДИАЛОГ СРАВНЕНИЯ
// ====================================================================

@Composable
fun CompareDialog(
    data: StatsData,
    initialLeft: CompareTarget?,
    initialRight: CompareTarget?,
    onDismiss: () -> Unit
) {
    var left by remember { mutableStateOf(initialLeft) }
    var right by remember { mutableStateOf(initialRight) }

    var pickerForLeft by remember { mutableStateOf(false) }
    var pickerForRight by remember { mutableStateOf(false) }

    val leftStats = left?.let { statsForTarget(data, it) } ?: GroupStats()
    val rightStats = right?.let { statsForTarget(data, it) } ?: GroupStats()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сравнение") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TargetField(
                    label = "Объект 1",
                    target = left,
                    onClick = { pickerForLeft = true }
                )

                TargetField(
                    label = "Объект 2",
                    target = right,
                    onClick = { pickerForRight = true }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (left != null && right != null) {
                    CompareStatsTable(
                        leftLabel = left!!.displayTitle,
                        rightLabel = right!!.displayTitle,
                        leftStats = leftStats,
                        rightStats = rightStats
                    )
                } else {
                    Text(
                        "Выберите два объекта для сравнения",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )

    if (pickerForLeft) {
        TargetPickerDialog(
            title = "Объект 1",
            data = data,
            selected = left,
            disabledId = right?.id,
            onSelect = {
                left = it
                pickerForLeft = false
            },
            onDismiss = { pickerForLeft = false }
        )
    }

    if (pickerForRight) {
        TargetPickerDialog(
            title = "Объект 2",
            data = data,
            selected = right,
            disabledId = left?.id,
            onSelect = {
                right = it
                pickerForRight = false
            },
            onDismiss = { pickerForRight = false }
        )
    }
}

// ====================================================================
// ПОЛЕ-КНОПКА ВЫБОРА
// ====================================================================

@Composable
private fun TargetField(
    label: String,
    target: CompareTarget?,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable { onClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (target != null) {
                    Text(
                        target.displayTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        target.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        "Выбрать объект",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (target is CompareTarget.Order) {
                StatusDot(target.status)
                Spacer(Modifier.width(6.dp))
                Text(
                    target.status.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(target.status),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(
                Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ====================================================================
// ПИКЕР С ПОИСКОМ И ДЕРЕВОМ
// ====================================================================

@Composable
private fun TargetPickerDialog(
    title: String,
    data: StatsData,
    selected: CompareTarget?,
    disabledId: String?,
    onSelect: (CompareTarget) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val groups = remember(data) { buildAreaGroups(data) }

    val normQuery = query.trim().lowercase()

    // Фильтруем группы и их наряды.
    val visibleGroups: List<Pair<AreaGroup, List<CompareTarget.Order>>> = remember(groups, normQuery) {
        if (normQuery.isEmpty()) {
            groups.map { it to it.orders }
        } else {
            groups.mapNotNull { group ->
                val areaMatches = group.areaName.lowercase().contains(normQuery)
                val matchingOrders = if (areaMatches) {
                    group.orders
                } else {
                    group.orders.filter { o ->
                        o.displayTitle.lowercase().contains(normQuery)
                    }
                }
                if (areaMatches || matchingOrders.isNotEmpty()) {
                    group to matchingOrders
                } else null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выбор · $title") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "Поиск участка или наряда",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(
                                onClick = { query = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Очистить",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }),
                    textStyle = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(8.dp))

                if (visibleGroups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Ничего не найдено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        visibleGroups.forEach { (group, matchingOrders) ->
                            val areaTarget = areaTarget(group)
                            val areaDisabled = areaTarget.id == disabledId
                            val areaSelected = selected?.id == areaTarget.id

                            item(key = "area_${group.areaId}") {
                                AreaPickerRow(
                                    area = group,
                                    selected = areaSelected,
                                    enabled = !areaDisabled,
                                    onSelect = { onSelect(areaTarget) }
                                )
                            }

                            items(matchingOrders, key = { "order_${it.id}" }) { order ->
                                val orderDisabled = order.id == disabledId
                                val orderSelected = selected?.id == order.id

                                OrderPickerRow(
                                    order = order,
                                    selected = orderSelected,
                                    enabled = !orderDisabled,
                                    onSelect = { onSelect(order) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun AreaPickerRow(
    area: AreaGroup,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(enabled = enabled) { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            area.areaName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${area.areaOrderCount} наряд(ов)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun OrderPickerRow(
    order: CompareTarget.Order,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable(enabled = enabled) { onSelect() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(order.status)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                order.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                order.status.title,
                style = MaterialTheme.typography.labelSmall,
                color = statusColor(order.status),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

// ====================================================================
// СТАТУС
// ====================================================================

@Composable
private fun StatusDot(status: OrderStatus) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(50))
            .background(statusColor(status))
    )
}

private fun statusColor(status: OrderStatus): Color = when (status) {
    OrderStatus.READY -> Color(0xFF2E7D32)
    OrderStatus.NEEDS_REVIEW -> Color(0xFF1976D2)
    OrderStatus.IN_PROGRESS -> Color(0xFFF9A825)
    OrderStatus.NOT_STARTED -> Color(0xFF9E9E9E)
    OrderStatus.EMPTY -> Color(0xFF424242)
}

// ====================================================================
// ТАБЛИЦА СРАВНЕНИЯ
// ====================================================================

@Composable
private fun CompareStatsTable(
    leftLabel: String,
    rightLabel: String,
    leftStats: GroupStats,
    rightStats: GroupStats
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Text(
                "Метрика",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1.4f)
            )
            Text(
                leftLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                rightLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        val rows = listOf(
            Triple("Всего", leftStats.total, rightStats.total),
            Triple("Найдено", leftStats.found, rightStats.found),
            Triple("Не найдено", leftStats.notFound, rightStats.notFound),
            Triple("Холостые", leftStats.blanks, rightStats.blanks),
            Triple("ВК", leftStats.weightControls, rightStats.weightControls),
            Triple("Отложено", leftStats.postponed, rightStats.postponed),
            Triple("Ошибки", leftStats.errors, rightStats.errors)
        )

        rows.forEachIndexed { idx, (label, l, r) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (idx % 2 == 0) Color.Transparent
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1.4f)
                )
                Text(
                    l.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    r.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}