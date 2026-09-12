package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * Экран «Сверка и поиск».
 *
 * Плоский LazyColumn: каждая строка пробы — отдельный item,
 * рендерится лениво. Заголовок группы тоже item.
 */

/**
 * Элемент плоского списка.
 */
sealed interface ReconItem {
    val key: String

    data class GroupHeader(
        val group: SampleGroup,
        val kind: GroupKind,
        val isForeign: Boolean,
        val expanded: Boolean
    ) : ReconItem {
        override val key: String get() = "h_${group.id}"
    }

    data class Sample(
        val row: SampleRow,
        val serial: Int,
        val showCharacteristic: Boolean
    ) : ReconItem {
        override val key: String get() = "r_${row.id}"
    }

    data class TableHead(
        val groupId: String,
        val showCharacteristic: Boolean,
        val wellColumnTitle: String
    ) : ReconItem {
        override val key: String get() = "t_$groupId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: ReconciliationViewModel = viewModel()
) {

    val state = viewModel.state

    var weightDialogRowId by remember { mutableStateOf<String?>(null) }
    var weightDialogIsControl by remember { mutableStateOf(false) }
    var characteristicDialogRowId by remember { mutableStateOf<String?>(null) }
    var noteDialogRowId by remember { mutableStateOf<String?>(null) }
    var editDialogRowId by remember { mutableStateOf<String?>(null) }
    var deleteDialogRowId by remember { mutableStateOf<String?>(null) }
    var alreadyFoundRowId by remember { mutableStateOf<String?>(null) }
    var errorDialogRowId by remember { mutableStateOf<String?>(null) }
    var postponedDialogRowId by remember { mutableStateOf<String?>(null) }
    var settingsOrderTitle by remember { mutableStateOf<String?>(null) }
    var confirmResetBlankWeight by remember { mutableStateOf(false) }
    var bulkDialogGroupId by remember { mutableStateOf<String?>(null) }
    var confirmClearAllGroupId by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current

    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    fun onToggleFound(row: SampleRow) {
        if (row.found) { alreadyFoundRowId = row.id; return }
        if (row.hasImportError) { errorDialogRowId = row.id; return }
        if (row.postponed) { postponedDialogRowId = row.id; return }
        if (row.weightControl && row.controlWeight == null) {
            weightDialogRowId = row.id
            weightDialogIsControl = true
            return
        }
        if (row.isBlank) {
            if (row.weight != null) {
                viewModel.setFound(row.id, true)
                return
            }
            val settings = state.currentBlankWeight
            when (settings.mode) {
                BlankWeightMode.FIXED -> {
                    val v = settings.fixedValue
                    if (v != null && v > 0) viewModel.setBlankWeightAndMarkFound(row.id, v)
                    else {
                        weightDialogRowId = row.id
                        weightDialogIsControl = false
                    }
                }
                BlankWeightMode.AVERAGE -> {
                    val group = state.groups.firstOrNull { it.id == row.groupId }
                    val avg = group?.let { calculateAverageNeighborWeight(it, row.id) }
                    if (avg != null && avg > 0) viewModel.setBlankWeightAndMarkFound(row.id, avg)
                    else {
                        weightDialogRowId = row.id
                        weightDialogIsControl = false
                    }
                }
                BlankWeightMode.MANUAL -> {
                    weightDialogRowId = row.id
                    weightDialogIsControl = false
                }
            }
            return
        }
        viewModel.setFound(row.id, true)
    }

    fun onMarkAllClick(groupId: String) {
        val decisions = state.collectBulkDecisions(groupId)
        if (decisions.isEmpty()) {
            val marked = viewModel.applyBulkMarkFound(groupId, emptyMap(), emptyMap())
            scope.launch { snackbarHostState.showSnackbar("Отмечено проб: $marked") }
        } else {
            bulkDialogGroupId = groupId
        }
    }

    // ================================================================
    // Плоский список
    // ================================================================
    val visible = state.visibleGroups
    val items by remember(state.showCharacteristic) {
        derivedStateOf { buildFlatList(state) }
    }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {

            TopActionsPanel(
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                undoCount = state.undoCount,
                redoCount = state.redoCount,
                undoDescription = state.describeTopUndoAction(),
                showCharacteristic = state.showCharacteristic,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onShowCharacteristicChange = { state.showCharacteristic = it },
                onHelpClick = { state.showLegend = true }
            )

            HorizontalDivider()

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {

                item(key = "selectors") {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        AreaAndOrderSelectors(
                            selectedArea = state.selectedArea,
                            selectedOrder = state.selectedOrder,
                            availableAreas = state.availableAreas,
                            availableOrders = state.availableOrders,
                            onAreaChange = {
                                state.selectedArea = it
                                state.selectedOrder = null
                            },
                            onOrderChange = { state.selectedOrder = it },
                            onOrderSettingsClick = {
                                state.selectedOrder?.let { settingsOrderTitle = it }
                            }
                        )
                    }
                }

                item(key = "search") {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        SearchRowWithIndicator(
                            query = state.query,
                            onQueryChange = { state.query = it },
                            matchInfo = state.matchInfo,
                            hasSelection = state.hasSelection,
                            onSearchAction = { keyboard?.hide() },
                            onClear = { state.query = "" },
                            onVoiceClick = {
                                scope.launch { snackbarHostState.showSnackbar("Голос — в разработке") }
                            }
                        )
                    }
                }

                item(key = "stats") {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        StatisticsPanel(
                            stats = statsToItems(state.currentStatistics),
                            orderSelected = state.selectedOrder != null
                        )
                    }
                }

