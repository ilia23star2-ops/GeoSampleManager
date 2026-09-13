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