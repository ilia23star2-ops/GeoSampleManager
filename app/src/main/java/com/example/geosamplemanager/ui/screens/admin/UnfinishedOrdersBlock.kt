package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.10-stat-admin-ui-5a:
 * Блок «Незавершённые» (§7.6). Список нарядов, которые оператор
 * начал, но не закрыл (status != DONE).
 *
 * Формат строки:
 *   ▸ Наряд 27 · Коптеловский · 8/15 (53%)
 *     Поиск: 🟢 готов · Сверка: 🟡 в работе · Общий: 🟡 в сверке
 *
 * Если список пуст — блок скрывается.
 */
@Composable
fun UnfinishedOrdersBlock(orders: List<OrderWorkView>) {
    if (orders.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Незавершённые",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${orders.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            orders.forEach { order ->
                UnfinishedOrderRow(order)
            }
        }
    }
}

@Composable
private fun UnfinishedOrderRow(order: OrderWorkView) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "▸ ${order.orderTitle} · ${order.areaTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${order.foundSamples}/${order.totalSamples} (${order.percent}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhaseChip(label = "Поиск", sec = order.searchSec)
            PhaseChip(label = "Сверка", sec = order.verifySec)
            StatusChip(label = order.statusLabel, status = order.status)
        }
    }
}

/**
 * Фаза наряда. Цвет точки: 🟢 если секунды > 0 (была активность),
 * 🟡 если ещё нет.
 */
@Composable
private fun PhaseChip(label: String, sec: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (sec > 0) Color(0xFF4CAF50) else Color(0xFFFFC107),
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "$label: ${AdminPanelAggregator.formatDuration(sec)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusChip(label: String, status: com.example.geosamplemanager.data.stats.OrderWorkStatus) {
    val color = when (status) {
        com.example.geosamplemanager.data.stats.OrderWorkStatus.IN_PROGRESS ->
            MaterialTheme.colorScheme.primary
        com.example.geosamplemanager.data.stats.OrderWorkStatus.HALF_DONE ->
            MaterialTheme.colorScheme.tertiary
        com.example.geosamplemanager.data.stats.OrderWorkStatus.DONE ->
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        "Общий: $label",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold
    )
}