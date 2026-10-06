package com.example.geosamplemanager.ui.screens.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * FIX 5.10-stat-admin-ui-2:
 * Блок «Требует внимания»: проблемы БД (сироты нарядов, сироты проб,
 * битые ссылки на фото). Если проблем нет — блок скрывается.
 *
 * В первой версии ProblemsView приходит с нулями (заглушка), поэтому
 * на экране блок не появится. Реальная загрузка — отдельный заход
 * через DatabaseRepository.runDiagnostics.
 *
 * onOpenDiagnostics — заглушка, реальная интеграция в -3.
 */
@Composable
fun ProblemsBlock(
    problems: ProblemsView,
    onOpenDiagnostics: () -> Unit
) {
    if (problems.isEmpty) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Требует внимания",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            if (problems.orphanOrders > 0) {
                ProblemRow("Наряды без участка", problems.orphanOrders)
            }
            if (problems.orphanSamples > 0) {
                ProblemRow("Пробы без наряда", problems.orphanSamples)
            }
            if (problems.brokenPhotos > 0) {
                ProblemRow("Битые ссылки на фото", problems.brokenPhotos)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenDiagnostics,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Открыть диагностику →")
            }
        }
    }
}

@Composable
private fun ProblemRow(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            "▸ $label",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error
        )
    }
}