package com.example.geosamplemanager.data.merge

import com.example.geosamplemanager.data.AppDatabase
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import java.io.File

/**
 * FIX 5.9-db-merge-v2/7:
 *  - SampleConflict получил areaName / orderNumber / wellNumber —
 *    чтобы UI мог группировать конфликты по наряду и скважине;
 *  - добавлены ConflictTreeNode / ConflictWellNode — модель дерева;
 *  - MassStrategy — стратегии для массовых действий на группе.
 */

data class TempDatabaseHandle(
    val db: AppDatabase,
    val file: File,
    val photosDir: File
)

// ================================================================
// /1: участки
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
// /1: наряды
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
// /5: поля
// ================================================================

enum class SampleField(val label: String) {
    WEIGHT("Вес"),
    SAMPLE_TYPE("Тип пробы"),
    STATUS("Состояние"),
    CONTROL_WEIGHT("Вес ВК"),
    INTERVAL_FROM("Интервал от"),
    INTERVAL_TO("Интервал до"),
    FOUND("Отмечена"),
    POSTPONED("Отложена"),
    WEIGHT_CONTROL("Вес. контроль"),
    MATERIAL_DESC("Характеристика")
}

data class FieldDiff(
    val field: SampleField,
    val myRaw: Any?,
    val theirRaw: Any?,
    val myDisplay: String,
    val theirDisplay: String
)

enum class FieldOwner { MINE, THEIRS }

data class FieldResolution(
    val map: Map<SampleField, FieldOwner> = emptyMap()
) {
    fun ownerOf(field: SampleField): FieldOwner? = map[field]

    fun with(field: SampleField, owner: FieldOwner): FieldResolution =
        copy(map = map + (field to owner))

    fun without(field: SampleField): FieldResolution =
        copy(map = map - field)

    fun isFullyResolved(diffs: List<FieldDiff>): Boolean =
        diffs.all { map.containsKey(it.field) }

    fun merge(other: FieldResolution): FieldResolution =
        copy(map = map + other.map)

    companion object {
        val Empty = FieldResolution()

        fun all(diffs: List<FieldDiff>, owner: FieldOwner): FieldResolution =
            FieldResolution(diffs.associate { it.field to owner })

        fun fillEmpty(diffs: List<FieldDiff>): FieldResolution {
            val m = mutableMapOf<SampleField, FieldOwner>()
            for (d in diffs) {
                m[d.field] = if (isMyEmpty(d.myRaw)) FieldOwner.THEIRS
                else FieldOwner.MINE
            }
            return FieldResolution(m)
        }

        private fun isMyEmpty(raw: Any?): Boolean = when (raw) {
            null -> true
            is String -> raw.isBlank()
            else -> false
        }
    }
}

// ================================================================
// /5, /7: пробы
// ================================================================

data class SampleToAdd(
    val theirId: Long,
    val entity: SampleEntity
)

/**
 * FIX 5.9-db-merge-v2/7:
 *  - areaName / orderNumber / wellNumber — для группировки в UI.
 *    Заполняются в planSamples (для area/order нужны myOrders/myAreas).
 */
data class SampleConflict(
    val theirId: Long,
    val myId: Long,
    val sampleNumber: String,
    val myEntity: SampleEntity,
    val theirEntity: SampleEntity,
    val fieldDiffs: List<FieldDiff>,
    val areaName: String = "",
    val orderNumber: String = "",
    val wellNumber: String = ""
)

data class SampleMatch(
    val theirId: Long,
    val myId: Long
)

data class SamplePlan(
    val toAdd: List<SampleToAdd>,
    val identical: List<SampleMatch>,
    val conflicts: List<SampleConflict>,
    val skippedOrphans: Int
) {
    fun allResolved(resolutions: Map<Long, FieldResolution>): Boolean =
        conflicts.all { c ->
            resolutions[c.theirId]?.isFullyResolved(c.fieldDiffs) == true
        }
}

