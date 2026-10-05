package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.ui.navigation.Screen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * FIX 5.9-main:
 * Вкладка «Главная» — сводка, продолжить, незавершённые, проблемы.
 *
 * FIX 5.9-main-fix:
 *  - убран вложенный Scaffold. Внешний Scaffold в AppScaffold
 *    уже есть, а вложенный вызывает ArrayIndexOutOfBoundsException
 *    в Compose Runtime (внутри AnimatedContent у Material3 Scaffold);
 *  - убран `return@Column` из SummaryCard: заменён на if/else if/else;
 *  - DbStatusBadge без деструктуризации Triple.
 */
@Composable
fun MainScreen(
    onNavigate: (Screen) -> Unit,
    onOpenOrder: (orderId: Long, areaTitle: String, orderTitle: String) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val info by viewModel.info.collectAsState()
    val unfinished by viewModel.unfinished.collectAsState()
    val continueInfo by viewModel.continueInfo.collectAsState()
    val problems by viewModel.problems.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val message by viewModel.message.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.reload() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var showAllUnfinished by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                info = info,
                problems = problems,
                loading = loading,
                onRefresh = { viewModel.reload() }
            )

            val ci = continueInfo
            if (ci != null) {
                ContinueCard(
                    info = ci,
                    onContinue = { onOpenOrder(ci.orderId, ci.areaTitle, ci.orderTitle) }
                )
            }

            if (unfinished.isNotEmpty()) {
                UnfinishedBlock(
                    items = unfinished,
                    expanded = showAllUnfinished,
                    onToggleExpand = { showAllUnfinished = !showAllUnfinished },
                    onOpenOrder = { u -> onOpenOrder(u.orderId, u.areaTitle, u.orderTitle) },
                    onOpenStats = { onNavigate(Screen.STATS) }
                )
            }

            val p = problems
            if (p != null) {
                ProblemsBlock(
                    problems = p,
                    onOpenDiagnostics = { onNavigate(Screen.DB) }
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

// ============================================================
// 1. Сводка
// ============================================================

@Composable
private fun SummaryCard(
    info: MainInfo?,
    problems: DbProblems?,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Сводка по базе",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(6.dp))
                DbStatusBadge(problems)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onRefresh,
                    enabled = !loading,
                    modifier = Modifier.size(36.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Обновить",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                info == null -> {
                    Text(
                        if (loading) "Загрузка…" else "Нет данных",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                info.isEmpty -> {
                    Text(
                        "База пуста. Начните с импорта описи проб.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Импорт доступен через меню ☰",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${info.found} / ${info.samples}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "(${info.progressPercent}%)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = info.progressFraction,
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.surface
                    )

                    Spacer(Modifier.height(12.dp))
                    SummaryNumbers(info)

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Изменена ${formatTimestamp(info.lastModified)} · " +
                                "${formatSize(info.dbSizeBytes)} · " +
                                "Свободно ${formatSize(info.freeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DbStatusBadge(problems: DbProblems?) {
    val icon: ImageVector
    val tint: Color
    val label: String

    if (problems == null) {
        icon = Icons.Filled.CheckCircle
        tint = MaterialTheme.colorScheme.primary
        label = "База в порядке"
    } else {
        icon = Icons.Filled.Error
        tint = MaterialTheme.colorScheme.error
        label = "Проблемы: ${problems.total}"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SummaryNumbers(info: MainInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NumberCell("Участки", info.areas, Modifier.weight(1f))
        NumberCell("Наряды", info.orders, Modifier.weight(1f))
        NumberCell("Скважины", info.wells, Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NumberCell("Готово", info.readyOrders, Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primary)
        NumberCell("В работе", info.inProgressOrders, Modifier.weight(1f))
        NumberCell("Не отмечено", info.notFound, Modifier.weight(1f),
            color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        NumberCell("Фото", info.photos, Modifier.weight(1f))
        NumberCell("Заметки", info.notes, Modifier.weight(1f))
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun NumberCell(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    color: Color? = null
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color ?: MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ============================================================
// 2. Продолжить работу
// ============================================================

@Composable
private fun ContinueCard(info: ContinueInfo, onContinue: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Продолжить работу",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${info.areaTitle} / ${info.orderTitle}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                "Отмечено ${info.found} из ${info.total} (${info.percent}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (info.lastActionAt > 0L) {
                Text(
                    "Последнее действие: ${formatTime(info.lastActionAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Продолжить сверку")
            }
        }
    }
}

// ============================================================
// 3. Незавершённые наряды
// ============================================================

private const val COLLAPSED_LIMIT = 5

@Composable
private fun UnfinishedBlock(
    items: List<UnfinishedOrder>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenOrder: (UnfinishedOrder) -> Unit,
    onOpenStats: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.HourglassEmpty,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Незавершённые наряды (${items.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenStats) {
                    Text("Все →", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(4.dp))

            val shown = if (expanded) items else items.take(COLLAPSED_LIMIT)
            shown.forEach { u ->
                UnfinishedRow(u, onClick = { onOpenOrder(u) })
                Spacer(Modifier.height(4.dp))
            }

            if (items.size > COLLAPSED_LIMIT) {
                TextButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (expanded) "Свернуть"
                        else "Развернуть все (${items.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun UnfinishedRow(item: UnfinishedOrder, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${item.areaTitle} / ${item.orderTitle}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.found} из ${item.total} (${item.percent}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(3.dp))
            LinearProgressIndicator(
                progress = item.progressFraction,
                modifier = Modifier.fillMaxWidth().height(5.dp),
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

// ============================================================
// 4. Требует внимания
// ============================================================

@Composable
private fun ProblemsBlock(problems: DbProblems, onOpenDiagnostics: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Требует внимания",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(6.dp))

            if (problems.orphanOrders > 0) {
                Text(
                    "• Наряды без участка: ${problems.orphanOrders}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            if (problems.orphanSamples > 0) {
                Text(
                    "• Пробы без наряда: ${problems.orphanSamples}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть диагностику")
            }
        }
    }
}

// ============================================================
// Форматтеры
// ============================================================

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "—"
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU"))
    return sdf.format(Date(millis))
}

private fun formatTime(millis: Long): String {
    if (millis <= 0L) return "—"
    val sdf = SimpleDateFormat("HH:mm", Locale("ru", "RU"))
    return sdf.format(Date(millis))
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 Б"
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes < kb -> "$bytes Б"
        bytes < mb -> "%.1f КБ".format(bytes / kb)
        bytes < gb -> "%.1f МБ".format(bytes / mb)
        else -> "%.2f ГБ".format(bytes / gb)
    }
}