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
 * ViewModel экрана админа. Грузит день из stats.db и собирает
 * DayView через AdminPanelAggregator.
 *
 * FIX 5.10-stat-admin-ui-4:
 *  - выбор дня: availableDates + selectedDate + selectDate;
 *  - по умолчанию — сегодня; сбрасывается при пересоздании VM;
 *  - список дат — из sessions (гибрид), daily_summary позже.
 */
class AdminPanelViewModel(application: Application) : AndroidViewModel(application) {

    private val _dayView = MutableStateFlow<DayView?>(null)
    val dayView: StateFlow<DayView?> = _dayView.asStateFlow()

    private val _availableDates = MutableStateFlow<List<String>>(emptyList())
    val availableDates: StateFlow<List<String>> = _availableDates.asStateFlow()

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

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

    /**
     * Обновить текущий день (кнопка «Обновить» и pull при открытии).
     * Сбрасывает выбор на сегодня, потому что админ чаще смотрит
     * свежие данные.
     */
    fun loadToday() {
        viewModelScope.launch {
            val today = AdminPanelDateUtils.today()
            _selectedDate.value = today
            refreshAvailableDates(today)
            loadDayInternal(today)
        }
    }

    /**
     * Выбрать конкретный день. Если он уже выбран — no-op.
     */
    fun selectDate(date: String) {
        if (date == _selectedDate.value) return
        _selectedDate.value = date
        viewModelScope.launch { loadDayInternal(date) }
    }

    /**
     * Обновить список доступных дат. Сегодня добавляется всегда —
     * даже если по нему ещё нет сессий.
     */
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
        // Объединяем: [today] + все из БД, без дублей.
        // Сортировка — по убыванию строки: YYYY-MM-DD сортируется
        // лексикографически так же, как хронологически.
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

            val view = withContext(Dispatchers.IO) {
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

                AdminPanelAggregator.buildDayView(
                    date = date,
                    sessions = sessions,
                    visitsBySession = visitsBySession,
                    orderWorkBySession = orderWorkBySession,
                    events = events,
                    dailySummary = summary,
                    problems = ProblemsView(0, 0, 0)
                )
            }
            _dayView.value = view
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _message.value = "Ошибка загрузки дня: ${e.message}"
        }
    }
}