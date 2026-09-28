package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.voice.AnswerState
import kotlinx.coroutines.launch

/**
 * FIX 5.9-stats-screen:
 * Экран «Статистика» — дерево участок → наряд → проба.
 * Сводка сверху, чипы фильтров, кнопка «Сформировать отчёт»
 * (пока заглушка — появится в report-pdf / report-xlsx).
 *
 * FIX 5.9-stats-screen/4:
 * Добавлены импорты AnswerState и kotlinx.coroutines.launch —
 * без них CI падал на compileDebugKotlin.
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val data by viewModel.data.collectAsState()
    val expandedAreas by viewModel.expandedAreaIds.collectAsState()
    val expandedOrders by viewModel.expandedOrderIds.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val current = data
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val items = remember(current, expandedAreas, expandedOrders) {
        buildStatsItems(current, expandedAreas, expandedOrders)
    }

    val allExpanded = expandedAreas.isNotEmpty() || expandedOrders.isNotEmpty()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TotalsHeader(current.totals)

            FiltersAndActions(
                filter = current.filter,
                onFilterChange = { viewModel.setFilter(it) },
                allExpanded = allExpanded,
                onToggleAll = {
                    if (allExpanded) viewModel.collapseAll()
                    else viewModel.expandAll()
                },
                onReportClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Отчёты — в разработке")
                    }
                }
            )

            HorizontalDivider()

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.BarChart,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (current.filter == StatsFilter.ALL)
                                "В базе пока нет данных"
                            else
                                "Нет проб, соответствующих фильтру",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is StatsItem.AreaHeader -> AreaHeaderCard(
                                area = item.area,
                                expanded = item.expanded,
                                onToggle = { viewModel.toggleArea(item.area.areaId) }
                            )
                            is StatsItem.OrderHeader -> OrderHeaderCard(
                                order = item.order,
                                expanded = item.expanded,
                                onToggle = { viewModel.toggleOrder(item.order.orderId) }
                            )
                            is StatsItem.TableHeadItem -> TableHeadRow()
                            is StatsItem.SampleRowItem -> StatsSampleRow(item.row)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ====================================================================
// ВЕРХНЯЯ СВОДКА
// ====================================================================

@Composable
private fun TotalsHeader(stats: GroupStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "Общая статистика",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StatChip("Всего", stats.total, null)
                StatChip("Найдено", stats.found, Color(0xFF2E7D32))
                StatChip("Не найдено", stats.notFound, Color(0xFFC62828))
                StatChip("Холостые", stats.blanks, null)
                StatChip("Весовой", stats.weightControls, null)
                StatChip("Отложено", stats.postponed, Color(0xFF1976D2))
                StatChip("Ошибки", stats.errors, Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int, color: Color?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

// ====================================================================
// ФИЛЬТРЫ + КНОПКИ
// ====================================================================

@Composable
private fun FiltersAndActions(
    filter: StatsFilter,
    onFilterChange: (StatsFilter) -> Unit,
    allExpanded: Boolean,
    onToggleAll: () -> Unit,
    onReportClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatsFilter.values().forEach { f ->
            FilterChip(
                selected = f == filter,
                onClick = { onFilterChange(f) },
                label = { Text(f.title) }
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onToggleAll) {
            Icon(
                if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (allExpanded) "Свернуть всё" else "Развернуть всё",
                style = MaterialTheme.typography.labelMedium
            )
        }
        OutlinedButton(
            onClick = onReportClick,
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            Icon(
                Icons.Filled.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Отчёт", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// ====================================================================
// УЧАСТОК
// ====================================================================

@Composable
private fun AreaHeaderCard(
    area: StatsAreaUi,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = AnswerStateColors.of(AnswerState.OK)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = colors.background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp).height(48.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    area.areaName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildAreaSummary(area),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildAreaSummary(area: StatsAreaUi): String {
    val s = area.stats
    val orders = area.orders.size
    return "$orders наряд(ов) · всего проб: ${s.total} · найдено: ${s.found} · не найдено: ${s.notFound}"
}

// ====================================================================
// НАРЯД
// ====================================================================

@Composable
private fun OrderHeaderCard(
    order: StatsOrderUi,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, start = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Наряд №${order.orderNumber}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildOrderSummary(order.stats),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun buildOrderSummary(s: GroupStats): String {
    val parts = mutableListOf<String>()
    parts.add("Всего: ${s.total}")
    parts.add("найдено: ${s.found}")
    if (s.notFound > 0) parts.add("не найдено: ${s.notFound}")
    if (s.blanks > 0) parts.add("холостых: ${s.blanks}")
    if (s.weightControls > 0) parts.add("ВК: ${s.weightControls}")
    if (s.postponed > 0) parts.add("отложено: ${s.postponed}")
    if (s.errors > 0) parts.add("ошибок: ${s.errors}")
    return parts.joinToString(" · ")
}

// ====================================================================
// ТАБЛИЦА ПРОБ
// ====================================================================

@Composable
private fun TableHeadRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeadCell("п/п", 36.dp)
        HeadCell("№ пробы", 110.dp)
        HeadCell("Скважина", 90.dp)
        HeadCell("Интервал", 90.dp)
        HeadCell("Вес", 80.dp)
        HeadCell("Характеристика", 120.dp)
        HeadCell("Тип", 100.dp)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(28.dp))
    }
}

@Composable
private fun HeadCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width)
    )
}

@Composable
private fun StatsSampleRow(row: SampleRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .background(rowBackgroundColor(row))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (row.serialNumber > 0) row.serialNumber.toString() else "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Text(
            row.sampleNumber,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            row.wellNumber,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(90.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            buildIntervalText(row),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(90.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            buildWeightText(row),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(80.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            row.characteristic,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(120.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            displayType(row.type, row.status),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(100.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))

        if (row.weightControl) {
            Icon(
                Icons.Filled.Scale,
                contentDescription = "ВК",
                tint = Color(0xFF7B1FA2),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        if (row.postponed) {
            Icon(
                Icons.Filled.PauseCircle,
                contentDescription = "Отложена",
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun buildIntervalText(row: SampleRow): String {
    val from = row.intervalFrom
    val to = row.intervalTo
    return if (from == "—" && to == "—") "—" else "$from–$to"
}

private fun buildWeightText(row: SampleRow): String {
    val w = row.weight
    val cw = row.controlWeight
    return when {
        w != null && cw != null -> "$w ($cw)"
        w != null -> w.toString()
        cw != null -> "($cw)"
        else -> "—"
    }
}

@Composable
private fun rowBackgroundColor(row: SampleRow): Color {
    if (row.found) return Color(0xFFA5D6A7).copy(alpha = 0.35f)
    return when {
        row.hasImportError -> Color(0xFFEF9A9A).copy(alpha = 0.35f)
        row.postponed -> Color(0xFF90CAF9).copy(alpha = 0.35f)
        row.isBlank -> Color(0xFFFFF59D).copy(alpha = 0.35f)
        row.weightControl -> Color(0xFFCE93D8).copy(alpha = 0.30f)
        else -> Color.Transparent
    }
}
