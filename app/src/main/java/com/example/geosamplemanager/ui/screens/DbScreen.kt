package com.example.geosamplemanager.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.backup.BackupManifest
import com.example.geosamplemanager.data.backup.BackupSource
import com.example.geosamplemanager.data.backup.GsmBackupWriter
import com.example.geosamplemanager.data.backup.PublicBackup
import com.example.geosamplemanager.data.backup.RollbackBackup
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.merge.MergeWizardState
import com.example.geosamplemanager.ui.navigation.Screen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DbScreen(viewModel: DbViewModel = viewModel()) {
    val context = LocalContext.current
    val app = context.applicationContext as GeoSampleApp
    val scope = rememberCoroutineScope()

    val areas by viewModel.areas.collectAsState()
    val selectedArea by viewModel.selectedArea.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val samples by viewModel.samples.collectAsState()
    val message by viewModel.message.collectAsState()
    val dbInfo by viewModel.dbInfo.collectAsState()
    val dbInfoLoading by viewModel.dbInfoLoading.collectAsState()
    val exporting by viewModel.exporting.collectAsState()
    val lastExportUri by viewModel.lastExportUri.collectAsState()
    val restoreState by viewModel.restoreState.collectAsState()

    val rollbackBackups by viewModel.rollbackBackups.collectAsState()
    val rollbackLoading by viewModel.rollbackLoading.collectAsState()
    val rollbackState by viewModel.rollbackState.collectAsState()

    val cleanState by viewModel.cleanState.collectAsState()

    val publicBackups by viewModel.publicBackups.collectAsState()
    val publicBackupsLoading by viewModel.publicBackupsLoading.collectAsState()

    val managerBackups by viewModel.managerBackups.collectAsState()
    val managerLoading by viewModel.managerLoading.collectAsState()

    val mergeState by viewModel.mergeState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.migrateOldPublicBackupsIfNeeded()
    }

    LaunchedEffect(Unit) {
        val doneMessage = app.consumeRestartMessage()
        if (doneMessage != null) {
            snackbarHostState.showSnackbar(doneMessage)
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var showAddAreaDialog by remember { mutableStateOf(false) }
    var showAddOrderDialog by remember { mutableStateOf(false) }
    var areaToDelete by remember { mutableStateOf<AreaEntity?>(null) }
    var orderToDelete by remember { mutableStateOf<OrderEntity?>(null) }
    var showDbInfoDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }
    var pendingManifest by remember { mutableStateOf<BackupManifest?>(null) }

    var pendingExternalName by remember { mutableStateOf<String?>(null) }
    var shareAfterNextExport by remember { mutableStateOf(false) }

    var showRollbackDialog by remember { mutableStateOf(false) }
    var pendingRollback by remember { mutableStateOf<RollbackBackup?>(null) }
    var pendingRollbackName by remember { mutableStateOf<String?>(null) }
    var pendingRollbackManifest by remember { mutableStateOf<BackupManifest?>(null) }

    var showCleanDialog by remember { mutableStateOf(false) }
    var showImportPickerDialog by remember { mutableStateOf(false) }
    var showBackupManagerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showDbInfoDialog) {
        if (showDbInfoDialog) viewModel.loadDbInfo()
    }

    LaunchedEffect(showRollbackDialog) {
        if (showRollbackDialog) viewModel.loadRollbackBackups()
    }

    LaunchedEffect(showCleanDialog) {
        if (showCleanDialog) viewModel.loadDbInfo()
    }

    LaunchedEffect(showImportPickerDialog) {
        if (showImportPickerDialog) viewModel.loadPublicBackups()
    }

    LaunchedEffect(showBackupManagerDialog) {
        if (showBackupManagerDialog) viewModel.loadAllBackupsForManager()
    }

    LaunchedEffect(lastExportUri) {
        val uri = lastExportUri ?: return@LaunchedEffect
        if (shareAfterNextExport) {
            shareAfterNextExport = false
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Отправить бэкап"))
            } catch (_: Exception) {}
        }
        viewModel.consumeLastExportUri()
    }

    LaunchedEffect(restoreState) {
        when (val s = restoreState) {
            is RestoreState.Done -> {
                app.scheduleRestartMessage("Готово. БД импортирована")
                delay(400)
                app.requestRestart(Screen.DB.route)
            }
            is RestoreState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetRestoreState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(rollbackState) {
        when (val s = rollbackState) {
            is RestoreState.Done -> {
                app.scheduleRestartMessage("Готово. БД восстановлена из бэкапа")
                delay(400)
                app.requestRestart(Screen.DB.route)
            }
            is RestoreState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetRollbackState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(cleanState) {
        when (val s = cleanState) {
            is RestoreState.Done -> {
                app.scheduleRestartMessage("Готово. БД очищена")
                delay(400)
                app.requestRestart(Screen.DB.route)
            }
            is RestoreState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetCleanState()
            }
            else -> Unit
        }
    }

    val externalExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val name = pendingExternalName
        pendingExternalName = null
        if (uri != null) viewModel.exportToUri(uri)
        else shareAfterNextExport = false
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val manifest = viewModel.readManifest(uri)
                if (manifest == null) {
                    snackbarHostState.showSnackbar(
                        "Не удалось прочитать архив (нет manifest.json)"
                    )
                } else {
                    pendingImportUri = uri
                    pendingManifest = manifest
                    pendingImportName = uri.lastPathSegment?.substringAfterLast('/')
                        ?: "backup.gsmbackup"
                }
            }
        }
    }

    val mergeFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')
                ?: "backup.gsmbackup"
            viewModel.loadMergePreview(uri, name)
        } else {
            viewModel.resetMergeState()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = { showAddAreaDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Участок", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showBackupDialog = true },
                    enabled = !exporting,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(2.dp))
                    Text("Экспорт", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showImportPickerDialog = true },
                    enabled = restoreState is RestoreState.Idle,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Импорт", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showDbInfoDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Info, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Инфо", maxLines = 1)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = { showRollbackDialog = true },
                    enabled = rollbackState is RestoreState.Idle,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Откат", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showBackupManagerDialog = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Storage, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Бэкапы", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        viewModel.startMergeWizard()
                        mergeFileLauncher.launch(arrayOf("*/*"))
                    },
                    enabled = mergeState is MergeWizardState.Idle
                            && restoreState is RestoreState.Idle
                            && rollbackState is RestoreState.Idle,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Merge, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Слияние", maxLines = 1)
                }
                OutlinedButton(
                    onClick = { showCleanDialog = true },
                    enabled = cleanState is RestoreState.Idle,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(2.dp))
                    Text("Очистить", maxLines = 1)
                }
            }

            HorizontalDivider()

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val isWide = maxWidth >= 600.dp

                if (isWide) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AreasList(
                            areas = areas,
                            selectedArea = selectedArea,
                            onSelect = { viewModel.selectArea(it) },
                            onDelete = { areaToDelete = it },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                        OrdersList(
                            selectedArea = selectedArea,
                            orders = orders,
                            selectedOrder = selectedOrder,
                            onSelect = { viewModel.selectOrder(it) },
                            onAdd = { showAddOrderDialog = true },
                            onDelete = { orderToDelete = it },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AreasList(
                            areas = areas,
                            selectedArea = selectedArea,
                            onSelect = { viewModel.selectArea(it) },
                            onDelete = { areaToDelete = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OrdersList(
                            selectedArea = selectedArea,
                            orders = orders,
                            selectedOrder = selectedOrder,
                            onSelect = { viewModel.selectOrder(it) },
                            onAdd = { showAddOrderDialog = true },
                            onDelete = { orderToDelete = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            val order = selectedOrder
            if (order != null) {
                HorizontalDivider()
                SamplesList(
                    order = order,
                    samples = samples,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        val rs = restoreState
        if (rs is RestoreState.InProgress) ProgressOverlay(rs.message)

        val rbs = rollbackState
        if (rbs is RestoreState.InProgress) ProgressOverlay(rbs.message)

        val cs = cleanState
        if (cs is RestoreState.InProgress) ProgressOverlay(cs.message)
    }

    MergeWizard(
        state = mergeState,
        onContinue = { viewModel.continueFromPreview() },
        onSetField = { id, f, o -> viewModel.setFieldResolution(id, f, o) },
        onSetSample = { id, o -> viewModel.setSampleResolution(id, o) },
        onMassAll = { o -> viewModel.applyMassStrategy(o) },
        onMassFillEmpty = { viewModel.applyFillEmptyStrategy() },
        onMassByField = { f, o -> viewModel.applyStrategyForField(f, o) },
        onGroupMass = { conflicts, strategy ->
            viewModel.applyGroupMass(conflicts, strategy)
        },
        onConfirmConflicts = { viewModel.confirmConflicts() },
        onCancel = { viewModel.cancelMerge() },
        onCloseDone = { viewModel.resetMergeState() }
    )

    if (showAddAreaDialog) {
        TextInputDialog(
            title = "Новый участок",
            label = "Название участка",
            onConfirm = {
                viewModel.addArea(it)
                showAddAreaDialog = false
            },
            onDismiss = { showAddAreaDialog = false }
        )
    }

    if (showAddOrderDialog) {
        TextInputDialog(
            title = "Новый наряд в «${selectedArea?.areaName ?: ""}»",
            label = "Номер наряда",
            onConfirm = {
                viewModel.addOrder(it)
                showAddOrderDialog = false
            },
            onDismiss = { showAddOrderDialog = false }
        )
    }

    areaToDelete?.let { area ->
        ConfirmDialog(
            title = "Удалить участок?",
            text = "Будут удалены все наряды и пробы участка " +
                    "«${area.areaName}». Действие необратимо.",
            onConfirm = {
                viewModel.deleteArea(area)
                areaToDelete = null
            },
            onDismiss = { areaToDelete = null }
        )
    }

    orderToDelete?.let { order ->
        ConfirmDialog(
            title = "Удалить наряд?",
            text = "Будут удалены все пробы наряда " +
                    "«${order.orderNumber}». Действие необратимо.",
            onConfirm = {
                viewModel.deleteOrder(order)
                orderToDelete = null
            },
            onDismiss = { orderToDelete = null }
        )
    }

    if (showDbInfoDialog) {
        DbInfoDialog(
            info = dbInfo,
            loading = dbInfoLoading,
            onRefresh = { viewModel.loadDbInfo() },
            onDismiss = { showDbInfoDialog = false }
        )
    }

    if (showBackupDialog) {
        val defaultName = remember {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
            "geosamples_${sdf.format(Date())}"
        }
        DbBackupDialog(
            defaultName = defaultName,
            onExport = { name, shareAfter ->
                showBackupDialog = false
                shareAfterNextExport = shareAfter
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    viewModel.exportToDownloads(name)
                } else {
                    pendingExternalName = name
                    externalExportLauncher.launch("$name.${GsmBackupWriter.EXTENSION}")
                }
            },
            onDismiss = { showBackupDialog = false }
        )
    }

    if (showImportPickerDialog) {
        DbImportPickerDialog(
            backups = publicBackups,
            loading = publicBackupsLoading,
            onSelect = { backup: PublicBackup ->
                showImportPickerDialog = false
                val mf = backup.manifest
                if (mf == null) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Не удалось прочитать архив")
                    }
                } else {
                    pendingImportUri = Uri.parse(backup.uri)
                    pendingManifest = mf
                    pendingImportName = backup.displayName
                }
            },
            onPickExternal = {
                showImportPickerDialog = false
                importLauncher.launch(arrayOf("*/*"))
            },
            onDismiss = { showImportPickerDialog = false }
        )
    }

    val mf = pendingManifest
    val uri = pendingImportUri
    val name = pendingImportName
    if (mf != null && uri != null && name != null) {
        DbRestoreDialog(
            fileName = name,
            manifest = mf,
            onConfirm = {
                pendingManifest = null
                pendingImportUri = null
                pendingImportName = null
                viewModel.restoreFromUri(uri)
            },
            onDismiss = {
                pendingManifest = null
                pendingImportUri = null
                pendingImportName = null
            }
        )
    }

    if (showRollbackDialog) {
        DbRollbackDialog(
            backups = rollbackBackups,
            loading = rollbackLoading,
            onSelect = { backup ->
                showRollbackDialog = false
                pendingRollback = backup
                pendingRollbackManifest = backup.manifest
                pendingRollbackName = backup.fileName
            },
            onDismiss = { showRollbackDialog = false }
        )
    }

    val rbBackup = pendingRollback
    val rbName = pendingRollbackName
    if (rbName != null && rbBackup != null) {
        DbRollbackConfirmDialog(
            fileName = rbName,
            manifest = pendingRollbackManifest,
            onConfirm = {
                val b = pendingRollback
                pendingRollback = null
                pendingRollbackName = null
                pendingRollbackManifest = null
                if (b != null) {
                    when (b.source) {
                        BackupSource.PRIVATE -> {
                            b.file?.let { viewModel.rollbackFromInternal(it) }
                        }
                        BackupSource.PUBLIC -> {
                            val uriStr = b.publicUri
                            if (uriStr != null) {
                                viewModel.rollbackFromPublic(Uri.parse(uriStr))
                            }
                        }
                    }
                }
            },
            onDismiss = {
                pendingRollback = null
                pendingRollbackName = null
                pendingRollbackManifest = null
            }
        )
    }

    if (showCleanDialog) {
        DbCleanDialog(
            info = dbInfo,
            loading = dbInfoLoading,
            onConfirm = {
                showCleanDialog = false
                viewModel.cleanDatabase()
            },
            onDismiss = { showCleanDialog = false }
        )
    }

    if (showBackupManagerDialog) {
        BackupManagerDialog(
            backups = managerBackups,
            loading = managerLoading,
            onDelete = { viewModel.deleteBackup(it) },
            onDeleteOld = { viewModel.deleteOldBackups() },
            onDeleteAll = { viewModel.deleteAllBackups() },
            onDismiss = { showBackupManagerDialog = false }
        )
    }
}

@Composable
private fun ProgressOverlay(message: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// ============================================================
// ПОДКОМПОНЕНТЫ
// ============================================================

@Composable
private fun AreasList(
    areas: List<AreaEntity>,
    selectedArea: AreaEntity?,
    onSelect: (AreaEntity) -> Unit,
    onDelete: (AreaEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Участки (${areas.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (areas.isEmpty()) {
                Text(
                    "Пусто. Нажмите «+ Участок».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(areas, key = { it.id }) { area ->
                        ListRow(
                            text = area.areaName,
                            selected = selectedArea?.id == area.id,
                            onClick = { onSelect(area) },
                            onDelete = { onDelete(area) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersList(
    selectedArea: AreaEntity?,
    orders: List<OrderEntity>,
    selectedOrder: OrderEntity?,
    onSelect: (OrderEntity) -> Unit,
    onAdd: () -> Unit,
    onDelete: (OrderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Наряды (${orders.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (selectedArea != null) {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить наряд")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            when {
                selectedArea == null -> Text(
                    "Выберите участок слева",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                orders.isEmpty() -> Text(
                    "Пусто. Нажмите «+».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(orders, key = { it.id }) { order ->
                        ListRow(
                            text = order.orderNumber,
                            selected = selectedOrder?.id == order.id,
                            onClick = { onSelect(order) },
                            onDelete = { onDelete(order) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplesList(
    order: OrderEntity,
    samples: List<SampleEntity>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Пробы наряда «${order.orderNumber}» (${samples.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (samples.isEmpty()) {
                Text(
                    "Нет проб в этом наряде",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(samples, key = { it.id }) { sample ->
                        SampleRow(sample)
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleRow(sample: SampleEntity) {
    val intervalText = if (sample.intervalFrom != null && sample.intervalTo != null) {
        "${sample.intervalFrom}–${sample.intervalTo}"
    } else "—"

    val statusText = when (sample.status) {
        "blank" -> "Холостая"
        "control" -> "ВК"
        else -> when (sample.sampleType) {
            "auger" -> "Шнековая"
            "channel" -> "Бороздовая"
            "cobra" -> "Кобра"
            else -> sample.sampleType
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when (sample.status) {
                    "blank" -> Color(0x33FFD700)
                    "control" -> Color(0x33DDA0DD)
                    else -> Color.Transparent
                }
            )
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            sample.sampleNumber,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            sample.wellNumber,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            intervalText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            sample.weight?.toString() ?: "—",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (sample.found) "✓" else "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (sample.found) Color(0xFF2E7D32)
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ListRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}