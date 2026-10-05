package com.example.geosamplemanager.data.logs

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FIX 5.9-logs-1:
 * Запись журнала. Одна строка — одно событие.
 *
 * summary — человекочитаемая русская фраза для UI. Полное
 * предложение в прошедшем времени, без английского и без
 * технического жаргона.
 *
 * details — JSON с контекстом и техническими данными
 * (id, коды, тип исключения, stacktrace). UI показывает
 * details только при раскрытии записи.
 *
 * Таблица отдельная (logs.db, не geosamples.db) — журнал
 * переживает очистку и восстановление основной БД, включая
 * сам факт выполнения этих операций.
 */
@Entity(
    tableName = "operation_log",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["category"]),
        Index(value = ["level"]),
        Index(value = ["session_id"])
    ]
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "session_id")
    val sessionId: String,

    val category: String,

    val level: String,

    val summary: String,

    val details: String? = null
)