// ================================================================
// /7: дерево конфликтов
// ================================================================

data class ConflictWellNode(
    val wellNumber: String,
    val conflicts: List<SampleConflict>
)

data class ConflictTreeNode(
    val areaName: String,
    val orderNumber: String,
    val wells: List<ConflictWellNode>
) {
    /** Все пробы этого наряда — для массовых действий. */
    val allConflicts: List<SampleConflict>
        get() = wells.flatMap { it.conflicts }
}

/** FIX 5.9-db-merge-v2/7: массовая стратегия для группы. */
enum class MassStrategy { ALL_MINE, ALL_THEIRS, FILL_EMPTY }

// ================================================================
// /2: скважины
// ================================================================

data class OrderWellToAdd(
    val myOrderId: Long,
    val wellNumber: String
)

data class WellPlan(
    val toAdd: List<OrderWellToAdd>
)

// ================================================================
// /2: заметки
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
// /3: фото
// ================================================================

data class PhotoToAdd(
    val mySampleId: Long,
    val archiveFileName: String
)

data class PhotoPlan(
    val toAdd: List<PhotoToAdd>
)

// ================================================================
// /4: архив и предпросмотр
// ================================================================

data class ArchiveData(
    val areas: List<AreaEntity>,
    val orders: List<OrderEntity>,
    val samples: List<SampleEntity>,
    val wellsByOrderId: Map<Long, List<String>>,
    val notesBySampleId: Map<Long, String?>,
    val imagesBySampleId: Map<Long, List<SampleImageEntity>>
)

data class MergePreview(
    val fileName: String,
    val archive: ArchiveData,
    val areaPlan: AreaPlan,
    val orderPlan: OrderPlan,
    val samplePlan: SamplePlan,
    val wellPlan: WellPlan,
    val notePlan: NotePlan,
    val photoPlan: PhotoPlan,
    val stats: MergeStats
)

// ================================================================
// /4: состояние wizard
// ================================================================

sealed class MergeWizardState {
    data object Idle : MergeWizardState()
    data object Loading : MergeWizardState()
    data class Preview(val preview: MergePreview) : MergeWizardState()
    data class ConflictStep(
        val preview: MergePreview,
        val resolutions: Map<Long, FieldResolution>
    ) : MergeWizardState()
    data class Running(val message: String) : MergeWizardState()
    data class Done(val stats: MergeStats) : MergeWizardState()
    data class Error(val message: String) : MergeWizardState()
}

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
    val samplesIdentical: Int,
    val samplesConflicts: Int,
    val samplesSkipped: Int,
    val wellsAdded: Int,
    val notesAdded: Int,
    val notesConflicts: Int,
    val photosAdded: Int
) {
    companion object {
        fun from(
            areaPlan: AreaPlan,
            orderPlan: OrderPlan,
            samplePlan: SamplePlan? = null,
            wellPlan: WellPlan? = null,
            notePlan: NotePlan? = null,
            photoPlan: PhotoPlan? = null
        ): MergeStats = MergeStats(
            areasAdded = areaPlan.toAdd.size,
            areasMatched = areaPlan.existing.size,
            ordersAdded = orderPlan.toAdd.size,
            ordersMatched = orderPlan.existing.size,
            ordersSkipped = orderPlan.skippedOrphans,
            samplesAdded = samplePlan?.toAdd?.size ?: 0,
            samplesIdentical = samplePlan?.identical?.size ?: 0,
            samplesConflicts = samplePlan?.conflicts?.size ?: 0,
            samplesSkipped = samplePlan?.skippedOrphans ?: 0,
            wellsAdded = wellPlan?.toAdd?.size ?: 0,
            notesAdded = notePlan?.toAdd?.size ?: 0,
            notesConflicts = notePlan?.conflicts?.size ?: 0,
            photosAdded = photoPlan?.toAdd?.size ?: 0
        )
    }
}