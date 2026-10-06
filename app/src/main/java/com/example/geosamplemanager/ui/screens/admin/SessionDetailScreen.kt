package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.10-stat-admin-v2-details-a:
 * Экран «Сессия #N».
 *
 *  - заголовок: время, длительности, badge краша (У4=А);
 *  - таймлайн визитов внутри сессии;
 *  - список визитов — тап открывает детали.
 *
 * FIX 5.10-stat-admin-v2-details-a (фикс компиляции):
 *  - formatTime → formatEventTime (из EventRowExpandable.kt).
 */
@Composable
fun SessionDetailScreen(
    session: SessionView,
    onOpenVisit: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SessionHeader(session = session)
        SessionTimelineBar(session = session)
        Text(
            "Визиты",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        if (session.visits.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "В сессии не зафиксировано ни одного визита.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            session.visits.forEach { v ->
                VisitRow(visit = v, onClick = { onOpenVisit(session.id, v.fromTs) })
            }
        }
    }
}

@Composable
private fun SessionHeader(session: SessionView) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Сессия #${session.id}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(formatEventTime(session.startedAt))
                    append(" → ")
                    append(session.endedAt?.let { formatEventTime(it) } ?: "—")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Активно: ${AdminPanelAggregator.formatDuration(session.activeSec)} · " +
                        "Простой: ${AdminPanelAggregator.formatDuration(session.idleSec)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (session.crashFlag) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.errorContainer,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "⚠ Сессия не завершена",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionTimelineBar(session: SessionView) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Таймлайн сессии",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        RoundedCornerShape(4.dp)
                    )
            ) {
                if (session.visits.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize())
                } else {
                    session.visits.forEach { v ->
                        val dur = (v.durationSec.coerceAtLeast(1)).toFloat()
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(dur)
                                .background(
                                    tabColor(v.tab).copy(alpha = 0.7f),
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatEventTime(session.startedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    session.endedAt?.let { formatEventTime(it) } ?: "в процессе",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VisitRow(visit: TabVisitView, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(tabColor(visit.tab), RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    visit.tab.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${formatEventTime(visit.fromTs)} → " +
                            (visit.toTs?.let { formatEventTime(it) } ?: "—"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                AdminPanelAggregator.formatDuration(visit.durationSec),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}