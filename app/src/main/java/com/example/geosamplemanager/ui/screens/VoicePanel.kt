package com.example.geosamplemanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.voice.VoiceStatus

/**
 * FIX 5.8.10-g1 (И-3):
 * Панель голосового помощника — немодальная, встраивается в экран снизу.
 * Заменяет AlertDialog, который блокировал интерфейс сверки.
 *
 * Содержимое:
 *  - цветная полоска слева — статус ГП;
 *  - строка статуса (всегда видна);
 *  - кнопки «свернуть/развернуть» и «закрыть»;
 *  - развёрнутый блок: «Слышу», «Распознано», «Результат», кнопка «Ещё раз».
 *
 * Логики внутри нет — всё состояние и действия приходят через параметры.
 */
@Composable
fun VoicePanel(
    status: String,
    partialText: String,
    finalText: String,
    resultText: String,
    voiceStatus: VoiceStatus,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = statusColor(voiceStatus)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {

            // Цветная полоска слева — индикатор состояния ГП.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {

                // Строка статуса — видна всегда.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = accent
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandMore
                                          else Icons.Filled.ExpandLess,
                            contentDescription = if (expanded) "Свернуть" else "Развернуть"
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Закрыть"
                        )
                    }
                }

                // Развёрнутое содержимое — только когда expanded = true.
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (partialText.isNotEmpty()) {
                            Text(
                                text = "Слышу: $partialText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (finalText.isNotEmpty()) {
                            Text(
                                text = "Распознано: $finalText",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (resultText.isNotEmpty()) {
                            Text(
                                text = "Результат: $resultText",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onRetry) {
                                Text("Ещё раз")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * FIX 5.8.10-g1: цвет полоски и иконки по текущему состоянию ГП.
 * Если в VoiceStatus появятся новые варианты — сработает else (серый).
 */
private fun statusColor(status: VoiceStatus): Color = when (status) {
    VoiceStatus.Idle -> Color(0xFF9E9E9E)       // серый
    VoiceStatus.Listening -> Color(0xFF4CAF50)  // зелёный
    is VoiceStatus.Heard -> Color(0xFFFFC107)   // жёлтый
    is VoiceStatus.Found -> Color(0xFF2196F3)   // синий
    is VoiceStatus.Marked -> Color(0xFF4CAF50)  // зелёный
    is VoiceStatus.Error -> Color(0xFFF44336)   // красный
    else -> Color(0xFF9E9E9E)
}
