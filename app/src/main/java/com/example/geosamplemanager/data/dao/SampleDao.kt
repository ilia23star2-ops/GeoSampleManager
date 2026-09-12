package com.example.geosamplemanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.geosamplemanager.data.entity.SampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SampleDao {

    @Query("SELECT * FROM samples WHERE order_id = :orderId ORDER BY serial_number")
    fun getSamplesForOrder(orderId: Long): Flow<List<SampleEntity>>

    @Query("SELECT * FROM samples WHERE order_id = :orderId ORDER BY serial_number")
    suspend fun getSamplesForOrderList(orderId: Long): List<SampleEntity>

    @Query("SELECT * FROM samples WHERE id = :sampleId LIMIT 1")
    suspend fun getSampleById(sampleId: Long): SampleEntity?

    @Query("SELECT * FROM samples WHERE well_number = :wellNumber ORDER BY serial_number")
    suspend fun getSamplesByWell(wellNumber: String): List<SampleEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(sample: SampleEntity): Long

    @Update
    suspend fun update(sample: SampleEntity)

    @Query("DELETE FROM samples WHERE id = :sampleId")
    suspend fun deleteById(sampleId: Long)

    @Query("DELETE FROM samples WHERE order_id = :orderId")
    suspend fun deleteAllForOrder(orderId: Long)

    @Query("UPDATE samples SET found = NOT found WHERE id = :sampleId")
    suspend fun toggleFound(sampleId: Long)

    @Query("UPDATE samples SET found = :found WHERE id = :sampleId")
    suspend fun setFound(sampleId: Long, found: Boolean)

    @Query("UPDATE samples SET postponed = :postponed WHERE id = :sampleId")
    suspend fun setPostponed(sampleId: Long, postponed: Boolean)

    @Query("UPDATE samples SET control_weight = :weight WHERE id = :sampleId")
    suspend fun setControlWeight(sampleId: Long, weight: Double?)

    @Query("SELECT COUNT(*) FROM samples")
    suspend fun getTotalCount(): Int

    @Query("SELECT COUNT(*) FROM samples WHERE found = 1")
    suspend fun getFoundCount(): Int

    @Query("SELECT COUNT(*) FROM samples WHERE status = 'blank'")
    suspend fun getBlankCount(): Int

    @Query("SELECT COUNT(*) FROM samples WHERE status = 'control'")
    suspend fun getControlCount(): Int

    @Query("SELECT COUNT(*) FROM samples WHERE order_id = :orderId")
    suspend fun getCountForOrder(orderId: Long): Int

    @Query("SELECT COUNT(*) FROM samples WHERE order_id = :orderId AND found = 1")
    suspend fun getFoundCountForOrder(orderId: Long): Int

    @Query(
        """
        SELECT * FROM samples 
        WHERE (:query IS NULL OR sample_number LIKE '%' || :query || '%' 
               OR well_number LIKE '%' || :query || '%')
        ORDER BY sample_number
        """
    )
    suspend fun searchSamples(query: String?): List<SampleEntity>
}