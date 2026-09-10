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

    @Query("SELECT * FROM orders ORDER BY area_id, order_number")
    suspend fun getAllOrders(): List<OrderEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(order: OrderEntity): Long

    @Query("DELETE FROM orders WHERE id = :orderId")
    suspend fun deleteById(orderId: Long)
}
