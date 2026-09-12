package com.example.geosamplemanager.ui.screens

/**
 * Изменения, которые система предлагает запомнить после ручного маппинга.
 */
data class RememberChanges(
    /** Заголовок → роль. Например: «№ скв» → «well». */
    val newHeaderKeywords: Map<String, String> = emptyMap(),

    /** Префикс → название участка. Например: «KPD» → «Коптеловский». */
    val newAreaPrefix: Pair<String, String>? = null
) {
    val isEmpty: Boolean
        get() = newHeaderKeywords.isEmpty() && newAreaPrefix == null
}