package com.aiexile.animetrack.di

import android.content.Context
import com.aiexile.animetrack.data.AnimeDao
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.AnimeRepositoryImpl
import com.aiexile.animetrack.data.SettingsRepository

object AppContainer {
    
    private var database: AnimeDatabase? = null
    private var repository: AnimeRepository? = null
    private var settingsRepository: SettingsRepository? = null
    
    fun initialize(context: Context) {
        database = AnimeDatabase.getDatabase(context)
        repository = AnimeRepositoryImpl(database!!.animeDao())
        settingsRepository = SettingsRepository(context)
    }
    
    fun getAnimeDao(): AnimeDao {
        return database?.animeDao()
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }
    
    fun getAnimeRepository(): AnimeRepository {
        return repository
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }
    
    fun getSettingsRepository(): SettingsRepository {
        return settingsRepository
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }
}
