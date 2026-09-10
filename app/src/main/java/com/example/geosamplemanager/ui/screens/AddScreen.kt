package com.example.geosamplemanager.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun AddScreen(viewModel: AddViewModel = viewModel()) {
    val context = LocalContext.current
    val areas by viewModel.areas.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val preview by viewModel.preview.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(Unit) { viewModel.refreshAreas() }

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
                    "Выберите .xlsx-файл. Колонки определяются автоматически по заголовкам.",
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
            }
        }

        if (busy) {
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
            onDismiss = { viewModel.clearPreview() },
            onConfirm = { area, order ->
                viewModel.confirmImport(area, order)
            }
        )
    }
}

@Composable
private fun ImportPreviewDialog(
    preview: ImportPreview,
    areas: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var areaName by remember { mutableStateOf(preview.order.areaName ?: areas.firstOrNull() ?: "") }
    var orderNumber by remember { mutableStateOf(preview.order.orderNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Предпросмотр импорта") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Файл: ${preview.fileName}", style = MaterialTheme.typography.bodySmall)
                Text("Лист: ${preview.sheetName}", style = MaterialTheme.typography.bodySmall)
                Text("Строка заголовка: ${preview.headerRowIndex + 1}", style = MaterialTheme.typography.bodySmall)
                Text("Найдено проб: ${preview.order.samples.size}", style = MaterialTheme.typography.bodySmall)
                Text("Скважин: ${preview.order.wellsCount}", style = MaterialTheme.typography.bodySmall)

                HorizontalDivider()

                Text("Участок", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = areaName,
                    onValueChange = { areaName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Название участка") }
                )

                Text("Номер наряда", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = orderNumber,
                    onValueChange = { orderNumber = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Наряд") }
                )

                HorizontalDivider()

                Text("Определённые колонки:", style = MaterialTheme.typography.labelLarge)
                preview.mapping.forEach { (role, idx) ->
                    val header = if (idx != null && idx < preview.headers.size) preview.headers[idx] else "—"
                    Text(
                        "• $role → $header",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (idx != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }

                HorizontalDivider()

                Text("Первые 5 проб:", style = MaterialTheme.typography.labelLarge)
                preview.order.samples.take(5).forEach { s ->
                    Text(
                        "№ ${s.sampleNumber} | скв. ${s.wellNumber} | " +
                                "${s.intervalFrom ?: "—"}–${s.intervalTo ?: "—"} | " +
                                "вес ${s.weight ?: "—"} | ${s.status}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (preview.order.samples.size > 5) {
                    Text("… и ещё ${preview.order.samples.size - 5}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(areaName.trim(), orderNumber.trim()) },
                enabled = areaName.isNotBlank() && orderNumber.isNotBlank()
            ) { Text("Импортировать") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
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