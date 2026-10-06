package com.example.geosamplemanager.data.stats

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FIX 5.10-stat-activity-b: трекер фаз наряда.
 *
 * Что делает:
 *  - держит один активный наряд в памяти;
 *  - переключает фазу «Поиск» / «Сверка» по событиям;
 *  - пишет строку в order_work при старте, каждом событии и закрытии;
 *  - закрывает наряд при смене наряда, простое или закрытии сессии.
 *
 * Правила (SHADOW_STATS §4.6):
 *  - начало — первое событие с упоминанием наряда (поиск / отметка);
 *  - «Поиск» — между запросами без отметок;
 *  - «Сверка» — между отметками;
 *  - возврат в «Поиск» — 5+ событий без отметок после «Сверки»;
 *  - статусы: in_progress / half_done / done;
 *  - готов — все found = true;
 *  - закрытие — 30 мин без действий, смена наряда или конец сессии.
 *
 * FIX 5.10-stat-activity-b (уточнение):
 *  - время фазы накапливается на КАЖДОМ событии, а не только при
 *    смене фазы. Без этого при быстрой работе search_sec/verify_sec
 *    оставались нулевыми;
 *  - разрыв между событиями ≥ 60 сек считается простоем и в фазу
 *    не идёт.
 *
 * Чистая логика переходов — в OrderWorkPhaseLogic. Чистое
 * разложение списка действий на фазы — в computePhases,
 * оно покрыто unit-тестами.
 */
object OrderWorkTracker {

    private const val TAG = "OrderWorkTracker"

    /** Возврат в «Поиск»: сколько поисковых событий без отметок. */
    private const val SEARCH_RETURN_THRESHOLD = 5

    /** Простой: 30 минут без действий — закрываем наряд. */
    private const val IDLE_END_MS = 30L * 60L * 1000L

    /** Разрыв, который считается простоем и не идёт в фазы. */
    private const val IDLE_GAP_SEC = 60

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentSessionId: Long? = null

    @Volatile
    private var current: CurrentWork? = null

    /**
     * FIX 5.10-stat-activity-b:
     * Атомарное действие для чистой функции computePhases.
     * isMark = true — отметка пробы, false — поиск или иное событие.
     */
    data class Action(val at: Long, val isMark: Boolean)

    /**
     * FIX 5.10-stat-activity-b:
     * Результат разложения списка действий на фазы.
     */
    data class Phases(val searchSec: Int, val verifySec: Int)

    /**
     * FIX 5.10-stat-activity-b:
     * Чистое разложение списка действий на фазы «Поиск» / «Сверка».
     *
     * Правила:
     *  - фаза стартует как «Поиск»;
     *  - отметка всегда переводит в «Сверку» и сбрасывает счётчик
     *    событий без отметок;
     *  - 5 событий без отметок подряд в «Сверке» — возврат в «Поиск»;
     *  - дельта между соседними действиями идёт в текущую фазу;
     *  - дельта ≥ 60 сек считается простоем и не учитывается.
     */
    fun computePhases(actions: List<Action>): Phases {
        if (actions.size < 2) return Phases(0, 0)

        var searchSec = 0
        var verifySec = 0
        var phase = OrderWorkPhase.SEARCH
        var nonMarkStreak = 0

        for (i in 0 until actions.size - 1) {
            val cur = actions[i]
            val next = actions[i + 1]
            val deltaSec = ((next.at - cur.at) / 1000L).toInt()

            // Обновляем фазу по действию cur.
            if (cur.isMark) {
                phase = OrderWorkPhaseLogic.decideAfterMark(phase)
                nonMarkStreak = 0
            } else {
                nonMarkStreak++
                val next2 = OrderWorkPhaseLogic.decideAfterSearch(
                    currentPhase = phase,
                    eventsSinceLastMark = nonMarkStreak,
                    threshold = SEARCH_RETURN_THRESHOLD
                )
                if (next2 != phase) {
                    phase = next2
                    nonMarkStreak = 0
                }
            }

            // Дельта идёт в текущую фазу, если это не простой.
            if (deltaSec in 0 until IDLE_GAP_SEC) {
                when (phase) {
                    OrderWorkPhase.SEARCH -> searchSec += deltaSec
                    OrderWorkPhase.VERIFY -> verifySec += deltaSec
                }
            }
        }

        return Phases(searchSec, verifySec)
    }

    /**
     * FIX 5.10-stat-activity-b:
     * Статус наряда по счётчикам. Упрощённая версия без флага
     * «была ли сверка» — используется в unit-тестах.
     *
     *  - done: все пробы отмечены и их больше 0;
     *  - in_progress: во всех остальных случаях.
     *
     * Для полной версии с half_done используй
     * OrderWorkPhaseLogic.computeStatus (там есть hasVerifyActivity).
     */
    fun computeStatus(total: Int, found: Int): String {
        return OrderWorkPhaseLogic.computeStatus(
            totalSamples = total,
            foundSamples = found,
            hasVerifyActivity = false
        ).code
    }

