package com.example.geosamplemanager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    MAIN("main", "Главное меню", Icons.Filled.Home),
    ADD("add", "Добавить наряд", Icons.Filled.AddCircle),
    SEARCH("search", "Сверка и поиск", Icons.Filled.Search),
    STATS("stats", "Статистика", Icons.Filled.BarChart),
    EDIT("edit", "Редактирование", Icons.Filled.Edit),
    DB("db", "Управление БД", Icons.Filled.Storage),
    SETTINGS("settings", "Настройки", Icons.Filled.Settings)
}
