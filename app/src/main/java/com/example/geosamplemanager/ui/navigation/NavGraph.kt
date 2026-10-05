package com.example.geosamplemanager.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.logs.Log
import com.example.geosamplemanager.ui.screens.*
import kotlinx.coroutines.launch

/**
 * FIX 5.9-main-a: onOpenOrder — переход в Сверку с нарядом.
 *
 * FIX 5.9-main-b:
 *  - onOpenReport — переход в Статистику с открытием диалога формата
 *    для выбранного наряда. Через app.requestReportFor(orderId).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(initialRoute: String = Screen.MAIN.route) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val app = context.applicationContext as GeoSampleApp
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: initialRoute
    val currentScreen = Screen.values().firstOrNull { it.route == currentRoute }
        ?: Screen.MAIN

    var firstNav by remember { mutableStateOf(true) }
    LaunchedEffect(currentScreen) {
        if (firstNav) {
            firstNav = false
            return@LaunchedEffect
        }
        Log.nav("Открыта вкладка «${currentScreen.title}»")
            .detail("route", currentScreen.route)
            .write()
    }

    fun navigateTo(screen: Screen) {
        navController.navigate(screen.route) {
            popUpTo(Screen.MAIN.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text(
                    "GeoSample Manager",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(Screen.values()) { screen ->
                        NavigationDrawerItem(
                            icon = {
                                Icon(screen.icon, contentDescription = screen.title)
                            },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navigateTo(screen)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(
                                horizontal = 12.dp,
                                vertical = 2.dp
                            )
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentScreen.title) },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Icon(Icons.Filled.Menu, contentDescription = "Меню")
                        }
                    }
                )
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = initialRoute,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.MAIN.route) {
                    MainScreen(
                        onNavigate = { screen -> navigateTo(screen) },
                        onOpenOrder = { orderId, areaTitle, orderTitle ->
                            app.requestSearchForOrder(orderId, areaTitle, orderTitle)
                            navigateTo(Screen.SEARCH)
                        },
                        onOpenReport = { orderId ->
                            app.requestReportFor(orderId)
                            navigateTo(Screen.STATS)
                        }
                    )
                }
                composable(Screen.ADD.route) { AddScreen() }
                composable(Screen.SEARCH.route) { SearchScreen() }
                composable(Screen.STATS.route) { StatsScreen() }
                composable(Screen.EDIT.route) { EditScreen() }
                composable(Screen.DB.route) { DbScreen() }
                composable(Screen.SETTINGS.route) { SettingsScreen() }
            }
        }
    }
}