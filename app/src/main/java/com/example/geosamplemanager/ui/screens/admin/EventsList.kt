package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.stats.EventLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.10-stat-admin-ui-2:
 * Лента событий дня. Плашка-фильтр «✕ N · ⚠ M» сверху — тап
 * включает режим «только warn и error». Показываем до 200 событий,
 * больше — с пометкой. Для одного дня этого достаточно.
 */
@Composable
fun EventsList(events: List<EventView>) {
    if (events.isEmpty()) return

    var onlyProblems by remember { mutableStateOf(false) }

    val errors = events.count { it.level == EventLevel.ERROR }
    val warns = events.count { it.level == EventLevel.WARN }
    val filtered = if (onlyProblems) {
        events.filter { it.level == EventLevel.ERROR || it.level == EventLevel.WARN }
    } else events

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "События",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "${events.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (errors > 0 || warns > 0) {
                Spacer(Modifier.height(8.dp))
                FilterChip(
                    selected = onlyProblems,
                    onClick = { onlyProblems = !onlyProblems },
                    label = {
                        Text(
                            "✕ $errors · ⚠ $warns",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (filtered.isEmpty()) {
                Text(
                    "Нет событий под фильтр.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                filtered.take(MAX_EVENTS).forEach { event ->
                    EventRow(event)
                }
                if (filtered.size > MAX_EVENTS) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Показаны первые $MAX_EVENTS из ${filtered.size}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: EventView) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(levelColor(event.level), RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            formatTime(event.atTs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Text(
            event.summary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            event.category.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun levelColor(level: EventLevel): Color = when (level) {
    EventLevel.INFO -> Color(0xFF9E9E9E)
    EventLevel.WARN -> Color(0xFFFF9800)
    EventLevel.ERROR -> Color(0xFFE53935)
}

private fun formatTime(ts: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ts))
}

private const val MAX_EVENTS = 200