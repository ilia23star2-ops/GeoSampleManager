package com.example.geosamplemanager.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.backup.PublicBackup
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-db-import-picker:
 * Диалог импорта — список .gsmbackup из папки exports.
 * Авто-бэкапы pre_* здесь НЕ показываются — ими занимается «Откат».
 *
 * FIX 5.9-db-rollback-public:
 *  - PublicBackup.uri теперь String; key — сама строка.
 */
@Composable
fun DbImportPickerDialog(
    backups: List<PublicBackup>,
    loading: Boolean,
    onSelect: (PublicBackup) -> Unit,
    onPickExternal: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy, HH:mm", Locale("ru", "RU"))
    val apiOk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт БД") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Файлы для импорта (папка exports):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                when {
                    !apiOk -> {
                        Text(
                            "Автопоиск файлов доступен на Android 10+. " +
                                    "Нажмите «Выбрать файл вручную…».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    backups.isEmpty() -> {
                        Text(
                            "Файлов в exports нет. Сделайте «Экспорт» или " +
                                    "нажмите «Выбрать файл вручную…».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text(
                            "Найдено ${backups.size}. Выберите файл:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 380.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(backups, key = { it.uri }) { b ->
                                PublicBackupRow(b, dateFormat) { onSelect(b) }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                TextButton(
                    onClick = onPickExternal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Выбрать файл вручную…")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        }
    )
}

@Composable
private fun PublicBackupRow(
    backup: PublicBackup,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                backup.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        dateFormat.format(Date(backup.lastModified)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    formatSize(backup.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val mf = backup.manifest
            if (mf != null) {
                Text(
                    "Пробы: ${mf.samples}, наряды: ${mf.orders}, " +
                            "фото: ${mf.photos}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Манифест не прочитан",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f МБ", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.0f КБ", kb)
        else -> "$bytes Б"
    }
}