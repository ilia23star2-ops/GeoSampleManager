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
import java.util.Calendar

/**
 * FIX 5.10-stat-admin-ui-1:
 * ViewModel экрана админа. Грузит сегодняшний день из stats.db
 * и собирает DayView через AdminPanelAggregator.
 *
 * Особенности:
 *  - открытые визиты «дозакрываются» на момент чтения: иначе
 *    duration_sec в БД ещё нулевой;
 *  - ProblemsView на первой версии — нули. Реальная загрузка
 *    диагностики подключается отдельно.
 */
class AdminPanelViewModel(application: Application) : AndroidViewModel(application) {

    private val _dayView = MutableStateFlow<DayView?>(null)
    val dayView: StateFlow<DayView?> = _dayView.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadToday()
    }

    fun clearMessage() {
        _message.value = null
    }

    fun loadToday() {
        loadDay(formatDate(System.currentTimeMillis()))
    }

    fun loadDay(date: String) {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                val (start, end) = dayBounds(date)
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

    /**
     * Границы дня в миллисекундах. Дата — `YYYY-MM-DD`, локальная зона.
     * Возврат: [начало дня, начало следующего дня).
     */
    private fun dayBounds(date: String): Pair<Long, Long> {
        val parts = date.split("-")
        val cal = Calendar.getInstance()
        cal.set(
            parts[0].toInt(),
            parts[1].toInt() - 1,
            parts[2].toInt(),
            0, 0, 0
        )
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }

    private fun formatDate(ts: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ts
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }
}