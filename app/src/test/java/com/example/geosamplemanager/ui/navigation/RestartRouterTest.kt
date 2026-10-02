package com.example.geosamplemanager.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FIX 5.9-db-smooth-restart:
 * Юнит-тесты чистой логики RestartRouter.validateRoute.
 * Сама работа с SharedPreferences — device-check.
 */
class RestartRouterTest {

    @Test
    fun validateRoute_db_returnsDb() {
        assertEquals(
            Screen.DB.route,
            RestartRouter.validateRoute(Screen.DB.route)
        )
    }

    @Test
    fun validateRoute_settings_returnsSettings() {
        assertEquals(
            Screen.SETTINGS.route,
            RestartRouter.validateRoute(Screen.SETTINGS.route)
        )
    }

    @Test
    fun validateRoute_main_returnsMain() {
        assertEquals(
            Screen.MAIN.route,
            RestartRouter.validateRoute(Screen.MAIN.route)
        )
    }

    @Test
    fun validateRoute_null_returnsMain() {
        assertEquals(
            Screen.MAIN.route,
            RestartRouter.validateRoute(null)
        )
    }

    @Test
    fun validateRoute_unknown_returnsMain() {
        assertEquals(
            Screen.MAIN.route,
            RestartRouter.validateRoute("no_such_route")
        )
    }

    @Test
    fun validateRoute_emptyString_returnsMain() {
        assertEquals(
            Screen.MAIN.route,
            RestartRouter.validateRoute("")
        )
    }

    @Test
    fun validateRoute_allScreensAreValid() {
        for (screen in Screen.values()) {
            assertEquals(screen.route, RestartRouter.validateRoute(screen.route))
        }
    }
}