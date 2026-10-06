package com.example.geosamplemanager.data.stats

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * FIX 5.10-stat-tabs: трекер визитов вкладок.
 *
 * Что делает:
 *  - открывает запись в tab_visits при входе на вкладку;
 *  - закрывает предыдущий визит при смене вкладки;
 *  - закрывает открытый визит при завершении сессии;
 *  - сбрасывает состояние при старте новой сессии.
 *
 * Правила:
 *  - один открытый визит на сессию;
 *  - повторный вход на ту же вкладку — no-op;
 *  - смена сессии — сброс в памяти (без записи в БД).
 *
 * Все операции сериализованы через Mutex — защита от двух
 * открытых визитов при быстрых тапах по вкладкам.
 */
object TabVisitTracker {

    private const val TAG = "TabVisitTracker"

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var currentSessionId: Long? = null

    @Volatile
    private var currentVisit: CurrentVisit? = null

    private data class CurrentVisit(
        val rowDbId: Long,
        val sessionId: Long,
        val tab: String,
        val fromTs: Long
    )

    /**
     * FIX 5.10-stat-tabs:
     * Решение по смене вкладки. Внутреннее — для unit-тестов.
     */
    internal enum class Decision { OPEN, KEEP, SWITCH }

    internal fun decide(currentTab: String?, newTab: String): Decision = when {
        currentTab == null -> Decision.OPEN
        currentTab == newTab -> Decision.KEEP
        else -> Decision.SWITCH
    }

    internal fun durationSec(fromTs: Long, toTs: Long): Int {
        if (toTs <= fromTs) return 0
        return ((toTs - fromTs) / 1000L).toInt()
    }

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    /**
     * Привязка к сессии. Сбрасывает текущий визит — в новой
     * сессии работа начинается с чистого листа.
     */
    fun bindSession(sessionId: Long) {
        currentSessionId = sessionId
        currentVisit = null
    }

    /**
     * Вход на вкладку. Зовётся из NavGraph при каждом
     * изменении текущего экрана.
     */
    fun onEnter(tab: TabKind) {
        val sessionId = currentSessionId ?: return
        scope.launch {
            mutex.withLock { enterLocked(sessionId, tab) }
        }
    }

    private suspend fun enterLocked(sessionId: Long, tab: TabKind) {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()
        val prev = currentVisit

        when (decide(prev?.tab, tab.code)) {
            Decision.KEEP -> return

            Decision.OPEN -> {
                val id = insertVisit(ctx, sessionId, tab.code, now)
                currentVisit = CurrentVisit(
                    rowDbId = id,
                    sessionId = sessionId,
                    tab = tab.code,
                    fromTs = now
                )
            }

            Decision.SWITCH -> {
                if (prev != null) closeVisit(ctx, prev, now)
                val id = insertVisit(ctx, sessionId, tab.code, now)
                currentVisit = CurrentVisit(
                    rowDbId = id,
                    sessionId = sessionId,
                    tab = tab.code,
                    fromTs = now
                )
            }
        }
    }

    /**
     * Закрытие открытого визита — конец сессии. Безопасно вызывать
     * когда нет открытого — no-op. Suspend — ждёт завершения
     * записи, чтобы to_ts успел лечь в БД.
     */
    suspend fun closeCurrentVisit() {
        val ctx = appContext ?: return
        mutex.withLock {
            val prev = currentVisit ?: return@withLock
            val now = System.currentTimeMillis()
            closeVisit(ctx, prev, now)
            currentVisit = null
        }
    }

    private suspend fun insertVisit(
        ctx: Context,
        sessionId: Long,
        tabCode: String,
        now: Long
    ): Long {
        return try {
            val dao = StatsDatabase.getInstance(ctx).statsDao()
            dao.insertTabVisit(
                TabVisitEntity(
                    sessionId = sessionId,
                    tab = tabCode,
                    fromTs = now,
                    toTs = null,
                    durationSec = 0
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "insertVisit: ошибка", e)
            0L
        }
    }

    private suspend fun closeVisit(ctx: Context, visit: CurrentVisit, now: Long) {
        if (visit.rowDbId <= 0L) return
        try {
            val dao = StatsDatabase.getInstance(ctx).statsDao()
            val dur = durationSec(visit.fromTs, now)
            dao.updateTabVisit(
                TabVisitEntity(
                    id = visit.rowDbId,
                    sessionId = visit.sessionId,
                    tab = visit.tab,
                    fromTs = visit.fromTs,
                    toTs = now,
                    durationSec = dur
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "closeVisit: ошибка", e)
        }
    }
}