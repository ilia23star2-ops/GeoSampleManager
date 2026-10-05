package com.example.geosamplemanager.data.diagnostics

/**
 * FIX 5.9-db-diagnostics:
 * Одна проблема, найденная в БД.
 *
 * Sealed — чтобы UI (в пачке db-diagnostics-2) мог рендерить
 * каждую категорию по-своему: иконка, описание, текст кнопки
 * «Исправить».
 *
 * Поле [id] — стабильный ключ для Compose `key(...)` и чек-боксов.
 * Он уникален в пределах всего списка проблем.
 */
sealed class DbIssue {

    /** Стабильный идентификатор проблемы (для UI-ключа). */
    abstract val id: String

    /** Наряд, чей area_id ссылается на несуществующий участок. */
    data class OrphanOrder(
        val orderId: Long,
        val orderNumber: String
    ) : DbIssue() {
        override val id: String get() = "orphan_order_$orderId"
    }

    /** Проба, чей order_id ссылается на несуществующий наряд. */
    data class OrphanSample(
        val sampleId: Long,
        val sampleNumber: String,
        val wellNumber: String,
        val orderId: Long
    ) : DbIssue() {
        override val id: String get() = "orphan_sample_$sampleId"
    }

    /** Запись в sample_images есть — файла по пути нет. */
    data class BrokenPhotoLink(
        val imageId: Long,
        val sampleId: Long,
        val sampleNumber: String,
        val imagePath: String
    ) : DbIssue() {
        override val id: String get() = "broken_photo_$imageId"
    }

    /** У пробы есть живые фото на диске, но флаг has_photo = false. */
    data class PhotoFlagMismatch(
        val sampleId: Long,
        val sampleNumber: String,
        val photoCount: Int
    ) : DbIssue() {
        override val id: String get() = "photo_flag_$sampleId"
    }
}