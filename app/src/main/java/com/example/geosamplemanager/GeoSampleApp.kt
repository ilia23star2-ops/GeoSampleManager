package com.example.geosamplemanager

import android.app.Application
import com.example.geosamplemanager.data.DatabaseRepository
import com.example.geosamplemanager.data.history.ImportHistoryRepository
import com.example.geosamplemanager.data.settings.SettingsRepository

class GeoSampleApp : Application() {

    lateinit var repository: DatabaseRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var importHistoryRepository: ImportHistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = DatabaseRepository(this)
        settingsRepository = SettingsRepository(this)
        importHistoryRepository = ImportHistoryRepository(this)
    }
}