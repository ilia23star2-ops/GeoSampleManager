package com.example.geosamplemanager.data.stats

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FIX 5.10-stat-session:
 * Трекер жизненного цикла сессии. Пишет в stats.db.
 *
 * Жизненный цикл:
 *  1. SessionTracker.init(context) — один раз из GeoSampleApp.onCreate.
 *  2. SessionTracker.onAppStart() — открыть новую сессию.
 *     Перед этим закрыть «висящую» сессию (ended_at == null) как crash.
 *  3. SessionTracker.onActivityResume() / onActivityPause() — из
 *     MainActivity. Учёт fg/bg времени.
 *  4. SessionTracker.endSession(crashFlag = false) — при явном выходе.
 *
 * Всё I/O — в отдельном scope (Dispatchers.IO). UI не блокируем.
 *
 * Если процесс убили — сессия останется с ended_at == null. При
 * следующем onAppStart помечаем её crash_flag = true.
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
    }

    fun currentId(): Long? = currentSessionId

    /**
     * Открыть новую сессию. Закрыть «висящую» предыдущую.
     */
    fun onAppStart() {
        val ctx = appContext ?: return
        val now = System.currentTimeMillis()

        scope.launch {
            try {
                val dao = StatsDatabase.getInstance(ctx).statsDao()

                // Закрыть висящую сессию, если есть.
                dao.getOpenSession()?.let { old ->
                    val closedAt = old.endedAt ?: now
                    dao.updateSession(
                        old.copy(
                            endedAt = closedAt,
                            crashFlag = true,
                            fgSec = old.fgSec,
                            bgSec = old.bgSec,
                            activeSec = old.fgSec,
                            idleSec = old.bgSec
                        )
                    )
                    Log.w(TAG, "Закрыта висящая сессия id=${old.id} как crash")
                }

                // Открыть новую.
                val newId = dao.insertSession(
                    SessionEntity(
                        startedAt = now,
                        inWorkWindow = WorkWindow.isInWorkWindow(now)
                    )
                )
                currentSessionId = newId
                Log.i(TAG, "Сессия открыта id=$newId")
            } catch (e: Exception) {
                Log.e(TAG, "onAppStart: ошибка", e)
            }
        }
    }

    fun onActivityResume() {
        val id = currentSessionId ?: return
        accumulator.resume(System.currentTimeMillis())
        // Периодически писать в БД не нужно — пишем при закрытии.
        // Но на случай краха можно сохранять fg/bg и на pause.
        persistPartial(id)
    }

    fun onActivityPause() {
        val id = currentSessionId ?: return
        accumulator.pause(System.currentTimeMillis())
        persistPartial(id)
    }

    /**
     * Сохранить текущие fg/bg в БД. Не закрывает сессию.
     * Вызывается на resume/pause — чтобы при краше не потерять
     * хотя бы последние значения.
     */
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
                        bgSec = bg,
                        activeSec = fg,
                        idleSec = bg
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Закрыть сессию. crashFlag = false — явный выход.
     */
    suspend fun endSession(crashFlag: Boolean) {
        val ctx = appContext ?: return
        val id = currentSessionId ?: return
        val now = System.currentTimeMillis()

        accumulator.flush(now)

        try {
            val dao = StatsDatabase.getInstance(ctx).statsDao()
            val current = dao.getSessionById(id) ?: return
            dao.updateSession(
                current.copy(
                    endedAt = now,
                    crashFlag = crashFlag,
                    fgSec = accumulator.fgSec,
                    bgSec = accumulator.bgSec,
                    activeSec = accumulator.fgSec,
                    idleSec = accumulator.bgSec
                )
            )
            Log.i(TAG, "Сессия закрыта id=$id, fg=${accumulator.fgSec}s, bg=${accumulator.bgSec}s")
        } catch (e: Exception) {
            Log.e(TAG, "endSession: ошибка", e)
        } finally {
            currentSessionId = null
        }
    }
}