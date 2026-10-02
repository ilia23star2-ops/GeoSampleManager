package com.example.geosamplemanager.data.merge

import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-db-merge-v2/1, /2, /3:
 * Юнит-тесты чистой логики движка слияния.
 */
class MergeEngineTest {

    private fun area(id: Long, name: String): AreaEntity =
        AreaEntity(id = id, areaName = name, createdDate = id * 1000L)

    private fun order(id: Long, areaId: Long, number: String): OrderEntity =
        OrderEntity(
            id = id,
            areaId = areaId,
            orderNumber = number,
            createdDate = id * 1000L
        )

    private fun sample(
        id: Long,
        orderId: Long,
        number: String,
        weight: Double? = null,
        status: String = "normal"
    ): SampleEntity = SampleEntity(
        id = id,
        orderId = orderId,
        serialNumber = 1,
        sampleNumber = number,
        wellNumber = "W1",
        weight = weight,
        status = status
    )

    private fun image(id: Long, sampleId: Long, path: String): SampleImageEntity =
        SampleImageEntity(
            id = id,
            sampleId = sampleId,
            imagePath = path,
            createdDate = id * 1000L
        )

    // ============================================================
    // /1: planAreas
    // ============================================================

    @Test
    fun planAreas_bothEmpty_returnsEmptyPlan() {
        val p = MergeEngine.planAreas(emptyList(), emptyList())
        assertEquals(0, p.existing.size)
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planAreas_matchByName_goesToExisting() {
        val p = MergeEngine.planAreas(
            listOf(area(1, "Поле 1")),
            listOf(area(10, "Поле 1"))
        )
        assertEquals(1, p.existing.size)
        assertEquals(1L, p.existing[10L])
    }

    @Test
    fun planAreas_noMatch_goesToAdd() {
        val p = MergeEngine.planAreas(
            listOf(area(1, "Поле A")),
            listOf(area(10, "Поле B"))
        )
        assertEquals(1, p.toAdd.size)
        assertEquals(10L, p.toAdd[0].theirId)
    }

    @Test
    fun planAreas_duplicatesInMine_picksMinId() {
        val p = MergeEngine.planAreas(
            listOf(area(5, "Поле 1"), area(2, "Поле 1")),
            listOf(area(10, "Поле 1"))
        )
        assertEquals(2L, p.existing[10L])
        assertEquals(1, p.duplicatesInMine.size)
    }

    // ============================================================
    // /1: planOrders
    // ============================================================

    @Test
    fun planOrders_matchByAreaAndNumber_goesToExisting() {
        val p = MergeEngine.planOrders(
            myOrders = listOf(order(1, areaId = 1, number = "42")),
            theirOrders = listOf(order(10, areaId = 100, number = "42")),
            areaIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.existing.size)
        assertEquals(1L, p.existing[10L])
    }

    @Test
    fun planOrders_newAreaFromNewAreaMap_goesToAdd() {
        val p = MergeEngine.planOrders(
            myOrders = emptyList(),
            theirOrders = listOf(order(10, areaId = 100, number = "42")),
            areaIdMap = emptyMap(),
            newlyAddedAreaIds = mapOf(100L to 555L)
        )
        assertEquals(1, p.toAdd.size)
        assertEquals(555L, p.toAdd[0].entity.areaId)
    }

    @Test
    fun planOrders_unknownArea_goesToSkipped() {
        val p = MergeEngine.planOrders(
            myOrders = emptyList(),
            theirOrders = listOf(order(10, areaId = 999, number = "42")),
            areaIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.skippedOrphans)
    }

    // ============================================================
    // /2: planSamples
    // ============================================================

    @Test
    fun planSamples_matchByOrderAndNumber_goesToConflict() {
        val p = MergeEngine.planSamples(
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1")),
            theirSamples = listOf(sample(10, orderId = 100, number = "W1-1")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.conflicts.size)
        assertEquals(1L, p.conflicts[0].myId)
        assertEquals(10L, p.conflicts[0].theirId)
    }

    @Test
    fun planSamples_newSample_goesToAddWithMyOrderId() {
        val p = MergeEngine.planSamples(
            mySamples = emptyList(),
            theirSamples = listOf(sample(10, orderId = 100, number = "W1-1")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.toAdd.size)
        assertEquals(1L, p.toAdd[0].entity.orderId)
        assertEquals(0L, p.toAdd[0].entity.id)
    }

    @Test
    fun planSamples_unknownOrder_goesToSkipped() {
        val p = MergeEngine.planSamples(
            mySamples = emptyList(),
            theirSamples = listOf(sample(10, orderId = 999, number = "W1-1")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.skippedOrphans)
    }

    // ============================================================
    // /2: resolveSample
    // ============================================================

    @Test
    fun resolveSample_keepMine_returnsNull() {
        val result = MergeEngine.resolveSample(
            sample(1, 1, "W1-1", weight = 5.0),
            sample(10, 100, "W1-1", weight = 7.5),
            ConflictResolution.KEEP_MINE
        )
        assertNull(result)
    }

    @Test
    fun resolveSample_takeTheirs_copiesFieldsKeepsIds() {
        val result = MergeEngine.resolveSample(
            sample(1, 1, "W1-1", weight = 5.0, status = "normal"),
            sample(10, 100, "W1-1", weight = 7.5, status = "blank"),
            ConflictResolution.TAKE_THEIRS
        )
        assertNotNull(result)
        assertEquals(1L, result!!.id)
        assertEquals(1L, result.orderId)
        assertEquals("W1-1", result.sampleNumber)
        assertEquals(7.5, result.weight!!, 0.001)
        assertEquals("blank", result.status)
    }

    @Test
    fun resolveSample_takeTheirs_keepsHasNoteAndHasPhoto() {
        val my = sample(1, 1, "W1-1").copy(hasNote = true, hasPhoto = true)
        val their = sample(10, 100, "W1-1").copy(hasNote = false, hasPhoto = false)
        val result = MergeEngine.resolveSample(
            my, their, ConflictResolution.TAKE_THEIRS
        )
        assertTrue(result!!.hasNote)
        assertTrue(result.hasPhoto)
    }

    // ============================================================
    // /2: planWells
    // ============================================================

    @Test
    fun planWells_newWells_added() {
        val p = MergeEngine.planWells(
            myWells = mapOf(1L to listOf("W1")),
            theirWells = mapOf(100L to listOf("W1", "W2", "W3")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(2, p.toAdd.size)
    }

    @Test
    fun planWells_duplicateInArchive_deduped() {
        val p = MergeEngine.planWells(
            myWells = emptyMap(),
            theirWells = mapOf(100L to listOf("W1", "W1", "W2")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(2, p.toAdd.size)
    }

    // ============================================================
    // /2: planNotes
    // ============================================================

    @Test
    fun planNotes_onlyTheirs_goesToAdd() {
        val p = MergeEngine.planNotes(
            myNotes = emptyMap(),
            theirNotes = mapOf(10L to "их текст"),
            sampleIdMap = mapOf(10L to 1L)
        )
        assertEquals(1, p.toAdd.size)
        assertEquals(1L, p.toAdd[0].mySampleId)
    }

    @Test
    fun planNotes_bothNonEmpty_goesToConflict() {
        val p = MergeEngine.planNotes(
            myNotes = mapOf(1L to "мой"),
            theirNotes = mapOf(10L to "их"),
            sampleIdMap = mapOf(10L to 1L)
        )
        assertEquals(1, p.conflicts.size)
    }

    @Test
    fun planNotes_mineEmpty_theirsNonEmpty_goesToAdd() {
        val p = MergeEngine.planNotes(
            myNotes = mapOf(1L to ""),
            theirNotes = mapOf(10L to "их"),
            sampleIdMap = mapOf(10L to 1L)
        )
        assertEquals(1, p.toAdd.size)
        assertEquals(0, p.conflicts.size)
    }

    // ============================================================
    // /3: extractArchivePhotoName
    // ============================================================

    @Test
    fun extractArchivePhotoName_fullPath_returnsName() {
        assertEquals(
            "photo_abc.jpg",
            MergeEngine.extractArchivePhotoName(
                "/data/user/0/app/files/sample_photos/photo_abc.jpg"
            )
        )
    }

    @Test
    fun extractArchivePhotoName_bareName_returnsName() {
        assertEquals(
            "photo_abc.jpg",
            MergeEngine.extractArchivePhotoName("photo_abc.jpg")
        )
    }

    @Test
    fun extractArchivePhotoName_emptyString_returnsNull() {
        assertNull(MergeEngine.extractArchivePhotoName(""))
    }

    @Test
    fun extractArchivePhotoName_blankString_returnsNull() {
        assertNull(MergeEngine.extractArchivePhotoName("   "))
    }

    @Test
    fun extractArchivePhotoName_pathEndingWithSlash_returnsNull() {
        assertNull(MergeEngine.extractArchivePhotoName("/some/path/"))
    }

    // ============================================================
    // /3: planPhotos
    // ============================================================

    @Test
    fun planPhotos_empty_returnsEmptyPlan() {
        val p = MergeEngine.planPhotos(
            theirImages = emptyList(),
            sampleIdMap = emptyMap(),
            conflictSampleIds = emptySet(),
            resolutions = emptyMap()
        )
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planPhotos_newSample_allPhotosAdded() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(
                image(1, sampleId = 10, path = "/x/sample_photos/a.jpg"),
                image(2, sampleId = 10, path = "/x/sample_photos/b.jpg")
            ),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = emptySet(),
            resolutions = emptyMap()
        )
        assertEquals(2, p.toAdd.size)
        assertEquals(100L, p.toAdd[0].mySampleId)
        assertEquals("a.jpg", p.toAdd[0].archiveFileName)
    }

    @Test
    fun planPhotos_conflictKeepMine_photosSkipped() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(
                image(1, sampleId = 10, path = "/x/sample_photos/a.jpg")
            ),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = setOf(10L),
            resolutions = mapOf(10L to ConflictResolution.KEEP_MINE)
        )
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planPhotos_conflictTakeTheirs_photosAdded() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(
                image(1, sampleId = 10, path = "/x/sample_photos/a.jpg")
            ),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = setOf(10L),
            resolutions = mapOf(10L to ConflictResolution.TAKE_THEIRS)
        )
        assertEquals(1, p.toAdd.size)
        assertEquals("a.jpg", p.toAdd[0].archiveFileName)
    }

    @Test
    fun planPhotos_conflictWithoutResolution_defaultsToKeepMine() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(
                image(1, sampleId = 10, path = "/x/sample_photos/a.jpg")
            ),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = setOf(10L),
            resolutions = emptyMap()
        )
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planPhotos_unknownSample_skipped() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(
                image(1, sampleId = 999, path = "/x/sample_photos/a.jpg")
            ),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = emptySet(),
            resolutions = emptyMap()
        )
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planPhotos_invalidPath_skipped() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(
                image(1, sampleId = 10, path = "")
            ),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = emptySet(),
            resolutions = emptyMap()
        )
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planPhotos_mixed_allCases() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(
                // новая проба — 2 фото, оба добавляем
                image(1, sampleId = 10, path = "/x/a.jpg"),
                image(2, sampleId = 10, path = "/x/b.jpg"),
                // конфликт KEEP_MINE — пропускаем
                image(3, sampleId = 11, path = "/x/c.jpg"),
                // конфликт TAKE_THEIRS — добавляем
                image(4, sampleId = 12, path = "/x/d.jpg")
            ),
            sampleIdMap = mapOf(10L to 100L, 11L to 101L, 12L to 102L),
            conflictSampleIds = setOf(11L, 12L),
            resolutions = mapOf(
                11L to ConflictResolution.KEEP_MINE,
                12L to ConflictResolution.TAKE_THEIRS
            )
        )
        assertEquals(3, p.toAdd.size)
    }

    // ============================================================
    // MergeStats
    // ============================================================

    @Test
    fun mergeStats_withPhotoPlan_countsPhotos() {
        val areaPlan = AreaPlan(emptyMap(), emptyList(), emptyList())
        val orderPlan = OrderPlan(emptyMap(), emptyList(), 0)
        val photoPlan = PhotoPlan(
            toAdd = listOf(
                PhotoToAdd(1L, "a.jpg"),
                PhotoToAdd(1L, "b.jpg")
            )
        )
        val s = MergeStats.from(
            areaPlan, orderPlan,
            photoPlan = photoPlan
        )
        assertEquals(2, s.photosAdded)
    }
}