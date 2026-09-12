package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.geosamplemanager.data.excel.ExcelAnalyzer

@Composable
fun MappingEditorDialog(
    headers: List<String>,
    rows: List<List<String>>,
    autoMapping: Map<String, Int?>,
    initialMapping: Map<String, Int?>,
    onApply: (Map<String, Int?>) -> Unit,
    onDismiss: () -> Unit
) {
    var currentMapping by remember { mutableStateOf(initialMapping.toMutableMap()) }
    var activeRole by remember { mutableStateOf<String?>(ExcelAnalyzer.Roles.WELL) }

    Dialog(
        onDismissRequest = { /* тап мимо — ничего */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(0.97f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ручной маппинг колонок",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "Активная роль: ${activeRole?.let { RoleColors.roleTitle(it) } ?: "не выбрана"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                HorizontalDivider()

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.6f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    ) {
                        val vScroll = rememberScrollState()
                        val hScroll = rememberScrollState()
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .verticalScroll(vScroll)
                                .horizontalScroll(hScroll)
                        ) {
                            Column {
                                // Шапка
                                Row {
                                    for (i in headers.indices) {
                                        val role = roleOfColumn(i, currentMapping)
                                        val bg = if (role != null) RoleColors.forRole(role)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                        val fg = if (role != null) RoleColors.textOn(bg)
                                        else MaterialTheme.colorScheme.onSurfaceVariant

                                        Box(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .background(bg)
                                                .border(0.5.dp, MaterialTheme.colorScheme.outline)
                                                .clickable {
                                                    val r = activeRole ?: return@clickable
                                                    val newMapping = currentMapping.toMutableMap()
                                                    newMapping.entries
                                                        .firstOrNull { it.value == i }
                                                        ?.let { newMapping[it.key] = null }
                                                    newMapping[r] = i
                                                    currentMapping = newMapping
                                                }
                                                .padding(6.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    "${columnLetter(i)} · ${headers[i].ifEmpty { "—" }}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = fg,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 3
                                                )
                                                if (role != null) {
                                                    Text(
                                                        RoleColors.roleTitle(role),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = fg,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // Данные — 20 строк
                                for (row in rows.take(20)) {
                                    Row {
                                        for (i in headers.indices) {
                                            val v = row.getOrNull(i) ?: ""
                                            Box(
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                                    .padding(6.dp)
                                            ) {
                                                Text(
                                                    v,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    VerticalDivider()

                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.4f)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Роли", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))

                        RoleColors.ALL_ROLES.forEach { role ->
                            RoleRow(
                                role = role,
                                columnIndex = currentMapping[role],
                                headers = headers,
                                isActive = activeRole == role,
                                isChanged = currentMapping[role] != autoMapping[role],
                                onSelect = { activeRole = role },
                                onClear = {
                                    val newMapping = currentMapping.toMutableMap()
                                    newMapping[role] = null
                                    currentMapping = newMapping
                                }
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { currentMapping = autoMapping.toMutableMap() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Сбросить к авто")
                        }
                    }
                }

                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Что программа прочитала в шапке:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    RoleColors.ALL_ROLES.forEach { role ->
                        val idx = currentMapping[role]
                        val headerText = if (idx != null && idx < headers.size)
                            headers[idx].ifEmpty { "—" } else "—"
                        val color = if (idx != null) RoleColors.forRole(role)
                        else MaterialTheme.colorScheme.surfaceVariant
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color, RoundedCornerShape(2.dp))
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "${RoleColors.roleTitle(role)}: $headerText",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(currentMapping) }) { Text("Применить") }
                }
            }
        }
    }
}

@Composable
private fun RoleRow(
    role: String,
    columnIndex: Int?,
    headers: List<String>,
    isActive: Boolean,
    isChanged: Boolean,
    onSelect: () -> Unit,
    onClear: () -> Unit
) {
    val color = RoleColors.forRole(role)
    val headerText = if (columnIndex != null && columnIndex < headers.size)
        headers[columnIndex].ifEmpty { "—" } else "—"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        RoleColors.roleTitle(role),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isChanged) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "изменено",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    headerText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (columnIndex != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Clear, contentDescription = "Убрать", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun roleOfColumn(i: Int, mapping: Map<String, Int?>): String? =
    mapping.entries.firstOrNull { it.value == i }?.key

private fun columnLetter(index: Int): String {
    var n = index
    val sb = StringBuilder()
    while (true) {
        sb.insert(0, ('A' + (n % 26)))
        n = n / 26 - 1
        if (n < 0) break
    }
    return sb.toString()
}