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
// /2: пробы
// ================================================================

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
// /4: сбор данных из архива
// ================================================================

/**
 * FIX 5.9-db-merge-v2/4:
 * Всё содержимое архива, прочитанное из temp-БД.
 * Хранится в wizard-state, чтобы Runner мог пересчитать планы
 * с реальными id после apply-фаз.
 */
data class ArchiveData(
    val areas: List<AreaEntity>,
    val orders: List<OrderEntity>,
    val samples: List<SampleEntity>,
    val wellsByOrderId: Map<Long, List<String>>,
    val notesBySampleId: Map<Long, String?>,
    val imagesBySampleId: Map<Long, List<SampleImageEntity>>
)

/**
 * FIX 5.9-db-merge-v2/4:
 * Полный предпросмотр перед слиянием.
 * Планы — предварительные (без пересчёта id), counts корректные.
 */
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
        val currentIndex: Int
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
    val samplesMatched: Int,
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
            samplesMatched = samplePlan?.conflicts?.size ?: 0,
            samplesSkipped = samplePlan?.skippedOrphans ?: 0,
            wellsAdded = wellPlan?.toAdd?.size ?: 0,
            notesAdded = notePlan?.toAdd?.size ?: 0,
            notesConflicts = notePlan?.conflicts?.size ?: 0,
            photosAdded = photoPlan?.toAdd?.size ?: 0
        )
    }
}