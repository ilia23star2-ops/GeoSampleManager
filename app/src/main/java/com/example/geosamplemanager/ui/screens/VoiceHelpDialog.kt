package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Справка по голосовым командам.
 */
@Composable
fun VoiceHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Голосовые команды") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Section("Поиск")
                Bullet("«1524» — найти скважину или пробу.")
                Bullet("«капэдэ сто девять...» — с буквенным префиксом.")
                Bullet("«1524 и 1525» — сортировка двух номеров, без отметок.")

                Spacer(Modifier.height(4.dp))
                Section("Внутри найденной скважины")
                Bullet("«первая» … «тридцатая» — отметить пробу.")
                Bullet("«вес два пять» — вес 2.5 кг.")
                Bullet("«снять первую» — снять отметку.")
                Bullet("«снять последнюю» — снять последнюю.")
                Bullet("«снять все» — снять все отметки в скважине.")
                Bullet("«отложить» / «снять отложенную».")

                Spacer(Modifier.height(4.dp))
                Section("Общие")
                Bullet("«следующая» — перейти к новой скважине.")
                Bullet("«отмена» / «повтори» — undo / redo.")
                Bullet("«пауза» / «продолжить».")
                Bullet("«стоп» / «хватит» — завершить сессию.")
                Bullet("«сколько осталось».")
                Bullet("«показать отложенные» / «показать найденные».")
                Bullet("«помощь» — эта справка.")

                Spacer(Modifier.height(4.dp))
                Text(
                    "Авто-режим отметок включается, когда найден ровно " +
                            "один вариант — звучит двойной бип.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Понятно") } }
    )
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Bullet(text: String) {
    Text("• $text", style = MaterialTheme.typography.bodySmall)
}