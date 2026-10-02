package com.example.geosamplemanager.data.merge

import com.example.geosamplemanager.data.AppDatabase
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.OrderWellEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity
import java.io.File

/**
 * FIX 5.9-db-merge-v2:
 * Модели для движка слияния БД.
 *
 * /1 — участки и наряды.
 * /2 — пробы, скважины, заметки.
 * /3 — фото (физическое копирование файлов).
 * /4 — UI.
 */

/** Открытая временная БД архива + путь к её файлу. */
data class TempDatabaseHandle(
    val db: AppDatabase,
    val file: File
)

// ================================================================
// Участки (/1)
// ================================================================

data class AreaToAdd(
    val theirId: Long,
    val entity: AreaEntity
)

data class AreaPlan(
    val existing: Map<Long, Long>,
    val toAdd: List<AreaToAdd>,
    val duplicatesInMine: List<String>
)

// ================================================================
// Наряды (/1)
// ================================================================

data class OrderToAdd(
    val theirId: Long,
    val entity: OrderEntity
)

data class OrderPlan(
    val existing: Map<Long, Long>,
    val toAdd: List<OrderToAdd>,
    val skippedOrphans: Int
)

// ================================================================
// Пробы (/2)
// ================================================================

/** Решение по конфликту. Пошаговый режим «спросить» — в UI. */
enum class ConflictResolution {
    KEEP_MINE,
    TAKE_THEIRS
}

data class SampleToAdd(
    val theirId: Long,
    val entity: SampleEntity
)

data class SampleConflict(
    val theirId: Long,
    val myId: Long,
    val sampleNumber: String,
    val myEntity: SampleEntity,
    val theirEntity: SampleEntity
)

data class SamplePlan(
    val toAdd: List<SampleToAdd>,
    val conflicts: List<SampleConflict>,
    val skippedOrphans: Int
)

// ================================================================
// Скважины (/2)
// ================================================================

data class OrderWellToAdd(
    val myOrderId: Long,
    val wellNumber: String
)

data class WellPlan(
    val toAdd: List<OrderWellToAdd>
)

// ================================================================
// Заметки (/2)
// ================================================================

data class NoteToAdd(
    val mySampleId: Long,
    val text: String?
)

data class NoteConflict(
    val theirSampleId: Long,
    val mySampleId: Long,
    val myText: String?,
    val theirText: String?
)

data class NotePlan(
    val toAdd: List<NoteToAdd>,
    val conflicts: List<NoteConflict>
)

// ================================================================
// Статистика
// ================================================================

data class MergeStats(
    val areasAdded: Int,
    val areasMatched: Int,
    val ordersAdded: Int,
    val ordersMatched: Int,
    val ordersSkipped: Int,
    val samplesAdded: Int,
    val samplesMatched: Int,
    val samplesSkipped: Int,
    val wellsAdded: Int,
    val notesAdded: Int,
    val notesConflicts: Int
) {
    companion object {
        fun from(
            areaPlan: AreaPlan,
            orderPlan: OrderPlan,
            samplePlan: SamplePlan? = null,
            wellPlan: WellPlan? = null,
            notePlan: NotePlan? = null
        ): MergeStats = MergeStats(
            areasAdded = areaPlan.toAdd.size,
            areasMatched = areaPlan.existing.size,
            ordersAdded = orderPlan.toAdd.size,
            ordersMatched = orderPlan.existing.size,
            ordersSkipped = orderPlan.skippedOrphans,
            samplesAdded = samplePlan?.toAdd?.size ?: 0,
            samplesMatched = samplePlan?.conflicts?.size ?: 0,
            samplesSkipped = samplePlan?.skippedOrphans ?: 0,
            wellsAdded = wellPlan?.toAdd?.size ?: 0,
            notesAdded = notePlan?.toAdd?.size ?: 0,
            notesConflicts = notePlan?.conflicts?.size ?: 0
        )
    }
}