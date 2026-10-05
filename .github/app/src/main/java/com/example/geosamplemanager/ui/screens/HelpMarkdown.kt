package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.help.HelpBlock

/**
 * FIX 5.9-settings-help-2a:
 * Рендер блоков справки в Compose.
 *
 * Поддержка:
 *  - H1 / H2 / H3 — разные размеры и веса;
 *  - параграф — обычный текст с поддержкой **жирного**;
 *  - список — с буллитом «•» слева.
 *
 * Дополнительное оформление (картинки, таблицы, ссылки) —
 * не в первой версии.
 */
@Composable
fun HelpBlocksView(blocks: List<HelpBlock>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is HelpBlock.Heading1 -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is HelpBlock.Heading2 -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp)
                )
                is HelpBlock.Heading3 -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                is HelpBlock.Paragraph -> Text(
                    text = renderInline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is HelpBlock.BulletList -> Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    block.items.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "•",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = renderInline(item),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * FIX 5.9-settings-help-2a:
 * Разбор **жирного** внутри строки.
 * Чистая функция, тестируется без Compose.
 *
 * Возвращает AnnotatedString: сегменты между парами `**` — жирные.
 * Нечётное количество — последний кусок остаётся обычным.
 */
fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    var bold = false
    parts.forEachIndexed { idx, part ->
        if (idx > 0 && bold) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) }
        } else {
            append(part)
        }
        bold = !bold
    }
}