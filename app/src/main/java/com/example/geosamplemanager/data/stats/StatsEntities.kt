package com.example.geosamplemanager.data.stats

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * FIX 5.10-stat-model:
 * Сущности теневой статистики (stats.db, version = 1).
 *
 * Пять таблиц:
 *  - sessions     — сессии (запуск → выход).
 *  - tab_visits   — визиты вкладок внутри сессии.
 *  - order_work   — работа с нарядом внутри сессии.
 *  - events       — события внутри сессии.
 *  - daily_summary — сводка по дню (пересчитывается).
 *
 * Все дочерние таблицы ссылаются на sessions.id с CASCADE —
 * при удалении сессии её записи уходят автоматически.
 *
 * Время — Unix timestamp в миллисекундах.
 * Длительности — секунды (Int).
 */

@Entity(
    tableName = "sessions",
    indices = [
        Index(value = ["started_at"])
    ]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,

    @ColumnInfo(name = "active_sec")
    val activeSec: Int = 0,

    @ColumnInfo(name = "idle_sec")
    val idleSec: Int = 0,

    @ColumnInfo(name = "fg_sec")
    val fgSec: Int = 0,

    @ColumnInfo(name = "bg_sec")
    val bgSec: Int = 0,

    @ColumnInfo(name = "screen_off_sec")
    val screenOffSec: Int = 0,

    @ColumnInfo(name = "crash_flag")
    val crashFlag: Boolean = false,

    @ColumnInfo(name = "in_work_window")
    val inWorkWindow: Boolean = false
)

@Entity(
    tableName = "tab_visits",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"])
    ]
)
data class TabVisitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    val tab: String,

    @ColumnInfo(name = "from_ts")
    val fromTs: Long,

    @ColumnInfo(name = "to_ts")
    val toTs: Long? = null,

    @ColumnInfo(name = "duration_sec")
    val durationSec: Int = 0
)

@Entity(
    tableName = "order_work",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"])
    ]
)
data class OrderWorkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "order_id")
    val orderId: Long,

    @ColumnInfo(name = "area_title")
    val areaTitle: String,

    @ColumnInfo(name = "order_title")
    val orderTitle: String,

    @ColumnInfo(name = "started_at")
    val startedAt: Long,

    @ColumnInfo(name = "ended_at")
    val endedAt: Long? = null,

    @ColumnInfo(name = "search_sec")
    val searchSec: Int = 0,

    @ColumnInfo(name = "verify_sec")
    val verifySec: Int = 0,

    val status: String,

    @ColumnInfo(name = "total_samples")
    val totalSamples: Int = 0,

    @ColumnInfo(name = "found_samples")
    val foundSamples: Int = 0
)

@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["at_ts"]),
        Index(value = ["level"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "session_id")
    val sessionId: Long,

    @ColumnInfo(name = "at_ts")
    val atTs: Long,

    val level: String,

    val category: String,

    val summary: String,

    @ColumnInfo(name = "details_json")
    val detailsJson: String? = null,

    @ColumnInfo(name = "raw_voice")
    val rawVoice: String? = null,

    @ColumnInfo(name = "parsed_voice")
    val parsedVoice: String? = null
)

@Entity(tableName = "daily_summary")
data class DailySummaryEntity(
    @PrimaryKey
    val date: String,

    @ColumnInfo(name = "total_active_sec")
    val totalActiveSec: Int = 0,

    @ColumnInfo(name = "total_idle_sec")
    val totalIdleSec: Int = 0,

    val runs: Int = 0,

    @ColumnInfo(name = "ready_orders")
    val readyOrders: Int = 0,

    @ColumnInfo(name = "errors_count")
    val errorsCount: Int = 0,

    @ColumnInfo(name = "by_tab_json")
    val byTabJson: String = "{}",

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L
)