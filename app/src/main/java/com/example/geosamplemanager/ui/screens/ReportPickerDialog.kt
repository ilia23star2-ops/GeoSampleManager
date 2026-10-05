package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-main-b:
 * Диалог выбора наряда для отчёта. Открывается с Главной по кнопке
 * «Сделать отчёт». Фильтр «Только готовые» включён по умолчанию.
 *
 * Тап на наряд — возвращает orderId. Дальше NavGraph переходит
 * в Статистику и открывает там диалог формата (HTML / Excel).
 */
@Composable
fun ReportPickerDialog(
    orders: List<OrderPickerItem>,
    initialReadyOnly: Boolean = true,
    onPick: (orderId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var readyOnly by remember { mutableStateOf(initialReadyOnly) }

    val filtered = remember(orders, readyOnly) {
        if (readyOnly) orders.filter { it.isReady } else orders
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите наряд для отчёта") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { readyOnly = !readyOnly }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = readyOnly,
                        onCheckedChange = { readyOnly = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Только готовые",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (readyOnly) "Нет готовых нарядов"
                            else "Нет нарядов",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    ) {
                        items(filtered, key = { it.orderId }) { o ->
                            ReportPickerRow(o, onClick = { onPick(o.orderId) })
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.surfaceVariant
                                    .copy(alpha = 0.5f)
                            )
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
private fun ReportPickerRow(o: OrderPickerItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dotColor = if (o.isReady) Color(0xFF2E7D32) else Color(0xFFF9A825)
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${o.areaTitle} / ${o.orderTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${o.found} из ${o.total} (${o.percent}%)" +
                        if (o.isReady) " · готов" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}