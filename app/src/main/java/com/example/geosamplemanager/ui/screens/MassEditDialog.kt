package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-edit-mass-ops:
 * Диалог массовой правки выделенных проб.
 *
 * Три поля, каждое — чекбокс «менять»:
 *  - Характеристика (текст);
 *  - Тип (radio);
 *  - Статус (radio).
 *
 * Кнопка «Применить» — активна, если включён хотя бы один чекбокс
 * И (для характеристики) значение непустое.
 */
@Composable
fun MassEditDialog(
    count: Int,
    onApply: (MassEditFields) -> Unit,
    onDismiss: () -> Unit
) {
    var editCharacteristic by remember { mutableStateOf(false) }
    var editType by remember { mutableStateOf(false) }
    var editStatus by remember { mutableStateOf(false) }

    var characteristic by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(SampleType.AUGER) }
    var status by remember { mutableStateOf(SampleStatus.NORMAL) }

    val characteristicValid = !editCharacteristic || characteristic.isNotBlank()
    val anySelected = editCharacteristic || editType || editStatus
    val canApply = anySelected && characteristicValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Массовая правка")
                Text(
                    "Выбрано проб: $count",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Отметьте поля, которые нужно изменить. Остальные " +
                            "останутся как были у каждой пробы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Характеристика.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = editCharacteristic,
                            onValueChange = { editCharacteristic = it }
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = editCharacteristic,
                        onCheckedChange = { editCharacteristic = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Характеристика",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (editCharacteristic) {
                    OutlinedTextField(
                        value = characteristic,
                        onValueChange = { characteristic = it },
                        label = { Text("Новое значение") },
                        maxLines = 3,
                        isError = !characteristicValid,
                        supportingText = {
                            if (!characteristicValid) {
                                Text(
                                    "Заполните значение",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                // Тип.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = editType,
                            onValueChange = { editType = it }
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = editType,
                        onCheckedChange = { editType = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Тип пробы",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (editType) {
                    SampleType.values().forEach { t ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = type == t,
                                    onClick = { type = t }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = type == t,
                                onClick = { type = t }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(t.title)
                        }
                    }
                }

                HorizontalDivider()

                // Статус.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = editStatus,
                            onValueChange = { editStatus = it }
                        )
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = editStatus,
                        onCheckedChange = { editStatus = it }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Статус",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (editStatus) {
                    SampleStatus.values().forEach { s ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = status == s,
                                    onClick = { status = s }
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = status == s,
                                onClick = { status = s }
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(s.title)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canApply,
                onClick = {
                    onApply(
                        MassEditFields(
                            characteristic = if (editCharacteristic) characteristic else null,
                            type = if (editType) type else null,
                            status = if (editStatus) status else null
                        )
                    )
                }
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}