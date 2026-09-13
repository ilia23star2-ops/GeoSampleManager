package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.voice.VoiceStatus

/**
 * Тонкая полоса статуса ГП внизу экрана сверки (§12.1 VOICE.md).
 */
@Composable
fun VoiceStatusBar(status: VoiceStatus) {
    val (dotColor, text) = when (status) {
        VoiceStatus.Idle     -> Color(0xFF9E9E9E) to "Голос: выкл"
        VoiceStatus.Listening-> Color(0xFFD32F2F) to "Слушаю…"
        is VoiceStatus.Heard -> Color(0xFFFBC02D) to "Понял: «${status.text}»"
        VoiceStatus.Searching-> Color(0xFFEF6C00) to "Ищу…"
        is VoiceStatus.Found -> Color(0xFF2E7D32) to "Найдено: ${status.display}"
        is VoiceStatus.Marked-> Color(0xFF2E7D32) to "Отмечено: ${status.sampleNumber}"
        is VoiceStatus.Error -> Color(0xFFD32F2F) to status.message
        VoiceStatus.Paused   -> Color(0xFF616161) to "⏸ Пауза"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}