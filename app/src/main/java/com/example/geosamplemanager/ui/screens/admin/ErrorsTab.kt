package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.stats.EventCategory
import com.example.geosamplemanager.data.stats.EventLevel

/**
 * FIX 5.10-stat-admin-v2-details-c:
 * Таб «Ошибки» — список ERROR + WARN за день с фильтром по категории.
 *
 * Обычный тап по событию — раскрыть detailsJson.
 * Долгий тап — открыть контекст ±2 мин (EventDetailScreen).
 */
private const val ERRORS_LIMIT = 30

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorsTab(
    allEvents: List<EventView>,
    filterCategory: EventCategory?,
    onFilterChange: (EventCategory?) -> Unit,
    onOpenEvent: (Long, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val problems = allEvents.filter {
        it.level == EventLevel.ERROR || it.level == EventLevel.WARN
    }
    val filtered = if (filterCategory == null) problems
    else problems.filter { it.category == filterCategory }
    val shown = filtered.take(ERRORS_LIMIT)

    Column(modifier = modifier.fillMaxSize()) {
        CategoryChips(
            problems = problems,
            selected = filterCategory,
            onSelect = onFilterChange
        )
        HorizontalDivider()

        if (problems.isEmpty()) {
            EmptyState("За этот день ошибок и предупреждений нет.")
        } else if (filtered.isEmpty()) {
            EmptyState("В категории «${filterCategory?.label ?: ""}» событий нет.")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                CategorySummary(problems)
                Spacer(Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        shown.forEachIndexed { index, event ->
                            EventRowExpandable(
                                event = event,
                                highlighted = false,
                                onOpenDetail = {
                                    onOpenEvent(event.sessionId, event.atTs)
                                }
                            )
                            if (index < shown.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Обычный тап — раскрыть подробности. " +
                            "Долгий тап — открыть контекст ±2 мин.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (filtered.size > shown.size) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Показано ${shown.size} из ${filtered.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryChips(
    problems: List<EventView>,
    selected: EventCategory?,
    onSelect: (EventCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = {
                Text(
                    "Все (${problems.size})",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        )
        EventCategory.values().forEach { cat ->
            val count = problems.count { it.category == cat }
            if (count == 0) return@forEach
            FilterChip(
                selected = selected == cat,
                onClick = { onSelect(cat) },
                label = {
                    Text(
                        "${cat.label} ($count)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            )
        }
    }
}

@Composable
private fun CategorySummary(problems: List<EventView>) {
    val byCategory = problems.groupingBy { it.category }.eachCount()
        .toList()
        .sortedByDescending { it.second }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "По категориям",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            byCategory.forEach { (cat, count) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "$count",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}