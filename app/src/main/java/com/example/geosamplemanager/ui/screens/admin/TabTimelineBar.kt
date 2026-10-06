package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.stats.TabKind

/**
 * FIX 5.10-stat-admin-ui-2:
 * Горизонтальная полоса timeline вкладок. Ширина сегмента
 * пропорциональна доле вкладки в общем времени дня. Под полосой —
 * легенда с процентом и длительностью.
 */
@Composable
fun TabTimelineBar(usage: List<TabUsage>) {
    if (usage.isEmpty()) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Вкладки",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                usage.forEach { u ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(u.percent.coerceAtLeast(1).toFloat())
                            .background(
                                tabColor(u.tab),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            usage.forEach { u ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(tabColor(u.tab), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        u.tab.title,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${u.percent}% · ${AdminPanelAggregator.formatDuration(u.totalSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * FIX 5.10-stat-admin-ui-2:
 * Цвет вкладки. Используется в TabTimelineBar и SessionCard.
 * Internal — виден в пределах пакета admin.
 */
internal fun tabColor(tab: TabKind): Color = when (tab) {
    TabKind.MAIN -> Color(0xFF4CAF50)
    TabKind.ADD -> Color(0xFF2196F3)
    TabKind.SEARCH -> Color(0xFF3F51B5)
    TabKind.STATS -> Color(0xFF9C27B0)
    TabKind.EDIT -> Color(0xFFFF9800)
    TabKind.DB -> Color(0xFF795548)
    TabKind.SETTINGS -> Color(0xFF607D8B)
}