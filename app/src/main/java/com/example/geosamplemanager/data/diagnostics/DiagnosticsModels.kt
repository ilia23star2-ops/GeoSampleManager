package com.example.geosamplemanager.data.diagnostics

/**
 * FIX 5.9-db-diagnostics:
 * Состояние экрана диагностики БД.
 *
 * Ready хранит найденные проблемы и набор id выбранных
 * (чек-боксы в UI). applying — короткий промежуток между нажатием
 * «Исправить» и завершением; Done — успех и число исправленных.
 */
sealed class DbDiagnosticsState {

    data object Idle : DbDiagnosticsState()

    data object Loading : DbDiagnosticsState()

    data class Ready(
        val issues: List<DbIssue>,
        val selectedIds: Set<String>
    ) : DbDiagnosticsState() {
        val allSelected: Boolean
            get() = issues.isNotEmpty() && selectedIds.size == issues.size

        val canApply: Boolean
            get() = selectedIds.isNotEmpty()
    }

    data class Applying(val message: String) : DbDiagnosticsState()

    data class Done(val fixedCount: Int) : DbDiagnosticsState()

    data class Error(val message: String) : DbDiagnosticsState()
}