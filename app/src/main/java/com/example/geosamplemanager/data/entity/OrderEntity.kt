package com.example.geosamplemanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["area_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["area_id", "order_number"], unique = true)]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "area_id")
    val areaId: Long,

    @ColumnInfo(name = "order_number")
    val orderNumber: String,

    @ColumnInfo(name = "created_date")
    val createdDate: Long = System.currentTimeMillis()
)