                item(key = "filters_header") {
                    FiltersHeader(
                        expanded = state.filtersExpanded,
                        onToggle = { state.filtersExpanded = !state.filtersExpanded },
                        activeCount = state.activeFilters.size
                    )
                }

                if (state.filtersExpanded) {
                    item(key = "filters_row") {
                        FiltersRow(
                            activeFilters = state.activeFilters,
                            onFilterToggle = { f ->
                                state.activeFilters = if (f in state.activeFilters)
                                    state.activeFilters - f else state.activeFilters + f
                            }
                        )
                    }
                }

                item(key = "divider") {
                    Box(modifier = Modifier.padding(vertical = 4.dp)) { HorizontalDivider() }
                }

                when {
                    !state.hasSelection && state.query.isBlank() -> {
                        item(key = "empty") { EmptyState() }
                    }
                    state.selectedArea != null &&
                            state.selectedOrder == null &&
                            state.query.isBlank() -> {
                        item(key = "hint") { HintSelectOrderState() }
                    }
                    visible.isEmpty() -> {
                        item(key = "no_results") { NoResultsState() }
                    }
                    else -> {
                        items(items, key = { it.key }) { item ->
                            when (item) {
                                is ReconItem.GroupHeader -> {
                                    GroupHeaderCard(
                                        group = item.group,
                                        kind = item.kind,
                                        expanded = item.expanded,
                                        onToggleExpand = { state.toggleGroupExpanded(item.group.id) },
                                        onMarkAllClick = { onMarkAllClick(item.group.id) },
                                        onClearAllClick = { confirmClearAllGroupId = item.group.id },
                                        onAddSample = {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Добавить пробу — в разработке")
                                            }
                                        }
                                    )
                                }
                                is ReconItem.TableHead -> {
                                    TableHeader(
                                        showCharacteristic = item.showCharacteristic,
                                        wellColumnTitle = item.wellColumnTitle
                                    )
                                }
                                is ReconItem.Sample -> {
                                    SampleRowItem(
                                        serialNumber = item.serial,
                                        row = item.row,
                                        showCharacteristic = item.showCharacteristic,
                                        onToggleFound = { onToggleFound(item.row) },
                                        onOpenNote = { noteDialogRowId = item.row.id },
                                        onTogglePostponed = {
                                            viewModel.setPostponed(item.row.id, !item.row.postponed)
                                        },
                                        onOpenEdit = { editDialogRowId = item.row.id },
                                        onOpenDelete = { deleteDialogRowId = item.row.id },
                                        onToggleControl = {
                                            val ok = viewModel.toggleWeightControl(item.row.id)
                                            if (!ok) {
                                                scope.launch {
                                                    snackbarHostState.showSnackbar(
                                                        "Холостая не может быть весовым контролем"
                                                    )
                                                }
                                            } else if (!item.row.weightControl) {
                                                weightDialogRowId = item.row.id
                                                weightDialogIsControl = true
                                            }
                                        },
                                        onWeightClick = {
                                            weightDialogRowId = item.row.id
                                            weightDialogIsControl = item.row.weightControl
                                        },
                                        onCharacteristicClick = { characteristicDialogRowId = item.row.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
        )
    }

    // ================================================================
    // Диалоги
    // ================================================================

    weightDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            val isControl = weightDialogIsControl
            val initial = if (isControl) row.controlWeight else row.weight
            val title = if (isControl) "Весовой контроль" else "Вес холостой пробы"
            WeightDialog(
                title = title,
                sampleNumber = row.sampleNumber,
                initialWeight = initial,
                onConfirm = { v ->
                    if (isControl) viewModel.setControlWeightAndFound(id, v)
                    else viewModel.setBlankWeightAndMarkFound(id, v)
                    weightDialogRowId = null
                },
                onDismiss = { weightDialogRowId = null }
            )
        }
    }

    characteristicDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            CharacteristicDialog(
                sampleNumber = row.sampleNumber,
                characteristic = row.characteristic,
                onDismiss = { characteristicDialogRowId = null }
            )
        }
    }

    noteDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            NoteDialog(
                sampleNumber = row.sampleNumber,
                initialText = "",
                onSave = { _ ->
                    scope.launch { snackbarHostState.showSnackbar("Заметка — в разработке") }
                    noteDialogRowId = null
                },
                onDismiss = { noteDialogRowId = null }
            )
        }
    }

    editDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            EditSampleDialog(
                row = row,
                onSave = {
                    scope.launch { snackbarHostState.showSnackbar("Редактор — в разработке") }
                    editDialogRowId = null
                },
                onDismiss = { editDialogRowId = null }
            )
        }
    }

    deleteDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            DeleteSampleDialog(
                row = row,
                onConfirm = { recalc ->
                    viewModel.deleteRow(id, recalc)
                    deleteDialogRowId = null
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (recalc) "Проба удалена, номера пересчитаны"
                            else "Проба удалена"
                        )
                    }
                },
                onDismiss = { deleteDialogRowId = null }
            )
        }
    }

    alreadyFoundRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            AlreadyFoundDialog(
                row = row,
                onUnmarkFound = { viewModel.setFound(id, false); alreadyFoundRowId = null },
                onTogglePostponed = {
                    viewModel.setPostponed(id, !row.postponed)
                    alreadyFoundRowId = null
                },
                onViewNote = { noteDialogRowId = id; alreadyFoundRowId = null },
                onEdit = { editDialogRowId = id; alreadyFoundRowId = null },
                onDismiss = { alreadyFoundRowId = null }
            )
        }
    }

    errorDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            ImportErrorDialog(
                row = row,
                onConfirm = { viewModel.setFound(id, true); errorDialogRowId = null },
                onEdit = { editDialogRowId = id; errorDialogRowId = null },
                onDismiss = { errorDialogRowId = null }
            )
        }
    }

    postponedDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            PostponedDialog(
                row = row,
                onConfirm = { viewModel.setFound(id, true); postponedDialogRowId = null },
                onViewNote = { noteDialogRowId = id; postponedDialogRowId = null },
                onDismiss = { postponedDialogRowId = null }
            )
        }
    }

    settingsOrderTitle?.let { orderTitle ->
        OrderSettingsDialog(
            orderTitle = orderTitle,
            initialBlank = state.blankWeightFor(orderTitle),
            initialWeightControlStep = state.weightControlStepFor(orderTitle),
            isUsingGlobal = state.isUsingGlobal(orderTitle),
            onSave = { settings, step ->
                val changed = viewModel.applyBlankSettingsForOrder(orderTitle, settings, step)
                settingsOrderTitle = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (changed == 0) "Настройки сохранены. Холостых без веса не найдено."
                        else "Настройки сохранены. Вес проставлен $changed пробам — отметьте их вручную."
                    )
                }
            },
            onResetToGlobal = {
                val changed = viewModel.resetBlankSettingsToGlobal(orderTitle)
                settingsOrderTitle = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (changed == 0) "Сброшено к глобальным"
                        else "Сброшено. Вес проставлен $changed пробам — отметьте их вручную."
                    )
                }
            },
            onResetBlankWeights = { confirmResetBlankWeight = true },
            onDismiss = { settingsOrderTitle = null }
        )
    }

    if (confirmResetBlankWeight) {
        val orderTitle = settingsOrderTitle
        ConfirmResetBlankWeightDialog(
            onConfirm = {
                if (orderTitle != null) {
                    val changed = viewModel.resetBlankWeightsForOrder(orderTitle)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (changed == 0) "Холостых с весом не найдено"
                            else "Сброшен вес у $changed холостых проб"
                        )
                    }
                }
                confirmResetBlankWeight = false
            },
            onDismiss = { confirmResetBlankWeight = false }
        )
    }

    bulkDialogGroupId?.let { gid ->
        val decisions = state.collectBulkDecisions(gid)
        BulkActionsDialog(
            decisions = decisions,
            onApply = { weights, postponedActions ->
                val marked = viewModel.applyBulkMarkFound(gid, weights, postponedActions)
                bulkDialogGroupId = null
                scope.launch { snackbarHostState.showSnackbar("Отмечено проб: $marked") }
            },
            onDismiss = { bulkDialogGroupId = null }
        )
    }

    confirmClearAllGroupId?.let { gid ->
        val group = state.groups.firstOrNull { it.id == gid }
        if (group != null) {
            AlertDialog(
                onDismissRequest = { confirmClearAllGroupId = null },
                title = { Text("Сбросить все отметки?") },
                text = {
                    Text("Все отметки «найдена» в группе «${group.areaTitle} / ${group.orderTitle}» " +
                            "будут сняты. Продолжить?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearAllFound(gid)
                        confirmClearAllGroupId = null
                    }) { Text("Да") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearAllGroupId = null }) { Text("Отмена") }
                }
            )
        }
    }

    if (state.showLegend) {
        LegendDialog(onDismiss = { state.showLegend = false })
    }
}

