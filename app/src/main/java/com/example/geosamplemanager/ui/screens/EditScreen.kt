package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * FIX 5.9-edit-add-sample/3:
 *  - цвет статуса строки виден ВСЕГДА (и при выделении тоже);
 *  - выделение — рамка primary 2dp + лёгкий overlay 0.18 поверх
 *    цветного фона (не затирает цвет статуса).
 *
 * FIX 5.9-edit-save-guard:
 *  - EditSampleDialog получает findConflict по БД.
 */
@Composable
fun EditScreen(viewModel: EditViewModel = viewModel()) {
    val tree by viewModel.tree.collectAsState()
    val selectedId by viewModel.selectedSampleId.collectAsState()
    val selectedSample by viewModel.selectedSample.collectAsState()
    val expandedAreas by viewModel.expandedAreaIds.collectAsState()
    val expandedOrders by viewModel.expandedOrderIds.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val activeFilters by viewModel.activeFilters.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var editDialogRow by remember { mutableStateOf<SampleRow?>(null) }
    var deleteDialogRow by remember { mutableStateOf<SampleRow?>(null) }
    var addForOrderId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val data = tree
    if (data == null) {
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
                EditTreePanel(
                    data = data,
                    selectedId = selectedId,
                    expandedAreaIds = expandedAreas,
                    expandedOrderIds = expandedOrders,
                    searchQuery = searchQuery,
                    activeFilters = activeFilters,
                    onSelectSample = { viewModel.selectSample(it) },
                    onToggleArea = { viewModel.toggleArea(it) },
                    onToggleOrder = { viewModel.toggleOrder(it) },
                    onExpandAll = { viewModel.expandAll() },
                    onCollapseAll = { viewModel.collapseAll() },
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onFilterToggle = { viewModel.toggleFilter(it) },
                    onClearFilters = { viewModel.clearFilters() },
                    onAddToOrder = { addForOrderId = it },
                    modifier = Modifier.fillMaxHeight().width(treeWidth)
                )
                VerticalDivider()
                EditDetailsPanel(
                    sample = selectedSample,
                    onEdit = { editDialogRow = it },
                    onDelete = { deleteDialogRow = it },
                    onBack = null,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
            }
        } else {
            if (selectedSample == null) {
                EditTreePanel(
                    data = data,
                    selectedId = null,
                    expandedAreaIds = expandedAreas,
                    expandedOrderIds = expandedOrders,
                    searchQuery = searchQuery,
                    activeFilters = activeFilters,
                    onSelectSample = { viewModel.selectSample(it) },
                    onToggleArea = { viewModel.toggleArea(it) },
                    onToggleOrder = { viewModel.toggleOrder(it) },
                    onExpandAll = { viewModel.expandAll() },
                    onCollapseAll = { viewModel.collapseAll() },
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onFilterToggle = { viewModel.toggleFilter(it) },
                    onClearFilters = { viewModel.clearFilters() },
                    onAddToOrder = { addForOrderId = it },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EditDetailsPanel(
                    sample = selectedSample,
                    onEdit = { editDialogRow = it },
                    onDelete = { deleteDialogRow = it },
                    onBack = { viewModel.selectSample(null) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // FIX 5.9-edit-save-guard: findConflict → из ViewModel по БД.
    editDialogRow?.let { row ->
        EditSampleDialog(
            row = row,
            findConflict = { sn -> viewModel.findConflictForEdit(row.id, sn) },
            onSave = { updated ->
                viewModel.saveSample(updated)
                editDialogRow = null
            },
            onDismiss = { editDialogRow = null }
        )
    }

    deleteDialogRow?.let { row ->
        DeleteSampleDialog(
            row = row,
            onConfirm = { recalc ->
                viewModel.deleteSample(row.id, recalc)
                deleteDialogRow = null
            },
            onDismiss = { deleteDialogRow = null }
        )
    }

    addForOrderId?.let { orderId ->
        val order = data.findOrder(orderId)
        if (order != null) {
            AddSampleDialog(
                orderTitle = order.orderTitle,
                wellOptions = viewModel.wellsInOrder(orderId),
                commonPrefix = viewModel.commonPrefix(orderId),
                initialWellNumber = selectedSample?.wellNumber,
                suggestSampleNumber = { w -> viewModel.suggestSampleNumber(w, orderId) },
                suggestIntervalFrom = { w -> viewModel.suggestIntervalFrom(w, orderId) },
                findConflict = { sn -> viewModel.findConflictInOrder(orderId, sn) },
                onAdd = { w, sn, from, to, weight, ch, t, st ->
                    viewModel.addSample(orderId, w, sn, from, to, weight, ch, t, st)
                    addForOrderId = null
                },
                onDismiss = { addForOrderId = null }
            )
        } else {
            addForOrderId = null
        }
    }
}

// ====================================================================
// ДЕРЕВО
// ====================================================================

internal sealed interface EditTreeItem {
    val key: String

    data class AreaHeader(val area: EditAreaUi, val expanded: Boolean) : EditTreeItem {
        override val key: String get() = "a_${area.areaId}"
    }

    data class OrderHeader(
        val areaId: Long,
        val order: EditOrderUi,
        val expanded: Boolean
    ) : EditTreeItem {
        override val key: String get() = "o_${order.orderId}"
    }

    data class SampleItem(val orderId: Long, val row: SampleRow) : EditTreeItem {
        override val key: String get() = "s_${row.id}"
    }
}

internal fun buildTreeItems(
    data: EditTreeData,
    expandedAreaIds: Set<Long>,
    expandedOrderIds: Set<Long>
): List<EditTreeItem> {
    val result = ArrayList<EditTreeItem>(64)
    data.areas.forEach { area ->
        val areaExpanded = area.areaId in expandedAreaIds
        result.add(EditTreeItem.AreaHeader(area, areaExpanded))
        if (!areaExpanded) return@forEach
        area.orders.forEach { order ->
            val orderExpanded = order.orderId in expandedOrderIds
            result.add(EditTreeItem.OrderHeader(area.areaId, order, orderExpanded))
            if (!orderExpanded) return@forEach
            order.samples.forEach { sample ->
                result.add(EditTreeItem.SampleItem(order.orderId, sample))
            }
        }
    }
    return result
}

@Composable
private fun EditTreePanel(
    data: EditTreeData,
    selectedId: String?,
    expandedAreaIds: Set<Long>,
    expandedOrderIds: Set<Long>,
    searchQuery: String,
    activeFilters: Set<EditFilter>,
    onSelectSample: (String) -> Unit,
    onToggleArea: (Long) -> Unit,
    onToggleOrder: (Long) -> Unit,
    onExpandAll: () -> Unit,
    onCollapseAll: () -> Unit,
    onSearchChange: (String) -> Unit,
    onFilterToggle: (EditFilter) -> Unit,
    onClearFilters: () -> Unit,
    onAddToOrder: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val items = remember(data, expandedAreaIds, expandedOrderIds) {
        buildTreeItems(data, expandedAreaIds, expandedOrderIds)
    }

    val anyExpanded = expandedAreaIds.isNotEmpty() || expandedOrderIds.isNotEmpty()
    val isFiltered = searchQuery.isNotBlank() || activeFilters.isNotEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                placeholder = {
                    Text(
                        "Поиск: № пробы, скважины, характеристика",
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
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchChange("") },
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

            FiltersChipRow(
                activeFilters = activeFilters,
                onFilterToggle = onFilterToggle,
                onClearFilters = onClearFilters
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Пробы",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                TextButton(
                    onClick = { if (anyExpanded) onCollapseAll() else onExpandAll() },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        if (anyExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                        null, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (anyExpanded) "Свернуть" else "Развернуть",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            HorizontalDivider()

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            isFiltered -> "Ничего не найдено"
                            else -> "В базе пока нет данных"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is EditTreeItem.AreaHeader -> AreaHeaderRow(
                                area = item.area,
                                expanded = item.expanded,
                                onToggle = { onToggleArea(item.area.areaId) }
                            )
                            is EditTreeItem.OrderHeader -> OrderHeaderRow(
                                order = item.order,
                                expanded = item.expanded,
                                onToggle = { onToggleOrder(item.order.orderId) },
                                onAdd = { onAddToOrder(item.order.orderId) }
                            )
                            is EditTreeItem.SampleItem -> SampleItemRow(
                                row = item.row,
                                selected = item.row.id == selectedId,
                                onClick = { onSelectSample(item.row.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltersChipRow(
    activeFilters: Set<EditFilter>,
    onFilterToggle: (EditFilter) -> Unit,
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditFilter.values().forEach { f ->
            FilterChip(
                selected = f in activeFilters,
                onClick = { onFilterToggle(f) },
                label = { Text(f.title, fontSize = 12.sp) },
                leadingIcon = if (f in activeFilters) {
                    {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize)
                        )
                    }
                } else null
            )
        }
        if (activeFilters.isNotEmpty()) {
            TextButton(
                onClick = onClearFilters,
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text("Сбросить", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun AreaHeaderRow(
    area: EditAreaUi,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    area.areaName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${area.orders.size} наряд(ов)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun OrderHeaderRow(
    order: EditOrderUi,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, start = 12.dp)
            .clickable { onToggle() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    order.orderTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${order.samples.size} проб",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
            IconButton(
                onClick = onAdd,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Добавить пробу",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun sampleRowBackground(row: SampleRow): Color {
    if (row.found) return Color(0xFFA5D6A7).copy(alpha = 0.35f)
    return when {
        row.hasImportError -> Color(0xFFEF9A9A).copy(alpha = 0.35f)
        row.postponed -> Color(0xFF90CAF9).copy(alpha = 0.35f)
        row.isBlank -> Color(0xFFFFF59D).copy(alpha = 0.35f)
        row.weightControl -> Color(0xFFCE93D8).copy(alpha = 0.30f)
        else -> Color.Transparent
    }
}

/**
 * FIX 5.9-edit-add-sample/3:
 * Цвет статуса виден ВСЕГДА. При выделении — оверлей + рамка,
 * базовый цвет не затирается.
 */
@Composable
private fun SampleItemRow(
    row: SampleRow,
    selected: Boolean,
    onClick: () -> Unit
) {
    val baseBg = sampleRowBackground(row)
    val selectedOverlay = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, start = 24.dp, end = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(baseBg)
            .then(
                if (selected) Modifier.background(selectedOverlay) else Modifier
            )
            .then(
                if (selected) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(4.dp)
                ) else Modifier
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.sampleNumber,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "скв. ${row.wellNumber} · №${row.numberInWell}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (row.found) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Найдена",
                tint = Color(0xFF2E7D32),
                modifier = Modifier.size(18.dp).padding(end = 2.dp)
            )
        }
        if (row.postponed) {
            Icon(
                Icons.Filled.PauseCircle,
                contentDescription = "Отложена",
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(18.dp).padding(end = 2.dp)
            )
        }
        if (row.weightControl) {
            Icon(
                Icons.Filled.Scale,
                contentDescription = "ВК",
                tint = Color(0xFF7B1FA2),
                modifier = Modifier.size(18.dp).padding(end = 2.dp)
            )
        }
        if (row.hasImportError) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Ошибка",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp).padding(end = 2.dp)
            )
        }
        if (row.hasNote) {
            Icon(
                Icons.Filled.EditNote,
                contentDescription = "Заметка",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(end = 2.dp)
            )
        }
        if (row.hasPhoto) {
            Icon(
                Icons.Filled.PhotoCamera,
                contentDescription = "Фото",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp).padding(end = 2.dp)
            )
        }
    }
}

// ====================================================================
// КАРТОЧКА ПРОБЫ
// ====================================================================

@Composable
private fun EditDetailsPanel(
    sample: SampleRow?,
    onEdit: (SampleRow) -> Unit,
    onDelete: (SampleRow) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (sample == null) {
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
                    "Выберите пробу слева",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (onBack != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                }
                Text(
                    "К дереву",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Проба ${sample.sampleNumber}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatusBadge(
                        label = if (sample.found) "Найдена" else "Не найдена",
                        bg = if (sample.found) Color(0xFF2E7D32)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        fg = if (sample.found) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusBadge(
                        label = if (sample.postponed) "Отложена" else "Не отложена",
                        bg = if (sample.postponed) Color(0xFF1976D2)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        fg = if (sample.postponed) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StatusBadge(
                        label = if (sample.weightControl) "ВК" else "Не ВК",
                        bg = if (sample.weightControl) Color(0xFF7B1FA2)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        fg = if (sample.weightControl) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (sample.isBlank) {
                        StatusBadge(
                            label = "Холостая",
                            bg = Color(0xFFF9A825),
                            fg = Color.White
                        )
                    }
                    if (sample.hasImportError) {
                        StatusBadge(
                            label = "Ошибка",
                            bg = MaterialTheme.colorScheme.error,
                            fg = MaterialTheme.colorScheme.onError
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                InfoRow("Скважина / выработка", sample.wellNumber)
                InfoRow("№ в скважине", sample.numberInWell.toString())
                InfoRow("п/п", if (sample.serialNumber > 0) sample.serialNumber.toString() else "—")
                InfoRow("Интервал, м", "${sample.intervalFrom}–${sample.intervalTo}")
                InfoRow("Вес, кг", sample.weight?.toString() ?: "—")
                if (sample.controlWeight != null) {
                    InfoRow("ВК, кг", sample.controlWeight.toString())
                }
                InfoRow("Характеристика", sample.characteristic)
                InfoRow("Тип", displayType(sample.type, sample.status))

                if (sample.hasNote || sample.hasPhoto) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    if (sample.hasNote) InfoRow("Заметка", "есть")
                    if (sample.hasPhoto) InfoRow("Фото", "есть")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { onEdit(sample) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Редактировать")
            }
            OutlinedButton(
                onClick = { onDelete(sample) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Удалить",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(
    label: String,
    bg: Color,
    fg: Color
) {
    Surface(
        color = bg,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(140.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}