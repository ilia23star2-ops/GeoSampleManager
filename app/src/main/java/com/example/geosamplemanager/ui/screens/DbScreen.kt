package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.entity.AreaEntity
import kotlinx.coroutines.launch

@Composable
fun DbScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as GeoSampleApp
    val repo = app.repository
    val scope = rememberCoroutineScope()

    var areas by remember { mutableStateOf<List<AreaEntity>>(emptyList()) }
    var status by remember { mutableStateOf("Готово к работе") }

    // Автоматически подгружаем участки при старте
    LaunchedEffect(Unit) {
        repo.getAreasFlow().collect { list ->
            areas = list
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Управление БД", style = MaterialTheme.typography.titleLarge)

        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Статус: $status", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Тестовая кнопка: добавить участок
        Button(
            onClick = {
                scope.launch {
                    val name = "Тестовый участок ${System.currentTimeMillis() % 10000}"
                    repo.addArea(name)
                    status = "Добавлен участок: $name"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Добавить тестовый участок")
        }

        // Тестовая кнопка: добавить наряд и 3 пробы
        Button(
            onClick = {
                scope.launch {
                    if (areas.isEmpty()) {
                        status = "Сначала добавьте участок"
                        return@launch
                    }
                    val area = areas.first()
                    val orderNumber = "Наряд ${System.currentTimeMillis() % 1000}"
                    val orderId = repo.addOrder(area.id, orderNumber)

                    // Добавим 3 тестовые пробы
                    for (i in 1..3) {
                        repo.addSample(
                            com.example.geosamplemanager.data.entity.SampleEntity(
                                orderId = orderId,
                                serialNumber = i,
                                sampleNumber = "TEST-$i",
                                wellNumber = "W-${i / 2 + 1}",
                                intervalFrom = i * 1.0,
                                intervalTo = i * 1.0 + 1.0,
                                weight = 1.5,
                                sampleType = "auger",
                                status = "normal"
                            )
                        )
                    }
                    status = "Добавлен $orderNumber с 3 пробами"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Добавить наряд + 3 пробы")
        }

        // Тестовая кнопка: показать статистику
        Button(
            onClick = {
                scope.launch {
                    val total = repo.getTotalCount()
                    val found = repo.getFoundCount()
                    val blank = repo.getBlankCount()
                    val control = repo.getControlCount()
                    status = "Всего: $total, найдено: $found, холостых: $blank, контроль: $control"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Показать общую статистику")
        }

        // Тестовая кнопка: очистить участок
        Button(
            onClick = {
                scope.launch {
                    areas.firstOrNull()?.let {
                        repo.deleteArea(it.areaName)
                        status = "Удалён участок: ${it.areaName}"
                    } ?: run {
                        status = "Нет участков для удаления"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Удалить первый участок (с каскадом)")
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()

        // Список участков
        Text("Участки в БД (${areas.size}):", style = MaterialTheme.typography.titleMedium)
        if (areas.isEmpty()) {
            Text("Пусто. Нажмите «Добавить тестовый участок».")
        } else {
            areas.forEach { area ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("ID: ${area.id}")
                        Text("Название: ${area.areaName}")
                    }
                }
            }
        }
    }
}
