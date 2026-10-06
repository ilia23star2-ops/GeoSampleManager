package com.example.geosamplemanager.data.dao

/**
 * FIX 5.10-stat-admin-v2-orders-a:
 * POJO для агрегата «сколько всего проб в наряде и сколько
 * отмечено». Заполняется одним SQL-запросом
 * `getSampleCountsByOrder()` в SampleDao.
 *
 * Используется табом «Наряды» админ-панели: чтобы построить
 * список всех нарядов и прогресс-бар без загрузки всех проб
 * в память.
 */
data class OrderSampleCounts(
    val orderId: Long,
    val totalSamples: Int,
    val foundSamples: Int
)