// ====================================================================
// Построение плоского списка
// ====================================================================

private fun buildFlatList(state: ReconciliationState): List<ReconItem> {
    val result = ArrayList<ReconItem>(256)
    val visible = state.visibleGroups
    val selectedArea = state.selectedArea
    val selectedOrder = state.selectedOrder
    val showCharacteristic = state.showCharacteristic

    visible.forEach { group ->
        val kind = determineGroupKind(group, selectedArea, selectedOrder)
        val isForeign = kind == GroupKind.SAME_AREA || kind == GroupKind.OTHER_AREA
        val expanded = state.isGroupExpanded(group.id)

        result.add(ReconItem.GroupHeader(group, kind, isForeign, expanded))
        if (expanded) {
            val wellTitle = if (groupHasChannel(group)) "Выработка" else "Скважина"
            result.add(ReconItem.TableHead(group.id, showCharacteristic, wellTitle))
            group.rows.forEachIndexed { idx, row ->
                result.add(ReconItem.Sample(row, idx + 1, showCharacteristic))
            }
        }
    }
    return result
}

// ====================================================================
// Компоненты
// ====================================================================

/**
 * Заголовок группы — отдельный item плоского списка.
 */
@Composable
private fun GroupHeaderCard(
    group: SampleGroup,
    kind: GroupKind,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onMarkAllClick: () -> Unit,
    onClearAllClick: () -> Unit,
    onAddSample: () -> Unit
) {
    val isForeign = kind == GroupKind.SAME_AREA || kind == GroupKind.OTHER_AREA
    val cardColor = when (kind) {
        GroupKind.CURRENT_ORDER -> MaterialTheme.colorScheme.surface
        GroupKind.SAME_AREA     -> MaterialTheme.colorScheme.surfaceVariant
        GroupKind.OTHER_AREA    -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        GroupKind.NEUTRAL       -> MaterialTheme.colorScheme.surface
    }
    val titleColor = when (kind) {
        GroupKind.CURRENT_ORDER -> MaterialTheme.colorScheme.onSurface
        GroupKind.SAME_AREA     -> MaterialTheme.colorScheme.tertiary
        GroupKind.OTHER_AREA    -> MaterialTheme.colorScheme.error
        GroupKind.NEUTRAL       -> MaterialTheme.colorScheme.onSurface
    }
    val stripeColor = when (kind) {
        GroupKind.SAME_AREA  -> MaterialTheme.colorScheme.tertiary
        GroupKind.OTHER_AREA -> MaterialTheme.colorScheme.error
        else                 -> Color.Transparent
    }

    val subtitle = buildGroupSubtitle(group)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isForeign) {
                Box(modifier = Modifier.width(4.dp).height(40.dp)
                    .clip(RoundedCornerShape(2.dp)).background(stripeColor))
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${group.areaTitle} / ${group.orderTitle}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = titleColor)
                if (kind == GroupKind.SAME_AREA) {
                    Text("Не из выбранного наряда",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary)
                } else if (kind == GroupKind.OTHER_AREA) {
                    Text("Другой участок",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onMarkAllClick, Modifier.size(40.dp)) {
                Icon(Icons.Filled.DoneAll, "Выделить все")
            }
            IconButton(onClearAllClick, Modifier.size(40.dp)) {
                Icon(Icons.Filled.RemoveDone, "Сбросить все")
            }
            IconButton(onAddSample, Modifier.size(40.dp)) {
                Icon(Icons.Filled.AddCircleOutline, "Добавить")
            }
            IconButton(onToggleExpand, Modifier.size(40.dp)) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    if (expanded) "Свернуть" else "Развернуть"
                )
            }
        }
    }
}

