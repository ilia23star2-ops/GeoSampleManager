package com.example.geosamplemanager.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.logs.LogCategory
import com.example.geosamplemanager.data.logs.LogEntry
import com.example.geosamplemanager.data.logs.LogFormatter
import com.example.geosamplemanager.data.logs.LogLevel
import com.example.geosamplemanager.data.logs.LogsFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-logs-5:
 * Полноэкранный экран «Журнал событий». Открывается из
 * Настроек. Закрывается кнопкой «Назад» или системным back
 * (onClose).
 *
 * Список — от свежих к старым, разделители по дням.
 * Фильтр — чипы по категориям.
 * Тап на запись с details раскрывает технические данные.
 * Для ошибок — level=error, красный текст, полный stacktrace
 * в раскрытии.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    onClose: () -> Unit,
    viewModel: LogsViewModel = viewModel()
) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал событий") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearConfirm = true },
                        enabled = records.isNotEmpty()
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Очистить журнал"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(LogsFilter.values().toList()) { f ->
                    FilterChip(
                        selected = f == filter,
                        onClick = { viewModel.setFilter(f) },
                        label = { Text(f.label) }
                    )
                }
            }

            HorizontalDivider()

            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                records.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Записей нет.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                else -> {
                    val grouped = remember(records) { groupByDay(records) }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        grouped.forEach { (day, entries) ->
                            item(key = "day_$day") { DayHeader(day) }
                            items(entries, key = { it.id }) { entry ->
                                LogEntryItem(entry)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Очистить журнал?") },
            text = {
                Text(
                    "Все записи журнала будут удалены безвозвратно. " +
                            "Данные проб, нарядов и фото не пострадают."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Отмена") }
            }
        )
    }
}

// ============================================================
// Подкомпоненты
// ============================================================

@Composable
private fun DayHeader(day: String) {
    Text(
        day,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }
    val level = LogLevel.fromCode(entry.level) ?: LogLevel.INFO
    val category = LogCategory.fromCode(entry.category)
    val hasDetails = !entry.details.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasDetails) { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    LogFormatter.timeOnly(entry.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                if (category != null) {
                    CategoryBadge(category, level)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    entry.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (level) {
                        LogLevel.ERROR -> MaterialTheme.colorScheme.error
                        LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
                        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (expanded && hasDetails) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    entry.details!!,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryBadge(category: LogCategory, level: LogLevel) {
    val bg = when (level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
        LogLevel.WARN -> MaterialTheme.colorScheme.tertiaryContainer
        LogLevel.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        LogLevel.WARN -> MaterialTheme.colorScheme.onTertiaryContainer
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(
            category.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ============================================================
// Группировка по дням
// ============================================================

private fun groupByDay(records: List<LogEntry>): List<Pair<String, List<LogEntry>>> {
    if (records.isEmpty()) return emptyList()
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale("ru", "RU"))
    val result = mutableListOf<Pair<String, List<LogEntry>>>()
    var currentDay: String? = null
    val bucket = mutableListOf<LogEntry>()

    for (r in records) {
        val day = sdf.format(Date(r.createdAt))
        if (day != currentDay) {
            if (bucket.isNotEmpty() && currentDay != null) {
                result += currentDay to bucket.toList()
                bucket.clear()
            }
            currentDay = day
        }
        bucket += r
    }
    if (bucket.isNotEmpty() && currentDay != null) {
        result += currentDay to bucket.toList()
    }
    return result
}