package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
 * FIX 5.9-stats-layout: master-detail, dropdown диаграмм.
 *
 * FIX 5.9-stats-search: поиск наряда/участка сверху.
 *
 * FIX 5.9-stats-search-2:
 *  - В правой панели — кнопка «Наверх» (FAB), если прокрутили > 8.
 *  - Порог small: 8 блоков до первой пробы (шапка, прогресс, сводка,
 *    dropdown, диаграмма, разделитель, кнопка разворота, шапка таблицы).
 */
@Composable
fun StatsScreen(viewModel: StatsViewModel = viewModel()) {
    val data by viewModel.data.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val expandedAreas by viewModel.expandedAreaIds.collectAsState()
    val expandedOrders by viewModel.expandedOrderIds.collectAsState()
    val selectedOrderId by viewModel.selectedOrderId.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        val treeWidth = maxWidth * 0.35f

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                LeftTreePanel(
                    data = current,
                    searchQuery = searchQuery,
                    expandedAreaIds = expandedAreas,
                    expandedOrderIds = expandedOrders,
                    selectedOrderId = selectedOrderId,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onToggleArea = { viewModel.toggleArea(it) },
                    onToggleOrder = { viewModel.toggleOrder(it) },
                    onSelectOrder = { viewModel.selectOrder(it) },
                    onFilterChange = { viewModel.setFilter(it) },
                    onToggleAll = {
                        val anyExpanded = expandedAreas.isNotEmpty() || expandedOrders.isNotEmpty()
                        if (anyExpanded) viewModel.collapseAll() else viewModel.expandAll()
                    },
                    modifier = Modifier.fillMaxHeight().width(treeWidth)
                )
                VerticalDivider()
                RightDetailsPanel(
                    order = selectedOrderId?.let { viewModel.findOrder(it) },
                    areaName = selectedOrderId?.let { viewModel.findAreaNameFor(it) },
                    showBackButton = false,
                    onBack = { viewModel.clearSelection() },
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
            }
        } else {
            val order = selectedOrderId?.let { viewModel.findOrder(it) }
            if (order == null) {
                LeftTreePanel(
                    data = current,
                    searchQuery = searchQuery,
                    expandedAreaIds = expandedAreas,
                    expandedOrderIds = expandedOrders,
                    selectedOrderId = null,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onToggleArea = { viewModel.toggleArea(it) },
                    onToggleOrder = { viewModel.toggleOrder(it) },
                    onSelectOrder = { viewModel.selectOrder(it) },
                    onFilterChange = { viewModel.setFilter(it) },
                    onToggleAll = {
                        val anyExpanded = expandedAreas.isNotEmpty() || expandedOrders.isNotEmpty()
                        if (anyExpanded) viewModel.collapseAll() else viewModel.expandAll()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                RightDetailsPanel(
                    order = order,
                    areaName = viewModel.findAreaNameFor(order.orderId),
                    showBackButton = true,
                    onBack = { viewModel.clearSelection() },
                    snackbarHostState = snackbarHostState,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ====================================================================
// ЛЕВАЯ ПАНЕЛЬ — ДЕРЕВО
// ====================================================================

@Composable
private fun LeftTreePanel(
    data: StatsData,
    searchQuery: String,
    expandedAreaIds: Set<Long>,
    expandedOrderIds: Set<Long>,
    selectedOrderId: Long?,
    onSearchChange: (String) -> Unit,
    onToggleArea: (Long) -> Unit,
    onToggleOrder: (Long) -> Unit,
    onSelectOrder: (Long) -> Unit,
    onFilterChange: (StatsFilter) -> Unit,
    onToggleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SearchField(
            query = searchQuery,
            onQueryChange = onSearchChange
        )
        TotalsHeader(data.totals)
        FiltersRow(
            filter = data.filter,
            onFilterChange = onFilterChange,
            allExpanded = expandedAreaIds.isNotEmpty() || expandedOrderIds.isNotEmpty(),
            onToggleAll = onToggleAll
        )
        HorizontalDivider()

        val items = remember(data, expandedAreaIds, expandedOrderIds) {
            buildStatsItems(data, expandedAreaIds, expandedOrderIds)
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when {
                        searchQuery.isNotBlank() ->
                            "Ничего не найдено по запросу «$searchQuery»"
                        data.filter == StatsFilter.ALL ->
                            "В базе пока нет данных"
                        else ->
                            "Нет проб, соответствующих фильтру"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { it.key }) { item ->
                    when (item) {
                        is StatsItem.AreaHeader -> AreaHeaderCard(
                            area = item.area,
                            expanded = item.expanded,
                            onToggle = { onToggleArea(item.area.areaId) }
                        )
                        is StatsItem.OrderHeader -> OrderHeaderCard(
                            order = item.order,
                            expanded = item.expanded,
                            selected = item.order.orderId == selectedOrderId,
                            onToggle = { onToggleOrder(item.order.orderId) },
                            onSelect = { onSelectOrder(item.order.orderId) }
                        )
                        is StatsItem.TableHeadItem -> Unit
                        is StatsItem.SampleRowItem -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        placeholder = {
            Text(
                "Поиск наряда или участка",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingIcon = {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Очистить",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun TotalsHeader(stats: GroupStats) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Общая статистика",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SmallStatChip("Всего", stats.total, null)
                SmallStatChip("Найдено", stats.found, Color(0xFF2E7D32))
                SmallStatChip("Не найдено", stats.notFound, Color(0xFFC62828))
                SmallStatChip("Холостые", stats.blanks, null)
                SmallStatChip("ВК", stats.weightControls, null)
                SmallStatChip("Отложено", stats.postponed, Color(0xFF1976D2))
                if (stats.errors > 0) SmallStatChip("Ошибки", stats.errors, Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun SmallStatChip(label: String, value: Int, color: Color?) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun FiltersRow(
    filter: StatsFilter,
    onFilterChange: (StatsFilter) -> Unit,
    allExpanded: Boolean,
    onToggleAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatsFilter.values().forEach { f ->
            FilterChip(
                selected = f == filter,
                onClick = { onFilterChange(f) },
                label = { Text(f.title, fontSize = 12.sp) }
            )
        }
        TextButton(onClick = onToggleAll, contentPadding = PaddingValues(horizontal = 6.dp)) {
            Icon(
                if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AreaHeaderCard(
    area: StatsAreaUi,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val colors = AnswerStateColors.of(AnswerState.OK)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        color = colors.background,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp).height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.accent)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    area.areaName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${area.orders.size} наряд(ов) · проб: ${area.stats.total} · " +
                            "найдено: ${area.stats.found}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun OrderHeaderCard(
    order: StatsOrderUi,
    expanded: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    onSelect: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, start = 12.dp)
            .clickable { onSelect() },
        color = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Наряд №${order.orderNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    buildOrderShortSummary(order.stats),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun buildOrderShortSummary(s: GroupStats): String {
    val parts = mutableListOf<String>()
    parts.add("${s.found}/${s.total}")
    if (s.blanks > 0) parts.add("хол: ${s.blanks}")
    if (s.weightControls > 0) parts.add("ВК: ${s.weightControls}")
    if (s.postponed > 0) parts.add("отл: ${s.postponed}")
    if (s.errors > 0) parts.add("ош: ${s.errors}")
    return parts.joinToString(" · ")
}

// ====================================================================
// ПРАВАЯ ПАНЕЛЬ — РАБОЧАЯ ЗОНА
// ====================================================================

private enum class ChartType(val title: String) {
    PIE("Круговая"),
    BARS("Столбцы"),
    WELLS("По скважинам")
}

/** FIX 5.9-stats-search-2: порог показа кнопки «Наверх». */
private const val SCROLL_TOP_THRESHOLD = 8

@Composable
private fun RightDetailsPanel(
    order: StatsOrderUi?,
    areaName: String?,
    showBackButton: Boolean,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    if (order == null) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Выберите наряд слева",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var chartType by remember { mutableStateOf(ChartType.PIE) }
    var samplesExpanded by rememberSaveable { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val showScrollTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > SCROLL_TOP_THRESHOLD
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {

            item(key = "header_${order.orderId}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Наряд №${order.orderNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (areaName != null) {
                            Text(
                                areaName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch { snackbarHostState.showSnackbar("Отчёты — в разработке") }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Отчёт", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.width(6.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch { snackbarHostState.showSnackbar("Сравнение — в разработке") }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Filled.CompareArrows,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Сравнить", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            item(key = "progress_${order.orderId}") {
                ProgressBarBlock(order.stats)
            }

            item(key = "summary_${order.orderId}") {
                OrderSummaryRow(order.stats)
            }

            item(key = "div1_${order.orderId}") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            item(key = "chartdrop_${order.orderId}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Тип диаграммы:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    ChartTypeDropdown(
                        current = chartType,
                        onSelect = { chartType = it }
                    )
                }
            }

            item(key = "chart_${order.orderId}") {
                Spacer(Modifier.height(8.dp))
                DiagramPlaceholder(chartType)
                Spacer(Modifier.height(8.dp))
            }

            item(key = "div2_${order.orderId}") {
                HorizontalDivider()
            }

            item(key = "toggle_${order.orderId}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { samplesExpanded = !samplesExpanded }) {
                        Icon(
                            if (samplesExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (samplesExpanded) "Свернуть список проб"
                            else "Развернуть список проб (${order.group.rows.size})"
                        )
                    }
                }
            }

            if (samplesExpanded) {
                item(key = "thead_${order.orderId}") {
                    TableHeadRow()
                }
                items(order.group.rows, key = { "row_${it.id}" }) { row ->
                    StatsSampleRow(row)
                }
                item(key = "tailspace_${order.orderId}") {
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        // FIX 5.9-stats-search-2: FAB «Наверх».
        if (showScrollTop) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch { listState.scrollToItem(0) }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    Icons.Filled.KeyboardArrowUp,
                    contentDescription = "Наверх"
                )
            }
        }
    }
}

@Composable
private fun ProgressBarBlock(stats: GroupStats) {
    val total = stats.total
    val progress = if (total > 0) stats.found.toFloat() / total.toFloat() else 0f
    val pct = (progress * 1000).toInt() / 10f

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(stats)
            Spacer(Modifier.width(8.dp))
            Text(
                "$pct%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun StatusBadge(stats: GroupStats) {
    val (label, bg) = when {
        stats.total == 0 -> "Пустой" to MaterialTheme.colorScheme.surfaceVariant
        stats.found == stats.total -> "Готов" to Color(0xFF2E7D32)
        stats.found == 0 -> "Не начат" to MaterialTheme.colorScheme.surfaceVariant
        else -> "В работе" to Color(0xFFF9A825)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (bg == MaterialTheme.colorScheme.surfaceVariant)
                MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun OrderSummaryRow(stats: GroupStats) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryInline("Всего", stats.total, null)
            SummaryInline("Найдено", stats.found, Color(0xFF2E7D32))
            SummaryInline("Не найдено", stats.notFound, Color(0xFFC62828))
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryInline("Холостые", stats.blanks, null)
            SummaryInline("ВК", stats.weightControls, null)
            SummaryInline("Отложено", stats.postponed, Color(0xFF1976D2))
            if (stats.errors > 0) SummaryInline("Ошибки", stats.errors, Color(0xFFC62828))
        }
    }
}

@Composable
private fun SummaryInline(label: String, value: Int, color: Color?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label + ": ",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartTypeDropdown(
    current: ChartType,
    onSelect: (ChartType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = current.title,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .width(180.dp)
                .height(48.dp),
            textStyle = MaterialTheme.typography.bodySmall
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ChartType.values().forEach { t ->
                DropdownMenuItem(
                    text = { Text(t.title) },
                    onClick = {
                        onSelect(t)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DiagramPlaceholder(type: ChartType) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .height(180.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.BarChart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${type.title} — в разработке",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ====================================================================
// ТАБЛИЦА ПРОБ
// ====================================================================

@Composable
private fun TableHeadRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
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
            .background(rowBackgroundColor(row))
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(110.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            row.wellNumber,
            style = MaterialTheme.typography.bodySmall,
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
