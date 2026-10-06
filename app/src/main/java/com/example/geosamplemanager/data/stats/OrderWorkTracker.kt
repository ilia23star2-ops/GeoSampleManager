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
 * FIX 5.10-stat-activity-b-2 (смена модели фаз):
 *  - фазу определяет ПОСЛЕДНЕЕ событие: поиск → SEARCH,
 *    отметка → VERIFY;
 *  - правило «интервал идёт в фазу, которой начался»: интервал
 *    з1→з2 в SEARCH, з2→о1 в SEARCH, о1→о2 и о2→о3 в VERIFY;
 *  - убран порог «5 событий без отметок» — модель стала проще;
 *  - время копится на КАЖДОМ событии; миллисекундная точность
 *    с переносом остатка (см. accumulateCurrentPhase);
 *  - пауза ≥ 60 сек считается простоем и в фазу не идёт.
 *
 * Чистая логика — в OrderWorkPhaseLogic. Покрыта unit-тестами.
 */
object OrderWorkTracker {

    private const val TAG = "OrderWorkTracker"

    /** Простой: 30 минут без действий — закрываем наряд. */
    private const val IDLE_END_MS = 30L * 60L * 1000L

    /** Разрыв, который считается простоем и не идёт в фазы. */
    private const val IDLE_GAP_SEC = 60

    /** То же в миллисекундах — для чистой логики накопления. */
    private const val IDLE_GAP_MS = IDLE_GAP_SEC * 1000L

    private var appContext: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var currentSessionId: Long? = null

    @Volatile
    private var current: CurrentWork? = null

    /**
     * Атомарное действие для чистой функции computePhases.
     * isMark = true — отметка пробы, false — поиск или иное событие.
     */
    data class Action(val at: Long, val isMark: Boolean)

    /**
     * Результат разложения списка действий на фазы.
     */
    data class Phases(val searchSec: Int, val verifySec: Int)

    /**
     * FIX 5.10-stat-activity-b-2 (смена модели):
     * Чистое разложение списка действий на фазы.
     *
     * Правило «интервал идёт в фазу, которой начался»:
     *  - фаза интервала = фаза ПОСЛЕДНЕГО события перед ним
     *    (действие `cur` определяет фазу интервала `cur → next`);
     *  - поиск (cur.isMark = false) → SEARCH;
     *  - отметка (cur.isMark = true) → VERIFY;
     *  - первый интервал всегда SEARCH (народа начинается с поиска);
     *  - дельта ≥ 60 сек считается простоем и не учитывается.
     *
     * Пример `[з1, з2, о1, о2, о3]`:
     *  - з1→з2 — SEARCH;
     *  - з2→о1 — SEARCH;
     *  - о1→о2 — VERIFY;
     *  - о2→о3 — VERIFY.
     */
    fun computePhases(actions: List<Action>): Phases {
        if (actions.size < 2) return Phases(0, 0)

        var searchSec = 0
        var verifySec = 0

        for (i in 0 until actions.size - 1) {
            val cur = actions[i]
            val next = actions[i + 1]
            val deltaSec = ((next.at - cur.at) / 1000L).toInt()

            // Фаза интервала определяется действием cur.
            val phase = if (cur.isMark) OrderWorkPhase.VERIFY
            else OrderWorkPhase.SEARCH

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
     * Упрощённая версия статуса для unit-тестов.
     */
    fun computeStatus(total: Int, found: Int): String {
        return OrderWorkPhaseLogic.computeStatus(
            totalSamples = total,
            foundSamples = found,
            hasVerifyActivity = false
        ).code
    }

    /**
     * Активный наряд в памяти. Один на всю сессию.
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
        var totalSamples: Int,
        var foundSamples: Int,
        var lastActivityAt: Long
    )

    fun init(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext
    }

    /**
     * Привязка к сессии. Сбрасывает текущий наряд.
     */
    fun bindSession(sessionId: Long) {
        currentSessionId = sessionId
        current = null
    }

    /**
     * Событие поиска. Если наряд тот же — фаза становится SEARCH.
     * Другой наряд — закрываем предыдущий, открываем новый в SEARCH.
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

        // Время в текущую фазу накапливаем до переключения.
        accumulateCurrentPhase(work, now)

        work.totalSamples = totalSamples
        work.foundSamples = foundSamples
        work.lastActivityAt = now

        // FIX 5.10-stat-activity-b-2 (смена модели):
        // поиск всегда переводит фазу в SEARCH. Никакого порога.
        if (work.phase != OrderWorkPhase.SEARCH) {
            Log.i(TAG, "Фаза наряда ${work.orderId}: ${work.phase} → SEARCH (поиск)")
            work.phase = OrderWorkPhase.SEARCH
            work.phaseStartedAt = now
        }

        persist(work)
    }

    /**
     * Событие отметки. Если наряд тот же — фаза становится VERIFY.
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

        // Время в текущую фазу накапливаем до переключения.
        accumulateCurrentPhase(work, now)

        work.totalSamples = totalSamples
        work.foundSamples = foundSamples
        work.lastActivityAt = now

        // FIX 5.10-stat-activity-b-2 (смена модели):
        // отметка всегда переводит фазу в VERIFY.
        if (work.phase != OrderWorkPhase.VERIFY) {
            Log.i(TAG, "Фаза наряда ${work.orderId}: ${work.phase} → VERIFY (отметка)")
            work.phase = OrderWorkPhase.VERIFY
            work.phaseStartedAt = now
        }

        persist(work)
    }

    /**
     * Закрытие текущего наряда. Безопасно вызывать когда
     * нет активного — no-op.
     */
    fun closeCurrentWork() {
        closeCurrent(System.currentTimeMillis())
    }

    /**
     * FIX 5.10-stat-activity-b-2:
     * Накопить время текущей фазы с последнего события.
     *
     *  - считаем в миллисекундах, деление на 1000 в конце;
     *  - phaseStartedAt сдвигаем ТОЛЬКО на целые накопленные
     *    секунды — остаток переносится на следующее событие;
     *  - простой (≥ IDLE_GAP_MS) в фазу не копится, но сбрасывает
     *    точку отсчёта на `now`.
     *
     * При смене фазы (см. onSearch / onMark) phaseStartedAt
     * дополнительно сбрасывается на now — точка отсчёта новой
     * фазы.
     */
    private fun accumulateCurrentPhase(work: CurrentWork, now: Long) {
        val elapsedMs = now - work.phaseStartedAt
        val r = OrderWorkPhaseLogic.accumulate(elapsedMs, IDLE_GAP_MS)

        when {
            r.advanceMs == -1L -> {
                work.phaseStartedAt = now
            }
            r.seconds > 0 -> {
                when (work.phase) {
                    OrderWorkPhase.SEARCH -> work.searchSec += r.seconds
                    OrderWorkPhase.VERIFY -> work.verifySec += r.seconds
                }
                work.phaseStartedAt += r.advanceMs
            }
        }
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

        // Дозакрываем текущую фазу — без ограничения IDLE_GAP_MS.
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