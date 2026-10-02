package com.example.geosamplemanager.data.compare

import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-db-compare (fix-2):
 * Юнит-тесты движка построения дерева сравнения.
 */
class CompareEngineTest {

    private fun area(id: Long, name: String): AreaEntity =
        AreaEntity(id = id, areaName = name, createdDate = id * 1000L)

    private fun order(id: Long, areaId: Long, number: String): OrderEntity =
        OrderEntity(
            id = id,
            areaId = areaId,
            orderNumber = number,
            createdDate = id * 1000L
        )

    private fun sample(id: Long, orderId: Long, number: String): SampleEntity =
        SampleEntity(
            id = id,
            orderId = orderId,
            serialNumber = 1,
            sampleNumber = number,
            wellNumber = "W1"
        )

    // ============================================================
    // Пусто
    // ============================================================

    @Test
    fun buildResult_allEmpty_allTreesEmpty() {
        val r = CompareEngine.buildResult(
            fileName = "test.gsmbackup",
            myAreas = emptyList(),
            theirAreas = emptyList(),
            myOrders = emptyList(),
            theirOrders = emptyList(),
            mySamples = emptyList(),
            theirSamples = emptyList(),
            myWellsByOrder = emptyMap(),
            theirWellsByOrder = emptyMap(),
            conflicts = emptyList()
        )
        assertTrue(r.inArchive.isEmpty)
        assertTrue(r.matched.isEmpty)
        assertTrue(r.different.isEmpty)
        assertTrue(r.myOnly.isEmpty)
    }

    // ============================================================
    // inArchive — только в архиве
    // ============================================================

