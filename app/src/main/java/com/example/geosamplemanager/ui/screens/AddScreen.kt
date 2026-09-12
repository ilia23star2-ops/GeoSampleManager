package com.example.geosamplemanager.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.history.ImportHistoryEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AddScreen(viewModel: AddViewModel = viewModel()) {
    val context = LocalContext.current
    val areas by viewModel.areas.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val queueState by viewModel.queueState.collectAsState()
    val history by viewModel.history.collectAsState()
    val pendingConflict by viewModel.pendingConflict.collectAsState()

    var showSummary by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshAreas()
        viewModel.reloadHistory()
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val name = queryFileName(context, it)
            viewModel.importFromUri(it, name)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Добавить наряд", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        Card {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Импорт из Excel", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Выберите .xlsx-файл. Каждый лист обрабатывается отдельно, " +
                            "если листов несколько.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        pickFileLauncher.launch(
                            arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel",
                                "*/*"
                            )
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Выбрать файл .xlsx")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.reloadHistory()
                        showHistory = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("История импортов (${history.size})")
                }
            }
        }

        if (busy && preview == null && !queueState.isFinished && pendingConflict == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Обработка...")
            }
        }

        if (queueState.totalSheets > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSummary = true }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (queueState.isFinished) "Импорт завершён"
                            else "Прогресс импорта",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${queueState.currentIndex} / ${queueState.totalSheets}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { queueState.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    if (queueState.importedCount > 0) {
                        Text(
                            "Импортировано: ${queueState.importedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    if (queueState.replacedCount > 0) {
                        Text(
                            "Заменено: ${queueState.replacedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF1565C0)
                        )
                    }
                    if (queueState.generalListCount > 0) {
                        Text(
                            "Общих списков пропущено: ${queueState.generalListCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (queueState.existingSkippedCount > 0) {
                        Text(
                            "Пропущено (уже есть): ${queueState.existingSkippedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF616161)
                        )
                    }
                    if (queueState.skippedCount > 0) {
                        Text(
                            "Пропущено вручную: ${queueState.skippedCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (queueState.errorCount > 0) {
                        Text(
                            "Ошибок: ${queueState.errorCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (queueState.totalSamplesImported > 0) {
                        Text(
                            "Всего проб загружено: ${queueState.totalSamplesImported}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { showSummary = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Показать сводку") }
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Участки в БД: ${areas.size}", style = MaterialTheme.typography.titleMedium)
                if (areas.isNotEmpty()) {
                    Text(
                        areas.take(5).joinToString(", ") { it.areaName } +
                                if (areas.size > 5) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Пока пусто. Первый импорт создаст участок автоматически.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    preview?.let { p ->
        ImportPreviewDialog(
            preview = p,
            areas = areas.map { it.areaName },
            onDismiss = { viewModel.cancelAll() },
            onSkip = { viewModel.skipCurrent() },
            onConfirm = { area, order -> viewModel.confirmImport(area, order) },
            onApplyMapping = { viewModel.updateMapping(it) },
            computeChanges = { area -> viewModel.computeRememberChanges(area) },
            onRememberAndImport = { area, order, changes ->
                viewModel.rememberAndImport(area, order, changes)
            },
            onAutoImportRest = { viewModel.autoImportRest() }
        )
    }

    pendingConflict?.let { conflict ->
        ConflictDialog(
            conflict = conflict,
            onResolve = { action, rememberForAll ->
                viewModel.resolveConflict(action, rememberForAll)
            }
        )
    }

    if (showSummary) {
        ImportSummaryDialog(
            queueState = queueState,
            onDismiss = {
                showSummary = false
                if (queueState.isFinished) viewModel.resetQueueState()
            }
        )
    }

    if (showHistory) {
        HistoryDialog(
            history = history,
            onClear = { viewModel.clearHistory() },
            onDismiss = { showHistory = false }
        )
    }
}

// ============================================================
// ДИАЛОГ КОНФЛИКТА
// ============================================================

@Composable
private fun ConflictDialog(
    conflict: PendingConflict,
    onResolve: (ConflictAction, Boolean) -> Unit
) {
    var rememberForAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { /* тап мимо — ничего */ },
        title = { Text("Наряд уже существует") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "В базе уже есть наряд с таким участком и номером.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Участок: ${conflict.areaName}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Наряд: ${conflict.orderNumber}",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Уже в базе: ${conflict.existingSamples} проб",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("В новом импорте: ${conflict.newSamples} проб",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Что сделать?", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                Text("• Добавить — старые пробы останутся, добавятся только новые",
                    style = MaterialTheme.typography.bodySmall)
                Text("• Пропустить — не импортировать этот лист",
                    style = MaterialTheme.typography.bodySmall)
                Text("• Заменить — удалить все старые пробы и залить новые",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberForAll,
                        onCheckedChange = { rememberForAll = it }
                    )
                    Text("Применить ко всем похожим",
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onResolve(ConflictAction.SKIP, rememberForAll) }
                ) { Text("Пропустить") }
                Button(onClick = { onResolve(ConflictAction.ADD, rememberForAll) }
                ) { Text("Добавить") }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onResolve(ConflictAction.REPLACE, rememberForAll) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Заменить") }
        }
    )
}

// ============================================================
// ИСТОРИЯ
// ============================================================

@Composable
private fun HistoryDialog(
    history: List<ImportHistoryEntry>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* тап мимо */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(0.97f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("История импортов",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("Всего записей: ${history.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Очистить",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                HorizontalDivider()

                if (history.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center) {
                        Text("История пуста.\nИмпортированные файлы появятся здесь.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(history) { entry -> HistoryEntryCard(entry) }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Очистить историю?") },
            text = { Text("Все записи будут удалены. Сами данные в базе не пострадают.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearConfirm = false
                }) { Text("Очистить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun HistoryEntryCard(entry: ImportHistoryEntry) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(fmt.format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold)
                    Text(entry.fileName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1)
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (entry.importedCount > 0) {
                    Text("Импортировано: ${entry.importedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32))
                }
                if (entry.replacedCount > 0) {
                    Text("Заменено: ${entry.replacedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF1565C0))
                }
                if (entry.generalListCount > 0) {
                    Text("Общих: ${entry.generalListCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.skippedCount > 0) {
                    Text("Пропущено: ${entry.skippedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (entry.errorCount > 0) {
                    Text("Ошибок: ${entry.errorCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
            Text("Проб всего: ${entry.totalSamples}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary)

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                entry.items.forEach { item ->
                    val bg = when (item.status) {
                        "imported" -> Color(0x1A2E7D32)
                        "replaced" -> Color(0x1A1565C0)
                        "general_list" -> Color(0x1AFFA000)
                        "skipped" -> Color(0x1A9E9E9E)
                        "existing_skipped" -> Color(0x1A616161)
                        "error" -> Color(0x33C62828)
                        else -> Color.Transparent
                    }
                    val statusText = when (item.status) {
                        "imported" -> "✓"
                        "replaced" -> "↻"
                        "general_list" -> "⚠"
                        "skipped" -> "—"
                        "existing_skipped" -> "⊘"
                        "error" -> "✗"
                        else -> "?"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(statusText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${item.areaName} / ${item.orderNumber}",
                                    style = MaterialTheme.typography.bodySmall)
                                Text("Лист: ${item.sheetName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("${item.sampleCount} проб",
                                style = MaterialTheme.typography.labelSmall)
                        }
                        item.reason?.let { r ->
                            Row(modifier = Modifier.padding(start = 20.dp, top = 2.dp)) {
                                Icon(Icons.Default.Info, contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                                Text(r, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// СВОДКА
// ============================================================

@Composable
private fun ImportSummaryDialog(
    queueState: QueueState,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* тап мимо */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(0.97f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Сводка импорта",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("Обработано листов: ${queueState.currentIndex} из ${queueState.totalSheets}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                HorizontalDivider()

                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryStat("Импортировано",
                        queueState.importedCount.toString(),
                        Color(0xFF2E7D32))
                    if (queueState.replacedCount > 0) {
                        SummaryStat("Заменено",
                            queueState.replacedCount.toString(),
                            Color(0xFF1565C0))
                    }
                    SummaryStat("Пропущено",
                        (queueState.skippedCount + queueState.existingSkippedCount).toString(),
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    SummaryStat("Общих списков",
                        queueState.generalListCount.toString(),
                        MaterialTheme.colorScheme.onSurfaceVariant)
                    if (queueState.errorCount > 0) {
                        SummaryStat("Ошибок",
                            queueState.errorCount.toString(),
                            MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider()

                if (queueState.processedItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center) {
                        Text("Пока ничего не обработано",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    val hScroll = rememberScrollState()
                    val totalW = 1000
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(hScroll)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(modifier = Modifier.width(totalW.dp)) {
                                SummaryHeaderCell("Лист", 180)
                                SummaryHeaderCell("Участок", 150)
                                SummaryHeaderCell("Наряд", 90)
                                SummaryHeaderCell("Проб", 70)
                                SummaryHeaderCell("Статус", 170)
                                SummaryHeaderCell("Причина", 340)
                            }
                        }
                        HorizontalDivider()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .horizontalScroll(hScroll)
                        ) {
                            LazyColumn(modifier = Modifier.width(totalW.dp)) {
                                items(queueState.processedItems) { item ->
                                    SummaryRow(item)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String, color: Color) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryHeaderCell(text: String, widthDp: Int) {
    Box(modifier = Modifier
        .width(widthDp.dp)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        .padding(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SummaryRow(item: ProcessedItem) {
    val bg = when (item.status) {
        ProcessedStatus.IMPORTED -> Color(0x1A2E7D32)
        ProcessedStatus.REPLACED -> Color(0x1A1565C0)
        ProcessedStatus.GENERAL_LIST -> Color(0x1AFFA000)
        ProcessedStatus.SKIPPED -> Color(0x1A9E9E9E)
        ProcessedStatus.EXISTING_SKIPPED -> Color(0x1A616161)
        ProcessedStatus.ERROR -> Color(0x33C62828)
    }
    val statusText = when (item.status) {
        ProcessedStatus.IMPORTED -> "✓ Импортирован"
        ProcessedStatus.REPLACED -> "↻ Заменён"
        ProcessedStatus.GENERAL_LIST -> "⚠ Общий список"
        ProcessedStatus.SKIPPED -> "— Пропущен"
        ProcessedStatus.EXISTING_SKIPPED -> "⊘ Уже в базе"
        ProcessedStatus.ERROR -> "✗ Ошибка"
    }
    Row(modifier = Modifier.background(bg)) {
        SummaryBodyCell(item.sheetName, 180)
        SummaryBodyCell(item.areaName, 150)
        SummaryBodyCell(item.orderNumber, 90)
        SummaryBodyCell(item.sampleCount.toString(), 70)
        SummaryBodyCell(statusText, 170)
        SummaryBodyCell(item.reason ?: "—", 340)
    }
}

@Composable
private fun SummaryBodyCell(text: String, widthDp: Int) {
    Box(modifier = Modifier
        .width(widthDp.dp)
        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
        .padding(6.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 3)
    }
}

// ============================================================
// ПРЕДПРОСМОТР
// ============================================================

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    areas: List<String>,
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onConfirm: (String, String) -> Unit,
    onApplyMapping: (Map<String, Int?>) -> Unit,
    computeChanges: (String) -> RememberChanges,
    onRememberAndImport: (String, String, RememberChanges) -> Unit,
    onAutoImportRest: () -> Unit
) {
    var areaName by remember(preview.order.areaName) {
        mutableStateOf(preview.order.areaName ?: areas.firstOrNull() ?: "")
    }
    var orderNumber by remember(preview.order.orderNumber) {
        mutableStateOf(preview.order.orderNumber)
    }
    var showEditor by remember { mutableStateOf(false) }
    var showFullList by remember { mutableStateOf(false) }
    var autoOpened by remember { mutableStateOf(false) }
    var pendingChanges by remember { mutableStateOf<RememberChanges?>(null) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showGeneralListWarning by remember(preview.sheetName) {
        mutableStateOf(preview.isGeneralList)
    }

    LaunchedEffect(preview) {
        if (!autoOpened) {
            val well = preview.mapping["well"]
            val sample = preview.mapping["sample"]
            if (well == null || sample == null) showEditor = true
            autoOpened = true
        }
    }

    val wellCounter = SampleDisplay.wellCounterLabel(preview.order.samples)

    AlertDialog(
        onDismissRequest = { showCancelConfirm = true },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Предпросмотр импорта")
                    Text("Лист ${preview.queuePosition} из ${preview.queueTotal}: ${preview.sheetName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Импортировано: ${preview.importedCount}, пропущено: ${preview.skippedCount + preview.existingSkippedCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showCancelConfirm = true }) {
                    Icon(Icons.Default.Close, contentDescription = "Отменить импорт")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 600.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // === ПЛАШКА 1: общий список ===
                if (showGeneralListWarning) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Возможно, это общий список",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold)
                                Text("В листе ${preview.sheetSampleCount} проб.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // === ПЛАШКА 2: наряд уже существует ===
                if (preview.existingConflict) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFE0B2) // светлый оранжевый
                        )
                    ) {
                        Row(modifier = Modifier.padding(8.dp)) {
                            Icon(Icons.Default.Warning, contentDescription = null,
                                tint = Color(0xFFE65100))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("Наряд уже существует в базе",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4E2C00))
                                Text(
                                    "Участок «${preview.order.areaName}», " +
                                            "наряд «${preview.order.orderNumber}» — " +
                                            "уже есть ${preview.existingSamples} проб. " +
                                            "При импорте потребуется выбрать действие.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF4E2C00)
                                )
                            }
                        }
                    }
                }

                Text("Файл: ${preview.fileName}", style = MaterialTheme.typography.bodySmall)
                Text("Лист: ${preview.sheetName}", style = MaterialTheme.typography.bodySmall)
                Text("Строка заголовка: ${preview.headerRowIndex + 1}", style = MaterialTheme.typography.bodySmall)
                Text("Найдено проб: ${preview.order.samples.size}", style = MaterialTheme.typography.bodySmall)
                Text("$wellCounter: ${preview.order.wellsCount}", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()

                Text("Участок", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = areaName,
                    onValueChange = { areaName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название участка") },
                    supportingText = {
                        Text(
                            if (preview.areaAutoDetected) "Определён автоматически по префиксу"
                            else "Не удалось определить — введите вручную",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (preview.areaAutoDetected)
                                Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                )

                Text("Номер наряда", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = orderNumber,
                    onValueChange = { orderNumber = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Наряд") },
                    supportingText = {
                        Text(
                            if (preview.orderAutoDetected) "Определён автоматически"
                            else "Не удалось определить — введите вручную",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (preview.orderAutoDetected)
                                Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                        )
                    }
                )

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Определённые колонки", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { showEditor = true }) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Ручной маппинг")
                    }
                }
                preview.mapping.forEach { (role, idx) ->
                    val header = if (idx != null && idx < preview.headers.size) preview.headers[idx] else "—"
                    Text("• ${RoleColors.roleTitle(role)} → $header",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (idx != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                }

                HorizontalDivider()

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Пробы", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (preview.order.samples.size > 10) {
                        TextButton(onClick = { showFullList = true }) {
                            Text("Показать все (${preview.order.samples.size})")
                        }
                    }
                }
                SamplesTable(
                    samples = preview.order.samples.take(10),
                    modifier = Modifier.fillMaxWidth().height(300.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSkip) { Text("Пропустить") }
                Button(
                    onClick = {
                        val changes = computeChanges(areaName.trim())
                        if (changes.isEmpty) {
                            onConfirm(areaName.trim(), orderNumber.trim())
                        } else {
                            pendingChanges = changes
                        }
                    },
                    enabled = areaName.isNotBlank() && orderNumber.isNotBlank()
                ) { Text("Импортировать") }
            }
        },
        dismissButton = {
            TextButton(onClick = onAutoImportRest) { Text("Авто для остальных") }
        }
    )

    if (showEditor) {
        MappingEditorDialog(
            headers = preview.headers,
            rows = preview.rows,
            autoMapping = preview.autoMapping,
            initialMapping = preview.mapping,
            onApply = { newMapping ->
                onApplyMapping(newMapping)
                showEditor = false
            },
            onDismiss = { showEditor = false }
        )
    }

    if (showFullList) {
        FullSamplesDialog(
            samples = preview.order.samples,
            onDismiss = { showFullList = false }
        )
    }

    pendingChanges?.let { changes ->
        RememberChangesDialog(
            changes = changes,
            onRemember = {
                onRememberAndImport(areaName.trim(), orderNumber.trim(), changes)
                pendingChanges = null
            },
            onSkip = {
                onConfirm(areaName.trim(), orderNumber.trim())
                pendingChanges = null
            },
            onDismiss = { pendingChanges = null }
        )
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Отменить импорт?") },
            text = {
                Text("Уже импортированные листы (${preview.importedCount}) останутся в базе. " +
                        "Текущий лист и оставшиеся обработаны не будут.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onDismiss()
                }) { Text("Отменить импорт", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Продолжить") }
            }
        )
    }
}

@Composable
private fun RememberChangesDialog(
    changes: RememberChanges,
    onRemember: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* тап мимо */ },
        title = { Text("Запомнить для будущих импортов?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Следующие данные будут использованы автоматически в следующий раз.",
                    style = MaterialTheme.typography.bodyMedium)
                if (changes.newHeaderKeywords.isNotEmpty()) {
                    Text("Заголовки колонок:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                    changes.newHeaderKeywords.forEach { (word, role) ->
                        Text("• «$word» → ${RoleColors.roleTitle(role)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                changes.newAreaPrefix?.let { (prefix, area) ->
                    Text("Префикс участка:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold)
                    Text("• «$prefix» → участок «$area»",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { Button(onClick = onRemember) { Text("Запомнить") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                TextButton(onClick = onSkip) { Text("Без запоминания") }
            }
        }
    )
}

@Composable
private fun FullSamplesDialog(
    samples: List<com.example.geosamplemanager.data.excel.ParsedSample>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* ничего */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(0.97f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Все пробы (${samples.size})",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                SamplesTable(samples = samples, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

private fun queryFileName(context: Context, uri: Uri): String {
    var name = "file.xlsx"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) {
            name = c.getString(idx) ?: name
        }
    }
    return name
}