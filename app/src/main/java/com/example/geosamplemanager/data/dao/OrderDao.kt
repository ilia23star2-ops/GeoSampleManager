package com.example.geosamplemanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.geosamplemanager.data.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Query("SELECT * FROM orders WHERE area_id = :areaId ORDER BY order_number")
    fun getOrdersForAreaId(areaId: Long): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE area_id = :areaId ORDER BY order_number")
    suspend fun getOrdersForAreaIdList(areaId: Long): List<OrderEntity>

    @Query("SELECT id FROM orders WHERE area_id = :areaId AND order_number = :orderNumber LIMIT 1")
    suspend fun getOrderId(areaId: Long, orderNumber: String): Long?

    /**
     * FIX 5.8.10-d:
     * Поиск нарядов по ИМЕНИ участка и номеру наряда, минуя area_id.
     *
     * Зачем: если в БД когда-либо оказались два участка с одинаковым
     * area_name (без UNIQUE-индекса на areas.area_name это возможно),
     * getOrderId(area_id, ...) ищет не там. Здесь связь идёт напрямую
     * по имени — все совпадения возвращаются, вызывающий код может
     * сложить статистику.
     *
     * Возвращает все id нарядов с таким номером в любом «дубликате»
     * участка с данным именем. Обычно — 0 или 1 элемент.
     */
    @Query(
        """
        SELECT o.id FROM orders o
        JOIN areas a ON a.id = o.area_id
        WHERE a.area_name = :areaName AND o.order_number = :orderNumber
        ORDER BY o.id
        """
    )
    suspend fun getOrderIdsByName(areaName: String, orderNumber: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(order: OrderEntity): Long

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteById(orderId: Long)

    @Query("SELECT * FROM orders ORDER BY order_number")
    suspend fun getAllOrders(): List<OrderEntity>

    /**
     * Flow всех нарядов. Используется для реактивного обновления
     * списка на экране «Сверка и поиск».
     */
    @Query("SELECT * FROM orders ORDER BY order_number")
    fun getAllOrdersFlow(): Flow<List<OrderEntity>>
}
