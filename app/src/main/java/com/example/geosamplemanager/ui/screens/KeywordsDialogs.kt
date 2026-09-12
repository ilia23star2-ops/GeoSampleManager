package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Данные одной секции диалога ключевых слов.
 */
data class KeywordSectionUi(
    val key: String,
    val title: String,
    val userWords: List<String>,
    val defaultWords: List<String>
)

/**
 * Диалог со всеми ролями заголовков и их словами.
 * Для каждой роли — две части:
 *   • Ваши слова (можно добавлять и удалять)
 *   • Стандартные (используются автоматически, только для справки)
 */
@Composable
fun HeaderKeywordsDialog(
    title: String,
    help: String,
    sections: List<KeywordSectionUi>,
    onAdd: (sectionKey: String, word: String) -> Unit,
    onRemove: (sectionKey: String, word: String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = { /* тап мимо — ничего */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        help,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    sections.forEach { section ->
                        key(section.key) {
                            KeywordSectionCard(
                                section = section,
                                onAdd = { w -> onAdd(section.key, w) },
                                onRemove = { w -> onRemove(section.key, w) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeywordSectionCard(
    section: KeywordSectionUi,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    var newWord by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(section.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // ===== ВАШИ СЛОВА =====
            Text(
                "Ваши слова (${section.userWords.size})",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))

            if (section.userWords.isEmpty()) {
                Text(
                    "Пока ничего не добавлено. Введите слово ниже и нажмите «+».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                section.userWords.forEach { w ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "• $w",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onRemove(w) }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Удалить",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    singleLine = true,
                    label = { Text("Добавить слово", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                FilledTonalIconButton(
                    onClick = {
                        val w = newWord.trim()
                        if (w.isNotEmpty()) {
                            onAdd(w)
                            newWord = ""
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            }

            // ===== СТАНДАРТНЫЕ СЛОВА =====
            if (section.defaultWords.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    "Стандартные (используются автоматически)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    section.defaultWords.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}