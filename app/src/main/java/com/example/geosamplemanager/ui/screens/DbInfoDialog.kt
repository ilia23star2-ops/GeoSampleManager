package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.DbInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-db-info:
 * Диалог «Информация о БД».
 *
 * Показывает:
 *  - путь к файлу БД;
 *  - размер БД и папки фото;
 *  - дату последнего изменения файла БД;
 *  - счётчики: участков / нарядов / скважин / проб / фото / заметок.
 *
 * Кнопка «Обновить» — повторный вызов loadDbInfo().
 */
@Composable
fun DbInfoDialog(
    info: DbInfo?,
    loading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru", "RU"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Информация о БД",
                    modifier = Modifier.weight(1f)
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            if (info == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (loading) "Загрузка…" else "Нет данных",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    InfoRow(
                        "Путь",
                        info.dbPath,
                        wrap = true
                    )
                    InfoRow("Размер БД", formatBytes(info.dbSizeBytes))
                    InfoRow("Размер фото", formatBytes(info.photosSizeBytes))
                    InfoRow(
                        "Обновлено",
                        if (info.lastModified > 0) dateFormat.format(Date(info.lastModified))
                        else "—"
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                    InfoRow("Участков", info.areasCount.toString())
                    InfoRow("Нарядов", info.ordersCount.toString())
                    InfoRow("Скважин", info.wellsCount.toString())
                    InfoRow("Проб", info.samplesCount.toString())
                    InfoRow("Фото", info.photosCount.toString())
                    InfoRow("Заметок", info.notesCount.toString())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Обновить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    wrap: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(110.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (wrap) Int.MAX_VALUE else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * FIX 5.9-db-info:
 * Форматирование размера: Б / КБ / МБ / ГБ.
 */
internal fun formatBytes(bytes: Long): String {
    return when {
        bytes < 0 -> "—"
        bytes < 1024 -> "$bytes Б"
        bytes < 1024L * 1024 -> "%.1f КБ".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f МБ".format(bytes / (1024.0 * 1024))
        else -> "%.2f ГБ".format(bytes / (1024.0 * 1024 * 1024))
    }
}