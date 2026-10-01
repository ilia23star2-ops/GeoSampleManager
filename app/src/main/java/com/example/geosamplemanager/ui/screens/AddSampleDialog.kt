package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-edit-add-sample/3:
 *  - фикс: при переключении статуса BLANK ↔ NORMAL интервал
 *    перевосстанавливается;
 *  - убран вариант «Заменить» при конфликте.
 *
 * FIX 5.9-validation-fix:
 *  - кнопка «Добавить» всегда активна;
 *  - при клике выставляется validationAttempted = true — подписи
 *    ошибок появляются;
 *  - добавление не проходит, пока форма невалидна.
 *
 * HOTFIX 5.9-validation-fix:
 *  - возвращена аннотация @OptIn(ExperimentalMaterial3Api::class)
 *    для ExposedDropdownMenuBox / ExposedDropdownMenu / menuAnchor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSampleDialog(
    orderTitle: String,
    wellOptions: List<String>,
    commonPrefix: String,
    initialWellNumber: String?,
    suggestSampleNumber: (String) -> String,
    suggestIntervalFrom: (String) -> String,
    findConflict: (String) -> SampleConflict?,
    onAdd: (
        wellNumber: String,
        sampleNumber: String,
        intervalFrom: Double?,
        intervalTo: Double?,
        weight: Double?,
        characteristic: String,
        type: SampleType,
        status: SampleStatus
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var isNewWell by remember { mutableStateOf(wellOptions.isEmpty()) }
    var selectedWell by remember {
        mutableStateOf(initialWellNumber ?: wellOptions.firstOrNull().orEmpty())
    }
    var newWellText by remember { mutableStateOf("") }
    var wellMenuExpanded by remember { mutableStateOf(false) }
    var wellFilter by remember { mutableStateOf("") }

    val actualWell = if (isNewWell) newWellText.trim() else selectedWell

    var sampleNumber by remember { mutableStateOf("") }
    var sampleNumberUserTouched by remember { mutableStateOf(false) }

    var intervalFrom by remember { mutableStateOf("") }
    var intervalTo by remember { mutableStateOf("") }

    var weight by remember { mutableStateOf("") }
    var characteristic by remember { mutableStateOf("") }

    var type by remember { mutableStateOf(SampleType.AUGER) }
    var status by remember { mutableStateOf(SampleStatus.NORMAL) }

    val isBlank = status == SampleStatus.BLANK

    LaunchedEffect(actualWell) {
        if (!sampleNumberUserTouched) {
            sampleNumber = suggestSampleNumber(actualWell)
        }
    }

    LaunchedEffect(actualWell, isBlank) {
        if (!isBlank && intervalFrom.isBlank()) {
            intervalFrom = suggestIntervalFrom(actualWell)
        }
    }

    val conflict = remember(sampleNumber, actualWell) {
        if (sampleNumber.isBlank()) null else findConflict(sampleNumber)
    }

    var validationAttempted by remember { mutableStateOf(false) }
    val wellError = actualWell.isBlank()
    val sampleError = sampleNumber.isBlank()
    val intervalFromError = !isBlank && intervalFrom.replace(',', '.').toDoubleOrNull() == null
    val intervalToError = !isBlank && intervalTo.replace(',', '.').toDoubleOrNull() == null
    val intervalOrderError = !isBlank && !intervalFromError && !intervalToError &&
            (intervalFrom.replace(',', '.').toDoubleOrNull() ?: 0.0) >=
            (intervalTo.replace(',', '.').toDoubleOrNull() ?: 0.0)

    val formValid = !wellError && !sampleError &&
            (isBlank || (!intervalFromError && !intervalToError && !intervalOrderError))

    var showConflictConfirm by remember { mutableStateOf(false) }

    val filteredWells = remember(wellFilter, wellOptions) {
        val q = wellFilter.trim().lowercase()
        if (q.isEmpty()) wellOptions
        else wellOptions.filter { it.lowercase().contains(q) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Новая проба")
                Text(
                    orderTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Скважина",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (wellOptions.isNotEmpty()) {
                        TextButton(onClick = {
                            isNewWell = !isNewWell
                            if (isNewWell) {
                                newWellText = commonPrefix
                                wellMenuExpanded = false
                            }
                        }) {
                            Text(if (isNewWell) "Из списка" else "Новая")
                        }
                    }
                }

                if (isNewWell) {
                    OutlinedTextField(
                        value = newWellText,
                        onValueChange = { newWellText = it },
                        label = { Text("№ скважины") },
                        placeholder = { Text(commonPrefix + "...") },
                        singleLine = true,
                        isError = validationAttempted && wellError,
                        supportingText = {
                            if (validationAttempted && wellError) {
                                Text(
                                    "Заполните № скважины",
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else if (commonPrefix.isNotBlank() &&
                                !newWellText.startsWith(commonPrefix)
                            ) {
                                Text(
                                    "Префикс наряда: $commonPrefix",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = wellMenuExpanded,
                        onExpandedChange = { wellMenuExpanded = !wellMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedWell,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Скважина") },
                            isError = validationAttempted && wellError,
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(wellMenuExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = wellMenuExpanded,
                            onDismissRequest = { wellMenuExpanded = false }
                        ) {
                            OutlinedTextField(
                                value = wellFilter,
                                onValueChange = { wellFilter = it },
                                placeholder = { Text("Фильтр") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Search, null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            if (filteredWells.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Ничего не найдено") },
                                    enabled = false,
                                    onClick = {}
                                )
                            } else {
                                filteredWells.forEach { w ->
                                    DropdownMenuItem(
                                        text = { Text(w) },
                                        onClick = {
                                            selectedWell = w
                                            wellFilter = ""
                                            wellMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conflict != null) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Конфликт",
                            tint = Color(0xFFF9A825),
                            modifier = Modifier.size(20.dp).padding(end = 4.dp)
                        )
                    }
                    OutlinedTextField(
                        value = sampleNumber,
                        onValueChange = {
                            sampleNumber = it
                            sampleNumberUserTouched = true
                        },
                        label = { Text("№ пробы") },
                        singleLine = true,
                        isError = validationAttempted && sampleError,
                        supportingText = {
                            when {
                                validationAttempted && sampleError ->
                                    Text(
                                        "Заполните № пробы",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                conflict != null ->
                                    Text(
                                        "Уже есть: №${conflict.existingNumberInWell}" +
                                                ", интервал " +
                                                "${conflict.existingIntervalFrom}–" +
                                                "${conflict.existingIntervalTo}",
                                        color = Color(0xFFF9A825)
                                    )
                                else -> null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()

                if (!isBlank) {
                    Text(
                        "Интервал, м",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        OutlinedTextField(
                            value = intervalFrom,
                            onValueChange = { intervalFrom = it },
                            label = { Text("От") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            isError = validationAttempted && intervalFromError,
                            supportingText = {
                                if (validationAttempted && intervalFromError) {
                                    Text(
                                        "Заполните",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else null
                            },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = intervalTo,
                            onValueChange = { intervalTo = it },
                            label = { Text("До") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            isError = validationAttempted &&
                                    (intervalToError || intervalOrderError),
                            supportingText = {
                                if (validationAttempted && intervalToError) {
                                    Text(
                                        "Заполните",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else if (validationAttempted && intervalOrderError) {
                                    Text(
                                        "«До» должно быть больше «От»",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            "У холостой пробы нет интервала",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Вес, кг (необязательно)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = characteristic,
                    onValueChange = { characteristic = it },
                    label = { Text("Характеристика (необязательно)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Тип пробы",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
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

                Text(
                    "Статус",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
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
            // FIX 5.9-validation-fix: кнопка всегда активна.
            TextButton(onClick = {
                validationAttempted = true
                if (!formValid) return@TextButton
                if (conflict != null) {
                    showConflictConfirm = true
                } else {
                    onAdd(
                        actualWell,
                        sampleNumber.trim(),
                        if (isBlank) null else intervalFrom.replace(',', '.').toDoubleOrNull(),
                        if (isBlank) null else intervalTo.replace(',', '.').toDoubleOrNull(),
                        weight.replace(',', '.').toDoubleOrNull(),
                        characteristic.trim(),
                        type,
                        status
                    )
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Добавить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )

    if (showConflictConfirm && conflict != null) {
        AlertDialog(
            onDismissRequest = { showConflictConfirm = false },
            title = { Text("№ ${sampleNumber} уже есть") },
            text = {
                Text(
                    "Проба ${conflict.existingSampleNumber} " +
                            "(№${conflict.existingNumberInWell}) уже существует. " +
                            "Новая встанет на её место. Все последующие пробы " +
                            "сдвинутся: номера +1, интервалы — на длину " +
                            "интервала новой пробы. Продолжить?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showConflictConfirm = false
                    onAdd(
                        actualWell,
                        sampleNumber.trim(),
                        if (isBlank) null else intervalFrom.replace(',', '.').toDoubleOrNull(),
                        if (isBlank) null else intervalTo.replace(',', '.').toDoubleOrNull(),
                        weight.replace(',', '.').toDoubleOrNull(),
                        characteristic.trim(),
                        type,
                        status
                    )
                }) { Text("Со сдвигом") }
            },
            dismissButton = {
                TextButton(onClick = { showConflictConfirm = false }) { Text("Отмена") }
            }
        )
    }
}