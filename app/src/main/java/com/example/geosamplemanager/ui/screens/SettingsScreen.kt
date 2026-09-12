package com.example.geosamplemanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.settings.OrderNumberRule
import com.example.geosamplemanager.data.settings.OrderSource

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsState()
    val selected by viewModel.selectedCategory.collectAsState()

    LaunchedEffect(Unit) { viewModel.reload() }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxW = maxWidth
        val isWide = maxW >= 600.dp

        if (isWide) {
            Row(modifier = Modifier.fillMaxSize()) {
                CategoryTree(
                    selected = selected,
                    onSelect = { viewModel.selectCategory(it) },
                    modifier = Modifier.fillMaxHeight().width(maxW * 0.4f)
                )
                VerticalDivider()
                CategoryContent(
                    category = selected,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxHeight().weight(1f)
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                CategoryChipsRow(selected = selected, onSelect = { viewModel.selectCategory(it) })
                HorizontalDivider()
                CategoryContent(
                    category = selected,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun CategoryTree(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(8.dp)
    ) {
        Text(
            "Настройки",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
        Spacer(Modifier.height(8.dp))
        SettingsCategory.values().forEach { cat ->
            val isSelected = cat == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    cat.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun CategoryChipsRow(
    selected: SettingsCategory,
    onSelect: (SettingsCategory) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SettingsCategory.values().forEach { cat ->
            FilterChip(
                selected = cat == selected,
                onClick = { onSelect(cat) },
                label = { Text(cat.title, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

@Composable
private fun CategoryContent(
    category: SettingsCategory,
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    when (category) {
        SettingsCategory.IMPORT -> ImportSettingsContent(viewModel, modifier)
        SettingsCategory.VOICE -> StubContent("Голос", "Настройки голосового помощника будут здесь.", modifier)
        SettingsCategory.APPEARANCE -> StubContent("Внешний вид", "Тема и размеры — в следующих обновлениях.", modifier)
        SettingsCategory.SYSTEM -> StubContent("Система", "Служебные настройки — позже.", modifier)
        SettingsCategory.ABOUT -> StubContent("О приложении", "GeoSample Manager, версия 1.0", modifier)
    }
}

@Composable
private fun StubContent(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================
// КОНТЕНТ «ИМПОРТ EXCEL»
// ============================================================

@Composable
private fun ImportSettingsContent(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsState()

    var showAddAreaDialog by remember { mutableStateOf(false) }
    var areaToRename by remember { mutableStateOf<String?>(null) }
    var areaToDelete by remember { mutableStateOf<String?>(null) }
    var showResetUserDialog by remember { mutableStateOf(false) }
    var showHeadersDialog by remember { mutableStateOf(false) }
    var showTypesDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportTo(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importFrom(it) } }

    val totalHeaderUserWords = settings.userHeaderKeywords.values.sumOf { it.size }
    val totalTypeUserWords = settings.userTypeValueKeywords.values.sumOf { it.size }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Настройки импорта", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

        InfoCard(
            title = "Участки и префиксы",
            help = "Префиксы нужны, чтобы при импорте автоматически определить участок " +
                    "по номерам скважин и проб. У одного участка может быть несколько префиксов."
        ) {
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

        InfoCard(
            title = "Определение наряда",
            help = "Программа берёт имя файла (если один лист) или имя листа " +
                    "(если листов несколько) и извлекает номер наряда. " +
                    "Например, из «02-КОПТ00027» получится наряд «27»."
        ) {
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
        }

        InfoCard(
            title = "Слова для заголовков",
            help = "Когда вы вручную назначите роль колонке при импорте, система " +
                    "запомнит это слово. Здесь можно посмотреть и отредактировать. " +
                    "Также показаны стандартные слова."
        ) {
            KeywordButton(
                label = "Слова для заголовков ($totalHeaderUserWords)",
                onClick = { showHeadersDialog = true }
            )
            Spacer(Modifier.height(6.dp))
            KeywordButton(
                label = "Слова для типов проб ($totalTypeUserWords)",
                onClick = { showTypesDialog = true }
            )
        }

        InfoCard(
            title = "Бланки и стандартные образцы",
            help = "Бланк — зарезервированное место под контрольный образец. У него нет " +
                    "интервала, веса и характеристики. Такие строки пропускаются при " +
                    "импорте. Холостые пробы сюда не относятся."
        ) {
            KeywordField(
                label = "Слова-маркеры (через запятую)",
                csv = settings.blankKeywords.joinToString(", "),
                onChange = { viewModel.setBlankKeywords(it) }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = settings.skipBlanksWithoutData,
                    onCheckedChange = { viewModel.setSkipBlanks(it) }
                )
                Text("Пропускать бланки без интервала и веса", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Управление настройками",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("geosample_import_settings.json") },
                        modifier = Modifier.weight(1f)
                    ) { Text("Экспорт") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) { Text("Импорт") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showResetUserDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сбросить пользовательские слова") }
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { viewModel.resetToDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Сбросить всё к стандартным") }
            }
        }
    }

    // ============ ДИАЛОГИ ============

    if (showAddAreaDialog) {
        TextInputDialog(
            title = "Новый участок",
            label = "Название",
            initial = "",
            onConfirm = { viewModel.addArea(it); showAddAreaDialog = false },
            onDismiss = { showAddAreaDialog = false }
        )
    }

    areaToRename?.let { old ->
        TextInputDialog(
            title = "Переименовать участок",
            label = "Новое имя",
            initial = old,
            onConfirm = { viewModel.renameArea(old, it); areaToRename = null },
            onDismiss = { areaToRename = null }
        )
    }

    areaToDelete?.let { area ->
        AlertDialog(
            onDismissRequest = { areaToDelete = null },
            title = { Text("Удалить участок?") },
            text = { Text("Участок «$area» и все его префиксы будут удалены из настроек.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeArea(area); areaToDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { areaToDelete = null }) { Text("Отмена") } }
        )
    }

    if (showResetUserDialog) {
        AlertDialog(
            onDismissRequest = { showResetUserDialog = false },
            title = { Text("Сбросить пользовательские слова?") },
            text = { Text("Будут удалены все слова, добавленные через ручной маппинг. " +
                    "Участки, наряды и стандартные словари не тронуты.") },
            confirmButton = {
                TextButton(onClick = { viewModel.resetUserKeywords(); showResetUserDialog = false }) {
                    Text("Сбросить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showResetUserDialog = false }) { Text("Отмена") } }
        )
    }

    // === Диалог слов для заголовков ===
    if (showHeadersDialog) {
        // Формируем sections из актуального settings.
        // Передаём список данных, а не лямбду — Compose будет перерисовывать.
        val sections = listOf(
            "serial" to "Серийный №",
            "well" to "Скважина / Выработка",
            "sample" to "Проба",
            "int_from" to "Интервал от",
            "int_to" to "Интервал до",
            "weight" to "Вес",
            "material" to "Характеристика",
            "type" to "Тип пробы"
        ).map { (role, title) ->
            KeywordSectionUi(
                key = role,
                title = title,
                userWords = settings.userKeywordsForHeader(role),
                defaultWords = settings.defaultHeaderKeywords[role].orEmpty()
            )
        }
        HeaderKeywordsDialog(
            title = "Слова для заголовков",
            help = "Ваши слова используются при автоматическом определении колонок. " +
                    "Стандартные слова уже работают автоматически — их трогать не нужно.",
            sections = sections,
            onAdd = { role, word -> viewModel.addUserHeaderKeyword(role, word) },
            onRemove = { role, word -> viewModel.removeUserHeaderKeyword(role, word) },
            onDismiss = { showHeadersDialog = false }
        )
    }

    // === Диалог слов для типов проб ===
    if (showTypesDialog) {
        val sections = listOf(
            "hollow" to "Холостая",
            "auger" to "Шнековая",
            "channel" to "Бороздовая / канава",
            "cobra" to "Кобра",
            "duplicate" to "Дубликат"
        ).map { (code, title) ->
            KeywordSectionUi(
                key = code,
                title = title,
                userWords = settings.userKeywordsForType(code),
                defaultWords = settings.defaultTypeValueKeywords[code].orEmpty()
            )
        }
        HeaderKeywordsDialog(
            title = "Слова для типов проб",
            help = "Значения из колонки «Тип пробы», которые надо отнести к определённому типу. " +
                    "Например, если в файле пишут «керновая» — добавьте это в шнековую.",
            sections = sections,
            onAdd = { code, word -> viewModel.addUserTypeKeyword(code, word) },
            onRemove = { code, word -> viewModel.removeUserTypeKeyword(code, word) },
            onDismiss = { showTypesDialog = false }
        )
    }
}

// ============================================================
// ВСПОМОГАТЕЛЬНЫЕ КОМПОНЕНТЫ
// ============================================================

@Composable
private fun KeywordButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun InfoCard(
    title: String,
    help: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    var helpVisible by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (help != null) {
                    IconButton(onClick = { helpVisible = !helpVisible }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Пояснение",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            if (help != null && helpVisible) {
                Text(
                    help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)
                )
            }
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
            onValueChange = { localCsv = it; onPrefixesChange(it) },
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
        onValueChange = { local = it; onChange(it) },
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
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("ОК") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}