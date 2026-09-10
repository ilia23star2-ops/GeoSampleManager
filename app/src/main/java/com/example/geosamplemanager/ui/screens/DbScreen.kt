package com.example.geosamplemanager.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity

@Composable
fun DbScreen(viewModel: DbViewModel = viewModel()) {
    val context = LocalContext.current
    val areas by viewModel.areas.collectAsState()
    val selectedArea by viewModel.selectedArea.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val selectedOrder by viewModel.selectedOrder.collectAsState()
    val samples by viewModel.samples.collectAsState()
    val message by viewModel.message.collectAsState()

    // Показ Toast при сообщении
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    // Диалоги
    var showAddAreaDialog by remember { mutableStateOf(false) }
    var showAddOrderDialog by remember { mutableStateOf(false) }
    var areaToDelete by remember { mutableStateOf<AreaEntity?>(null) }
    var orderToDelete by remember { mutableStateOf<OrderEntity?>(null) }

    // Launcher для бэкапа
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.backupDatabase(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Верхняя панель действий
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showAddAreaDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Участок")
            }
            OutlinedButton(
                onClick = {
                    backupLauncher.launch("geosamples_backup_${System.currentTimeMillis()}.db")
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Бэкап")
            }
        }

        HorizontalDivider()

        // Основной контент — адаптивный
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val isWide = maxWidth > 600.dp

            if (isWide) {
                // Планшет: две колонки
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AreasList(
                        areas = areas,
                        selectedArea = selectedArea,
                        onSelect = { viewModel.selectArea(it) },
                        onDelete = { areaToDelete = it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    OrdersList(
                        selectedArea = selectedArea,
                        orders = orders,
                        selectedOrder = selectedOrder,
                        onSelect = { viewModel.selectOrder(it) },
                        onAdd = { showAddOrderDialog = true },
                        onDelete = { orderToDelete = it },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                // Телефон: всё в столбик, скролл
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AreasList(
                        areas = areas,
                        selectedArea = selectedArea,
                        onSelect = { viewModel.selectArea(it) },
                        onDelete = { areaToDelete = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OrdersList(
                        selectedArea = selectedArea,
                        orders = orders,
                        selectedOrder = selectedOrder,
                        onSelect = { viewModel.selectOrder(it) },
                        onAdd = { showAddOrderDialog = true },
                        onDelete = { orderToDelete = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Список проб выбранного наряда
        if (selectedOrder != null) {
            HorizontalDivider()
            SamplesList(
                order = selectedOrder!!,
                samples = samples,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
            )
        }
    }

    // === Диалог добавления участка ===
    if (showAddAreaDialog) {
        TextInputDialog(
            title = "Новый участок",
            label = "Название участка",
            onConfirm = {
                viewModel.addArea(it)
                showAddAreaDialog = false
            },
            onDismiss = { showAddAreaDialog = false }
        )
    }

    // === Диалог добавления наряда ===
    if (showAddOrderDialog) {
        TextInputDialog(
            title = "Новый наряд в «${selectedArea?.areaName ?: ""}»",
            label = "Номер наряда",
            onConfirm = {
                viewModel.addOrder(it)
                showAddOrderDialog = false
            },
            onDismiss = { showAddOrderDialog = false }
        )
    }

    // === Подтверждение удаления участка ===
    areaToDelete?.let { area ->
        ConfirmDialog(
            title = "Удалить участок?",
            text = "Будут удалены все наряды и пробы участка «${area.areaName}». Действие необратимо.",
            onConfirm = {
                viewModel.deleteArea(area)
                areaToDelete = null
            },
            onDismiss = { areaToDelete = null }
        )
    }

    // === Подтверждение удаления наряда ===
    orderToDelete?.let { order ->
        ConfirmDialog(
            title = "Удалить наряд?",
            text = "Будут удалены все пробы наряда «${order.orderNumber}». Действие необратимо.",
            onConfirm = {
                viewModel.deleteOrder(order)
                orderToDelete = null
            },
            onDismiss = { orderToDelete = null }
        )
    }
}

// ============================================================
// ПОДКОМПОНЕНТЫ
// ============================================================

@Composable
private fun AreasList(
    areas: List<AreaEntity>,
    selectedArea: AreaEntity?,
    onSelect: (AreaEntity) -> Unit,
    onDelete: (AreaEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Участки (${areas.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (areas.isEmpty()) {
                Text(
                    "Пусто. Нажмите «+ Участок».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(areas, key = { it.id }) { area ->
                        ListRow(
                            text = area.areaName,
                            selected = selectedArea?.id == area.id,
                            onClick = { onSelect(area) },
                            onDelete = { onDelete(area) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersList(
    selectedArea: AreaEntity?,
    orders: List<OrderEntity>,
    selectedOrder: OrderEntity?,
    onSelect: (OrderEntity) -> Unit,
    onAdd: () -> Unit,
    onDelete: (OrderEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Наряды (${orders.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (selectedArea != null) {
                    IconButton(onClick = onAdd) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить наряд")
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            when {
                selectedArea == null -> Text(
                    "Выберите участок слева",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                orders.isEmpty() -> Text(
                    "Пусто. Нажмите «+».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(orders, key = { it.id }) { order ->
                        ListRow(
                            text = order.orderNumber,
                            selected = selectedOrder?.id == order.id,
                            onClick = { onSelect(order) },
                            onDelete = { onDelete(order) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplesList(
    order: OrderEntity,
    samples: List<SampleEntity>,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                "Пробы наряда «${order.orderNumber}» (${samples.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            if (samples.isEmpty()) {
                Text(
                    "Нет проб в этом наряде",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(samples, key = { it.id }) { sample ->
                        SampleRow(sample)
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleRow(sample: SampleEntity) {
    val intervalText = if (sample.intervalFrom != null && sample.intervalTo != null) {
        "${sample.intervalFrom}–${sample.intervalTo}"
    } else "—"

    val statusText = when (sample.status) {
        "blank" -> "Холостая"
        "control" -> "ВК"
        else -> when (sample.sampleType) {
            "auger" -> "Шнековая"
            "channel" -> "Бороздовая"
            "cobra" -> "Кобра"
            else -> sample.sampleType
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                when (sample.status) {
                    "blank" -> Color(0x33FFD700)
                    "control" -> Color(0x33DDA0DD)
                    else -> Color.Transparent
                }
            )
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            sample.sampleNumber,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1.2f)
        )
        Text(
            sample.wellNumber,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            intervalText,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            sample.weight?.toString() ?: "—",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.8f)
        )
        Text(
            statusText,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (sample.found) "✓" else "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (sample.found) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ListRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Удалить",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ============================================================
// ДИАЛОГИ
// ============================================================

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
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
            TextButton(onClick = { onConfirm(text) }) { Text("Добавить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
