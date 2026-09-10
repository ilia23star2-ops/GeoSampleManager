package com.example.geosamplemanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "areas")
data class AreaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "area_name")
    val areaName: String,

    @ColumnInfo(name = "created_date")
    val createdDate: Long = System.currentTimeMillis()
)
