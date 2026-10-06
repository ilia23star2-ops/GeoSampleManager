package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.10-stat-admin-v2-details-a:
 * Экран «Визит». Список событий в интервале [fromTs, toTs).
 *
 * FIX 5.10-stat-admin-v2-details-a (фикс компиляции):
 *  - formatTime → formatEventTime.
 */
@Composable
fun VisitDetailScreen(
    visit: TabVisitView,
    allEvents: List<EventView>,
    modifier: Modifier = Modifier
) {
    val endTs = visit.toTs ?: System.currentTimeMillis()
    val events = allEvents
        .filter { it.atTs >= visit.fromTs && it.atTs < endTs }
        .sortedBy { it.atTs }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        VisitHeader(visit = visit, eventCount = events.size)
        Text(
            "Действия",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (events.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "В этом визите не зафиксировано действий.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    events.forEachIndexed { index, event ->
                        EventRowExpandable(event = event)
                        if (index < events.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisitHeader(visit: TabVisitView, eventCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            tabColor(visit.tab),
                            RoundedCornerShape(3.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    visit.tab.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append(formatEventTime(visit.fromTs))
                    append(" → ")
                    append(visit.toTs?.let { formatEventTime(it) } ?: "—")
                    append("  ·  ")
                    append(AdminPanelAggregator.formatDuration(visit.durationSec))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Действий: $eventCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}