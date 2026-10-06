package com.example.geosamplemanager.ui.screens.admin

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.geosamplemanager.GeoSampleApp
import com.example.geosamplemanager.data.stats.StatsDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * FIX 5.10-stat-admin-ui-1: ViewModel экрана админа.
 * FIX 5.10-stat-admin-ui-4: выбор дня.
 * FIX 5.10-stat-admin-v2-nav: маршруты, таймлайн.
 * FIX 5.10-stat-admin-v2-time-filter: фильтр времени.
 *
 * FIX 5.10-stat-admin-v2-orders-b:
 *  - allOrders — список всех нарядов из основной БД + статистика
 *    из stats.db (order_work);
 *  - ordersFilterInput — фильтр по строке;
 *  - loadOrders — загрузка (лениво при выборе таба);
 *  - подключение DatabaseRepository через GeoSampleApp.
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

    private val _timeFilterInput = MutableStateFlow("")
    val timeFilterInput: StateFlow<String> = _timeFilterInput.asStateFlow()

    private val _timeFilterResult = MutableStateFlow<TimeFilterParseResult>(
        TimeFilterParseResult.Empty
    )
    val timeFilterResult: StateFlow<TimeFilterParseResult> = _timeFilterResult.asStateFlow()

    private val _allOrders = MutableStateFlow<List<AdminOrderSummary>>(emptyList())
    val allOrders: StateFlow<List<AdminOrderSummary>> = _allOrders.asStateFlow()

    private val _ordersFilterInput = MutableStateFlow("")
    val ordersFilterInput: StateFlow<String> = _ordersFilterInput.asStateFlow()

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
        // FIX 5.10-stat-admin-v2-orders-b: ленивая загрузка нарядов.
        if (tab == AdminPanelTab.ORDERS && _allOrders.value.isEmpty()) {
            loadOrders()
        }
    }

    fun openSession(sessionId: Long) {
        _route.value = AdminPanelRoute.SessionDetail(sessionId)
    }

    fun openVisit(visitId: Long) {
        _route.value = AdminPanelRoute.VisitDetail(visitId)
    }

    fun canGoBack(): Boolean = _route.value != AdminPanelRoute.Default

    fun goBack() {
        val current = _route.value
        _route.value = when (current) {
            is AdminPanelRoute.SessionDetail,
            is AdminPanelRoute.VisitDetail ->
                AdminPanelRoute.Tab(AdminPanelTab.DAY)
            is AdminPanelRoute.Tab -> AdminPanelRoute.Default
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

    // ============================================================
    // Фильтр времени
    // ============================================================

    fun setTimeFilterInput(input: String) {
        _timeFilterInput.value = input
        _timeFilterResult.value = TimeFilterParser.parse(input)
    }

    fun clearTimeFilter() {
        _timeFilterInput.value = ""
        _timeFilterResult.value = TimeFilterParseResult.Empty
    }

    // ============================================================
    // Наряды (FIX 5.10-stat-admin-v2-orders-b)
    // ============================================================

    fun setOrdersFilter(input: String) {
        _ordersFilterInput.value = input
    }

    /**
     * Загрузка всех нарядов. Дёргается при первом переходе на
     * таб «Наряды» (ленивая).
     *
     * Источники:
     *  - DatabaseRepository (основная БД): areas, orders, samples-counts;
     *  - StatsDatabase (stats.db): order_work за всё время.
     */
    fun loadOrders() {
        viewModelScope.launch {
            try {
                val app = getApplication<Application>() as GeoSampleApp
                val repo = app.repository

                val summary = withContext(Dispatchers.IO) {
                    val areas = repo.getAreas()
                    val orders = repo.getAllOrders()
                    val counts = repo.getSampleCountsByOrder()
                    val allWork = StatsDatabase.getInstance(app).statsDao()
                        .getAllOrderWork()
                    val workByOrder = allWork.groupBy { it.orderId }

                    AdminPanelAggregator.buildOrdersSummary(
                        areas = areas,
                        orders = orders,
                        countsByOrder = counts,
                        workByOrder = workByOrder,
                        limit = Int.MAX_VALUE
                    )
                }
                _allOrders.value = summary
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Ошибка загрузки нарядов: ${e.message}"
            }
        }
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