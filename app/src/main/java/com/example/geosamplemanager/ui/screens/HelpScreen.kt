package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * FIX 5.9-settings-help-1:
 * Экран «Справка» — каркас. Открывается из Настроек.
 *
 * Пока контент не загружается — показывается заглушка для
 * каждого раздела. Тексты будут добавлены в следующей пачке
 * (5.9-settings-help-2) через папку assets/help.
 *
 * Навигация:
 *  - широкий экран (≥600dp): слева список разделов, справа содержимое;
 *  - узкий экран: разделы — горизонтальные чипы сверху, содержимое ниже.
 *
 * Выбранный раздел сохраняется через rememberSaveable (строка имени
 * enum), чтобы поворот экрана не сбрасывал навигацию.
 *
 * FIX 5.9-settings-help-1-fix-2:
 * maxWidth из BoxWithConstraints вычисляется ДО входа в Row —
 * внутри RowScope к нему нет доступа (Compose-скоуп).
 */
enum class HelpTopic(val title: String) {
    START("Быстрый старт"),
    IMPORT("Импорт Excel"),
    SEARCH("Сверка и поиск"),
    VOICE("Голосовой помощник"),
    EDIT("Редактирование"),
    DB("База данных"),
    STATS("Статистика"),
    SETTINGS("Настройки");

    companion object {
        fun fromName(name: String?): HelpTopic {
            if (name.isNullOrBlank()) return START
            return values().firstOrNull { it.name == name } ?: START
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onClose: () -> Unit
) {
    var selectedName by rememberSaveable { mutableStateOf(HelpTopic.START.name) }
    val selected = HelpTopic.fromName(selectedName)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Справка") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            val isWide = maxWidth >= 600.dp
            // FIX 5.9-settings-help-1-fix-2:
            // вычислить ширину боковой панели здесь, до входа в RowScope.
            val sideWidth = maxWidth * 0.35f

            if (isWide) {
                Row(modifier = Modifier.fillMaxSize()) {
                    HelpTopicTree(
                        selected = selected,
                        onSelect = { selectedName = it.name },
                        modifier = Modifier.fillMaxHeight().width(sideWidth)
                    )
                    VerticalDivider()
                    HelpTopicContent(
                        topic = selected,
                        modifier = Modifier.fillMaxHeight().weight(1f)
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    HelpTopicChips(
                        selected = selected,
                        onSelect = { selectedName = it.name }
                    )
                    HorizontalDivider()
                    HelpTopicContent(
                        topic = selected,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpTopicTree(
    selected: HelpTopic,
    onSelect: (HelpTopic) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        Text(
            "Разделы",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
        Spacer(Modifier.height(8.dp))
        HelpTopic.values().forEach { topic ->
            val isSelected = topic == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { onSelect(topic) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    topic.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun HelpTopicChips(
    selected: HelpTopic,
    onSelect: (HelpTopic) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HelpTopic.values().forEach { topic ->
            FilterChip(
                selected = topic == selected,
                onClick = { onSelect(topic) },
                label = { Text(topic.title, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

/**
 * FIX 5.9-settings-help-1:
 * Заглушка содержимого раздела. Реальный контент (Markdown из
 * папки assets/help) появится в 5.9-settings-help-2.
 */
@Composable
private fun HelpTopicContent(
    topic: HelpTopic,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Default.Book,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            topic.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Содержимое раздела будет добавлено в следующем обновлении.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}