    @Test
    fun buildResult_inArchive_newAreaAndOrder() {
        val myArea = area(1, "A")
        val theirArea = area(10, "A")
        val theirOrder = order(10, areaId = 10, number = "1-26")

        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = listOf(myArea),
            theirAreas = listOf(theirArea),
            myOrders = emptyList(),
            theirOrders = listOf(theirOrder),
            mySamples = emptyList(),
            theirSamples = emptyList(),
            myWellsByOrder = emptyMap(),
            theirWellsByOrder = mapOf(10L to listOf("W1")),
            conflicts = emptyList()
        )
        assertEquals(1, r.inArchive.totalAreas)
        assertEquals(1, r.inArchive.totalOrders)
        assertEquals(1, r.inArchive.totalWells)
        assertEquals("A", r.inArchive.areas[0].areaName)
        assertEquals("1-26", r.inArchive.areas[0].orders[0].orderNumber)
    }

    @Test
    fun buildResult_inArchive_newSampleInExistingOrder() {
        val myArea = area(1, "A")
        val theirArea = area(10, "A")
        val myOrder = order(1, areaId = 1, number = "1-25")
        val theirOrder = order(10, areaId = 10, number = "1-25")

        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = listOf(myArea),
            theirAreas = listOf(theirArea),
            myOrders = listOf(myOrder),
            theirOrders = listOf(theirOrder),
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1")),
            theirSamples = listOf(
                sample(10, orderId = 10, number = "W1-1"),
                sample(11, orderId = 10, number = "W1-2")
            ),
            myWellsByOrder = emptyMap(),
            theirWellsByOrder = emptyMap(),
            conflicts = emptyList()
        )
        assertEquals(1, r.inArchive.totalAreas)
        assertEquals(1, r.inArchive.totalOrders)
        assertEquals(1, r.inArchive.totalSamples)
        assertEquals("W1-2", r.inArchive.areas[0].orders[0].samples[0].sampleNumber)
    }

    @Test
    fun buildResult_inArchive_newWellInExistingOrder() {
        val myArea = area(1, "A")
        val theirArea = area(10, "A")
        val myOrder = order(1, areaId = 1, number = "1-25")
        val theirOrder = order(10, areaId = 10, number = "1-25")

        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = listOf(myArea),
            theirAreas = listOf(theirArea),
            myOrders = listOf(myOrder),
            theirOrders = listOf(theirOrder),
            mySamples = emptyList(),
            theirSamples = emptyList(),
            myWellsByOrder = mapOf(1L to listOf("W1")),
            theirWellsByOrder = mapOf(10L to listOf("W1", "W2", "W3")),
            conflicts = emptyList()
        )
        assertEquals(1, r.inArchive.totalOrders)
        assertEquals(2, r.inArchive.totalWells)
        val wells = r.inArchive.areas[0].orders[0].wells.map { it.wellNumber }.toSet()
        assertTrue("W2" in wells)
        assertTrue("W3" in wells)
    }

    // ============================================================
    // matched — совпадает
    // ============================================================

    @Test
    fun buildResult_matched_sameAreaOrderAndSample() {
        val myArea = area(1, "A")
        val theirArea = area(10, "A")
        val myOrder = order(1, areaId = 1, number = "1-25")
        val theirOrder = order(10, areaId = 10, number = "1-25")

        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = listOf(myArea),
            theirAreas = listOf(theirArea),
            myOrders = listOf(myOrder),
            theirOrders = listOf(theirOrder),
            mySamples = listOf(sample(1, orderId = 1, number = "W1-1")),
            theirSamples = listOf(sample(10, orderId = 10, number = "W1-1")),
            myWellsByOrder = mapOf(1L to listOf("W1")),
            theirWellsByOrder = mapOf(10L to listOf("W1")),
            conflicts = emptyList()
        )
        assertEquals(1, r.matched.totalAreas)
        assertEquals(1, r.matched.totalOrders)
        assertEquals(1, r.matched.totalSamples)
        assertEquals(1, r.matched.totalWells)
        assertTrue(r.inArchive.isEmpty)
        assertTrue(r.myOnly.isEmpty)
    }

    // ============================================================
    // myOnly — только мои
    // ============================================================

    @Test
    fun buildResult_myOnly_areaOrderSample() {
        val myArea = area(1, "A")
        val theirArea = area(10, "A")
        val myOrder = order(1, areaId = 1, number = "1-26")

        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = listOf(myArea),
            theirAreas = listOf(theirArea),
            myOrders = listOf(myOrder),
            theirOrders = emptyList(),
            mySamples = emptyList(),
            theirSamples = emptyList(),
            myWellsByOrder = mapOf(1L to listOf("W1")),
            theirWellsByOrder = emptyMap(),
            conflicts = emptyList()
        )
        assertEquals(1, r.myOnly.totalAreas)
        assertEquals(1, r.myOnly.totalOrders)
        assertEquals(1, r.myOnly.totalWells)
        assertEquals("1-26", r.myOnly.areas[0].orders[0].orderNumber)
    }

    @Test
    fun buildResult_myOnly_areaWithoutTheirArea() {
        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = listOf(area(1, "A"), area(2, "B")),
            theirAreas = listOf(area(10, "A")),
            myOrders = emptyList(),
            theirOrders = emptyList(),
            mySamples = emptyList(),
            theirSamples = emptyList(),
            myWellsByOrder = emptyMap(),
            theirWellsByOrder = emptyMap(),
            conflicts = emptyList()
        )
        // Площадь B — только у меня, но без нарядов она в дереве
        // не появится. Значит пусто.
        assertTrue(r.myOnly.isEmpty)
    }

    // ============================================================
    // different — конфликты
    // ============================================================

    @Test
    fun buildResult_different_fromConflicts() {
        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = emptyList(),
            theirAreas = emptyList(),
            myOrders = emptyList(),
            theirOrders = emptyList(),
            mySamples = emptyList(),
            theirSamples = emptyList(),
            myWellsByOrder = emptyMap(),
            theirWellsByOrder = emptyMap(),
            conflicts = listOf(
                ConflictInfo("A", "1-25", "W1-1", "Вес"),
                ConflictInfo("A", "1-25", "W1-2", "Состояние"),
                ConflictInfo("B", "1-26", "W1-1", "Вес, Состояние")
            )
        )
        assertEquals(2, r.different.totalAreas)
        assertEquals(2, r.different.totalOrders)
        assertEquals(3, r.different.totalSamples)
        assertEquals("A", r.different.areas[0].areaName)
        assertEquals("Вес", r.different.areas[0].orders[0].samples[0].note)
    }

    // ============================================================
    // Комплексный
    // ============================================================

    @Test
    fun buildResult_fullScenario() {
        val myAreaA = area(1, "A")
        val myAreaB = area(2, "B")
        val theirAreaA = area(10, "A")
        val myOrderA = order(1, areaId = 1, number = "1-25")
        val myOrderB = order(2, areaId = 2, number = "1-26")
        val theirOrderA = order(10, areaId = 10, number = "1-25")

        val r = CompareEngine.buildResult(
            fileName = "test",
            myAreas = listOf(myAreaA, myAreaB),
            theirAreas = listOf(theirAreaA),
            myOrders = listOf(myOrderA, myOrderB),
            theirOrders = listOf(theirOrderA),
            mySamples = listOf(
                sample(1, orderId = 1, number = "W1-1"),
                sample(2, orderId = 2, number = "W1-1")
            ),
            theirSamples = listOf(
                sample(10, orderId = 10, number = "W1-1"),
                sample(11, orderId = 10, number = "W1-2")
            ),
            myWellsByOrder = mapOf(
                1L to listOf("W1"),
                2L to listOf("W1")
            ),
            theirWellsByOrder = mapOf(
                10L to listOf("W1", "W2")
            ),
            conflicts = listOf(
                ConflictInfo("A", "1-25", "W1-1", "Вес")
            )
        )

        // inArchive: наряд "1-25" в A существует; W1-2 проба новая; W2 скважина новая.
        assertEquals(1, r.inArchive.totalOrders)
        assertEquals(1, r.inArchive.totalSamples)
        assertEquals(1, r.inArchive.totalWells)

        // matched: проба W1-1, скважина W1.
        assertEquals(1, r.matched.totalSamples)
        assertEquals(1, r.matched.totalWells)

        // myOnly: участок B, наряд 1-26, проба W1-1 в нём, скважина W1.
        assertEquals(1, r.myOnly.totalAreas)
        assertEquals("B", r.myOnly.areas[0].areaName)
        assertEquals(1, r.myOnly.totalOrders)
        assertEquals(1, r.myOnly.totalSamples)
        assertEquals(1, r.myOnly.totalWells)

        // different: одна проба с полем "Вес".
        assertEquals(1, r.different.totalSamples)
        assertEquals("Вес", r.different.areas[0].orders[0].samples[0].note)
    }
}