@Composable
private fun TopActionsPanel(
    canUndo: Boolean, canRedo: Boolean,
    undoCount: Int, redoCount: Int,
    undoDescription: String,
    showCharacteristic: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit,
    onShowCharacteristicChange: (Boolean) -> Unit,
    onHelpClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onUndo, enabled = canUndo,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Filled.Undo, "Отменить", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (canUndo) "Отмена ($undoCount)" else "Отмена",
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        TextButton(
            onClick = onRedo, enabled = canRedo,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.height(36.dp)
        ) {
            Icon(Icons.Filled.Redo, "Повторить", modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (canRedo) "Вперёд ($redoCount)" else "Вперёд",
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        if (undoDescription.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(undoDescription,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
        } else Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable { onShowCharacteristicChange(!showCharacteristic) }
                .padding(horizontal = 4.dp)
        ) {
            Checkbox(checked = showCharacteristic,
                onCheckedChange = onShowCharacteristicChange,
                modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(2.dp))
            Text("Характеристика",
                style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
        IconButton(onClick = onHelpClick, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.HelpOutline, "Справка", modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SearchRowWithIndicator(
    query: String, onQueryChange: (String) -> Unit, matchInfo: MatchInfo,
    hasSelection: Boolean, onSearchAction: () -> Unit,
    onClear: () -> Unit, onVoiceClick: () -> Unit
) {
    val isLit = matchInfo is MatchInfo.Unique
    val lampColor = if (isLit) Color(0xFFFFC107)
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val indicatorText = when (matchInfo) {
        is MatchInfo.None     -> ""
        is MatchInfo.Unique   -> matchInfo.display
        is MatchInfo.Multiple -> "Несколько"
    }
    val placeholder = if (hasSelection) "Поиск по всем нарядам"
    else "Например: 1234 или TST1234"

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.width(110.dp).padding(end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.Lightbulb, null, tint = lampColor, modifier = Modifier.size(26.dp))
            if (indicatorText.isNotEmpty()) {
                Text(indicatorText, style = MaterialTheme.typography.labelSmall,
                    color = if (isLit) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            label = { Text("Номер скважины или пробы") },
            placeholder = { Text(placeholder) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Close, "Очистить")
                        }
                    }
                    IconButton(onClick = onVoiceClick) {
                        Icon(Icons.Filled.Mic, "Голос",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchAction() })
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AreaAndOrderSelectors(
    selectedArea: String?, selectedOrder: String?,
    availableAreas: List<String>, availableOrders: List<String>,
    onAreaChange: (String?) -> Unit, onOrderChange: (String?) -> Unit,
    onOrderSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedArea ?: "Все участки",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Участок", fontSize = 11.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp)
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Все участки") },
                        onClick = { onAreaChange(null); expanded = false }
                    )
                    availableAreas.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(a) },
                            onClick = { onAreaChange(a); expanded = false }
                        )
                    }
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
                OutlinedTextField(
                    value = selectedOrder ?: "Без наряда",
                    onValueChange = {}, readOnly = true,
                    label = { Text("Наряд", fontSize = 11.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth().height(56.dp)
                )
                ExposedDropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Без наряда") },
                        onClick = { onOrderChange(null); expanded = false }
                    )
                    availableOrders.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(o) },
                            onClick = { onOrderChange(o); expanded = false }
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onOrderSettingsClick,
            enabled = selectedOrder != null,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(Icons.Filled.Settings, "Настройки наряда",
                tint = if (selectedOrder != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun StatisticsPanel(stats: List<StatItem>, orderSelected: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Text(
                if (orderSelected) "Статистика по наряду" else "Статистика по найденному",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) { stats.forEach { StatChip(it) } }
        }
    }
}

@Composable
private fun StatChip(stat: StatItem) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stat.value.toString(), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = stat.color ?: MaterialTheme.colorScheme.onSurface)
        Text(stat.label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun FiltersHeader(expanded: Boolean, onToggle: () -> Unit, activeCount: Int) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        Text(
            if (activeCount == 0) "Фильтры" else "Фильтры (выбрано: $activeCount)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersRow(
    activeFilters: Set<ResultFilter>,
    onFilterToggle: (ResultFilter) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ResultFilter.values().forEach { f ->
            FilterChip(
                selected = f in activeFilters,
                onClick = { onFilterToggle(f) },
                label = { Text(f.title) },
                leadingIcon = if (f in activeFilters) {
                    { Icon(Icons.Filled.Check, null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}

@Composable
private fun TableHeader(showCharacteristic: Boolean, wellColumnTitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(48.dp))
        HeaderCell("п/п", 36.dp)
        HeaderCell(wellColumnTitle, 90.dp)
        HeaderCell("№ пробы", 110.dp)
        HeaderCell("Интервал", 100.dp)
        HeaderCell("Вес", 90.dp)
        if (showCharacteristic) HeaderCell("Характеристика", 120.dp)
        HeaderCell("Тип", 110.dp)
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.width(80.dp))
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(width))
}

@Composable
private fun SampleRowItem(
    serialNumber: Int,
    row: SampleRow,
    showCharacteristic: Boolean,
    onToggleFound: () -> Unit,
    onOpenNote: () -> Unit,
    onTogglePostponed: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenDelete: () -> Unit,
    onToggleControl: () -> Unit,
    onWeightClick: () -> Unit,
    onCharacteristicClick: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth()
            .background(rowBackgroundColor(row))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = row.found, onCheckedChange = { onToggleFound() })

        Text(
            text = serialNumber.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )

        Column(modifier = Modifier.width(90.dp)) {
            Text(row.wellNumber, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text("скв.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.width(110.dp)) {
            Text(row.sampleNumber, style = MaterialTheme.typography.bodyMedium)
            Text("№ в скв.: ${row.numberInWell}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(modifier = Modifier.width(100.dp)) {
            Text("${row.intervalFrom}–${row.intervalTo}",
                style = MaterialTheme.typography.bodySmall)
            Text("интервал, м", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val weightClickable = row.weightControl || row.isBlank
        Column(
            modifier = Modifier.width(90.dp)
                .then(if (weightClickable) Modifier.clickable { onWeightClick() }
                else Modifier)
        ) {
            Text(text = row.weight?.let { "$it кг" } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                color = if (weightClickable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface)
            if (row.controlWeight != null) {
                Text("(${row.controlWeight} кг)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (weightClickable) {
                Text("нажмите", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            }
        }

        if (showCharacteristic) {
            Column(modifier = Modifier.width(120.dp).clickable { onCharacteristicClick() }) {
                Text(row.characteristic, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("характеристика", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Тип — заменяем AssistChip на простой Text с фоном
        Box(
            modifier = Modifier.width(110.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = displayType(row.type, row.status),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.weight(1f))

        if (row.weightControl) Icon(Icons.Filled.Scale, "ВК",
            tint = Color(0xFF7B1FA2), modifier = Modifier.padding(end = 4.dp))
        if (row.postponed) Icon(Icons.Filled.PauseCircle, "Отложена",
            tint = Color(0xFF1976D2), modifier = Modifier.padding(end = 4.dp))
        if (row.hasNote) Icon(Icons.Filled.EditNote, "Заметка",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 4.dp))
        if (row.hasPhoto) Icon(Icons.Filled.PhotoCamera, "Фото",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 4.dp))
        if (row.hasImportError) Icon(Icons.Filled.Warning, "Ошибка",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(end = 4.dp))

        Box {
            IconButton({ menuOpen = true }, Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, "Действия")
            }
            // DropdownMenu создаётся только когда открыт
            if (menuOpen) {
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Заметка") },
                        leadingIcon = { Icon(Icons.Filled.EditNote, null) },
                        onClick = { menuOpen = false; onOpenNote() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (row.postponed) "Снять отложенную" else "Отложить") },
                        leadingIcon = { Icon(Icons.Filled.PauseCircle, null) },
                        onClick = { menuOpen = false; onTogglePostponed() }
                    )
                    if (!row.isBlank) {
                        DropdownMenuItem(
                            text = { Text(
                                if (row.weightControl) "Снять весовой контроль"
                                else "Поставить весовой контроль") },
                            leadingIcon = { Icon(Icons.Filled.Scale, null) },
                            onClick = { menuOpen = false; onToggleControl() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = { menuOpen = false; onOpenEdit() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null,
                            tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onOpenDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun rowBackgroundColor(row: SampleRow): Color {
    if (row.found) return Color(0xFFA5D6A7).copy(alpha = 0.35f)
    return when {
        row.hasImportError  -> Color(0xFFEF9A9A).copy(alpha = 0.35f)
        row.postponed       -> Color(0xFF90CAF9).copy(alpha = 0.35f)
        row.isBlank         -> Color(0xFFFFF59D).copy(alpha = 0.35f)
        row.weightControl   -> Color(0xFFCE93D8).copy(alpha = 0.30f)
        else                -> Color.Transparent
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Search, null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Text("Введите номер скважины или пробы",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Или выберите участок и наряд, чтобы увидеть весь список",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp))
    }
}

@Composable
private fun HintSelectOrderState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Checklist, null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Text("Выберите наряд или введите номер",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("На участке может быть много нарядов. " +
                "Выберите нужный наряд, чтобы увидеть его пробы.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp))
    }
}

@Composable
private fun NoResultsState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.SearchOff, null, modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Text("Ничего не найдено", style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("Попробуйте изменить запрос или снять фильтры",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пояснения к символике") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Индикатор слева от поиска",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Фонарик горит, если ответ единственный.",
                    style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))
                Text("Настройки наряда",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("• Холостые — режим веса: единый, среднее или вручную.\n" +
                        "• Весовой контроль — каждая N-я рядовая проба.\n" +
                        "• Кнопка «Сбросить вес холостых» очищает вес.\n\n" +
                        "Вес холостых проставляется СРАЗУ, но не отмечает пробы. " +
                        "Отметить нужно вручную.",
                    style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))
                Text("Отмена и Вперёд",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("До 20 шагов отмены. Статус-бар между кнопками показывает, " +
                        "что откатится следующим. При новом действии история «Вперёд» " +
                        "очищается.",
                    style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))
                Text("Цвет фона строки",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LegendRow(Color(0xFFA5D6A7), "Проба найдена")
                LegendRow(Color(0xFFFFF59D), "Холостая")
                LegendRow(Color(0xFFCE93D8), "Весовой контроль")
                LegendRow(Color(0xFF90CAF9), "Отложена")
                LegendRow(Color(0xFFEF9A9A), "Ошибка")
                LegendRow(Color.Transparent, "Не найдена")

                Spacer(Modifier.height(4.dp))
                Text("Значки в строке",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LegendIconRow(Icons.Filled.Scale, "Весовой контроль")
                LegendIconRow(Icons.Filled.PauseCircle, "Отложена")
                LegendIconRow(Icons.Filled.EditNote, "Есть заметка")
                LegendIconRow(Icons.Filled.PhotoCamera, "Есть фото")
                LegendIconRow(Icons.Filled.Warning, "Ошибка")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } }
    )
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(20.dp)
            .clip(RoundedCornerShape(4.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LegendIconRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}