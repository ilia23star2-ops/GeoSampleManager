package com.example.geosamplemanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Фото пробы.
 *
 * Одна проба — много фото (0..N).
 * Заметка (SampleNoteEntity) и фото независимы.
 * Файл фото лежит в filesDir/sample_photos/, здесь — только путь.
 */
@Entity(
    tableName = "sample_images",
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
data class SampleImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "sample_id")
    val sampleId: Long,

    @ColumnInfo(name = "image_path")
    val imagePath: String,

    @ColumnInfo(name = "created_date")
    val createdDate: Long = System.currentTimeMillis()
)