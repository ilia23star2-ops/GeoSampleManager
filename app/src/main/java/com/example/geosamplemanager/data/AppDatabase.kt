package com.example.geosamplemanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.geosamplemanager.data.dao.AreaDao
import com.example.geosamplemanager.data.dao.OrderDao
import com.example.geosamplemanager.data.dao.OrderWellDao
import com.example.geosamplemanager.data.dao.SampleDao
import com.example.geosamplemanager.data.dao.SampleNoteDao
import com.example.geosamplemanager.data.entity.AreaEntity
import com.example.geosamplemanager.data.entity.OrderEntity
import com.example.geosamplemanager.data.entity.OrderWellEntity
import com.example.geosamplemanager.data.entity.SampleEntity
import com.example.geosamplemanager.data.entity.SampleNoteEntity

@Database(
    entities = [
        AreaEntity::class,
        OrderEntity::class,
        SampleEntity::class,
        OrderWellEntity::class,
        SampleNoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun areaDao(): AreaDao
    abstract fun orderDao(): OrderDao
    abstract fun sampleDao(): SampleDao
    abstract fun orderWellDao(): OrderWellDao
    abstract fun sampleNoteDao(): SampleNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "geosamples.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
