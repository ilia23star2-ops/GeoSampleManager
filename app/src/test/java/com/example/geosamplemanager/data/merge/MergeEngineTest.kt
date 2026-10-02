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
 * FIX 5.9-db-merge-v2/1, /2, /3, /5, /6, /7:
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
    // /5, /6: diffFields
    // ============================================================

    @Test
    fun diffFields_foundMyTrueTheirFalse_noDiff() {
        val my = sample(1, 1, "W1-1").copy(found = true)
        val their = sample(2, 100, "W1-1").copy(found = false)
        assertTrue(MergeEngine.diffFields(my, their).none { it.field == SampleField.FOUND })
    }

    @Test
    fun diffFields_foundMyFalseTheirTrue_diffPresent() {
        val my = sample(1, 1, "W1-1").copy(found = false)
        val their = sample(2, 100, "W1-1").copy(found = true)
        assertEquals(1, MergeEngine.diffFields(my, their).count { it.field == SampleField.FOUND })
    }

    @Test
    fun diffFields_foundBothFalse_noDiff() {
        val my = sample(1, 1, "W1-1").copy(found = false)
        val their = sample(2, 100, "W1-1").copy(found = false)
        assertTrue(MergeEngine.diffFields(my, their).none { it.field == SampleField.FOUND })
    }

    @Test
    fun diffFields_foundBothTrue_noDiff() {
        val my = sample(1, 1, "W1-1").copy(found = true)
        val their = sample(2, 100, "W1-1").copy(found = true)
        assertTrue(MergeEngine.diffFields(my, their).none { it.field == SampleField.FOUND })
    }

    @Test
    fun diffFields_postponedMyTrueTheirFalse_noDiff() {
        val my = sample(1, 1, "W1-1").copy(postponed = true)
        val their = sample(2, 100, "W1-1").copy(postponed = false)
        assertTrue(MergeEngine.diffFields(my, their).none { it.field == SampleField.POSTPONED })
    }

    @Test
    fun diffFields_postponedMyFalseTheirTrue_diffPresent() {
        val my = sample(1, 1, "W1-1").copy(postponed = false)
        val their = sample(2, 100, "W1-1").copy(postponed = true)
        assertEquals(1, MergeEngine.diffFields(my, their).count { it.field == SampleField.POSTPONED })
    }

    @Test
    fun diffFields_weightControlMyTrueTheirFalse_noDiff() {
        val my = sample(1, 1, "W1-1").copy(weightControl = true)
        val their = sample(2, 100, "W1-1").copy(weightControl = false)
        assertTrue(MergeEngine.diffFields(my, their).none { it.field == SampleField.WEIGHT_CONTROL })
    }

    @Test
    fun diffFields_weightControlMyFalseTheirTrue_diffPresent() {
        val my = sample(1, 1, "W1-1").copy(weightControl = false)
        val their = sample(2, 100, "W1-1").copy(weightControl = true)
        assertEquals(1, MergeEngine.diffFields(my, their).count { it.field == SampleField.WEIGHT_CONTROL })
    }

    @Test
    fun diffFields_identical_returnsEmpty() {
        val a = sample(1, 1, "W1-1")
        val b = sample(2, 100, "W1-1")
        assertTrue(MergeEngine.diffFields(a, b).isEmpty())
    }

    @Test
    fun diffFields_onlyWeight_returnsOneDiff() {
        val a = sample(1, 1, "W1-1").copy(weight = 5.0)
        val b = sample(2, 100, "W1-1").copy(weight = 7.5)
        val diffs = MergeEngine.diffFields(a, b)
        assertEquals(1, diffs.size)
        assertEquals(SampleField.WEIGHT, diffs[0].field)
    }

    @Test
    fun diffFields_weightAndStatus_returnsTwoDiffs() {
        val a = sample(1, 1, "W1-1").copy(weight = 5.0, status = "normal")
        val b = sample(2, 100, "W1-1").copy(weight = 7.5, status = "blank")
        assertEquals(2, MergeEngine.diffFields(a, b).size)
    }

    @Test
    fun diffFields_statusHumanized() {
        val a = sample(1, 1, "W1-1").copy(status = "normal")
        val b = sample(2, 100, "W1-1").copy(status = "blank")
        val d = MergeEngine.diffFields(a, b).first()
        assertEquals("Обычная", d.myDisplay)
        assertEquals("Холостая", d.theirDisplay)
    }

    @Test
    fun diffFields_sampleTypeHumanized() {
        val a = sample(1, 1, "W1-1").copy(sampleType = "auger")
        val b = sample(2, 100, "W1-1").copy(sampleType = "channel")
        val d = MergeEngine.diffFields(a, b).first()
        assertEquals("Шнековая", d.myDisplay)
        assertEquals("Бороздовая", d.theirDisplay)
    }

    // ============================================================
    // /5: planSamples
    // ============================================================

    @Test
    fun planSamples_identicalSample_goesToIdentical() {
        val p = MergeEngine.planSamples(
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1")),
            theirSamples = listOf(sample(10, orderId = 100, number = "W1-1")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.identical.size)
        assertEquals(0, p.conflicts.size)
    }

    @Test
    fun planSamples_differentWeight_goesToConflict() {
        val p = MergeEngine.planSamples(
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1").copy(weight = 5.0)),
            theirSamples = listOf(sample(10, orderId = 100, number = "W1-1").copy(weight = 7.5)),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.conflicts.size)
    }

    // ============================================================
    // /7: planSamples — заполнение areaName / orderNumber / wellNumber
    // ============================================================

    @Test
    fun planSamples_fillsAreaNameAndOrderNumberFromMyData() {
        val myOrder = order(1, areaId = 5, number = "1-25")
        val myArea = area(5, "Актайский")
        val p = MergeEngine.planSamples(
            mySamples = listOf(
                sample(1, orderId = 1, number = "W1-1")
                    .copy(weight = 5.0, wellNumber = "W1")
            ),
            theirSamples = listOf(
                sample(10, orderId = 100, number = "W1-1").copy(weight = 7.5)
            ),
            orderIdMap = mapOf(100L to 1L),
            myOrders = listOf(myOrder),
            myAreas = listOf(myArea)
        )
        assertEquals(1, p.conflicts.size)
        val c = p.conflicts[0]
        assertEquals("Актайский", c.areaName)
        assertEquals("1-25", c.orderNumber)
        assertEquals("W1", c.wellNumber)
    }

    @Test
    fun planSamples_noOrders_fillsEmptyStrings() {
        val p = MergeEngine.planSamples(
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1").copy(weight = 5.0)),
            theirSamples = listOf(sample(10, orderId = 100, number = "W1-1").copy(weight = 7.5)),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.conflicts.size)
        assertEquals("", p.conflicts[0].areaName)
        assertEquals("", p.conflicts[0].orderNumber)
    }

    // ============================================================
    // /7: buildConflictTree
    // ============================================================

    @Test
    fun buildConflictTree_empty_returnsEmpty() {
        assertTrue(MergeEngine.buildConflictTree(emptyList()).isEmpty())
    }

    @Test
    fun buildConflictTree_singleConflict_singleNode() {
        val c = SampleConflict(
            theirId = 10L, myId = 1L, sampleNumber = "W1-1",
            myEntity = sample(1, 1, "W1-1"), theirEntity = sample(10, 100, "W1-1"),
            fieldDiffs = emptyList(),
            areaName = "A", orderNumber = "1-25", wellNumber = "W1"
        )
        val tree = MergeEngine.buildConflictTree(listOf(c))
        assertEquals(1, tree.size)
        assertEquals("A", tree[0].areaName)
        assertEquals("1-25", tree[0].orderNumber)
        assertEquals(1, tree[0].wells.size)
        assertEquals("W1", tree[0].wells[0].wellNumber)
    }

    @Test
    fun buildConflictTree_twoSamplesSameWellOneNode() {
        val c1 = SampleConflict(10L, 1L, "W1-1",
            sample(1, 1, "W1-1"), sample(10, 100, "W1-1"),
            emptyList(), "A", "1-25", "W1")
        val c2 = SampleConflict(11L, 2L, "W1-2",
            sample(2, 1, "W1-2"), sample(11, 100, "W1-2"),
            emptyList(), "A", "1-25", "W1")
        val tree = MergeEngine.buildConflictTree(listOf(c1, c2))
        assertEquals(1, tree.size)
        assertEquals(1, tree[0].wells.size)
        assertEquals(2, tree[0].wells[0].conflicts.size)
    }

    @Test
    fun buildConflictTree_twoWells_twoNodes() {
        val c1 = SampleConflict(10L, 1L, "W1-1",
            sample(1, 1, "W1-1"), sample(10, 100, "W1-1"),
            emptyList(), "A", "1-25", "W1")
        val c2 = SampleConflict(11L, 2L, "W2-1",
            sample(2, 1, "W2-1"), sample(11, 100, "W2-1"),
            emptyList(), "A", "1-25", "W2")
        val tree = MergeEngine.buildConflictTree(listOf(c1, c2))
        assertEquals(1, tree.size)
        assertEquals(2, tree[0].wells.size)
    }

    @Test
    fun buildConflictTree_twoOrders_twoNodes() {
        val c1 = SampleConflict(10L, 1L, "W1-1",
            sample(1, 1, "W1-1"), sample(10, 100, "W1-1"),
            emptyList(), "A", "1-25", "W1")
        val c2 = SampleConflict(11L, 2L, "W1-1",
            sample(2, 2, "W1-1"), sample(11, 200, "W1-1"),
            emptyList(), "A", "1-26", "W1")
        val tree = MergeEngine.buildConflictTree(listOf(c1, c2))
        assertEquals(2, tree.size)
    }

    @Test
    fun buildConflictTree_twoAreas_twoNodes() {
        val c1 = SampleConflict(10L, 1L, "W1-1",
            sample(1, 1, "W1-1"), sample(10, 100, "W1-1"),
            emptyList(), "A", "1-25", "W1")
        val c2 = SampleConflict(11L, 2L, "W1-1",
            sample(2, 2, "W1-1"), sample(11, 200, "W1-1"),
            emptyList(), "B", "1-25", "W1")
        val tree = MergeEngine.buildConflictTree(listOf(c1, c2))
        assertEquals(2, tree.size)
    }

    @Test
    fun buildConflictTree_allConflictsOfNode_combined() {
        val c1 = SampleConflict(10L, 1L, "W1-1",
            sample(1, 1, "W1-1"), sample(10, 100, "W1-1"),
            emptyList(), "A", "1-25", "W1")
        val c2 = SampleConflict(11L, 2L, "W2-1",
            sample(2, 1, "W2-1"), sample(11, 100, "W2-1"),
            emptyList(), "A", "1-25", "W2")
        val tree = MergeEngine.buildConflictTree(listOf(c1, c2))
        assertEquals(2, tree[0].allConflicts.size)
    }

    // ============================================================
    // /5: resolveSample
    // ============================================================

    @Test
    fun resolveSample_allMine_returnsNull() {
        val my = sample(1, 1, "W1-1").copy(weight = 5.0)
        val their = sample(2, 100, "W1-1").copy(weight = 7.5)
        val diffs = MergeEngine.diffFields(my, their)
        val r = FieldResolution.all(diffs, FieldOwner.MINE)
        assertNull(MergeEngine.resolveSample(my, their, r))
    }

    @Test
    fun resolveSample_allTheirs_copiesAllFields() {
        val my = sample(1, 1, "W1-1").copy(weight = 5.0, status = "normal")
        val their = sample(2, 100, "W1-1").copy(weight = 7.5, status = "blank")
        val diffs = MergeEngine.diffFields(my, their)
        val r = FieldResolution.all(diffs, FieldOwner.THEIRS)
        val result = MergeEngine.resolveSample(my, their, r)
        assertNotNull(result)
        assertEquals(7.5, result!!.weight!!, 0.001)
        assertEquals("blank", result.status)
    }

    @Test
    fun resolveSample_mixed_weightFromTheirsStatusMine() {
        val my = sample(1, 1, "W1-1").copy(weight = 5.0, status = "normal")
        val their = sample(2, 100, "W1-1").copy(weight = 7.5, status = "blank")
        val r = FieldResolution(
            mapOf(
                SampleField.WEIGHT to FieldOwner.THEIRS,
                SampleField.STATUS to FieldOwner.MINE
            )
        )
        val result = MergeEngine.resolveSample(my, their, r)
        assertNotNull(result)
        assertEquals(7.5, result!!.weight!!, 0.001)
        assertEquals("normal", result.status)
    }

    @Test
    fun resolveSample_fillEmpty_keepsMyFilledTakesTheirEmpty() {
        val my = sample(1, 1, "W1-1").copy(weight = null, status = "normal")
        val their = sample(2, 100, "W1-1").copy(weight = 7.5, status = "blank")
        val diffs = MergeEngine.diffFields(my, their)
        val r = FieldResolution.fillEmpty(diffs)
        val result = MergeEngine.resolveSample(my, their, r)
        assertNotNull(result)
        assertEquals(7.5, result!!.weight!!, 0.001)
        assertEquals("normal", result.status)
    }

    // ============================================================
    // /5: displayFor
    // ============================================================

    @Test
    fun displayFor_statusBlank_russian() {
        assertEquals("Холостая", MergeEngine.displayFor(SampleField.STATUS, "blank"))
    }

    @Test
    fun displayFor_statusControl_russian() {
        assertEquals("Вес. контроль", MergeEngine.displayFor(SampleField.STATUS, "control"))
    }

    @Test
    fun displayFor_sampleTypeChannel_russian() {
        assertEquals("Бороздовая", MergeEngine.displayFor(SampleField.SAMPLE_TYPE, "channel"))
    }

    @Test
    fun displayFor_sampleTypeCobra_russian() {
        assertEquals("Кобра", MergeEngine.displayFor(SampleField.SAMPLE_TYPE, "cobra"))
    }

    @Test
    fun displayFor_foundTrue_yes() {
        assertEquals("Да", MergeEngine.displayFor(SampleField.FOUND, true))
    }

    @Test
    fun displayFor_foundFalse_no() {
        assertEquals("Нет", MergeEngine.displayFor(SampleField.FOUND, false))
    }

    @Test
    fun displayFor_null_dash() {
        assertEquals("—", MergeEngine.displayFor(SampleField.MATERIAL_DESC, null))
    }

    @Test
    fun displayFor_blankString_dash() {
        assertEquals("—", MergeEngine.displayFor(SampleField.MATERIAL_DESC, "  "))
    }

    // ============================================================
    // /5: FieldResolution
    // ============================================================

    @Test
    fun fieldResolution_isFullyResolved_empty_false() {
        val my = sample(1, 1, "W1-1").copy(weight = 5.0)
        val their = sample(2, 100, "W1-1").copy(weight = 7.5)
        val diffs = MergeEngine.diffFields(my, their)
        assertTrue(!FieldResolution.Empty.isFullyResolved(diffs))
    }

    @Test
    fun fieldResolution_isFullyResolved_allSet_true() {
        val my = sample(1, 1, "W1-1").copy(weight = 5.0)
        val their = sample(2, 100, "W1-1").copy(weight = 7.5)
        val diffs = MergeEngine.diffFields(my, their)
        val r = FieldResolution.all(diffs, FieldOwner.MINE)
        assertTrue(r.isFullyResolved(diffs))
    }

    @Test
    fun fieldResolution_fillEmpty_myEmpty_takesTheirs() {
        val my = sample(1, 1, "W1-1").copy(weight = null)
        val their = sample(2, 100, "W1-1").copy(weight = 5.0)
        val diffs = MergeEngine.diffFields(my, their)
        val r = FieldResolution.fillEmpty(diffs)
        assertEquals(FieldOwner.THEIRS, r.ownerOf(SampleField.WEIGHT))
    }

    @Test
    fun fieldResolution_fillEmpty_myFilled_keepsMine() {
        val my = sample(1, 1, "W1-1").copy(weight = 5.0)
        val their = sample(2, 100, "W1-1").copy(weight = 7.5)
        val diffs = MergeEngine.diffFields(my, their)
        val r = FieldResolution.fillEmpty(diffs)
        assertEquals(FieldOwner.MINE, r.ownerOf(SampleField.WEIGHT))
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
    }

    @Test
    fun planPhotos_conflictKeepMine_noPhotos() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(image(1, 10, "/x/sample_photos/a.jpg")),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = setOf(10L),
            resolutions = mapOf(
                10L to FieldResolution(
                    mapOf(SampleField.WEIGHT to FieldOwner.MINE)
                )
            )
        )
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planPhotos_conflictAnyTheirs_photosAdded() {
        val p = MergeEngine.planPhotos(
            theirImages = listOf(image(1, 10, "/x/sample_photos/a.jpg")),
            sampleIdMap = mapOf(10L to 100L),
            conflictSampleIds = setOf(10L),
            resolutions = mapOf(
                10L to FieldResolution(
                    mapOf(SampleField.WEIGHT to FieldOwner.THEIRS)
                )
            )
        )
        assertEquals(1, p.toAdd.size)
    }

    // ============================================================
    // MergeStats
    // ============================================================

    @Test
    fun mergeStats_fromPlans_countsCorrectly() {
        val areaPlan = AreaPlan(
            existing = mapOf(10L to 1L),
            toAdd = listOf(AreaToAdd(20L, area(0, "A"))),
            duplicatesInMine = emptyList()
        )
        val orderPlan = OrderPlan(
            existing = mapOf(100L to 5L),
            toAdd = emptyList(),
            skippedOrphans = 2
        )
        val samplePlan = SamplePlan(
            toAdd = listOf(SampleToAdd(200L, sample(0, 1, "S1"))),
            identical = listOf(SampleMatch(201L, 6L)),
            conflicts = listOf(
                SampleConflict(
                    theirId = 202L,
                    myId = 7L,
                    sampleNumber = "S2",
                    myEntity = sample(7, 1, "S2"),
                    theirEntity = sample(202, 100, "S2"),
                    fieldDiffs = emptyList()
                )
            ),
            skippedOrphans = 3
        )
        val wellPlan = WellPlan(toAdd = listOf(OrderWellToAdd(1L, "W1")))
        val notePlan = NotePlan(
            toAdd = listOf(NoteToAdd(6L, "txt")),
            conflicts = emptyList()
        )
        val photoPlan = PhotoPlan(toAdd = listOf(PhotoToAdd(1L, "a.jpg")))

        val s = MergeStats.from(areaPlan, orderPlan, samplePlan, wellPlan, notePlan, photoPlan)
        assertEquals(1, s.areasAdded)
        assertEquals(1, s.areasMatched)
        assertEquals(0, s.ordersAdded)
        assertEquals(1, s.ordersMatched)
        assertEquals(2, s.ordersSkipped)
        assertEquals(1, s.samplesAdded)
        assertEquals(1, s.samplesIdentical)
        assertEquals(1, s.samplesConflicts)
        assertEquals(3, s.samplesSkipped)
        assertEquals(1, s.wellsAdded)
        assertEquals(1, s.notesAdded)
        assertEquals(0, s.notesConflicts)
        assertEquals(1, s.photosAdded)
    }

    @Test
    fun mergeStats_nullableSubPlans_returnZeros() {
        val areaPlan = AreaPlan(emptyMap(), emptyList(), emptyList())
        val orderPlan = OrderPlan(emptyMap(), emptyList(), 0)
        val s = MergeStats.from(areaPlan, orderPlan)
        assertEquals(0, s.samplesAdded)
        assertEquals(0, s.samplesIdentical)
        assertEquals(0, s.samplesConflicts)
        assertEquals(0, s.wellsAdded)
        assertEquals(0, s.notesAdded)
        assertEquals(0, s.photosAdded)
    }

    // ============================================================
    // SamplePlan.allResolved
    // ============================================================

    @Test
    fun samplePlan_allResolved_emptyConflicts_true() {
        val plan = SamplePlan(
            toAdd = emptyList(),
            identical = emptyList(),
            conflicts = emptyList(),
            skippedOrphans = 0
        )
        assertTrue(plan.allResolved(emptyMap()))
    }

    @Test
    fun samplePlan_allResolved_partially_false() {
        val my = sample(1, 1, "W1-1").copy(weight = 5.0, status = "normal")
        val their = sample(2, 100, "W1-1").copy(weight = 7.5, status = "blank")
        val c = SampleConflict(
            theirId = 2L,
            myId = 1L,
            sampleNumber = "W1-1",
            myEntity = my,
            theirEntity = their,
            fieldDiffs = MergeEngine.diffFields(my, their)
        )
        val plan = SamplePlan(
            toAdd = emptyList(),
            identical = emptyList(),
            conflicts = listOf(c),
            skippedOrphans = 0
        )
        val partial = mapOf(
            2L to FieldResolution(mapOf(SampleField.WEIGHT to FieldOwner.MINE))
        )
        assertTrue(!plan.allResolved(partial))
    }
}