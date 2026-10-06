package com.example.geosamplemanager.data.stats

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FIX 5.10-stat-session: трекер жизненного цикла сессии.
 * FIX 5.10-stat-activity-a: active/idle по событиям.
 *
 * FIX 5.10-stat-activity-b:
 *  - init OrderWorkTracker при старте сессии;
 *  - bindSession при открытии новой сессии;
 *  - closeCurrentWork при закрытии сессии.
 *
 * FIX 5.10-stat-tabs:
 *  - init TabVisitTracker при старте;
 *  - bindSession при открытии новой сессии;
 *  - closeCurrentVisit при закрытии сессии.
 */
object SessionTracker {

    private const val TAG = "SessionTracker"

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val accumulator = SessionTimeAccumulator()

    @Volatile
    private var currentSessionId: Long? = null

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
        OrderWorkTracker.init(context)
        TabVisitTracker.init(context)
    }

    fun currentId(): Long? = currentSessionId

    fun onAppStart() {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()

        scope.launch {
            try {
                val dao = StatsDatabase.getInstance(ctx).statsDao()

                dao.getOpenSession()?.let { old ->
                    val closedAt = now
                    val events = try {
                        dao.getEventsForSession(old.id).map { it.atTs }
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val activeSec = ActivityAccumulator.computeActiveSec(
                        eventsTs = events,
                        sessionStart = old.startedAt,
                        sessionEnd = closedAt
                    )
                    val idleSec = ActivityAccumulator.computeIdleSec(
                        old.fgSec, activeSec
                    )

                    dao.updateSession(
                        old.copy(
                            endedAt = closedAt,
                            crashFlag = true,
                            activeSec = activeSec,
                            idleSec = idleSec
                        )
                    )
                    Log.w(TAG, "Закрыта висящая сессия id=${old.id} как crash")
                }

                val newId = dao.insertSession(
                    SessionEntity(
                        startedAt = now,
                        inWorkWindow = WorkWindow.isInWorkWindow(now)
                    )
                )
                currentSessionId = newId
                OrderWorkTracker.bindSession(newId)
                TabVisitTracker.bindSession(newId)
                Log.i(TAG, "Сессия открыта id=$newId")
            } catch (e: Exception) {
                Log.e(TAG, "onAppStart: ошибка", e)
            }
        }
    }

    fun onActivityResume() {
        val id = currentSessionId ?: return
        accumulator.resume(System.currentTimeMillis())
        persistPartial(id)
    }

    fun onActivityPause() {
        val id = currentSessionId ?: return
        accumulator.pause(System.currentTimeMillis())
        persistPartial(id)
    }

    private fun persistPartial(sessionId: Long) {
        val ctx = appContext ?: return
        val fg = accumulator.fgSec
        val bg = accumulator.bgSec

        scope.launch {
            try {
                val dao = StatsDatabase.getInstance(ctx).statsDao()
                val current = dao.getSessionById(sessionId) ?: return@launch
                dao.updateSession(
                    current.copy(
                        fgSec = fg,
                        bgSec = bg
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    suspend fun endSession(crashFlag: Boolean) {
        val ctx = appContext ?: return
        val id = currentSessionId ?: return
        val now = System.currentTimeMillis()

        accumulator.flush(now)

        // FIX 5.10-stat-activity-b: закрыть открытый order_work.
        OrderWorkTracker.closeCurrentWork()

        // FIX 5.10-stat-tabs: закрыть открытый визит вкладки.
        TabVisitTracker.closeCurrentVisit()

        try {
            val dao = StatsDatabase.getInstance(ctx).statsDao()
            val current = dao.getSessionById(id) ?: return

            val events = try {
                dao.getEventsForSession(id).map { it.atTs }
            } catch (_: Exception) {
                emptyList()
            }
            val activeSec = ActivityAccumulator.computeActiveSec(
                eventsTs = events,
                sessionStart = current.startedAt,
                sessionEnd = now
            )
            val fgSec = accumulator.fgSec
            val idleSec = ActivityAccumulator.computeIdleSec(fgSec, activeSec)

            dao.updateSession(
                current.copy(
                    endedAt = now,
                    crashFlag = crashFlag,
                    fgSec = fgSec,
                    bgSec = accumulator.bgSec,
                    activeSec = activeSec,
                    idleSec = idleSec
                )
            )
            Log.i(
                TAG,
                "Сессия закрыта id=$id, fg=${fgSec}s, bg=${accumulator.bgSec}s, " +
                        "active=${activeSec}s, idle=${idleSec}s"
            )
        } catch (e: Exception) {
            Log.e(TAG, "endSession: ошибка", e)
        } finally {
            currentSessionId = null
        }
    }
}