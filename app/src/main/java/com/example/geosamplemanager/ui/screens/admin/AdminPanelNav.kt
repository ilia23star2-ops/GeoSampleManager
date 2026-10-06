package com.example.geosamplemanager.ui.screens.admin

/**
 * FIX 5.10-stat-admin-v2-nav:
 * Навигация внутри админ-панели. Панель открывается наложением
 * поверх Настроек (не через NavController), поэтому маршруты
 * живут локально.
 *
 * Уровни:
 *  - Tab(DAY | ORDERS | ERRORS) — верхние табы;
 *  - SessionDetail / VisitDetail — провалы (в пачке admin-v2-details).
 *
 * Back обрабатывается вручную в AdminPanelScreen: с деталей →
 * на предыдущий таб, с таба → на DAY, с DAY → закрытие панели.
 */
enum class AdminPanelTab(val title: String) {
    DAY("День"),
    ORDERS("Наряды"),
    ERRORS("Ошибки")
}

sealed class AdminPanelRoute {
    data class Tab(val tab: AdminPanelTab) : AdminPanelRoute()
    data class SessionDetail(val sessionId: Long) : AdminPanelRoute()
    data class VisitDetail(val visitId: Long) : AdminPanelRoute()

    companion object {
        val Default: AdminPanelRoute = Tab(AdminPanelTab.DAY)
    }
}