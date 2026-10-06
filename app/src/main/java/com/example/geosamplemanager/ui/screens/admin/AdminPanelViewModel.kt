package com.example.geosamplemanager.ui.screens.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.data.stats.StatsDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.10-stat-admin-ui-1:
 * ViewModel экрана админа.
 *
 * FIX 5.10-stat-admin-ui-4: выбор дня.
 *
 * FIX 5.10-stat-admin-v2-nav:
 *  - route — навигация внутри панели (табы + детали);
 *  - timelineScale — масштаб шкалы дня;
 *  - goBack() — обработка системного Back.
 */
class AdminPanelViewModel(application: Application) : AndroidViewModel(application) {

    private val _dayView = MutableStateFlow<DayView?>(null)
    val dayView: StateFlow<DayView?> = _dayView.asStateFlow()

    private val _availableDates = MutableStateFlow<List<String>>(emptyList())
    val availableDates: StateFlow<List<String>> = _availableDates.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    private val _timelineSegments = MutableStateFlow<List<TimelineSegment>>(emptyList())
    val timelineSegments: StateFlow<List<TimelineSegment>> = _timelineSegments.asStateFlow()

    private val _route = MutableStateFlow<AdminPanelRoute>(AdminPanelRoute.Default)
    val route: StateFlow<AdminPanelRoute> = _route.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            val today = AdminPanelDateUtils.today()
            _selectedDate.value = today
            refreshAvailableDates(today)
            loadDayInternal(today)
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    // ============================================================
    // Навигация
    // ============================================================

    fun selectTab(tab: AdminPanelTab) {
        _route.value = AdminPanelRoute.Tab(tab)
    }

    fun openSession(sessionId: Long) {
        _route.value = AdminPanelRoute.SessionDetail(sessionId)
    }

    fun openVisit(visitId: Long) {
        _route.value = AdminPanelRoute.VisitDetail(visitId)
    }

    /** Можно ли вернуться назад (обрабатывается BackHandler). */
    fun canGoBack(): Boolean = _route.value != AdminPanelRoute.Default

    /** Вернуться на уровень вверх. */
    fun goBack() {
        val current = _route.value
        _route.value = when (current) {
            is AdminPanelRoute.SessionDetail,
            is AdminPanelRoute.VisitDetail ->
                AdminPanelRoute.Tab(AdminPanelTab.DAY)
            is AdminPanelRoute.Tab -> if (current.tab == AdminPanelTab.DAY) {
                AdminPanelRoute.Default
            } else {
                AdminPanelRoute.Default
            }
        }
    }

    // ============================================================
    // День
    // ============================================================

    fun loadToday() {
        viewModelScope.launch {
            val today = AdminPanelDateUtils.today()
            _selectedDate.value = today
            refreshAvailableDates(today)
            loadDayInternal(today)
        }
    }

    fun selectDate(date: String) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
        viewModelScope.launch { loadDayInternal(date) }
    }

    private suspend fun refreshAvailableDates(today: String) {
        val fromDb = try {
            val ctx = getApplication<Application>()
            withContext(Dispatchers.IO) {
                StatsDatabase.getInstance(ctx).statsDao()
                    .getDistinctSessionDates()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        val merged = (listOf(today) + fromDb)
            .distinct()
            .sortedDescending()
        _availableDates.value = merged
    }

    private suspend fun loadDayInternal(date: String) {
        try {
            val ctx = getApplication<Application>()
            val (start, end) = AdminPanelDateUtils.dayBounds(date)
            val now = System.currentTimeMillis()

            val result = withContext(Dispatchers.IO) {
                val dao = StatsDatabase.getInstance(ctx).statsDao()

                val sessions = dao.getSessionsBetween(start, end)

                val visitsBySession = sessions.associate { s ->
                    s.id to dao.getVisitsForSession(s.id).map { v ->
                        if (v.toTs == null) {
                            v.copy(
                                toTs = now,
                                durationSec = ((now - v.fromTs) / 1000L).toInt()
                            )
                        } else v
                    }
                }

                val orderWorkBySession = sessions.associate { s ->
                    s.id to dao.getOrderWorkForSession(s.id)
                }

                val events = dao.getEventsBetween(start, end)
                val summary = dao.getDailySummary(date)

                val view = AdminPanelAggregator.buildDayView(
                    date = date,
                    sessions = sessions,
                    visitsBySession = visitsBySession,
                    orderWorkBySession = orderWorkBySession,
                    events = events,
                    dailySummary = summary,
                    problems = ProblemsView(0, 0, 0)
                )
                val segments = AdminPanelAggregator.computeTimelineSegments(
                    sessions = sessions,
                    date = date,
                    now = now
                )
                view to segments
            }
            _dayView.value = result.first
            _timelineSegments.value = result.second
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки дня: ${e.message}"
        }
    }
}