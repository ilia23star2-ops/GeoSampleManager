package com.example.geosamplemanager.data.stats

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

/**
 * FIX 5.10-stat-model: DAO теневой статистики.
 *
 * FIX 5.10-stat-activity-a: @Transaction на insertEvents.
 * FIX 5.10-stat-activity-b: getOrderWorkById.
 * FIX 5.10-stat-admin-ui-4: getDistinctSessionDates.
 * FIX 5.10-stat-admin-v2-orders-b: getAllOrderWork.
 *
 * FIX 5.10-stat-daily-file-2:
 *  - getMaxSessionStartedAt — максимальный started_at среди сессий.
 *    Используется StatsRotator'ом для определения месяца данных.
 */
@Dao
interface StatsDao {

    // ============================================================
    // sessions
    // ============================================================

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query(
        "SELECT * FROM sessions WHERE ended_at IS NULL " +
                "ORDER BY started_at DESC LIMIT 1"
    )
    suspend fun getOpenSession(): SessionEntity?

    @Query(
        "SELECT * FROM sessions " +
                "WHERE started_at >= :fromTs AND started_at < :toTs " +
                "ORDER BY started_at"
    )
    suspend fun getSessionsBetween(fromTs: Long, toTs: Long): List<SessionEntity>

    @Query(
        "SELECT DISTINCT date(started_at / 1000, 'unixepoch', 'localtime') " +
                "AS d FROM sessions ORDER BY started_at DESC"
    )
    suspend fun getDistinctSessionDates(): List<String>

    /**
     * FIX 5.10-stat-daily-file-2:
     * Максимальный started_at среди сессий, или null если таблица
     * пуста. Используется StatsRotator'ом для определения месяца
     * данных в active.db.
     */
    @Query("SELECT MAX(started_at) FROM sessions")
    suspend fun getMaxSessionStartedAt(): Long?

    // ============================================================
    // tab_visits
    // ============================================================

    @Insert
    suspend fun insertTabVisit(visit: TabVisitEntity): Long

    @Update
    suspend fun updateTabVisit(visit: TabVisitEntity)

    @Query("SELECT * FROM tab_visits WHERE session_id = :sessionId ORDER BY from_ts")
    suspend fun getVisitsForSession(sessionId: Long): List<TabVisitEntity>

    @Query(
        "SELECT * FROM tab_visits " +
                "WHERE session_id = :sessionId AND to_ts IS NULL " +
                "ORDER BY from_ts DESC LIMIT 1"
    )
    suspend fun getOpenTabVisit(sessionId: Long): TabVisitEntity?

    // ============================================================
    // order_work
    // ============================================================

    @Insert
    suspend fun insertOrderWork(work: OrderWorkEntity): Long

    @Update
    suspend fun updateOrderWork(work: OrderWorkEntity)

    @Query("SELECT * FROM order_work WHERE id = :id")
    suspend fun getOrderWorkById(id: Long): OrderWorkEntity?

    @Query("SELECT * FROM order_work WHERE session_id = :sessionId ORDER BY started_at")
    suspend fun getOrderWorkForSession(sessionId: Long): List<OrderWorkEntity>

    @Query("SELECT * FROM order_work ORDER BY started_at DESC")
    suspend fun getAllOrderWork(): List<OrderWorkEntity>

    @Query(
        "SELECT * FROM order_work " +
                "WHERE session_id = :sessionId AND order_id = :orderId " +
                "AND ended_at IS NULL LIMIT 1"
    )
    suspend fun getOpenOrderWork(sessionId: Long, orderId: Long): OrderWorkEntity?

    // ============================================================
    // events
    // ============================================================

    @Insert
    suspend fun insertEvent(event: EventEntity): Long

    @Transaction
    @Insert
    suspend fun insertEvents(events: List<EventEntity>): List<Long>

    @Query("SELECT * FROM events WHERE session_id = :sessionId ORDER BY at_ts")
    suspend fun getEventsForSession(sessionId: Long): List<EventEntity>

    @Query(
        "SELECT * FROM events " +
                "WHERE at_ts >= :fromTs AND at_ts < :toTs " +
                "ORDER BY at_ts"
    )
    suspend fun getEventsBetween(fromTs: Long, toTs: Long): List<EventEntity>

    @Query(
        "SELECT * FROM events " +
                "WHERE at_ts >= :fromTs AND at_ts < :toTs AND level = :level " +
                "ORDER BY at_ts"
    )
    suspend fun getEventsByLevel(
        fromTs: Long,
        toTs: Long,
        level: String
    ): List<EventEntity>

    @Query(
        "SELECT COUNT(*) FROM events " +
                "WHERE at_ts >= :fromTs AND at_ts < :toTs AND level = :level"
    )
    suspend fun countEventsByLevel(fromTs: Long, toTs: Long, level: String): Int

    // ============================================================
    // daily_summary
    // ============================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailySummary(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summary WHERE date = :date")
    suspend fun getDailySummary(date: String): DailySummaryEntity?

    @Query("SELECT * FROM daily_summary ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentDailySummaries(limit: Int): List<DailySummaryEntity>

    // ============================================================
    // Очистка
    // ============================================================

    @Query("DELETE FROM sessions WHERE started_at < :beforeTs")
    suspend fun deleteSessionsBefore(beforeTs: Long): Int

    @Query("DELETE FROM events WHERE at_ts < :beforeTs")
    suspend fun deleteEventsBefore(beforeTs: Long): Int

    @Query("DELETE FROM tab_visits WHERE from_ts < :beforeTs")
    suspend fun deleteTabVisitsBefore(beforeTs: Long): Int

    @Query("DELETE FROM order_work WHERE started_at < :beforeTs")
    suspend fun deleteOrderWorkBefore(beforeTs: Long): Int

    @Query("DELETE FROM daily_summary WHERE date < :beforeDate")
    suspend fun deleteDailySummariesBefore(beforeDate: String): Int
}