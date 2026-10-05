package com.example.geosamplemanager.data.diagnostics

import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleImageEntity

/**
 * FIX 5.9-db-diagnostics:
 * Движок диагностики БД. Ищет четыре вида проблем:
 *
 *   1. Наряды без участка (area_id → несуществующий areas.id).
 *   2. Пробы без наряда (order_id → несуществующий orders.id).
 *   3. Битые ссылки на фото (запись в sample_images есть,
 *      файла на диске нет).
 *   4. Флаг has_photo = false, но живые фото на диске есть.
 *
 * Чистая функция — не знает про Room, File, Android. Проверка
 * существования файла передаётся лямбдой [fileExists] — так
 * движок легко тестируется на JVM без реальной файловой системы.
 *
 * Схема БД не меняется. Миграция не нужна.
 */
object DbDiagnosticsEngine {

    /**
     * Найти все проблемы.
     *
     * @param orphanOrders наряды, чей area_id отсутствует в areas
     *        (SQL: LEFT JOIN areas … WHERE areas.id IS NULL).
     * @param orphanSamples пробы, чей order_id отсутствует в orders.
     * @param allSamples все пробы — нужны для связи фото → проба
     *        и для проверки флага has_photo.
     * @param allImages все записи sample_images.
     * @param fileExists проверка существования файла по абсолютному
     *        пути. На устройстве — File(path).exists(); в тестах —
     *        предикат по заранее известному множеству.
     */
    fun detect(
        orphanOrders: List<OrderEntity>,
        orphanSamples: List<SampleEntity>,
        allSamples: List<SampleEntity>,
        allImages: List<SampleImageEntity>,
        fileExists: (String) -> Boolean
    ): List<DbIssue> {
        val result = mutableListOf<DbIssue>()

        // 1. Наряды без участка.
        for (o in orphanOrders) {
            result += DbIssue.OrphanOrder(
                orderId = o.id,
                orderNumber = o.orderNumber
            )
        }

        // 2. Пробы без наряда.
        for (s in orphanSamples) {
            result += DbIssue.OrphanSample(
                sampleId = s.id,
                sampleNumber = s.sampleNumber,
                wellNumber = s.wellNumber,
                orderId = s.orderId
            )
        }

        val samplesById = allSamples.associateBy { it.id }
        val imagesBySampleId = allImages.groupBy { it.sampleId }

        // 3. Битые ссылки на фото: запись есть — файла нет.
        for (img in allImages) {
            if (fileExists(img.imagePath)) continue
            val sample = samplesById[img.sampleId]
            result += DbIssue.BrokenPhotoLink(
                imageId = img.id,
                sampleId = img.sampleId,
                sampleNumber = sample?.sampleNumber ?: "",
                imagePath = img.imagePath
            )
        }

        // 4. Флаг has_photo = false, но живые фото на диске есть.
        //    Битые ссылки уже учтены выше — здесь считаем только
        //    те записи, чьи файлы реально существуют.
        for (sample in allSamples) {
            if (sample.hasPhoto) continue
            val images = imagesBySampleId[sample.id].orEmpty()
            val alive = images.count { fileExists(it.imagePath) }
            if (alive == 0) continue
            result += DbIssue.PhotoFlagMismatch(
                sampleId = sample.id,
                sampleNumber = sample.sampleNumber,
                photoCount = alive
            )
        }

        return result
    }
}