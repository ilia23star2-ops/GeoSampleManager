package com.example.geosamplemanager.data.diagnostics

import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FIX 5.9-db-diagnostics:
 * Тесты движка диагностики БД. Все входные данные — in-memory,
 * проверка файлов — через лямбду-предикат (не трогаем реальную ФС).
 */
class DbDiagnosticsEngineTest {

    // ============================================================
    // Helpers
    // ============================================================

    private fun order(
        id: Long = 1L,
        areaId: Long = 1L,
        number: String = "27"
    ) = OrderEntity(
        id = id,
        areaId = areaId,
        orderNumber = number,
        createdDate = 0L
    )

    private fun sample(
        id: Long = 1L,
        orderId: Long = 1L,
        number: String = "01",
        well: String = "1524",
        hasPhoto: Boolean = false
    ) = SampleEntity(
        id = id,
        orderId = orderId,
        serialNumber = 1,
        sampleNumber = number,
        wellNumber = well,
        workings = null,
        intervalFrom = null,
        intervalTo = null,
        weight = null,
        controlWeight = null,
        actualWeight = null,
        sampleType = "auger",
        status = "normal",
        reservedType = null,
        materialDesc = null,
        found = false,
        weightControl = false,
        postponed = false,
        hasNote = false,
        hasPhoto = hasPhoto
    )

    private fun image(
        id: Long = 1L,
        sampleId: Long = 1L,
        path: String = "/photos/a.jpg"
    ) = SampleImageEntity(
        id = id,
        sampleId = sampleId,
        imagePath = path,
        createdDate = 0L
    )

    private val noFiles: (String) -> Boolean = { false }
    private val allFiles: (String) -> Boolean = { true }

    private fun call(
        orphanOrders: List<OrderEntity> = emptyList(),
        orphanSamples: List<SampleEntity> = emptyList(),
        allSamples: List<SampleEntity> = emptyList(),
        allImages: List<SampleImageEntity> = emptyList(),
        fileExists: (String) -> Boolean = noFiles
    ) = DbDiagnosticsEngine.detect(
        orphanOrders = orphanOrders,
        orphanSamples = orphanSamples,
        allSamples = allSamples,
        allImages = allImages,
        fileExists = fileExists
    )

    // ============================================================
    // Пустые входы
    // ============================================================

    @Test
    fun emptyDb_returnsEmptyList() {
        assertTrue(call().isEmpty())
    }

    @Test
    fun allClean_returnsEmptyList() {
        val s = sample(id = 1L, hasPhoto = true)
        val img = image(id = 1L, sampleId = 1L, path = "/photos/a.jpg")
        val result = call(
            allSamples = listOf(s),
            allImages = listOf(img),
            fileExists = allFiles
        )
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // 1. Наряды без участка
    // ============================================================

    @Test
    fun oneOrphanOrder_detected() {
        val result = call(orphanOrders = listOf(order(id = 5L, number = "27")))
        assertEquals(1, result.size)
        val issue = result[0] as DbIssue.OrphanOrder
        assertEquals(5L, issue.orderId)
        assertEquals("27", issue.orderNumber)
    }

    @Test
    fun twoOrphanOrders_bothDetected() {
        val result = call(
            orphanOrders = listOf(
                order(id = 1L, number = "27"),
                order(id = 2L, number = "28")
            )
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it is DbIssue.OrphanOrder })
    }

    // ============================================================
    // 2. Пробы без наряда
    // ============================================================

    @Test
    fun oneOrphanSample_detected() {
        val result = call(orphanSamples = listOf(sample(id = 7L, number = "05")))
        assertEquals(1, result.size)
        val issue = result[0] as DbIssue.OrphanSample
        assertEquals(7L, issue.sampleId)
        assertEquals("05", issue.sampleNumber)
    }

    // ============================================================
    // 3. Битые ссылки на фото
    // ============================================================

    @Test
    fun brokenPhotoLink_detected() {
        val s = sample(id = 1L, number = "01", hasPhoto = true)
        val img = image(id = 10L, sampleId = 1L, path = "/photos/missing.jpg")
        val result = call(
            allSamples = listOf(s),
            allImages = listOf(img),
            fileExists = noFiles
        )
        assertEquals(1, result.size)
        val issue = result[0] as DbIssue.BrokenPhotoLink
        assertEquals(10L, issue.imageId)
        assertEquals(1L, issue.sampleId)
        assertEquals("01", issue.sampleNumber)
        assertEquals("/photos/missing.jpg", issue.imagePath)
    }

