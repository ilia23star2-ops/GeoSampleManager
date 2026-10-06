package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.10-stat-admin-ui-2:
 * Карточка сессии. По умолчанию свёрнута: время старта/финиша,
 * длительности, badge краша, счётчики визитов и нарядов. Тап —
 * раскрывает список визитов и работ с нарядами.
 */
@Composable
fun SessionCard(session: SessionView) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        sessionTitle(session),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        sessionStats(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (session.crashFlag) {
                    CrashBadge()
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (session.visits.isNotEmpty()) {
                    Text(
                        "Вкладки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    session.visits.forEach { v ->
                        VisitRow(v)
                    }
                }
                if (session.orderWorks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Наряды",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    session.orderWorks.forEach { w ->
                        OrderWorkRow(w)
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitRow(visit: TabVisitView) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(tabColor(visit.tab), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            visit.tab.title,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            AdminPanelAggregator.formatDuration(visit.durationSec),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OrderWorkRow(work: OrderWorkView) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${work.areaTitle} / ${work.orderTitle}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${work.foundSamples}/${work.totalSamples}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Поиск: ${AdminPanelAggregator.formatDuration(work.searchSec)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Сверка: ${AdminPanelAggregator.formatDuration(work.verifySec)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                work.status.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun CrashBadge() {
    Box(
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.errorContainer,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "⚠ Не завершена",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

private fun sessionTitle(session: SessionView): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val start = sdf.format(Date(session.startedAt))
    val end = session.endedAt?.let { sdf.format(Date(it)) } ?: "—"
    return "Сессия $start → $end"
}

private fun sessionStats(session: SessionView): String {
    val active = AdminPanelAggregator.formatDuration(session.activeSec)
    val idle = AdminPanelAggregator.formatDuration(session.idleSec)
    return "Активно: $active · Простой: $idle · " +
            "Вкладок: ${session.visits.size} · Нарядов: ${session.orderWorks.size}"
}