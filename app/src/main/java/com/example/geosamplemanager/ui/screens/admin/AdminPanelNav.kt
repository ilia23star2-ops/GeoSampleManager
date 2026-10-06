package com.example.geosamplemanager.ui.screens.admin

/**
 * FIX 5.10-stat-admin-v2-nav: навигация внутри админ-панели.
 *
 * FIX 5.10-stat-admin-v2-details-a:
 *  - VisitDetail теперь адресуется парой (sessionId, fromTs):
 *    TabVisitView не имеет стабильного id, а fromTs уникален в
 *    пределах сессии.
 */
enum class AdminPanelTab(val title: String) {
    DAY("День"),
    ORDERS("Наряды"),
    ERRORS("Ошибки")
}

sealed class AdminPanelRoute {
    data class Tab(val tab: AdminPanelTab) : AdminPanelRoute()
    data class SessionDetail(val sessionId: Long) : AdminPanelRoute()
    data class VisitDetail(val sessionId: Long, val fromTs: Long) : AdminPanelRoute()

    companion object {
        val Default: AdminPanelRoute = Tab(AdminPanelTab.DAY)
    }
}