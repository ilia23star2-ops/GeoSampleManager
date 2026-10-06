package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.stats.EventLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.10-stat-admin-v2-details-a:
 * Строка события с раскрытием detailsJson по тапу.
 *
 * FIX 5.10-stat-admin-v2-details-a (фикс компиляции):
 *  - функции переименованы (levelColor → eventLevelColor,
 *    formatTime → formatEventTime), чтобы не конфликтовать с
 *    EventsList.kt, где уже есть private с теми же именами.
 */
@Composable
fun EventRowExpandable(event: EventView) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(eventLevelColor(event.level), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatEventTime(event.atTs),
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
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            DetailsBlock(event.detailsJson)
        }
    }
}

@Composable
private fun DetailsBlock(detailsJson: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(4.dp)
            )
            .padding(8.dp)
    ) {
        if (detailsJson.isNullOrBlank()) {
            Text(
                "Нет дополнительных данных",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Details:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                detailsJson,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                )
            )
        }
    }
}

internal fun eventLevelColor(level: EventLevel): Color = when (level) {
    EventLevel.INFO -> Color(0xFF9E9E9E)
    EventLevel.WARN -> Color(0xFFFF9800)
    EventLevel.ERROR -> Color(0xFFE53935)
}

internal fun formatEventTime(ts: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(ts))
}