package com.example.geosamplemanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.settings.OrderNumberRule
import com.example.geosamplemanager.data.settings.OrderSource

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    var showAddAreaDialog by remember { mutableStateOf(false) }
    var areaToRename by remember { mutableStateOf<String?>(null) }
    var areaToDelete by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(it) } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки импорта", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        // ============ УЧАСТКИ И ПРЕФИКСЫ ============
        SettingsCard("Участки и префиксы") {
            Text(
                "Префиксы нужны, чтобы при импорте автоматически определить участок " +
                        "по номерам скважин и проб.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            settings.areaPrefixes.forEach { (area, prefixes) ->
                AreaRow(
                    areaName = area,
                    prefixesCsv = prefixes.joinToString(", "),
                    onRename = { areaToRename = area },
                    onPrefixesChange = { viewModel.setAreaPrefixes(area, it) },
                    onDelete = { areaToDelete = area }
                )
            }

            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showAddAreaDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Добавить участок")
            }
        }

        // ============ ОПРЕДЕЛЕНИЕ НАРЯДА ============
        SettingsCard("Определение наряда") {
            Text("Источник имени", style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = listOf(
                    OrderSource.AUTO to "Авто",
                    OrderSource.FILENAME to "Имя файла",
                    OrderSource.SHEET_NAME to "Имя листа"
                ),
                selected = settings.orderSource,
                onSelect = { viewModel.setOrderSource(it) }
            )

            Spacer(Modifier.height(8.dp))
            Text("Как извлекать номер", style = MaterialTheme.typography.bodyMedium)
            ChoiceRow(
                options = listOf(
                    OrderNumberRule.LAST_INTEGER to "Последнее число",
                    OrderNumberRule.FULL_NAME to "Всё имя целиком"
                ),
                selected = settings.orderNumberRule,
                onSelect = { viewModel.setOrderNumberRule(it) }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Пример: «02-КОПТ00027» → «27» при правиле «последнее число».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ============ КЛЮЧЕВЫЕ СЛОВА ============
        SettingsCard("Ключевые слова") {
            KeywordField(
                label = "Холостая проба",
                csv = settings.hollowKeywords.joinToString(", "),
                onChange = { viewModel.setHollowKeywords(it) }
            )
            KeywordField(
                label = "Шнековая проба",
                csv = settings.augerKeywords.joinToString(", "),
                onChange = { viewModel.setAugerKeywords(it) }
            )
            KeywordField(
                label = "Бороздовая / канава",
                csv = settings.channelKeywords.joinToString(", "),
                onChange = { viewModel.setChannelKeywords(it) }
            )
            KeywordField(
                label = "Бланк / стандартный образец",
                csv = settings.blankKeywords.joinToString(", "),
                onChange = { viewModel.setBlankKeywords(it) }
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = settings.skipBlanksWithoutData,
                    onCheckedChange = { viewModel.setSkipBlanks(it) }
                )
                Text(
                    "Пропускать бланки без интервала и веса",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // ============ ДЕЙСТВИЯ ============
        SettingsCard("Действия") {
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Сохранить")
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        exportLauncher.launch("geosample_import_settings.json")
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Экспорт") }
                OutlinedButton(
                    onClick = {
                        importLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Импорт") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.resetToDefaults() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Сбросить к стандартным") }
        }
    }

    // ============ Диалоги ============

    if (showAddAreaDialog) {
        TextInputDialog(
            title = "Новый участок",
            label = "Название",
            initial = "",
            onConfirm = {
                viewModel.addArea(it)
                showAddAreaDialog = false
            },
            onDismiss = { showAddAreaDialog = false }
        )
    }

    areaToRename?.let { old ->
        TextInputDialog(
            title = "Переименовать участок",
            label = "Новое имя",
            initial = old,
            onConfirm = {
                viewModel.renameArea(old, it)
                areaToRename = null
            },
            onDismiss = { areaToRename = null }
        )
    }

    areaToDelete?.let { area ->
        AlertDialog(
            onDismissRequest = { areaToDelete = null },
            title = { Text("Удалить участок?") },
            text = { Text("Участок «$area» и все его префиксы будут удалены из настроек.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeArea(area)
                    areaToDelete = null
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { areaToDelete = null }) { Text("Отмена") }
            }
        )
    }
}

// ============================================================
// ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ
// ============================================================

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun AreaRow(
    areaName: String,
    prefixesCsv: String,
    onRename: () -> Unit,
    onPrefixesChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    var localCsv by remember(prefixesCsv) { mutableStateOf(prefixesCsv) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                areaName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRename) { Text("Переименовать") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        OutlinedTextField(
            value = localCsv,
            onValueChange = {
                localCsv = it
                onPrefixesChange(it)
            },
            singleLine = true,
            label = { Text("Префиксы через запятую") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun KeywordField(label: String, csv: String, onChange: (String) -> Unit) {
    var local by remember(csv) { mutableStateOf(csv) }
    OutlinedTextField(
        value = local,
        onValueChange = {
            local = it
            onChange(it)
        },
        singleLine = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    )
}

@Composable
private fun <T> ChoiceRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
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
            TextButton(onClick = { onConfirm(text) }) { Text("ОК") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}