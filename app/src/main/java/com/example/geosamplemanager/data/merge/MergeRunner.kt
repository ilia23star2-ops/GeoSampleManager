package com.example.geosamplemanager.data.merge

import android.content.Context
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * FIX 5.9-db-merge-v2/7:
 * Оркестратор. planSamples получает myOrders и myAreas, чтобы
 * SampleConflict был заполнен orderNumber/areaName.
 */
object MergeRunner {

    suspend fun readArchiveData(
        tempDb: TempDatabaseHandle
    ): ArchiveData = withContext(Dispatchers.IO) {
        val db = tempDb.db
        val areas = db.areaDao().getAreasList()
        val orders = db.orderDao().getAllOrders()
        val samples = db.sampleDao().getAllSamplesFlow().first()

        val wells = HashMap<Long, List<String>>()
        for (o in orders) wells[o.id] = db.orderWellDao().getWellsForOrder(o.id)

        val notes = HashMap<Long, String?>()
        for (s in samples) notes[s.id] = db.sampleNoteDao().getNote(s.id)?.noteText

        val images = HashMap<Long, List<SampleImageEntity>>()
        for (s in samples) images[s.id] = db.sampleImageDao().getPhotosForSample(s.id)

        ArchiveData(areas, orders, samples, wells, notes, images)
    }

    suspend fun run(
        context: Context,
        repo: DatabaseRepository,
        preview: MergePreview,
        archivePhotosDir: File,
        resolutions: Map<Long, FieldResolution>,
        onProgress: suspend (String) -> Unit
    ): MergeStats = withContext(Dispatchers.IO) {
        val archive = preview.archive

        onProgress("Сливаем участки…")
        val areaIdMap = MergeEngine.applyAreaPlan(repo, preview.areaPlan)
        val newlyAreaIds = preview.areaPlan.toAdd
            .mapNotNull { add -> areaIdMap[add.theirId]?.let { add.theirId to it } }
            .toMap()

        onProgress("Сливаем наряды…")
        val myOrders = repo.getAllOrders()
        val myAreas = repo.getAreas()
        val actualOrderPlan = MergeEngine.planOrders(
            myOrders = myOrders,
            theirOrders = archive.orders,
            areaIdMap = preview.areaPlan.existing,
            newlyAddedAreaIds = newlyAreaIds
        )
        val orderIdMap = MergeEngine.applyOrderPlan(repo, actualOrderPlan)
        val newlyOrderIds = actualOrderPlan.toAdd
            .mapNotNull { add -> orderIdMap[add.theirId]?.let { add.theirId to it } }
            .toMap()

        onProgress("Сливаем пробы…")
        val mySamples = collectMySamples(repo, orderIdMap, newlyOrderIds)
        val actualSamplePlan = MergeEngine.planSamples(
            mySamples = mySamples,
            theirSamples = archive.samples,
            orderIdMap = preview.orderPlan.existing,
            newlyAddedOrderIds = newlyOrderIds,
            myOrders = myOrders,
            myAreas = myAreas
        )
        val sampleIdMap = MergeEngine.applySamplePlan(
            repo = repo,
            plan = actualSamplePlan,
            resolutions = resolutions
        )

        onProgress("Сливаем скважины…")
        val myWells = HashMap<Long, List<String>>()
        for (myOrderId in orderIdMap.values) {
            myWells[myOrderId] = repo.getWellsForOrder(myOrderId)
        }
        val actualWellPlan = MergeEngine.planWells(
            myWells = myWells,
            theirWells = archive.wellsByOrderId,
            orderIdMap = preview.orderPlan.existing,
            newlyAddedOrderIds = newlyOrderIds
        )
        MergeEngine.applyWellPlan(repo, actualWellPlan)

        onProgress("Сливаем заметки…")
        val myNotes = HashMap<Long, String?>()
        for (mySampleId in sampleIdMap.values) {
            myNotes[mySampleId] = repo.getNote(mySampleId)?.noteText
        }
        val actualNotePlan = MergeEngine.planNotes(
            myNotes = myNotes,
            theirNotes = archive.notesBySampleId,
            sampleIdMap = sampleIdMap
        )
        MergeEngine.applyNotePlan(repo, actualNotePlan, resolutions)

        onProgress("Копируем фото…")
        val conflictSampleIds = actualSamplePlan.conflicts.map { it.theirId }.toHashSet()
        val theirImages = archive.imagesBySampleId.values.flatten()
        val actualPhotoPlan = MergeEngine.planPhotos(
            theirImages = theirImages,
            sampleIdMap = sampleIdMap,
            conflictSampleIds = conflictSampleIds,
            resolutions = resolutions
        )
        MergeEngine.applyPhotoPlan(
            context = context,
            repo = repo,
            plan = actualPhotoPlan,
            archivePhotosDir = archivePhotosDir
        )

        onProgress("Обновляем флаги…")
        for (mySampleId in sampleIdMap.values) {
            repo.syncHasNoteAndPhoto(mySampleId)
        }

        MergeStats.from(
            areaPlan = preview.areaPlan,
            orderPlan = actualOrderPlan,
            samplePlan = actualSamplePlan,
            wellPlan = actualWellPlan,
            notePlan = actualNotePlan,
            photoPlan = actualPhotoPlan
        )
    }

    private suspend fun collectMySamples(
        repo: DatabaseRepository,
        orderIdMap: Map<Long, Long>,
        newlyOrderIds: Map<Long, Long>
    ): List<SampleEntity> {
        val ids = (orderIdMap.values + newlyOrderIds.values).toSet().toList()
        if (ids.isEmpty()) return emptyList()
        return repo.getSamplesForOrders(ids)
    }
}