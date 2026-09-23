package com.example.geosamplemanager.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.entity.SampleImageEntity
import com.example.geosamplemanager.data.reconciliation.MarkDecision
import com.example.geosamplemanager.data.reconciliation.analyzeMark
import com.example.geosamplemanager.data.util.PhotoStorage
import com.example.geosamplemanager.data.voice.AnswerReason
import com.example.geosamplemanager.data.voice.AnswerState
import com.example.geosamplemanager.data.voice.UnifiedMatchKind
import com.example.geosamplemanager.data.voice.VoiceSessionMode
import com.example.geosamplemanager.data.voice.VoiceStatus
import kotlinx.coroutines.launch
import java.io.File

sealed interface ReconItem {
    val key: String

    data class QueryHeader(val group: QueryGroup, val expanded: Boolean) : ReconItem {
        override val key: String get() = "q_${group.id}"
    }
    data class GroupHeader(
        val queryGroupId: String?, val group: SampleGroup,
        val kind: GroupKind, val isForeign: Boolean, val expanded: Boolean,
        val state: AnswerState
    ) : ReconItem {
        override val key: String
            get() = if (queryGroupId == null) "h_${group.id}" else "h_${queryGroupId}_${group.id}"
    }
    data class Sample(
        val queryGroupId: String?, val row: SampleRow,
        val showCharacteristic: Boolean
    ) : ReconItem {
        override val key: String
            get() = if (queryGroupId == null) "r_${row.id}" else "r_${queryGroupId}_${row.id}"
    }
    data class TableHead(
        val queryGroupId: String?, val groupId: String,
        val showCharacteristic: Boolean, val wellColumnTitle: String
    ) : ReconItem {
        override val key: String
            get() = if (queryGroupId == null) "t_$groupId" else "t_${queryGroupId}_$groupId"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: ReconciliationViewModel = viewModel()
) {
    val state = viewModel.state
    val context = LocalContext.current

    // FIX 5.8.9e-3: не даём экрану гаснуть, пока сессия ГП активна.
    val view = LocalView.current
    DisposableEffect(state.voiceStatus) {
        view.keepScreenOn = state.voiceStatus != VoiceStatus.Idle
        onDispose { view.keepScreenOn = false }
    }

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
    var confirmResetWeightControl by remember { mutableStateOf(false) }
    var bulkDialogGroupId by remember { mutableStateOf<String?>(null) }
    var confirmClearAllGroupId by remember { mutableStateOf<String?>(null) }
    var voiceDialogOpen by remember { mutableStateOf(false) }
    var pendingCameraForSampleId by remember { mutableStateOf<Long?>(null) }

    var noteText by remember { mutableStateOf("") }
    var notePhotos by remember { mutableStateOf<List<SampleImageEntity>>(emptyList()) }
    var cameraTempFile by remember { mutableStateOf<File?>(null) }
    var cameraTargetSampleId by remember { mutableStateOf<Long?>(null) }

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

    fun hasMic(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    fun hasCamera(): Boolean = ContextCompat.checkSelfPermission(
        context, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = cameraTempFile
        val sid = cameraTargetSampleId
        cameraTempFile = null
        cameraTargetSampleId = null
        if (success && file != null && sid != null) {
            scope.launch {
                val ok = viewModel.addPhotoFromFile(sid, file)
                if (ok) {
                    val (_, photos) = viewModel.loadNoteWithPhotos(sid)
                    notePhotos = photos
                    snackbarHostState.showSnackbar("Фото добавлено")
                } else {
                    snackbarHostState.showSnackbar("Не удалось сохранить фото")
                    file.delete()
                }
            }
        } else file?.delete()
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val sid = noteDialogRowId?.toLongOrNull()
        if (uri != null && sid != null) {
            scope.launch {
                val ok = viewModel.addPhoto(sid, uri)
                if (ok) {
                    val (_, photos) = viewModel.loadNoteWithPhotos(sid)
                    notePhotos = photos
                    snackbarHostState.showSnackbar("Фото добавлено")
                } else {
                    snackbarHostState.showSnackbar("Не удалось сохранить фото")
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) voiceDialogOpen = true
        else scope.launch {
            snackbarHostState.showSnackbar("Без разрешения микрофона голос не работает")
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val sid = pendingCameraForSampleId
        pendingCameraForSampleId = null
        if (granted && sid != null) {
            val tmp = PhotoStorage.createTempFile(context)
            cameraTempFile = tmp
            cameraTargetSampleId = sid
            val uri = PhotoStorage.getUriForFile(context, tmp)
            takePictureLauncher.launch(uri)
        } else if (!granted) {
            scope.launch {
                snackbarHostState.showSnackbar("Без разрешения камеры фото не сделать")
            }
        }
    }

    fun requestMic() {
        if (hasMic()) voiceDialogOpen = true
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun requestCamera(sampleId: Long) {
        if (hasCamera()) {
            val tmp = PhotoStorage.createTempFile(context)
            cameraTempFile = tmp
            cameraTargetSampleId = sampleId
            val uri = PhotoStorage.getUriForFile(context, tmp)
            takePictureLauncher.launch(uri)
        } else {
            pendingCameraForSampleId = sampleId
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGallery() {
        pickMediaLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    LaunchedEffect(noteDialogRowId) {
        val sid = noteDialogRowId?.toLongOrNull() ?: return@LaunchedEffect
        val (note, photos) = viewModel.loadNoteWithPhotos(sid)
        noteText = note?.noteText ?: ""
        notePhotos = photos
    }

    /**
     * FIX 5.8.9d-3b: единый алгоритм через analyzeMark.
     */
    fun onToggleFound(row: SampleRow) {
        val decision = analyzeMark(toMarkContext(state, row))
        when (decision) {
            is MarkDecision.AlreadyFound -> {
                alreadyFoundRowId = row.id
            }
            is MarkDecision.ImportError -> {
                errorDialogRowId = row.id
            }
            is MarkDecision.Postponed -> {
                postponedDialogRowId = row.id
            }
            is MarkDecision.NeedsControlWeight -> {
                weightDialogRowId = row.id
                weightDialogIsControl = true
            }
            is MarkDecision.NeedsBlankWeight -> {
                weightDialogRowId = row.id
                weightDialogIsControl = false
            }
            is MarkDecision.MarkWithWeight -> {
                viewModel.setBlankWeightAndMarkFound(row.id, decision.weight)
            }
            is MarkDecision.CanMark -> {
                viewModel.setFound(row.id, true)
            }
        }
    }

    fun onMarkAllClick(groupId: String) {
        val decisions = state.collectBulkDecisions(groupId)
        if (decisions.isEmpty()) {
            val marked = viewModel.applyBulkMarkFound(groupId, emptyMap(), emptyMap())
            scope.launch { snackbarHostState.showSnackbar("Отмечено проб: $marked") }
        } else bulkDialogGroupId = groupId
    }

    val isMulti = state.isMultiQuery
    val items by remember {
        derivedStateOf {
            // FIX 5.8.6-5f: явные чтения State — гарантия, что
            // derivedStateOf подписан на изменения _groups и _queryGroups.
            @Suppress("UNUSED_EXPRESSION")
            state.groups.toList()
            @Suppress("UNUSED_EXPRESSION")
            state.queryGroups.toList()

            if (state.isMultiQuery) buildMultiQueryList(state) else buildFlatList(state)
        }
    }

    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopActionsPanel(
                canUndo = state.canUndo, canRedo = state.canRedo,
                undoCount = state.undoCount, redoCount = state.redoCount,
                undoDescription = state.describeTopUndoAction(),
                showCharacteristic = state.showCharacteristic,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onShowCharacteristicChange = { viewModel.setShowCharacteristic(it) },
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
                            onAreaChange = { viewModel.setSelectedArea(it) },
                            onOrderChange = { viewModel.setSelectedOrder(it) },
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
                            onQueryChange = { viewModel.setQuery(it) },
                            matchInfo = state.matchInfo,
                            quickAnswers = state.quickAnswers,
                            isMulti = isMulti,
                            hasSelection = state.hasSelection,
                            onSearchAction = { keyboard?.hide() },
                            onClear = { viewModel.setQuery("") },
                            onVoiceClick = { requestMic() },
                            voiceMode = viewModel.voiceSession.mode,
                            onVoiceModeToggle = {
                                viewModel.voiceSession.mode =
                                    if (viewModel.voiceSession.mode == VoiceSessionMode.SEARCH)
                                        VoiceSessionMode.SORT
                                    else
                                        VoiceSessionMode.SEARCH
                            }
                        )
                    }
                }

                if (!isMulti) {
                    item(key = "stats") {
                        Box(modifier = Modifier.padding(bottom = 8.dp)) {
                            StatisticsPanel(
                                stats = statsToItems(state.currentStatistics),
                                orderSelected = state.selectedOrder != null
                            )
                        }
                    }
                }

                item(key = "filters_header") {
                    FiltersHeader(
                        expanded = state.filtersExpanded,
                        onToggle = { state.filtersExpanded = !state.filtersExpanded },
                        activeCount = state.activeFilters.size,
                        showToggleAll = isMulti && state.queryGroups.isNotEmpty(),
                        allExpanded = state.allQueryGroupsExpanded,
                        onToggleAll = {
                            if (state.allQueryGroupsExpanded) state.collapseAllQueryGroups()
                            else state.expandAllQueryGroups()
                        }
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
                    state.selectedArea != null && state.selectedOrder == null &&
                            state.query.isBlank() -> {
                        item(key = "hint") { HintSelectOrderState() }
                    }
                    items.isEmpty() -> {
                        item(key = "no_results") { NoResultsState() }
                    }
                    else -> {
                        if (!isMulti && state.matchInfo.isAttention) {
                            item(key = "ambiguous_banner") {
                                AttentionBanner(reason = state.matchInfo.reason)
                            }
                        }
                        items(items, key = { it.key }) { item ->
                            when (item) {
                                is ReconItem.QueryHeader -> QueryHeaderCard(
                                    group = item.group,
                                    expanded = item.expanded,
                                    selectedArea = state.selectedArea,
                                    onToggleExpand = { state.toggleQueryGroup(item.group.id) }
                                )
                                is ReconItem.GroupHeader -> GroupHeaderCard(
                                    group = item.group, kind = item.kind,
                                    expanded = item.expanded,
                                    state = item.state,
                                    onToggleExpand = {
                                        state.toggleGroupExpanded(
                                            item.group.id,
                                            isAttention = item.state == AnswerState.ATTENTION
                                        )
                                    },
                                    onMarkAllClick = { onMarkAllClick(item.group.id) },
                                    onClearAllClick = { confirmClearAllGroupId = item.group.id },
                                    onAddSample = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Добавить пробу — в разработке")
                                        }
                                    }
                                )
                                is ReconItem.TableHead -> TableHeader(
                                    showCharacteristic = item.showCharacteristic,
                                    wellColumnTitle = item.wellColumnTitle
                                )
                                is ReconItem.Sample -> SampleRowItem(
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

        // FIX 5.8.10-g2 (И-3):
        // Раньше был VoiceStatusBar — тонкая полоска снизу Column.
        // Теперь всю нижнюю часть занимает VoicePanel. Две панели
        // одновременно не нужны.

        // FIX 5.8.10-g2 (И-3):
        // Snackbar поднят на 100 dp — не перекрывает панель ГП.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
        )

        // FIX 5.8.10-g1/g2 (И-3):
        // Панель ГП — немодальная, прижата к низу экрана.
        // Внутри VoiceDialog сам рендерит Box(fillMaxSize, BottomCenter).
        if (voiceDialogOpen) {
            VoiceDialog(
                viewModel = viewModel,
                onDismiss = { voiceDialogOpen = false }
            )
        }
    }

    // ================================================================
    // ДИАЛОГИ
    // ================================================================

    weightDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) {
            val isControl = weightDialogIsControl
            val initial = if (isControl) row.controlWeight else row.weight
            val title = if (isControl) "Весовой контроль" else "Вес холостой пробы"
            WeightDialog(
                title = title, sampleNumber = row.sampleNumber,
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
        if (row != null) CharacteristicDialog(
            sampleNumber = row.sampleNumber,
            characteristic = row.characteristic,
            onDismiss = { characteristicDialogRowId = null }
        )
    }

    noteDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) NotePhotoDialog(
            sampleNumber = row.sampleNumber,
            initialText = noteText, photos = notePhotos,
            onSaveText = { text ->
                val sid = id.toLongOrNull()
                if (sid != null) {
                    noteDialogRowId = null
                    noteText = ""
                    notePhotos = emptyList()
                    scope.launch {
                        val ok = viewModel.saveNoteText(sid, text)
                        snackbarHostState.showSnackbar(
                            if (ok) "Заметка сохранена"
                            else "Не удалось сохранить заметку"
                        )
                    }
                }
            },
            onTakePhoto = { id.toLongOrNull()?.let { requestCamera(it) } },
            onPickFromGallery = { launchGallery() },
            onDeletePhoto = { imageId ->
                val sid = id.toLongOrNull()
                if (sid != null) scope.launch {
                    val ok = viewModel.deletePhoto(imageId, sid)
                    if (ok) {
                        val (_, photos) = viewModel.loadNoteWithPhotos(sid)
                        notePhotos = photos
                        snackbarHostState.showSnackbar("Фото удалено")
                    } else snackbarHostState.showSnackbar("Не удалось удалить фото")
                }
            },
            onDismiss = { noteDialogRowId = null; noteText = ""; notePhotos = emptyList() }
        )
    }

    editDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) EditSampleDialog(
            row = row,
            onSave = {
                scope.launch { snackbarHostState.showSnackbar("Редактор — в разработке") }
                editDialogRowId = null
            },
            onDismiss = { editDialogRowId = null }
        )
    }

    deleteDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) DeleteSampleDialog(
            row = row,
            onConfirm = { recalc ->
                viewModel.deleteRow(id, recalc)
                deleteDialogRowId = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (recalc) "Проба удалена, номера пересчитаны" else "Проба удалена"
                    )
                }
            },
            onDismiss = { deleteDialogRowId = null }
        )
    }

    alreadyFoundRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) AlreadyFoundDialog(
            row = row,
            onUnmarkFound = { viewModel.setFound(id, false); alreadyFoundRowId = null },
            onTogglePostponed = {
                viewModel.setPostponed(id, !row.postponed); alreadyFoundRowId = null
            },
            onViewNote = { noteDialogRowId = id; alreadyFoundRowId = null },
            onEdit = { editDialogRowId = id; alreadyFoundRowId = null },
            onDismiss = { alreadyFoundRowId = null }
        )
    }

    errorDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) ImportErrorDialog(
            row = row,
            onConfirm = { viewModel.setFound(id, true); errorDialogRowId = null },
            onEdit = { editDialogRowId = id; errorDialogRowId = null },
            onDismiss = { errorDialogRowId = null }
        )
    }

    postponedDialogRowId?.let { id ->
        val row = state.rowById(id)
        if (row != null) PostponedDialog(
            row = row,
            onConfirm = { viewModel.setFound(id, true); postponedDialogRowId = null },
            onViewNote = { noteDialogRowId = id; postponedDialogRowId = null },
            onDismiss = { postponedDialogRowId = null }
        )
    }

    settingsOrderTitle?.let { orderTitle ->
        OrderSettingsDialog(
            orderTitle = orderTitle,
            initialBlank = state.blankWeightFor(orderTitle),
            initialWeightControlStep = state.weightControlStepFor(orderTitle),
            isUsingGlobal = state.isUsingGlobal(orderTitle),
            onApplyBlank = { settings ->
                val changed = viewModel.applyBlankSettingsForOrder(orderTitle, settings)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (changed == 0) "Холостые: изменений нет."
                        else "Холостые применены. Обновлено проб: $changed."
                    )
                }
            },
            onApplyWeightControl = { step ->
                val changed = viewModel.applyWeightControlForOrder(orderTitle, step)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (changed == 0) "Весовой контроль: изменений нет."
                        else "Весовой контроль применён. Обновлено проб: $changed."
                    )
                }
            },
            onResetToGlobal = {
                val changed = viewModel.resetBlankSettingsToGlobal(orderTitle)
                settingsOrderTitle = null
                scope.launch {
                    snackbarHostState.showSnackbar(
                        if (changed == 0) "Сброшено к глобальным"
                        else "Сброшено. Обновлено проб: $changed."
                    )
                }
            },
            onResetBlankWeights = { confirmResetBlankWeight = true },
            onResetWeightControl = { confirmResetWeightControl = true },
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

    if (confirmResetWeightControl) {
        val orderTitle = settingsOrderTitle
        ConfirmResetWeightControlDialog(
            onConfirm = {
                if (orderTitle != null) {
                    val changed = viewModel.resetWeightControlForOrder(orderTitle)
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (changed == 0) "Весовой контроль не найден"
                            else "Снят весовой контроль у $changed проб"
                        )
                    }
                }
                confirmResetWeightControl = false
            },
            onDismiss = { confirmResetWeightControl = false }
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
        val group = state.groupById(gid)
        if (group != null) {
            AlertDialog(
                onDismissRequest = { confirmClearAllGroupId = null },
                title = { Text("Сбросить все отметки?") },
                text = {
                    Text("Все отметки «найдена» в группе " +
                            "«${group.areaTitle} / ${group.orderTitle}» будут сняты. Продолжить?")
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearAllFound(gid); confirmClearAllGroupId = null
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

    if (state.voiceHelpVisible) {
        VoiceHelpDialog(onDismiss = { state.voiceHelpVisible = false })
    }

    if (state.voiceOnboardingVisible) {
        VoiceOnboarding(
            onDismiss = { state.voiceOnboardingVisible = false },
            onDontShowAgain = { viewModel.dismissOnboarding() }
        )
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
    val groupState = if (state.matchInfo.isAttention) AnswerState.ATTENTION
    else AnswerState.OK

    visible.forEach { group ->
        val kind = determineGroupKind(group, selectedArea, selectedOrder)
        val isForeign = kind == GroupKind.SAME_AREA || kind == GroupKind.OTHER_AREA
        val expanded = state.effectiveGroupExpanded(
            group.id,
            isAttention = groupState == AnswerState.ATTENTION
        )
        result.add(
            ReconItem.GroupHeader(
                null, group, kind, isForeign, expanded, groupState
            )
        )
        if (expanded) {
            val wellTitle = if (groupHasChannel(group)) "Выработка" else "Скважина"
            result.add(ReconItem.TableHead(null, group.id, showCharacteristic, wellTitle))
            group.rows.forEach { row ->
                result.add(ReconItem.Sample(null, row, showCharacteristic))
            }
        }
    }
    return result
}

private fun buildMultiQueryList(state: ReconciliationState): List<ReconItem> {
    val result = ArrayList<ReconItem>(256)
    val selectedArea = state.selectedArea
    val selectedOrder = state.selectedOrder
    val showCharacteristic = state.showCharacteristic
    val sorted = state.queryGroups.sortedBy { if (it.isFound) 1 else 0 }

    sorted.forEach { qg ->
        val expanded = state.isQueryGroupExpanded(qg.id)
        result.add(ReconItem.QueryHeader(qg, expanded))
        if (!expanded) return@forEach

        val groupState = when {
            !qg.isFound -> AnswerState.ERROR
            qg.variantCount > 1 -> AnswerState.ATTENTION
            qg.isForeignArea -> AnswerState.ATTENTION
            else -> AnswerState.OK
        }

        val filteredGroups = state.filteredGroupsForQuery(qg)
        filteredGroups.forEach { group ->
            val kind = determineGroupKind(group, selectedArea, selectedOrder)
            val isForeign = kind == GroupKind.SAME_AREA || kind == GroupKind.OTHER_AREA
            val groupExpanded = state.effectiveGroupExpanded(
                group.id,
                isAttention = groupState == AnswerState.ATTENTION
            )
            result.add(
                ReconItem.GroupHeader(
                    qg.id, group, kind, isForeign, groupExpanded, groupState
                )
            )
            if (groupExpanded) {
                val wellTitle = if (groupHasChannel(group)) "Выработка" else "Скважина"
                result.add(ReconItem.TableHead(qg.id, group.id, showCharacteristic, wellTitle))
                group.rows.forEach { row ->
                    result.add(ReconItem.Sample(qg.id, row, showCharacteristic))
                }
            }
        }
    }
    return result
}

// ====================================================================
// Компоненты
// ====================================================================

@Composable
private fun AttentionBanner(reason: AnswerReason) {
    val colors = AnswerStateColors.of(AnswerState.ATTENTION)
    val title = when (reason) {
        AnswerReason.FOUND_OTHER_ORDER -> "Найден в другом наряде"
        AnswerReason.FOUND_OTHER_AREA -> "Найден в другом участке"
        AnswerReason.FOUND_MULTIPLE -> "Найден в нескольких нарядах"
        AnswerReason.FOUND_MULTIPLE_AREA -> "Найден в нескольких участках"
        else -> "Внимание"
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = colors.background,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning, "Внимание",
                tint = colors.accent, modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Выберите нужный наряд в списке ниже",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QueryHeaderCard(
    group: QueryGroup,
    expanded: Boolean,
    selectedArea: String?,
    onToggleExpand: () -> Unit
) {
    val state = when {
        !group.isFound -> AnswerState.ERROR
        group.variantCount > 1 -> AnswerState.ATTENTION
        group.isForeignArea -> AnswerState.ATTENTION
        else -> AnswerState.OK
    }
    val colors = AnswerStateColors.of(state)

    val reasonText = when {
        !group.isFound -> ""
        group.variantCount > 1 -> "⚠ Несколько нарядов"
        group.isForeignArea -> "⚠ Другой участок"
        else -> ""
    }

    val areaLines: List<AreaVariantsLine> = if (group.isFound) {
        group.variants
            .groupBy { it.areaTitle }
            .map { (area, list) ->
                AreaVariantsLine(
                    areaTitle = area,
                    count = list.size,
                    orderNumbers = list
                        .map { it.orderTitle.removePrefix("Наряд №").trim() }
                        .distinct()
                        .sorted()
                        .joinToString(", ")
                )
            }
            .sortedBy { it.areaTitle }
    } else emptyList()

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = colors.background, shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { onToggleExpand() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(40.dp)
                .clip(RoundedCornerShape(2.dp)).background(colors.accent))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Запрос №${group.id.removePrefix("q")}: «${group.query}»",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!group.isFound) {
                    Text(
                        "Нет ответов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    areaLines.forEach { line ->
                        val foreign = selectedArea != null && line.areaTitle != selectedArea
                        Text(
                            text = buildAreaLineText(line, foreign),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (reasonText.isNotEmpty()) {
                    Text(reasonText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class AreaVariantsLine(
    val areaTitle: String,
    val count: Int,
    val orderNumbers: String
)

private fun buildAreaLineText(line: AreaVariantsLine, foreign: Boolean): String {
    val vWord = variantWord(line.count)
    val orderWord = if (line.count == 1) "Наряд" else "Наряды"
    val base = "${line.areaTitle} · ${line.count} $vWord · $orderWord ${line.orderNumbers}"
    return if (foreign) "$base ⚠" else base
}

private fun variantWord(n: Int): String {
    val mod100 = n % 100
    if (mod100 in 11..14) return "вариантов"
    return when (n % 10) {
        1 -> "вариант"
        2, 3, 4 -> "варианта"
        else -> "вариантов"
    }
}

@Composable
private fun GroupHeaderCard(
    group: SampleGroup, kind: GroupKind, expanded: Boolean,
    state: AnswerState,
    onToggleExpand: () -> Unit, onMarkAllClick: () -> Unit,
    onClearAllClick: () -> Unit, onAddSample: () -> Unit
) {
    val colors = AnswerStateColors.of(state)
    val subtitle = buildGroupSubtitle(group)

    val reasonText = when {
        state == AnswerState.ATTENTION && kind == GroupKind.OTHER_AREA -> "⚠ Другой участок"
        state == AnswerState.ATTENTION && kind == GroupKind.SAME_AREA -> "⚠ Другой наряд"
        state == AnswerState.ATTENTION -> "⚠ Внимание"
        else -> ""
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        color = colors.background, shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(4.dp).height(48.dp)
                .clip(RoundedCornerShape(2.dp)).background(colors.accent))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state == AnswerState.ATTENTION) {
                        Icon(
                            Icons.Filled.Warning, "Внимание",
                            tint = colors.accent, modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("${group.areaTitle} / ${group.orderTitle}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f))
                }
                if (reasonText.isNotEmpty()) {
                    Text(reasonText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface)
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
    undoDescription: String, showCharacteristic: Boolean,
    onUndo: () -> Unit, onRedo: () -> Unit,
    onShowCharacteristicChange: (Boolean) -> Unit, onHelpClick: () -> Unit
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
                Text(undoDescription, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(6.dp))
        } else Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clip(RoundedCornerShape(4.dp))
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
    query: String, onQueryChange: (String) -> Unit,
    matchInfo: MatchInfo, quickAnswers: List<QuickAnswer>,
    isMulti: Boolean, hasSelection: Boolean,
    onSearchAction: () -> Unit, onClear: () -> Unit, onVoiceClick: () -> Unit,
    voiceMode: VoiceSessionMode, onVoiceModeToggle: () -> Unit
) {
    val singleState = matchInfo.state
    val colors = AnswerStateColors.of(singleState)
    val icon = when (singleState) {
        AnswerState.OK -> Icons.Filled.Lightbulb
        AnswerState.ATTENTION -> Icons.Filled.Warning
        AnswerState.ERROR -> Icons.Filled.Lightbulb
        AnswerState.IDLE -> Icons.Filled.Lightbulb
    }

    val statusText = when (singleState) {
        AnswerState.IDLE -> "Жду"
        AnswerState.OK -> "Найдено"
        AnswerState.ATTENTION -> "Внимание"
        AnswerState.ERROR -> matchInfo.reason.shortLabel
    }

    val reasonText = matchInfo.reason.detailLabel

    val matchedLine: String = if (!isMulti) buildSingleAnswerLine(matchInfo) else ""

    val placeholder = if (hasSelection)
        "Поиск (несколько номеров через пробел)"
    else "Например: 1234 или KPD1090031 (несколько через пробел)"

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(
            modifier = Modifier.width(110.dp).padding(end = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isMulti && quickAnswers.isNotEmpty()) {
                MultiQueryIndicatorList(quickAnswers)
            } else {
                Icon(icon, null, tint = colors.accent, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(2.dp))
                if (statusText.isNotEmpty()) {
                    Text(statusText, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                if (reasonText.isNotEmpty()) {
                    Text(reasonText, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp, textAlign = TextAlign.Center)
                }
                if (matchedLine.isNotEmpty()) {
                    Text(matchedLine, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        fontSize = 10.sp, textAlign = TextAlign.Center)
                }
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
                        IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, "Очистить",
                                modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onVoiceClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Mic, "Голос",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp))
                    }
                    VoiceModeChip(mode = voiceMode, onClick = onVoiceModeToggle)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchAction() })
        )
    }
}

@Composable
private fun VoiceModeChip(
    mode: VoiceSessionMode,
    onClick: () -> Unit
) {
    val isSort = mode == VoiceSessionMode.SORT
    val container = if (isSort) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.primaryContainer
    val content = if (isSort) MaterialTheme.colorScheme.onTertiaryContainer
                  else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .padding(start = 4.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(50))
            .background(container)
            .clickable { onClick() }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isSort) "СОРТ" else "ПОИСК",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = content
        )
    }
}

private fun buildSingleAnswerLine(matchInfo: MatchInfo): String {
    val value = matchInfo.matchedValue ?: return ""
    val orderRaw = matchInfo.orderTitles.firstOrNull() ?: return ""
    val orderNum = orderRaw
        .substringAfterLast("/")
        .trim()
        .removePrefix("Наряд №")
        .removePrefix("Наряд ")
        .trim()
    val prefix = when (matchInfo.matchedKind) {
        UnifiedMatchKind.WELL -> "скв."
        UnifiedMatchKind.SAMPLE -> "проба"
        UnifiedMatchKind.NONE -> return ""
    }
    return if (orderNum.isBlank()) {
        "$prefix $value"
    } else {
        "Наряд $orderNum · $prefix $value"
    }
}

@Composable
private fun MultiQueryIndicatorList(quickAnswers: List<QuickAnswer>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        quickAnswers.take(5).forEach { qa ->
            val st = when {
                qa.orderTitle == null && !qa.isMultiple -> AnswerState.ERROR
                qa.isMultiple -> AnswerState.ATTENTION
                qa.isForeignArea -> AnswerState.ATTENTION
                else -> AnswerState.OK
            }
            val colors = AnswerStateColors.of(st)
            val icon = when (st) {
                AnswerState.OK -> Icons.Filled.Lightbulb
                AnswerState.ATTENTION -> Icons.Filled.Warning
                AnswerState.ERROR -> Icons.Filled.Lightbulb
                AnswerState.IDLE -> Icons.Filled.Lightbulb
            }
            val detail = when {
                qa.orderTitle == null && !qa.isMultiple -> "Не найдено"
                qa.isMultiple -> "Несколько"
                qa.isForeignArea -> "Другой участок"
                else -> buildAnswerLine(qa)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    icon, null,
                    tint = colors.accent,
                    modifier = Modifier.size(12.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(3.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "№${qa.index}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                    Text(
                        detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

private fun buildAnswerLine(qa: QuickAnswer): String {
    val orderNum = qa.orderTitle?.removePrefix("Наряд №")?.trim().orEmpty()
    val value = qa.answerValue
    return when {
        orderNum.isNotEmpty() && value != null -> "$orderNum · $value"
        orderNum.isNotEmpty() -> orderNum
        value != null -> value
        else -> ""
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
            val ordersEnabled = selectedArea != null
            val labelText = if (ordersEnabled) "Наряд" else "Сначала выберите участок"
            val valueText = if (!ordersEnabled) "" else (selectedOrder ?: "Без наряда")
            ExposedDropdownMenuBox(
                expanded = expanded && ordersEnabled,
                onExpandedChange = { if (ordersEnabled) expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = valueText, onValueChange = {},
                    readOnly = true, enabled = ordersEnabled,
                    label = { Text(labelText, fontSize = 11.sp) },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded && ordersEnabled)
                    },
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
private fun FiltersHeader(
    expanded: Boolean, onToggle: () -> Unit, activeCount: Int,
    showToggleAll: Boolean, allExpanded: Boolean, onToggleAll: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        Text(
            if (activeCount == 0) "Фильтры" else "Фильтры (выбрано: $activeCount)",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (showToggleAll) {
            TextButton(
                onClick = onToggleAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    if (allExpanded) Icons.Filled.UnfoldLess else Icons.Filled.UnfoldMore,
                    null, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (allExpanded) "Свернуть все" else "Развернуть все",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersRow(
    activeFilters: Set<ResultFilter>, onFilterToggle: (ResultFilter) -> Unit
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
    row: SampleRow, showCharacteristic: Boolean,
    onToggleFound: () -> Unit, onOpenNote: () -> Unit, onTogglePostponed: () -> Unit,
    onOpenEdit: () -> Unit, onOpenDelete: () -> Unit, onToggleControl: () -> Unit,
    onWeightClick: () -> Unit, onCharacteristicClick: () -> Unit
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
            text = if (row.serialNumber > 0) row.serialNumber.toString() else "—",
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
                .then(if (weightClickable) Modifier.clickable { onWeightClick() } else Modifier)
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

        Box(
            modifier = Modifier.width(110.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = displayType(row.type, row.status),
                fontSize = 11.sp, maxLines = 1,
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
            if (menuOpen) {
                DropdownMenu(menuOpen, { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Заметка и фото") },
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
        row.hasImportError -> Color(0xFFEF9A9A).copy(alpha = 0.35f)
        row.postponed -> Color(0xFF90CAF9).copy(alpha = 0.35f)
        row.isBlank -> Color(0xFFFFF59D).copy(alpha = 0.35f)
        row.weightControl -> Color(0xFFCE93D8).copy(alpha = 0.30f)
        else -> Color.Transparent
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
        Text("Можно ввести до 5 номеров через пробел",
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
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Индикатор ответа",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Слева от строки поиска. Показывает, что нашлось:\n" +
                        "• 🟢 OK — единственный ответ.\n" +
                        "• 🟡 Внимание — другой наряд / участок / несколько.\n" +
                        "• 🔴 Ошибка — не найдено.\n" +
                        "• ⚪ Жду — пустой запрос.\n\n" +
                        "Одиночный запрос — развёрнуто:\n" +
                        "«Наряд 7 · скв. NV1526» (нашли скважину)\n" +
                        "«Наряд 7 · проба NV152601» (нашли пробу)\n\n" +
                        "Мультипоиск — по строке на запрос, коротко:\n" +
                        "№1 — «7 · NV1524»; №2 — «Несколько»; №3 — «Не найдено».",
                    style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))
                Text("Строка поиска",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "• Ничего не выбрано — строгое совпадение.\n" +
                            "• Выбран только участок — строгое совпадение, участок = приоритет.\n" +
                            "• Выбраны участок И наряд — точное по всей базе + префикс в выбранном наряде.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(4.dp))
                Text("Настройки наряда",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("• Холостые и ВК применяются раздельно:\n" +
                        "    — «Применить холостые» — проставляет вес;\n" +
                        "    — «Применить весовой контроль» — ставит ВК на N-ю пробу.\n" +
                        "• Кнопки «Сбросить» очищают только свою часть.\n\n" +
                        "Вес холостых проставляется СРАЗУ, но не отмечает пробы.",
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

                Spacer(Modifier.height(4.dp))
                Text("Заголовки групп",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Фон и полоска слева — цвет состояния:\n" +
                        "• 🟢 зелёный — всё ок.\n" +
                        "• 🟡 жёлтый — внимание (другой наряд / участок / несколько).\n" +
                        "• 🔴 красный — не найдено.\n\n" +
                        "При «внимании» группы по умолчанию свёрнуты.",
                    style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))
                Text("Кнопка «Развернуть/Свернуть все»",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("При нескольких запросах справа в строке фильтров — " +
                        "переключатель. Свёрнутое состояние показывает только заголовки " +
                        "запросов, развёрнутое — ещё и заголовки нарядов с пробами.",
                    style = MaterialTheme.typography.bodySmall)

                Spacer(Modifier.height(4.dp))
                Text("Чип режима голосовой сессии",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Чип справа от кнопки 🎤. Показывает текущий режим ГП:\n" +
                        "• 🔵 ПОИСК — со статистикой и отметками (по умолчанию).\n" +
                        "• 🟠 СОРТ — сортировка: без статистики, без отметок.\n\n" +
                        "Тап по чипу переключает режим. Голосом: «поиск» / «сортировка».",
                    style = MaterialTheme.typography.bodySmall)
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
