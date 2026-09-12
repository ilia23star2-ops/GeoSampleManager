package com.example.geosamplemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.geosamplemanager.data.excel.ParsedSample

/**
 * Таблица проб. Заголовок фиксирован сверху, тело прокручивается.
 * Горизонтальный скролл общий для шапки и тела.
 */
@Composable
fun SamplesTable(
    samples: List<ParsedSample>,
    modifier: Modifier = Modifier
) {
    val hScroll = rememberScrollState()
    val wellTitle = SampleDisplay.wellColumnTitle(samples)
    val totalWidthDp = CELL_WIDTHS.sum()

    Column(modifier = modifier) {
        // Шапка
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(hScroll)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(modifier = Modifier.width(totalWidthDp.dp)) {
                HeaderCell("№ п/п", CELL_WIDTHS[0])
                HeaderCell(wellTitle, CELL_WIDTHS[1])
                HeaderCell("№ пробы", CELL_WIDTHS[2])
                HeaderCell("От", CELL_WIDTHS[3])
                HeaderCell("До", CELL_WIDTHS[4])
                HeaderCell("Вес", CELL_WIDTHS[5])
                HeaderCell("Тип", CELL_WIDTHS[6])
            }
        }
        HorizontalDivider()
        // Тело
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(hScroll)
        ) {
            LazyColumn(modifier = Modifier.width(totalWidthDp.dp)) {
                items(samples) { s -> SampleRow(s) }
            }
        }
    }
}

@Composable
private fun SampleRow(s: ParsedSample) {
    val bg = if (s.status == "blank") Color(0x33FFD700) else Color.Transparent
    Row(modifier = Modifier.background(bg)) {
        BodyCell(s.serialNumber.toString(), CELL_WIDTHS[0])
        BodyCell(s.wellNumber, CELL_WIDTHS[1])
        BodyCell(s.sampleNumber, CELL_WIDTHS[2])
        BodyCell(s.intervalFrom?.toString() ?: "—", CELL_WIDTHS[3])
        BodyCell(s.intervalTo?.toString() ?: "—", CELL_WIDTHS[4])
        BodyCell(SampleDisplay.weightDisplay(s.weight), CELL_WIDTHS[5])
        BodyCell(SampleDisplay.typeDisplay(s), CELL_WIDTHS[6])
    }
}

@Composable
private fun HeaderCell(text: String, widthDp: Int) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
    }
}

@Composable
private fun BodyCell(text: String, widthDp: Int) {
    Box(
        modifier = Modifier
            .width(widthDp.dp)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            .padding(6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

private val CELL_WIDTHS = listOf(60, 140, 140, 70, 70, 70, 130)