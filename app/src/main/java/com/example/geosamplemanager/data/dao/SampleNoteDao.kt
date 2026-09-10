package com.example.geosamplemanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.geosamplemanager.data.entity.SampleNoteEntity

@Dao
interface SampleNoteDao {

    @Query("SELECT * FROM sample_notes WHERE sample_id = :sampleId LIMIT 1")
    suspend fun getNote(sampleId: Long): SampleNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: SampleNoteEntity): Long

    @Query("DELETE FROM sample_notes WHERE sample_id = :sampleId")
    suspend fun deleteBySampleId(sampleId: Long)
}
