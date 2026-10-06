package com.example.geosamplemanager.ui.screens.admin

/**
 * FIX 5.10-stat-admin-v2-nav: навигация внутри админ-панели.
 *
 * FIX 5.10-stat-admin-v2-details-a:
 *  - VisitDetail адресуется парой (sessionId, fromTs).
 *
 * FIX 5.10-stat-admin-v2-details-c:
 *  - EventDetail(sessionId, atTs) — открытие ошибки/события
 *    из таба «Ошибки» и переход к контексту ±2 мин.
 *  - goBack() из EventDetail всегда возвращает на таб «Ошибки».
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
    data class EventDetail(val sessionId: Long, val atTs: Long) : AdminPanelRoute()

    companion object {
        val Default: AdminPanelRoute = Tab(AdminPanelTab.DAY)
    }
}