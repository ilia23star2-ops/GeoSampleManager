package com.example.geosamplemanager.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "samples",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["order_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["order_id", "sample_number"], unique = true)]
)
data class SampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "order_id")
    val orderId: Long,

    @ColumnInfo(name = "serial_number")
    val serialNumber: Int = 0,

    @ColumnInfo(name = "sample_number")
    val sampleNumber: String,

    @ColumnInfo(name = "well_number")
    val wellNumber: String = "",

    val workings: String? = null,

    @ColumnInfo(name = "interval_from")
    val intervalFrom: Double? = null,

    @ColumnInfo(name = "interval_to")
    val intervalTo: Double? = null,

    val weight: Double? = null,

    @ColumnInfo(name = "control_weight")
    val controlWeight: Double? = null,

    @ColumnInfo(name = "actual_weight")
    val actualWeight: Double? = null,

    @ColumnInfo(name = "sample_type")
    val sampleType: String = "auger",

    val status: String = "normal",

    @ColumnInfo(name = "reserved_type")
    val reservedType: String? = null,

    @ColumnInfo(name = "material_desc")
    val materialDesc: String? = null,

    val found: Boolean = false,

    @ColumnInfo(name = "weight_control")
    val weightControl: Boolean = false,

    val postponed: Boolean = false,

    @ColumnInfo(name = "has_note")
    val hasNote: Boolean = false
)
