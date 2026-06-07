package com.aiexile.animetrack.di

import android.content.Context
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.AnimeRepositoryImpl
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.sync.BangumiSyncManager
import com.aiexile.animetrack.data.sync.BilibiliSyncManager

object AppContainer {
    
    private var context: Context? = null
    private var database: AnimeDatabase? = null
    private var repository: AnimeRepository? = null
    private var settingsRepository: SettingsRepository? = null
    private var authManager: AuthManager? = null
    private var bilibiliAuthManager: BilibiliAuthManager? = null
    private var syncManager: BangumiSyncManager? = null
    private var bilibiliSyncManager: BilibiliSyncManager? = null
    
    fun initialize(context: Context) {
        this.context = context.applicationContext
        database = AnimeDatabase.getDatabase(this.context!!)
        repository = AnimeRepositoryImpl(database!!.animeDao(), this.context!!)
        settingsRepository = SettingsRepository(this.context!!)
        authManager = AuthManager(this.context!!)
        bilibiliAuthManager = BilibiliAuthManager(this.context!!)
        syncManager = BangumiSyncManager(authManager!!, repository!!)
        bilibiliSyncManager = BilibiliSyncManager(bilibiliAuthManager!!, repository!!)
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

    fun getBilibiliAuthManager(): BilibiliAuthManager {
        return bilibiliAuthManager
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    fun getBilibiliSyncManager(): BilibiliSyncManager {
        return bilibiliSyncManager
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }
}
