package com.example.geosamplemanager.ui.screens

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
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.backup.GsmBackupWriter
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-db-style: Snackbar, >= 600.dp, без !!.
 * FIX 5.9-db-info: кнопка «Инфо» → DbInfoDialog.
 *
 * FIX 5.9-db-backup-v2: диалог экспорта.
 *
 * FIX 5.9-db-backup-fix:
 *  - API 29+ → сохранение в публичные Загрузки (MediaStore);
 *  - API < 29 → fallback через CreateDocument (системный диалог);
 *  - если в диалоге включён «Поделиться после сохранения» —
 *    после успешной записи открывается Share-интент.
 */
@Composable
fun DbScreen(viewModel: DbViewModel = viewModel()) {
    val context = LocalContext.current
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

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Диалоги.
    var showAddAreaDialog by remember { mutableStateOf(false) }
    var showAddOrderDialog by remember { mutableStateOf(false) }
    var areaToDelete by remember { mutableStateOf<AreaEntity?>(null) }
    var orderToDelete by remember { mutableStateOf<OrderEntity?>(null) }
    var showDbInfoDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    // FIX 5.9-db-backup-fix: состояние fallback-экспорта (SAF).
    var pendingExternalName by remember { mutableStateOf<String?>(null) }
    var shareAfterNextExport by remember { mutableStateOf(false) }

    LaunchedEffect(showDbInfoDialog) {
        if (showDbInfoDialog) viewModel.loadDbInfo()
    }

    // FIX 5.9-db-backup-fix: открытие Share-интента после сохранения.
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
                context.startActivity(
                    Intent.createChooser(intent, "Отправить бэкап")
                )
            } catch (_: Exception) {
                // Нет приложений для шаринга — молча продолжаем.
            }
        }
        viewModel.consumeLastExportUri()
    }

    // FIX 5.9-db-backup-fix: fallback для API < 29.
    val externalExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val name = pendingExternalName
        pendingExternalName = null
        if (uri != null) {
            viewModel.exportToUri(uri)
        } else {
            shareAfterNextExport = false
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
                OutlinedButton(
                    onClick = { showDbInfoDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Инфо")
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

    // FIX 5.9-db-backup-fix: экспорт.
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
                    // Публичные Загрузки через MediaStore.
                    viewModel.exportToDownloads(name)
                } else {
                    // Fallback: системный диалог.
                    pendingExternalName = name
                    externalExportLauncher.launch(
                        "$name.${GsmBackupWriter.EXTENSION}"
                    )
                }
            },
            onDismiss = { showBackupDialog = false }
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

// ============================================================
// ДИАЛОГИ
// ============================================================

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