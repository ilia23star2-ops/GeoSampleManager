package com.example.geosamplemanager.ui.navigation

import android.content.Context

/**
 * FIX 5.9-db-smooth-restart:
 * Помощник для «мягкого» перезапуска стека приложения
 * (после очистки/отката/импорта БД).
 *
 * Идея: перед startActivity(CLEAR_TASK) сохраняем, на какой
 * вкладке пользователь был; при старте NavGraph читает это
 * значение как startDestination.
 *
 * Одноразово: consumePendingRoute возвращает и сразу удаляет.
 */
object RestartRouter {

    private const val PREFS_NAME = "restart_router"
    private const val KEY_ROUTE = "pending_route"

    /**
     * Сохранить route, на который надо вернуться после перезапуска.
     */
    fun scheduleRestart(context: Context, route: String) {
        val ctx = context.applicationContext
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ROUTE, route)
            .apply()
    }

    /**
     * Прочитать и удалить сохранённый route.
     * Возвращает null, если ничего не сохранено.
     */
    fun consumePendingRoute(context: Context): String? {
        val ctx = context.applicationContext
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val route = prefs.getString(KEY_ROUTE, null)
        if (route != null) {
            prefs.edit().remove(KEY_ROUTE).apply()
        }
        return route
    }

    /**
     * Проверить, что route — известный. Иначе — стартовая вкладка
     * (MAIN). Чистая функция — покрывается юнит-тестами.
     */
    fun validateRoute(route: String?): String {
        val known = Screen.values().map { it.route }.toSet()
        return if (route != null && route in known) route else Screen.MAIN.route
    }
}