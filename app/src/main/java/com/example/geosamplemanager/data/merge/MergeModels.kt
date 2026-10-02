package com.example.geosamplemanager.data.merge

import com.example.geosamplemanager.data.AppDatabase
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import java.io.File

/**
 * FIX 5.9-db-merge-v2:
 * Модели для движка слияния БД.
 *
 * /5 — умные конфликты: разрешение по полям, массовые стратегии.
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
// /5: поля пробы, по которым может быть расхождение
// ================================================================

/**
 * FIX 5.9-db-merge-v2/5:
 * Идентификатор поля пробы — используется в FieldDiff и
 * FieldResolution. Русская подпись — отдельно (label).
 */
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

/**
 * FIX 5.9-db-merge-v2/5:
 * Одно различающееся поле между моей пробой и пробой из архива.
 *
 * myRaw / theirRaw — сырые значения для применения (Double?, String?, Boolean?).
 * myDisplay / theirDisplay — человекочитаемые строки для UI.
 */
data class FieldDiff(
    val field: SampleField,
    val myRaw: Any?,
    val theirRaw: Any?,
    val myDisplay: String,
    val theirDisplay: String
)

/** Чьё значение выбрано для конкретного поля. */
enum class FieldOwner { MINE, THEIRS }

/**
 * FIX 5.9-db-merge-v2/5:
 * Карта «поле → чьё взять». Пустая = не разрешено ничего.
 */
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

    /**
     * Слить два разрешения: значения из other перекрывают мои.
     * Используется для массовых стратегий.
     */
    fun merge(other: FieldResolution): FieldResolution =
        copy(map = map + other.map)

    companion object {
        val Empty = FieldResolution()

        /** Все поля → чьё-то. */
        fun all(diffs: List<FieldDiff>, owner: FieldOwner): FieldResolution =
            FieldResolution(diffs.associate { it.field to owner })

        /**
         * «Заполнить пустые»: где у меня пусто → theirs;
         * где у меня есть значение → моё.
         */
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
// /2, /5: пробы
// ================================================================

data class SampleToAdd(
    val theirId: Long,
    val entity: SampleEntity
)

/**
 * FIX 5.9-db-merge-v2/5:
 * Конфликт пробы — различия по полям + мои/их entity.
 */
data class SampleConflict(
    val theirId: Long,
    val myId: Long,
    val sampleNumber: String,
    val myEntity: SampleEntity,
    val theirEntity: SampleEntity,
    val fieldDiffs: List<FieldDiff>
)

/**
 * FIX 5.9-db-merge-v2/5:
 * Идентичная проба (все поля совпадают). Не требует решения,
 * но нужна для sampleIdMap (фото/заметки).
 */
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
    /** Все решения разрешены? */
    fun allResolved(resolutions: Map<Long, FieldResolution>): Boolean =
        conflicts.all { c ->
            resolutions[c.theirId]?.isFullyResolved(c.fieldDiffs) == true
        }
}

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
// /4, /5: состояние wizard
// ================================================================

sealed class MergeWizardState {
    data object Idle : MergeWizardState()
    data object Loading : MergeWizardState()
    data class Preview(val preview: MergePreview) : MergeWizardState()

    /**
     * FIX 5.9-db-merge-v2/5:
     * Шаг разбора конфликтов.
     *  - preview: исходные данные;
     *  - resolutions: карта уже принятых решений (their sampleId → FieldResolution).
     */
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