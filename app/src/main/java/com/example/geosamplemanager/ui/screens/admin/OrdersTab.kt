package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.10-stat-admin-v2-orders-b:
 * Таб «Наряды» — список всех нарядов из основной БД с прогрессом
 * и временем из order_work.
 *
 * Формат:
 *  - свёрнутые секции по участку (тап — раскрыть);
 *  - заголовок секции: «Участок (N) · X в работе»;
 *  - строка наряда: точка статуса, название, прогресс-бар,
 *    «X/Y (Z%)», статус, время (общее / поиск / сверка);
 *  - при отсутствии order_work — «Время: —».
 *
 * Ограничение: показываем первые 30 после фильтра. Если больше —
 * под списком счётчик «Показано 30 из N».
 */
private const val ORDERS_LIMIT = 30

@Composable
fun OrdersTab(
    allOrders: List<AdminOrderSummary>,
    filterInput: String,
    onFilterChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filtered = remember(allOrders, filterInput) {
        AdminPanelAggregator.filterOrders(allOrders, filterInput)
    }
    val shown = remember(filtered) { filtered.take(ORDERS_LIMIT) }
    val grouped = remember(shown) {
        shown.groupBy { it.areaTitle }.toSortedMap()
    }

    // Раскрытые участки. По умолчанию все свёрнуты.
    var expandedAreas by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = modifier.fillMaxSize()) {
        FilterField(
            input = filterInput,
            onChange = onFilterChange,
            onClear = { onFilterChange("") }
        )

        if (allOrders.isEmpty()) {
            EmptyState()
        } else if (shown.isEmpty()) {
            NoMatches(filterInput)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                grouped.forEach { (areaTitle, orders) ->
                    item(key = "header_$areaTitle") {
                        AreaHeader(
                            areaTitle = areaTitle,
                            orders = orders,
                            expanded = areaTitle in expandedAreas,
                            onToggle = {
                                expandedAreas = if (areaTitle in expandedAreas)
                                    expandedAreas - areaTitle
                                else expandedAreas + areaTitle
                            }
                        )
                    }
                    if (areaTitle in expandedAreas) {
                        items(orders, key = { it.orderId }) { order ->
                            OrderCard(order)
                        }
                    }
                }

                if (filtered.size > shown.size) {
                    item(key = "footer") {
                        Text(
                            "Показано ${shown.size} из ${filtered.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterField(
    input: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = input,
        onValueChange = onChange,
        label = { Text("Фильтр") },
        placeholder = { Text("Участок / наряд / id") },
        singleLine = true,
        trailingIcon = {
            if (input.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Очистить")
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun AreaHeader(
    areaTitle: String,
    orders: List<AdminOrderSummary>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val inProgress = orders.count { it.status == AdminOrderStatus.IN_PROGRESS }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (expanded) Icons.Default.KeyboardArrowUp
            else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            areaTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            buildString {
                append("(${orders.size})")
                if (inProgress > 0) append(" · $inProgress в работе")
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OrderCard(order: AdminOrderSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(order.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    order.orderTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (order.totalSamples == 0) "0/0 проб"
                    else "${order.foundSamples}/${order.totalSamples} (${order.percent}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = if (order.totalSamples == 0) 0f
                else order.foundSamples.toFloat() / order.totalSamples,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Статус: ${order.status.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (order.hasWork) {
                    Text(
                        "Общее: ${AdminPanelAggregator.formatDuration(order.totalSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Поиск: ${formatNullableDuration(order.searchSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Сверка: ${formatNullableDuration(order.verifySec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Время: —",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: AdminOrderStatus) {
    val color = when (status) {
        AdminOrderStatus.DONE -> Color(0xFF4CAF50)
        AdminOrderStatus.IN_PROGRESS -> Color(0xFFFFC107)
        AdminOrderStatus.NOT_STARTED -> Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(color, CircleShape)
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "В базе нет нарядов.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoMatches(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "По запросу «$query» ничего не найдено.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatNullableDuration(sec: Int?): String {
    if (sec == null) return "—"
    return AdminPanelAggregator.formatDuration(sec)
}