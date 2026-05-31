package com.aiexile.animetrack.di

import android.content.Context
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.AnimeRepositoryImpl
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.sync.BangumiSyncManager

object AppContainer {
    
    private var context: Context? = null
    private var database: AnimeDatabase? = null
    private var repository: AnimeRepository? = null
    private var settingsRepository: SettingsRepository? = null
    private var authManager: AuthManager? = null
    private var syncManager: BangumiSyncManager? = null
    
    fun initialize(context: Context) {
        this.context = context.applicationContext
        database = AnimeDatabase.getDatabase(this.context!!)
        repository = AnimeRepositoryImpl(database!!.animeDao(), this.context!!)
        settingsRepository = SettingsRepository(this.context!!)
        authManager = AuthManager(this.context!!)
        syncManager = BangumiSyncManager(authManager!!, repository!!)
    }
    
    fun getApplication(): android.app.Application {
        return context?.applicationContext as? android.app.Application
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

    fun getAuthManager(): AuthManager {
        return authManager
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    fun getSyncManager(): BangumiSyncManager {
        return syncManager
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }
}
