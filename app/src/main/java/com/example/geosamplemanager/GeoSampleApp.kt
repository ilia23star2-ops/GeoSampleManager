package com.example.geosamplemanager

import android.app.Application
import com.example.geosamplemanager.data.DatabaseRepository

class GeoSampleApp : Application() {

    lateinit var repository: DatabaseRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DatabaseRepository(this)
    }
}
