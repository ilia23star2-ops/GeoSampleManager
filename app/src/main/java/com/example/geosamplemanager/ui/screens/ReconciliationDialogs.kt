package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Все диалоги экрана «Сверка и поиск».
 */

// ====================================================================
// Диалог веса
// ====================================================================

@Composable
fun WeightDialog(
    title: String,
    sampleNumber: String,
    initialWeight: Double? = null,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialWeight?.toString() ?: "") }
    val parsed = value.replace(',', '.').toDoubleOrNull()
    val valid = parsed != null && parsed > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    "Проба $sampleNumber. Введите вес — она сразу отметится как найденная.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Вес, кг") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = valid) {
                Text("Подтвердить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ====================================================================
// Характеристика
// ====================================================================

@Composable
fun CharacteristicDialog(
    sampleNumber: String,
    characteristic: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Характеристика материала") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Проба $sampleNumber",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(characteristic, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } }
    )
}

// ====================================================================
// Заметка
// ====================================================================

@Composable
fun NoteDialog(
    sampleNumber: String,
    initialText: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Заметка к пробе") },
        text = {
            Column {
                Text(
                    "Проба $sampleNumber",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Текст заметки") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    maxLines = 6
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PhotoCamera, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Прикрепить фото — в разработке",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ====================================================================
// Редактор пробы
// ====================================================================

@Composable
fun EditSampleDialog(
    row: SampleRow,
    onSave: (SampleRow) -> Unit,
    onDismiss: () -> Unit
) {
    var sampleNumber by remember { mutableStateOf(row.sampleNumber) }
    var wellNumber by remember { mutableStateOf(row.wellNumber) }
    var intervalFrom by remember { mutableStateOf(row.intervalFrom) }
    var intervalTo by remember { mutableStateOf(row.intervalTo) }
    var weight by remember { mutableStateOf(row.weight?.toString() ?: "") }
    var controlWeight by remember { mutableStateOf(row.controlWeight?.toString() ?: "") }
    var characteristic by remember { mutableStateOf(row.characteristic) }
    var type by remember { mutableStateOf(row.type) }
    var status by remember { mutableStateOf(row.status) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактирование пробы") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = wellNumber, onValueChange = { wellNumber = it },
                    label = { Text("№ скважины / выработки") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sampleNumber, onValueChange = { sampleNumber = it },
                    label = { Text("№ пробы") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = intervalFrom, onValueChange = { intervalFrom = it },
                        label = { Text("От, м") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = intervalTo, onValueChange = { intervalTo = it },
                        label = { Text("До, м") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = weight, onValueChange = { weight = it },
                        label = { Text("Вес, кг") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = controlWeight, onValueChange = { controlWeight = it },
                        label = { Text("ВК, кг") }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedTextField(
                    value = characteristic, onValueChange = { characteristic = it },
                    label = { Text("Характеристика") }, maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Тип пробы",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                SampleType.values().forEach { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = type == t, onClick = { type = t })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = type == t, onClick = { type = t })
                        Spacer(Modifier.width(6.dp))
                        Text(t.title)
                    }
                }

                Text("Статус",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                SampleStatus.values().forEach { s ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = status == s, onClick = { status = s })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = status == s, onClick = { status = s })
                        Spacer(Modifier.width(6.dp))
                        Text(s.title)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    row.copy(
                        sampleNumber = sampleNumber,
                        wellNumber = wellNumber,
                        intervalFrom = intervalFrom,
                        intervalTo = intervalTo,
                        weight = weight.replace(',', '.').toDoubleOrNull(),
                        controlWeight = controlWeight.replace(',', '.').toDoubleOrNull(),
                        characteristic = characteristic,
                        type = type,
                        status = status
                    )
                )
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ====================================================================
// Удаление пробы
// ====================================================================

@Composable
fun DeleteSampleDialog(
    row: SampleRow,
    onConfirm: (recalc: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var recalc by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить пробу?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Проба ${row.sampleNumber} будет удалена из наряда. " +
                            "Действие можно будет отменить кнопкой «Отмена» в шапке экрана.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = recalc, onClick = { recalc = !recalc })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = recalc,
                        onCheckedChange = { recalc = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text("Пересчитать № проб в скважине")
                        Text(
                            "Последующие пробы получат номера 01, 02, 03…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(recalc) },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ====================================================================
// НАСТРОЙКИ НАРЯДА — единый диалог с двумя секциями
// ====================================================================

@Composable
fun OrderSettingsDialog(
    orderTitle: String,
    initialBlank: BlankWeightSettings,
    initialWeightControlStep: Int,
    isUsingGlobal: Boolean,
    onSave: (BlankWeightSettings, Int) -> Unit,
    onResetToGlobal: () -> Unit,
    onResetBlankWeights: () -> Unit,
    onDismiss: () -> Unit
) {
    var mode by remember { mutableStateOf(initialBlank.mode) }
    var fixedValue by remember {
        mutableStateOf(initialBlank.fixedValue?.toString() ?: "")
    }
    var stepValue by remember {
        mutableStateOf(initialWeightControlStep.toString())
    }

    val fixedParsed = fixedValue.replace(',', '.').toDoubleOrNull()
    val stepParsed = stepValue.toIntOrNull()

    val canSave = (mode != BlankWeightMode.FIXED
            || (fixedParsed != null && fixedParsed > 0))
            && (stepParsed != null && stepParsed > 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки наряда") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    orderTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isUsingGlobal) {
                    Text(
                        "Используются глобальные настройки",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // ---------------- СЕКЦИЯ: ХОЛОСТЫЕ ----------------
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckBoxOutlineBlank, null,
                        tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Холостые пробы",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                BlankWeightMode.values().forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = mode == m, onClick = { mode = m })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == m, onClick = { mode = m })
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(m.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when (m) {
                                    BlankWeightMode.FIXED ->
                                        "Всем холостым без веса проставится один вес."
                                    BlankWeightMode.AVERAGE ->
                                        "Вес = среднее соседних не-холостых проб."
                                    BlankWeightMode.MANUAL ->
                                        "Рабочий вводит вес вручную в таблице."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (mode == BlankWeightMode.FIXED) {
                    OutlinedTextField(
                        value = fixedValue,
                        onValueChange = { fixedValue = it },
                        label = { Text("Единый вес, кг") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Пояснение
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Единый и средний вес проставляются только тем холостым пробам, " +
                                    "у которых вес ещё не введён. Если вес уже был задан — " +
                                    "он не меняется. Чтобы задать вес заново всем холостым, " +
                                    "нажмите «Сбросить вес холостых».",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Кнопка «Сбросить вес холостых»
                TextButton(
                    onClick = onResetBlankWeights,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.RestartAlt, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Сбросить вес холостых в наряде")
                }

                // ---------------- СЕКЦИЯ: ВЕСОВОЙ КОНТРОЛЬ ----------------
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Scale, null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Весовой контроль",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "Каждая N-я рядовая проба будет помечаться как весовой контроль " +
                            "при следующем импорте. Холостые при счёте пропускаются.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = stepValue,
                    onValueChange = { stepValue = it.filter { c -> c.isDigit() } },
                    label = { Text("Каждая N-я проба") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val settings = BlankWeightSettings(
                        mode = mode,
                        fixedValue = if (mode == BlankWeightMode.FIXED) fixedParsed else null
                    )
                    onSave(settings, stepParsed ?: 5)
                },
                enabled = canSave
            ) { Text("Сохранить") }
        },
        dismissButton = {
            Row {
                if (!isUsingGlobal) {
                    TextButton(onClick = onResetToGlobal) {
                        Text("Сбросить к глобальным")
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

// ====================================================================
// Подтверждение сброса веса холостых
// ====================================================================

@Composable
fun ConfirmResetBlankWeightDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сбросить вес холостых?") },
        text = {
            Text(
                "У всех холостых проб в этом наряде будет сброшен вес и " +
                        "снята отметка «найдена». Действие можно будет отменить.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) { Text("Сбросить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ====================================================================
// Массовая отметка — диалог решений
// ====================================================================

@Composable
fun BulkActionsDialog(
    decisions: List<ReconciliationState.BulkDecision>,
    onApply: (
        weights: Map<String, Double>,
        postponedActions: Map<String, Boolean>
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val weights = remember { mutableStateMapOf<String, String>() }
    val postponedMark = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(decisions) {
        decisions.forEach { d ->
            when (d) {
                is ReconciliationState.BulkDecision.WeightControlNeedsWeight ->
                    weights.putIfAbsent(d.row.id, "")
                is ReconciliationState.BulkDecision.PostponedNeedsAction ->
                    postponedMark.putIfAbsent(d.row.id, false)
            }
        }
    }

    val vkCount = decisions.count {
        it is ReconciliationState.BulkDecision.WeightControlNeedsWeight
    }
    val postCount = decisions.count {
        it is ReconciliationState.BulkDecision.PostponedNeedsAction
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Массовая отметка") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Нужно принять решения по пробам ниже. Остальные отметятся автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (vkCount > 0) {
                    Text(
                        "Весовой контроль без веса ($vkCount):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    decisions.filterIsInstance<ReconciliationState.BulkDecision.WeightControlNeedsWeight>()
                        .forEach { d ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(d.row.sampleNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text("скв. ${d.row.wellNumber}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                OutlinedTextField(
                                    value = weights[d.row.id] ?: "",
                                    onValueChange = { weights[d.row.id] = it },
                                    label = { Text("Вес, кг") },
                                    singleLine = true,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                }

                if (postCount > 0) {
                    Text(
                        "Отложенные пробы ($postCount):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    decisions.filterIsInstance<ReconciliationState.BulkDecision.PostponedNeedsAction>()
                        .forEach { d ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = postponedMark[d.row.id] == true,
                                        onClick = {
                                            postponedMark[d.row.id] =
                                                postponedMark[d.row.id] != true
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = postponedMark[d.row.id] == true,
                                    onCheckedChange = { postponedMark[d.row.id] = it }
                                )
                                Spacer(Modifier.width(6.dp))
                                Column {
                                    Text(d.row.sampleNumber,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium)
                                    Text("Отметить как найденную",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val wMap = weights.mapNotNull { (k, v) ->
                    v.replace(',', '.').toDoubleOrNull()
                        ?.let { if (it > 0) k to it else null }
                }.toMap()
                val pMap = postponedMark.toMap()
                onApply(wMap, pMap)
            }) { Text("Применить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// ====================================================================
// Уже найдена, Ошибка, Отложена — одиночные диалоги
// ====================================================================

@Composable
fun AlreadyFoundDialog(
    row: SampleRow,
    onUnmarkFound: () -> Unit,
    onTogglePostponed: () -> Unit,
    onViewNote: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проба уже отмечена") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Проба ${row.sampleNumber} отмечена как найденная.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                Text("Что сделать?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)

                TextButton(onClick = { onUnmarkFound() }) {
                    Icon(Icons.Filled.RemoveCircleOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Снять отметку «найдена»")
                }
                TextButton(onClick = { onTogglePostponed() }) {
                    Icon(Icons.Filled.PauseCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (row.postponed) "Снять «отложена»" else "Отложить пробу")
                }
                if (row.hasNote) {
                    TextButton(onClick = { onViewNote() }) {
                        Icon(Icons.Filled.EditNote, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Просмотреть заметку")
                    }
                }
                TextButton(onClick = { onEdit() }) {
                    Icon(Icons.Filled.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Редактировать пробу")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}

@Composable
fun ImportErrorDialog(
    row: SampleRow,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проба с ошибкой") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, null,
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Обнаружена проблема с данными",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Проба ${row.sampleNumber} помечена как ошибочная. " +
                            "Скорее всего номер пробы совпадает с номером скважины " +
                            "или содержит ошибку. Проверьте данные.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Продолжить и отметить") } },
        dismissButton = {
            Row {
                TextButton(onClick = onEdit) { Text("Редактировать") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable
fun PostponedDialog(
    row: SampleRow,
    onConfirm: () -> Unit,
    onViewNote: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Проба отложена") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PauseCircle, null,
                        tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(8.dp))
                    Text("Эта проба помечена как отложенная",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Проба ${row.sampleNumber}. Вы можете отметить её как найденную " +
                            "(если она найдена) или просмотреть заметку.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Отметить как найденную") } },
        dismissButton = {
            Row {
                if (row.hasNote) {
                    TextButton(onClick = onViewNote) { Text("Заметка") }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}