package com.example.geosamplemanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.geosamplemanager.data.entity.OrderWellEntity

@Dao
interface OrderWellDao {

    @Query("SELECT well_number FROM order_wells WHERE order_id = :orderId ORDER BY well_number")
    suspend fun getWellsForOrder(orderId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(well: OrderWellEntity): Long
}