    /**
     * Активный наряд в памяти. Один на всю сессию: при смене
     * наряда закрываем текущий и открываем новый.
     */
    private data class CurrentWork(
        val rowDbId: Long,
        val orderId: Long,
        val areaTitle: String,
        val orderTitle: String,
        var phase: OrderWorkPhase,
        var phaseStartedAt: Long,
        val startedAt: Long,
        var searchSec: Int,
        var verifySec: Int,
        var eventsSinceLastMark: Int,
        var totalSamples: Int,
        var foundSamples: Int,
        var lastActivityAt: Long
    )

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    /**
     * Привязка к сессии. Вызывается SessionTracker'ом после открытия
     * новой сессии. Сбрасывает текущий наряд — в новой сессии работа
     * начинается с нуля.
     */
    fun bindSession(sessionId: Long) {
        currentSessionId = sessionId
        current = null
    }

    /**
     * Событие поиска. Если наряд тот же — инкремент счётчика событий
     * без отметок; при 5+ в фазе «Сверка» — возврат в «Поиск».
     * Другой наряд — закрываем предыдущий, открываем новый.
     */
    fun onSearch(
        orderId: Long,
        areaTitle: String,
        orderTitle: String,
        totalSamples: Int,
        foundSamples: Int
    ) {
        val sessionId = currentSessionId ?: return
        val now = System.currentTimeMillis()

        checkIdle(now)

        val work = current
        if (work == null || work.orderId != orderId) {
            if (work != null) closeCurrent(now)
            startNew(
                sessionId, orderId, areaTitle, orderTitle,
                totalSamples, foundSamples, now, OrderWorkPhase.SEARCH
            )
            return
        }

        // FIX: время в текущую фазу накапливаем до переключения.
        accumulateCurrentPhase(work, now)

        work.totalSamples = totalSamples
        work.foundSamples = foundSamples
        work.lastActivityAt = now
        work.eventsSinceLastMark++

        val next = OrderWorkPhaseLogic.decideAfterSearch(
            currentPhase = work.phase,
            eventsSinceLastMark = work.eventsSinceLastMark,
            threshold = SEARCH_RETURN_THRESHOLD
        )
        if (next != work.phase) {
            Log.i(TAG, "Фаза наряда ${work.orderId}: ${work.phase} → $next (поиск)")
            work.phase = next
            if (next == OrderWorkPhase.SEARCH) {
                work.eventsSinceLastMark = 0
            }
        }

        persist(work)
    }

    /**
     * Событие отметки. Переключает фазу в «Сверку», если была
     * «Поиск». Сбрасывает счётчик событий без отметок.
     */
    fun onMark(
        orderId: Long,
        areaTitle: String,
        orderTitle: String,
        totalSamples: Int,
        foundSamples: Int
    ) {
        val sessionId = currentSessionId ?: return
        val now = System.currentTimeMillis()

        checkIdle(now)

        val work = current
        if (work == null || work.orderId != orderId) {
            if (work != null) closeCurrent(now)
            startNew(
                sessionId, orderId, areaTitle, orderTitle,
                totalSamples, foundSamples, now, OrderWorkPhase.VERIFY
            )
            return
        }

        // FIX: время в текущую фазу накапливаем до переключения.
        accumulateCurrentPhase(work, now)

        work.totalSamples = totalSamples
        work.foundSamples = foundSamples
        work.lastActivityAt = now
        work.eventsSinceLastMark = 0

        val next = OrderWorkPhaseLogic.decideAfterMark(work.phase)
        if (next != work.phase) {
            Log.i(TAG, "Фаза наряда ${work.orderId}: ${work.phase} → $next (отметка)")
            work.phase = next
        }

        persist(work)
    }

    /**
     * Закрытие текущего наряда — конец сессии, смена наряда или
     * простой. Безопасно вызывать когда нет активного — no-op.
     */
    fun closeCurrentWork() {
        closeCurrent(System.currentTimeMillis())
    }

    /**
     * FIX 5.10-stat-activity-b:
     * Накопить время текущей фазы с последнего события. Зовётся
     * перед каждым обновлением состояния — тогда search_sec и
     * verify_sec растут, а не обнуляются при быстрой работе.
     */
    private fun accumulateCurrentPhase(work: CurrentWork, now: Long) {
        val elapsed = secondsBetween(work.phaseStartedAt, now)
        if (elapsed in 1 until IDLE_GAP_SEC) {
            when (work.phase) {
                OrderWorkPhase.SEARCH -> work.searchSec += elapsed
                OrderWorkPhase.VERIFY -> work.verifySec += elapsed
            }
        }
        work.phaseStartedAt = now
    }

