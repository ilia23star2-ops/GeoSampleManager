package com.example.geosamplemanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.geosamplemanager.data.entity.AreaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AreaDao {

    @Query("SELECT * FROM areas ORDER BY area_name")
    fun getAllAreas(): Flow<List<AreaEntity>>

    @Query("SELECT * FROM areas ORDER BY area_name")
    suspend fun getAreasList(): List<AreaEntity>

    @Query("SELECT id FROM areas WHERE area_name = :name LIMIT 1")
    suspend fun getAreaId(name: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(area: AreaEntity): Long

    @Query("DELETE FROM areas WHERE area_name = :name")
    suspend fun deleteByName(name: String)

    @Query("DELETE FROM areas WHERE id = :id")
    suspend fun deleteById(id: Long)
}
