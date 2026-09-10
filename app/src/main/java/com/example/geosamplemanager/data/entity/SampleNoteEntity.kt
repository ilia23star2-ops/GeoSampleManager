package com.example.geosamplemanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sample_notes",
    foreignKeys = [
        ForeignKey(
            entity = SampleEntity::class,
            parentColumns = ["id"],
            childColumns = ["sample_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sample_id"])]
)
data class SampleNoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sample_id")
    val sampleId: Long,

    @ColumnInfo(name = "note_text")
    val noteText: String? = null,

    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,

    @ColumnInfo(name = "created_date")
    val createdDate: Long = System.currentTimeMillis()
)