    private fun checkIdle(now: Long) {
        val work = current ?: return
        if (now - work.lastActivityAt >= IDLE_END_MS) {
            Log.i(TAG, "Простой >30 мин — закрываю наряд id=${work.orderId}")
            closeCurrent(now)
        }
    }

    private fun startNew(
        sessionId: Long,
        orderId: Long,
        areaTitle: String,
        orderTitle: String,
        totalSamples: Int,
        foundSamples: Int,
        now: Long,
        initialPhase: OrderWorkPhase
    ) {
        val ctx = appContext ?: return
        val status = OrderWorkPhaseLogic.computeStatus(
            totalSamples = totalSamples,
            foundSamples = foundSamples,
            hasVerifyActivity = initialPhase == OrderWorkPhase.VERIFY
        )

        scope.launch {
            try {
                val dao = StatsDatabase.getInstance(ctx).statsDao()
                val entity = OrderWorkEntity(
                    sessionId = sessionId,
                    orderId = orderId,
                    areaTitle = areaTitle,
                    orderTitle = orderTitle,
                    startedAt = now,
                    endedAt = null,
                    searchSec = 0,
                    verifySec = 0,
                    status = status.code,
                    totalSamples = totalSamples,
                    foundSamples = foundSamples
                )
                val rowId = dao.insertOrderWork(entity)
                current = CurrentWork(
                    rowDbId = rowId,
                    orderId = orderId,
                    areaTitle = areaTitle,
                    orderTitle = orderTitle,
                    phase = initialPhase,
                    phaseStartedAt = now,
                    startedAt = now,
                    searchSec = 0,
                    verifySec = 0,
                    eventsSinceLastMark = 0,
                    totalSamples = totalSamples,
                    foundSamples = foundSamples,
                    lastActivityAt = now
                )
                Log.i(
                    TAG,
                    "Начало наряда id=$orderId (rowId=$rowId), фаза=$initialPhase"
                )
            } catch (e: Exception) {
                Log.e(TAG, "startNew: ошибка", e)
            }
        }
    }

    private fun closeCurrent(now: Long) {
        val work = current ?: return
        val ctx = appContext ?: return

        // Дозакрываем текущую фазу — без ограничения IDLE_GAP_SEC,
        // потому что это финальное закрытие, а не событие.
        val elapsed = secondsBetween(work.phaseStartedAt, now)
        when (work.phase) {
            OrderWorkPhase.SEARCH -> work.searchSec += elapsed
            OrderWorkPhase.VERIFY -> work.verifySec += elapsed
        }

        val status = OrderWorkPhaseLogic.computeStatus(
            totalSamples = work.totalSamples,
            foundSamples = work.foundSamples,
            hasVerifyActivity = work.verifySec > 0 || work.phase == OrderWorkPhase.VERIFY
        )

        current = null

        scope.launch {
            try {
                val dao = StatsDatabase.getInstance(ctx).statsDao()
                val existing = dao.getOrderWorkById(work.rowDbId) ?: return@launch
                dao.updateOrderWork(
                    existing.copy(
                        endedAt = now,
                        searchSec = work.searchSec,
                        verifySec = work.verifySec,
                        status = status.code,
                        totalSamples = work.totalSamples,
                        foundSamples = work.foundSamples
                    )
                )
                Log.i(
                    TAG,
                    "Закрыт наряд id=${work.orderId} (rowId=${work.rowDbId}), " +
                            "search=${work.searchSec}s, verify=${work.verifySec}s, " +
                            "status=$status"
                )
            } catch (e: Exception) {
                Log.e(TAG, "closeCurrent: ошибка", e)
            }
        }
    }

    private fun persist(work: CurrentWork) {
        val ctx = appContext ?: return
        val snapshot = work.copy()
        val status = OrderWorkPhaseLogic.computeStatus(
            totalSamples = snapshot.totalSamples,
            foundSamples = snapshot.foundSamples,
            hasVerifyActivity = snapshot.verifySec > 0 || snapshot.phase == OrderWorkPhase.VERIFY
        )

        scope.launch {
            try {
                val dao = StatsDatabase.getInstance(ctx).statsDao()
                val existing = dao.getOrderWorkById(snapshot.rowDbId) ?: return@launch
                dao.updateOrderWork(
                    existing.copy(
                        searchSec = snapshot.searchSec,
                        verifySec = snapshot.verifySec,
                        status = status.code,
                        totalSamples = snapshot.totalSamples,
                        foundSamples = snapshot.foundSamples
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "persist: ошибка", e)
            }
        }
    }

    private fun secondsBetween(from: Long, to: Long): Int {
        if (to <= from) return 0
        return ((to - from) / 1000L).toInt()
    }
}