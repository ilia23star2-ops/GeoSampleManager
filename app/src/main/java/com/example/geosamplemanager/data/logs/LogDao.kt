package com.example.geosamplemanager.data.logs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * FIX 5.9-logs-1:
 * DAO журнала. Все методы — suspend.
 *
 * Ротация — trimToMaxEntries(): оставляем последние N записей,
 * остальные удаляем. Вызывающий код решает, когда вызывать
 * (см. LogWriter в logs-2).
 */
@Dao
interface LogDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: LogEntry): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<LogEntry>)

    @Query(
        "SELECT * FROM operation_log " +
                "ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun getLatest(limit: Int, offset: Int = 0): List<LogEntry>

    @Query(
        "SELECT * FROM operation_log WHERE category = :category " +
                "ORDER BY created_at DESC LIMIT :limit"
    )
    suspend fun getByCategory(category: String, limit: Int = 500): List<LogEntry>

    @Query(
        "SELECT * FROM operation_log WHERE level = :level " +
                "ORDER BY created_at DESC LIMIT :limit"
    )
    suspend fun getByLevel(level: String, limit: Int = 500): List<LogEntry>

    @Query(
        "SELECT * FROM operation_log WHERE session_id = :sessionId " +
                "ORDER BY created_at ASC"
    )
    suspend fun getBySession(sessionId: String): List<LogEntry>

    @Query("SELECT COUNT(*) FROM operation_log")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM operation_log WHERE level = :level")
    suspend fun countByLevel(level: String): Int

    @Query(
        "DELETE FROM operation_log WHERE id NOT IN " +
                "(SELECT id FROM operation_log " +
                "ORDER BY created_at DESC LIMIT :maxEntries)"
    )
    suspend fun trimToMaxEntries(maxEntries: Int): Int

    @Query("DELETE FROM operation_log")
    suspend fun deleteAll()
}