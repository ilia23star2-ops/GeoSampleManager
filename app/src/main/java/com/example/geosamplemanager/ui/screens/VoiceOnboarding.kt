package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Онбординг ГП — 5 экранов при первом запуске (§12.3 VOICE.md).
 */
@Composable
fun VoiceOnboarding(
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    var dontShow by remember { mutableStateOf(false) }

    val pages = listOf(
        Page(
            Icons.Filled.Mic,
            "Голосовой помощник",
            "Скажите номер скважины или пробы — приложение найдёт и покажет. " +
                    "Работает офлайн, руки свободны."
        ),
        Page(
            Icons.Filled.PlayArrow,
            "Авто-режим отметок",
            "Если найдена ровно одна скважина, включится авто-режим — " +
                    "звучит двойной бип. Можно диктовать пробы: «первая», " +
                    "«вторая», «третья»."
        ),
        Page(
            Icons.Filled.VolumeUp,
            "Вес и снятие",
            "«Вес два пять» — поставить вес 2.5 кг. " +
                    "«Снять первую» — снять отметку. «Снять все» — сбросить всё."
        ),
        Page(
            Icons.Filled.Mic,
            "Управление",
            "«Следующая» — новая скважина. «Отмена» / «повтори» — undo / redo. " +
                    "«Стоп» — завершить сессию."
        ),
        Page(
            Icons.Filled.PlayArrow,
            "Помощь",
            "Скажите «помощь» — покажу список команд. Справка также " +
                    "доступна по значку ? в шапке экрана."
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(pages[page].icon, null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Знакомство (${page + 1}/${pages.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(pages[page].title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Text(pages[page].body,
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShow,
                        onCheckedChange = { dontShow = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Больше не показывать",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (page < pages.size - 1) {
                    page++
                } else {
                    if (dontShow) onDontShowAgain() else onDismiss()
                }
            }) {
                Text(if (page < pages.size - 1) "Далее" else "Готово")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (dontShow) onDontShowAgain() else onDismiss()
            }) { Text("Пропустить") }
        }
    )
}

private data class Page(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val body: String
)