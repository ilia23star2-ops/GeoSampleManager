package com.example.geosamplemanager.ui.screens

import android.app.Activity
import android.content.Intent
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
import androidx.compose.material.icons.filled.Restore
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
import com.example.geosamplemanager.MainActivity
import com.example.geosamplemanager.data.backup.BackupManifest
import com.example.geosamplemanager.data.backup.GsmBackupWriter
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-db-restore-v2-hotfix-2:
 *  - recreate() не сбрасывает ViewModel (rememberNavController
 *    переживает пересоздание, и ViewModel'и в NavHost остаются);
 *  - killProcess + AlarmManager — не работает на новых Android;
 *  - решение: после импорта — стартуем MainActivity заново с
 *    флагами NEW_TASK | CLEAR_TASK и закрываем текущую Activity.
 *    Это пересоздаёт весь Compose-стек: NavController, ViewModelStore,
 *    все экраны. Приложение мгновенно «открывается заново» без
 *    закрытия процесса.
 *
 * FIX 5.9-db-rollback:
 *  - кнопка «Откатиться к авто-бэкапу»;
 *  - список pre_*_* из filesDir/db_backups/;
 *  - двойное подтверждение + авто-бэкап pre_rollback_*.
 *
 * FIX 5.9-db-backups-ops/2:
 *  - ленивая миграция старых бэкапов из корня GeoSampleManager/
 *    в подпапки — при первом показе экрана.
 *
 * FIX 5.9-db-clean:
 *  - кнопка «Очистить БД» с двойным подтверждением;
 *  - авто-бэкап pre_clean_* + перезапуск стека.
 */
@Composable
fun DbScreen(viewModel: DbViewModel = viewModel()) {
    val context = LocalContext.current
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

    // FIX 5.9-db-rollback
    val rollbackBackups by viewModel.rollbackBackups.collectAsState()
    val rollbackLoading by viewModel.rollbackLoading.collectAsState()
    val rollbackState by viewModel.rollbackState.collectAsState()

    // FIX 5.9-db-clean
    val cleanState by viewModel.cleanState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // FIX 5.9-db-backups-ops/2: ленивая миграция старых бэкапов.
    LaunchedEffect(Unit) {
        viewModel.migrateOldPublicBackupsIfNeeded()
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

    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportName by remember { mutableStateOf<String?>(null) }
    var pendingManifest by remember { mutableStateOf<BackupManifest?>(null) }

    var pendingExternalName by remember { mutableStateOf<String?>(null) }
    var shareAfterNextExport by remember { mutableStateOf(false) }

    // FIX 5.9-db-rollback: состояние диалогов отката.
    var showRollbackDialog by remember { mutableStateOf(false) }
    var pendingRollbackFile by remember { mutableStateOf<File?>(null) }
    var pendingRollbackName by remember { mutableStateOf<String?>(null) }
    var pendingRollbackManifest by remember { mutableStateOf<BackupManifest?>(null) }

    // FIX 5.9-db-clean: диалог очистки.
    var showCleanDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showDbInfoDialog) {
        if (showDbInfoDialog) viewModel.loadDbInfo()
    }

    // FIX 5.9-db-rollback: подгрузка списка при открытии диалога.
    LaunchedEffect(showRollbackDialog) {
        if (showRollbackDialog) viewModel.loadRollbackBackups()
    }

    // FIX 5.9-db-clean: подгрузка счётчиков при открытии диалога.
    LaunchedEffect(showCleanDialog) {
        if (showCleanDialog) viewModel.loadDbInfo()
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

    // FIX 5.9-db-restore-v2-hotfix-2:
    // После успешного импорта — пересоздать весь стек приложения
    // через startActivity(MainActivity) + finish().
    LaunchedEffect(restoreState) {
        when (val s = restoreState) {
            is RestoreState.Done -> {
                snackbarHostState.showSnackbar("БД заменена. Перезапуск…")
                delay(700)
                val act = context as? Activity
                if (act != null) {
                    try {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        }
                        context.startActivity(intent)
                        act.finish()
                    } catch (_: Exception) {
                        try {
                            android.os.Process.killProcess(
                                android.os.Process.myPid()
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            is RestoreState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetRestoreState()
            }
            else -> Unit
        }
    }

    // FIX 5.9-db-rollback:
    // После отката — та же логика перезапуска стека.
    LaunchedEffect(rollbackState) {
        when (val s = rollbackState) {
            is RestoreState.Done -> {
                snackbarHostState.showSnackbar("БД восстановлена. Перезапуск…")
                delay(700)
                val act = context as? Activity
                if (act != null) {
                    try {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        }
                        context.startActivity(intent)
                        act.finish()
                    } catch (_: Exception) {
                        try {
                            android.os.Process.killProcess(
                                android.os.Process.myPid()
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
            is RestoreState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetRollbackState()
            }
            else -> Unit
        }
    }

    // FIX 5.9-db-clean:
    // После очистки — та же логика перезапуска стека.
    LaunchedEffect(cleanState) {
        when (val s = cleanState) {
            is RestoreState.Done -> {
                snackbarHostState.showSnackbar("БД очищена. Перезапуск…")
                delay(700)
                val act = context as? Activity
                if (act != null) {
                    try {
                        val intent = Intent(context, MainActivity::class.java).apply {
                            addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                            )
                        }
                        context.startActivity(intent)
                        act.finish()
                    } catch (_: Exception) {
                        try {
                            android.os.Process.killProcess(
                                android.os.Process.myPid()
                            )
                        } catch (_: Exception) {}
                    }
                }
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showAddAreaDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Участок")
                }
                OutlinedButton(
                    onClick = { showBackupDialog = true },
                    enabled = !exporting,
                    modifier = Modifier.weight(1f)
                ) {
                    if (exporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.FileUpload, contentDescription = null)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Экспорт")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = restoreState is RestoreState.Idle,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Импорт")
                }
                OutlinedButton(
                    onClick = { showDbInfoDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Инфо")
                }
            }
            // FIX 5.9-db-rollback
            OutlinedButton(
                onClick = { showRollbackDialog = true },
                enabled = rollbackState is RestoreState.Idle,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Откатиться к авто-бэкапу")
            }
            // FIX 5.9-db-clean
            OutlinedButton(
                onClick = { showCleanDialog = true },
                enabled = cleanState is RestoreState.Idle,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Очистить БД")
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
        if (rs is RestoreState.InProgress) {
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
                                rs.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // FIX 5.9-db-rollback: модалка прогресса отката.
        val rbs = rollbackState
        if (rbs is RestoreState.InProgress) {
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
                                rbs.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // FIX 5.9-db-clean: модалка прогресса очистки.
        val cs = cleanState
        if (cs is RestoreState.InProgress) {
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
                                cs.message,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

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

    // FIX 5.9-db-rollback: диалоги отката.
    if (showRollbackDialog) {
        DbRollbackDialog(
            backups = rollbackBackups,
            loading = rollbackLoading,
            onSelect = { backup ->
                showRollbackDialog = false
                pendingRollbackFile = backup.file
                pendingRollbackManifest = backup.manifest
                pendingRollbackName = backup.file.name
            },
            onDismiss = { showRollbackDialog = false }
        )
    }

    val rbFile = pendingRollbackFile
    val rbName = pendingRollbackName
    if (rbName != null && rbFile != null) {
        DbRollbackConfirmDialog(
            fileName = rbName,
            manifest = pendingRollbackManifest,
            onConfirm = {
                val f = pendingRollbackFile
                pendingRollbackFile = null
                pendingRollbackName = null
                pendingRollbackManifest = null
                if (f != null) viewModel.rollbackFromInternal(f)
            },
            onDismiss = {
                pendingRollbackFile = null
                pendingRollbackName = null
                pendingRollbackManifest = null
            }
        )
    }

    // FIX 5.9-db-clean: диалог очистки.
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