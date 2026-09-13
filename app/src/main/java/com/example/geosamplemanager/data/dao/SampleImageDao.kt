package com.example.geosamplemanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.geosamplemanager.data.entity.SampleImageEntity

@Dao
interface SampleImageDao {

    /** Все фото пробы, самые свежие — первыми. */
    @Query("SELECT * FROM sample_images WHERE sample_id = :sampleId ORDER BY created_date DESC")
    suspend fun getPhotosForSample(sampleId: Long): List<SampleImageEntity>

    /** Только пути — для быстрой синхронизации флагов и очистки файлов. */
    @Query("SELECT image_path FROM sample_images WHERE sample_id = :sampleId")
    suspend fun getImagePathsForSample(sampleId: Long): List<String>

    /** Пути фото всех проб наряда — для очистки файлов при удалении наряда. */
    @Query(
        """
        SELECT image_path FROM sample_images
        WHERE sample_id IN (SELECT id FROM samples WHERE order_id = :orderId)
        """
    )
    suspend fun getImagePathsForOrder(orderId: Long): List<String>

    /** Сколько фото у пробы. */
    @Query("SELECT COUNT(*) FROM sample_images WHERE sample_id = :sampleId")
    suspend fun countForSample(sampleId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(image: SampleImageEntity): Long

    @Query("DELETE FROM sample_images WHERE id = :imageId")
    suspend fun deleteById(imageId: Long)

    @Query("DELETE FROM sample_images WHERE sample_id = :sampleId")
    suspend fun deleteBySampleId(sampleId: Long)
}