    @Test
    fun twoBrokenLinksOnSameSample_bothDetected() {
        val s = sample(id = 1L)
        val result = call(
            allSamples = listOf(s),
            allImages = listOf(
                image(id = 10L, sampleId = 1L, path = "/a.jpg"),
                image(id = 11L, sampleId = 1L, path = "/b.jpg")
            ),
            fileExists = noFiles
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it is DbIssue.BrokenPhotoLink })
    }

    // ============================================================
    // 4. Флаг has_photo = false, но фото есть
    // ============================================================

    @Test
    fun flagFalse_butPhotoExists_detected() {
        val s = sample(id = 1L, number = "07", hasPhoto = false)
        val img = image(id = 10L, sampleId = 1L, path = "/photos/a.jpg")
        val result = call(
            allSamples = listOf(s),
            allImages = listOf(img),
            fileExists = allFiles
        )
        assertEquals(1, result.size)
        val issue = result[0] as DbIssue.PhotoFlagMismatch
        assertEquals(1L, issue.sampleId)
        assertEquals("07", issue.sampleNumber)
        assertEquals(1, issue.photoCount)
    }

    @Test
    fun flagFalse_twoPhotosExist_photoCountTwo() {
        val s = sample(id = 1L, hasPhoto = false)
        val result = call(
            allSamples = listOf(s),
            allImages = listOf(
                image(id = 10L, sampleId = 1L, path = "/a.jpg"),
                image(id = 11L, sampleId = 1L, path = "/b.jpg")
            ),
            fileExists = allFiles
        )
        assertEquals(1, result.size)
        val issue = result[0] as DbIssue.PhotoFlagMismatch
        assertEquals(2, issue.photoCount)
    }

    @Test
    fun flagFalse_brokenPhoto_doesNotTriggerMismatch() {
        val s = sample(id = 1L, hasPhoto = false)
        val img = image(id = 10L, sampleId = 1L, path = "/missing.jpg")
        val result = call(
            allSamples = listOf(s),
            allImages = listOf(img),
            fileExists = noFiles
        )
        // Должен быть только BrokenPhotoLink, без PhotoFlagMismatch.
        assertEquals(1, result.size)
        assertTrue(result[0] is DbIssue.BrokenPhotoLink)
    }

    @Test
    fun flagFalse_noImages_noIssue() {
        val s = sample(id = 1L, hasPhoto = false)
        val result = call(allSamples = listOf(s))
        assertTrue(result.isEmpty())
    }

    @Test
    fun flagTrue_noImages_noIssue() {
        // Проба помечена как «есть фото», но записей нет.
        // Это не наша забота — такой случай чинит syncHasNoteAndPhoto.
        val s = sample(id = 1L, hasPhoto = true)
        val result = call(allSamples = listOf(s))
        assertTrue(result.isEmpty())
    }

    // ============================================================
    // Комбинированный случай
    // ============================================================

    @Test
    fun mixedIssues_allReported() {
        val s1 = sample(id = 1L, number = "01", hasPhoto = false)
        val s2 = sample(id = 2L, number = "02", hasPhoto = true)
        val result = call(
            orphanOrders = listOf(order(id = 99L, number = "99")),
            orphanSamples = listOf(sample(id = 88L, number = "88")),
            allSamples = listOf(s1, s2),
            allImages = listOf(
                image(id = 10L, sampleId = 1L, path = "/alive.jpg"),
                image(id = 11L, sampleId = 2L, path = "/missing.jpg")
            ),
            fileExists = { path -> path == "/alive.jpg" }
        )
        assertEquals(4, result.size)
        assertTrue(result.any { it is DbIssue.OrphanOrder })
        assertTrue(result.any { it is DbIssue.OrphanSample })
        assertTrue(result.any { it is DbIssue.BrokenPhotoLink })
        assertTrue(result.any { it is DbIssue.PhotoFlagMismatch })
    }

    @Test
    fun issueIds_areStableAndUnique() {
        val result = call(
            orphanOrders = listOf(order(id = 1L), order(id = 2L)),
            orphanSamples = listOf(sample(id = 3L), sample(id = 4L))
        )
        val ids = result.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }
}