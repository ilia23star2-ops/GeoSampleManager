package com.example.geosamplemanager.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.backup.BackupCounts
import com.example.geosamplemanager.data.backup.ExitBackupWriter
import com.example.geosamplemanager.data.logs.AppLog
import com.example.geosamplemanager.data.logs.Log
import com.example.geosamplemanager.data.stats.TabKind
import com.example.geosamplemanager.data.stats.TabVisitTracker
import com.example.geosamplemanager.ui.screens.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.9-db-soft-restart: startDestination из initialRoute.
 * FIX 5.9-logs-3: логирование переходов.
 * FIX 5.9-main-a: onOpenOrder → Сверка с нарядом.
 * FIX 5.9-main-b: onOpenReport → Статистика с отчётом.
 *
 * FIX 5.9-exit:
 *  - пункт «Выход» в drawer с подтверждением и авто-бэкапом;
 *  - BackHandler на Главной → диалог выхода;
 *  - overlay «Создаём резервный бэкап…» во время работы;
 *  - onExitApp() вызывается после бэкапа — закрывает приложение.
 *
 * FIX 5.10-stat-tabs:
 *  - TabVisitTracker.onEnter при каждом изменении экрана,
 *    включая первый. Логирование Log.nav по-прежнему пропускает
 *    первый переход (firstNav) — это раздельные поведения.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    initialRoute: String = Screen.MAIN.route,
    onExitApp: () -> Unit = {}
) {
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
        // FIX 5.10-stat-tabs: визит вкладки пишем всегда,
        // включая первый. Логирование Log.nav — отдельное
        // поведение, оно по-прежнему пропускает первый шаг.
        TabVisitTracker.onEnter(currentScreen.toTabKind())

        if (firstNav) {
            firstNav = false
            return@LaunchedEffect
        }
        Log.nav("Открыта вкладка «${currentScreen.title}»")
            .detail("route", currentScreen.route)
            .write()
    }

    // FIX 5.9-exit: состояния диалога и процесса выхода.
    var showExitDialog by remember { mutableStateOf(false) }
    var exitInProgress by remember { mutableStateOf(false) }
    var exitDbEmpty by remember { mutableStateOf(false) }

    // FIX 5.9-exit: при открытии диалога один раз проверяем,
    // пуста ли база — чтобы показать правильную формулировку.
    LaunchedEffect(showExitDialog) {
        if (showExitDialog) {
            exitDbEmpty = withContext(Dispatchers.IO) {
                try {
                    val info = app.repository.getDbInfo()
                    info.samplesCount == 0 &&
                            info.ordersCount == 0 &&
                            info.areasCount == 0
                } catch (_: Exception) {
                    true
                }
            }
        }
    }

    // FIX 5.9-exit: Back на Главной (при закрытом drawer) —
    // диалог выхода. На других вкладках — обычный back.
    // Когда drawer открыт — обрабатывает ModalNavigationDrawer сам.
    BackHandler(
        enabled = currentScreen == Screen.MAIN && !drawerState.isOpen
    ) {
        showExitDialog = true
    }

    fun navigateTo(screen: Screen) {
        navController.navigate(screen.route) {
            popUpTo(Screen.MAIN.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun performExit() {
        showExitDialog = false
        exitInProgress = true
        scope.launch {
            val info = try {
                withContext(Dispatchers.IO) { app.repository.getDbInfo() }
            } catch (_: Exception) {
                null
            }

            val notEmpty = info != null && (
                    info.samplesCount > 0 ||
                            info.ordersCount > 0 ||
                            info.areasCount > 0
                    )

            if (notEmpty) {
                withContext(Dispatchers.IO) {
                    ExitBackupWriter.write(
                        context = app,
                        repo = app.repository,
                        appVersion = try {
                            app.packageManager
                                .getPackageInfo(app.packageName, 0)
                                .versionName ?: "?"
                        } catch (_: Exception) { "?" },
                        counts = BackupCounts(
                            areas = info!!.areasCount,
                            orders = info.ordersCount,
                            samples = info.samplesCount,
                            photos = info.photosCount,
                            notes = info.notesCount
                        ),
                        dbSchemaVersion = DatabaseRepository.DB_SCHEMA_VERSION
                    )
                }
                AppLog.db("Выход из приложения: резервный бэкап создан").write()
            } else {
                AppLog.db("Выход из приложения: база пуста, бэкап пропущен")
                    .write()
            }

            onExitApp()
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
                LazyColumn(modifier = Modifier.weight(1f)) {
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
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                // FIX 5.9-exit: пункт «Выход» — внизу drawer.
                NavigationDrawerItem(
                    icon = {
                        Icon(
                            Icons.Filled.ExitToApp,
                            contentDescription = "Выход"
                        )
                    },
                    label = { Text("Выход") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        showExitDialog = true
                    },
                    modifier = Modifier.padding(
                        horizontal = 12.dp,
                        vertical = 2.dp
                    )
                )
                Spacer(Modifier.height(12.dp))
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

    // FIX 5.9-exit: диалог подтверждения выхода.
    if (showExitDialog) {
        ExitConfirmDialog(
            dbEmpty = exitDbEmpty,
            onConfirm = { performExit() },
            onDismiss = { showExitDialog = false }
        )
    }

    // FIX 5.9-exit: overlay с прогрессом во время бэкапа.
    if (exitInProgress) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Создаём резервный бэкап…",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Это может занять несколько секунд",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * FIX 5.10-stat-tabs:
 * Маппинг экрана навигации на вкладку теневой статистики.
 * Держим здесь, а не в Screen.kt — Screen не знает про stats.
 */
private fun Screen.toTabKind(): TabKind = when (this) {
    Screen.MAIN -> TabKind.MAIN
    Screen.ADD -> TabKind.ADD
    Screen.SEARCH -> TabKind.SEARCH
    Screen.STATS -> TabKind.STATS
    Screen.EDIT -> TabKind.EDIT
    Screen.DB -> TabKind.DB
    Screen.SETTINGS -> TabKind.SETTINGS
}