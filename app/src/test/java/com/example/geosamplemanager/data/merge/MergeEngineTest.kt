package com.example.geosamplemanager.data.merge

import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-db-merge-v2/1, /2:
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
    fun planSamples_bothEmpty_returnsEmptyPlan() {
        val p = MergeEngine.planSamples(emptyList(), emptyList(), emptyMap())
        assertEquals(0, p.toAdd.size)
        assertEquals(0, p.conflicts.size)
        assertEquals(0, p.skippedOrphans)
    }

    @Test
    fun planSamples_matchByOrderAndNumber_goesToConflict() {
        val p = MergeEngine.planSamples(
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1")),
            theirSamples = listOf(sample(10, orderId = 100, number = "W1-1")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(0, p.toAdd.size)
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
        assertEquals(10L, p.toAdd[0].theirId)
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

    @Test
    fun planSamples_mixed_conflictAndAdd() {
        val p = MergeEngine.planSamples(
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1")),
            theirSamples = listOf(
                sample(10, orderId = 100, number = "W1-1"),
                sample(11, orderId = 100, number = "W1-2")
            ),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(1, p.conflicts.size)
        assertEquals(1, p.toAdd.size)
    }

    // ============================================================
    // /2: resolveSample
    // ============================================================

    @Test
    fun resolveSample_keepMine_returnsNull() {
        val my = sample(1, 1, "W1-1", weight = 5.0)
        val their = sample(10, 100, "W1-1", weight = 7.5)
        val result = MergeEngine.resolveSample(my, their, ConflictResolution.KEEP_MINE)
        assertNull(result)
    }

    @Test
    fun resolveSample_takeTheirs_copiesFieldsKeepsIds() {
        val my = sample(1, 1, "W1-1", weight = 5.0, status = "normal")
        val their = sample(10, 100, "W1-1", weight = 7.5, status = "blank")
        val result = MergeEngine.resolveSample(
            my, their, ConflictResolution.TAKE_THEIRS
        )
        assertNotNull(result)
        assertEquals(1L, result!!.id)             // мой id сохранён
        assertEquals(1L, result.orderId)          // мой orderId
        assertEquals("W1-1", result.sampleNumber) // ключ
        assertEquals(7.5, result.weight!!, 0.001) // из архива
        assertEquals("blank", result.status)      // из архива
    }

    @Test
    fun resolveSample_takeTheirs_keepsHasNoteAndHasPhotoFromMine() {
        val my = sample(1, 1, "W1-1").copy(hasNote = true, hasPhoto = true)
        val their = sample(10, 100, "W1-1").copy(hasNote = false, hasPhoto = false)
        val result = MergeEngine.resolveSample(
            my, their, ConflictResolution.TAKE_THEIRS
        )
        assertNotNull(result)
        assertTrue(result!!.hasNote)
        assertTrue(result.hasPhoto)
    }

    // ============================================================
    // /2: planWells
    // ============================================================

    @Test
    fun planWells_noWells_emptyPlan() {
        val p = MergeEngine.planWells(emptyMap(), emptyMap(), emptyMap())
        assertEquals(0, p.toAdd.size)
    }

    @Test
    fun planWells_newWells_added() {
        val p = MergeEngine.planWells(
            myWells = mapOf(1L to listOf("W1")),
            theirWells = mapOf(100L to listOf("W1", "W2", "W3")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(2, p.toAdd.size)
        val numbers = p.toAdd.map { it.wellNumber }.toSet()
        assertTrue("W2" in numbers)
        assertTrue("W3" in numbers)
        assertTrue(p.toAdd.all { it.myOrderId == 1L })
    }

    @Test
    fun planWells_unknownOrder_skipped() {
        val p = MergeEngine.planWells(
            myWells = emptyMap(),
            theirWells = mapOf(999L to listOf("W1")),
            orderIdMap = mapOf(100L to 1L)
        )
        assertEquals(0, p.toAdd.size)
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
    fun planNotes_bothEmpty_noNotes() {
        val p = MergeEngine.planNotes(emptyMap(), emptyMap(), emptyMap())
        assertEquals(0, p.toAdd.size)
        assertEquals(0, p.conflicts.size)
    }

    @Test
    fun planNotes_onlyMine_noNotes() {
        val p = MergeEngine.planNotes(
            myNotes = mapOf(1L to "мой текст"),
            theirNotes = emptyMap(),
            sampleIdMap = emptyMap()
        )
        assertEquals(0, p.toAdd.size)
        assertEquals(0, p.conflicts.size)
    }

    @Test
    fun planNotes_onlyTheirs_goesToAdd() {
        val p = MergeEngine.planNotes(
            myNotes = emptyMap(),
            theirNotes = mapOf(10L to "их текст"),
            sampleIdMap = mapOf(10L to 1L)
        )
        assertEquals(1, p.toAdd.size)
        assertEquals(1L, p.toAdd[0].mySampleId)
        assertEquals("их текст", p.toAdd[0].text)
    }

    @Test
    fun planNotes_bothNonEmpty_goesToConflict() {
        val p = MergeEngine.planNotes(
            myNotes = mapOf(1L to "мой"),
            theirNotes = mapOf(10L to "их"),
            sampleIdMap = mapOf(10L to 1L)
        )
        assertEquals(0, p.toAdd.size)
        assertEquals(1, p.conflicts.size)
        assertEquals(1L, p.conflicts[0].mySampleId)
        assertEquals(10L, p.conflicts[0].theirSampleId)
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

    @Test
    fun planNotes_bothBlank_skipped() {
        val p = MergeEngine.planNotes(
            myNotes = mapOf(1L to ""),
            theirNotes = mapOf(10L to "  "),
            sampleIdMap = mapOf(10L to 1L)
        )
        assertEquals(0, p.toAdd.size)
        assertEquals(0, p.conflicts.size)
    }

    @Test
    fun planNotes_unknownSample_skipped() {
        val p = MergeEngine.planNotes(
            myNotes = emptyMap(),
            theirNotes = mapOf(10L to "их"),
            sampleIdMap = emptyMap()
        )
        assertEquals(0, p.toAdd.size)
    }

    // ============================================================
    // MergeStats
    // ============================================================

    @Test
    fun mergeStats_fromFullPlans_countsAll() {
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
            conflicts = listOf(
                SampleConflict(201L, 6L, "S2", sample(6, 1, "S2"), sample(201, 100, "S2"))
            ),
            skippedOrphans = 3
        )
        val wellPlan = WellPlan(
            toAdd = listOf(OrderWellToAdd(1L, "W1"))
        )
        val notePlan = NotePlan(
            toAdd = listOf(NoteToAdd(6L, "txt")),
            conflicts = emptyList()
        )

        val s = MergeStats.from(areaPlan, orderPlan, samplePlan, wellPlan, notePlan)
        assertEquals(1, s.areasAdded)
        assertEquals(1, s.areasMatched)
        assertEquals(0, s.ordersAdded)
        assertEquals(1, s.ordersMatched)
        assertEquals(2, s.ordersSkipped)
        assertEquals(1, s.samplesAdded)
        assertEquals(1, s.samplesMatched)
        assertEquals(3, s.samplesSkipped)
        assertEquals(1, s.wellsAdded)
        assertEquals(1, s.notesAdded)
        assertEquals(0, s.notesConflicts)
    }

    @Test
    fun mergeStats_nullableSubPlans_returnZeros() {
        val areaPlan = AreaPlan(emptyMap(), emptyList(), emptyList())
        val orderPlan = OrderPlan(emptyMap(), emptyList(), 0)
        val s = MergeStats.from(areaPlan, orderPlan)
        assertEquals(0, s.samplesAdded)
        assertEquals(0, s.wellsAdded)
        assertEquals(0, s.notesAdded)
    }
}