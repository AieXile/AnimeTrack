package com.aiexile.animetrack.di

import android.content.Context
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.AnimeRepository
import com.aiexile.animetrack.data.AnimeRepositoryImpl
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.UsageStatsRepository
import com.aiexile.animetrack.data.auth.AuthManager
import com.aiexile.animetrack.data.auth.BilibiliAuthManager
import com.aiexile.animetrack.data.auth.UserAuthManager
import com.aiexile.animetrack.data.player.PlayerRepository
import com.aiexile.animetrack.data.remote.FeedbackRepository
import com.aiexile.animetrack.data.sync.BangumiSyncManager
import com.aiexile.animetrack.data.sync.BilibiliSyncManager
import com.aiexile.animetrack.data.sync.WebDAVAutoSyncManager
import kotlinx.coroutines.flow.MutableStateFlow

object AppContainer {
    
    private var context: Context? = null
    private var database: AnimeDatabase? = null
    private var repository: AnimeRepository? = null
    private var settingsRepository: SettingsRepository? = null
    private var authManager: AuthManager? = null
    private var bilibiliAuthManager: BilibiliAuthManager? = null
    private var userAuthManager: UserAuthManager? = null
    private var syncManager: BangumiSyncManager? = null
    private var bilibiliSyncManager: BilibiliSyncManager? = null
    private var playerRepository: PlayerRepository? = null
    private var usageStatsRepository: UsageStatsRepository? = null
    private var feedbackRepository: FeedbackRepository? = null

    // 当前会话开始时间（由 MainActivity.onStart 设置）
    @Volatile
    var sessionStartTime: Long = 0L

    // 首屏渲染完成标志：由 HomeViewModel.markFirstFrameRendered() 触发，
    // 由 WebDAVAutoSyncManager 等后台任务监听，延迟到首帧后再执行，避免与启动链路争抢资源。
    val firstFrameRendered: MutableStateFlow<Boolean> = MutableStateFlow(false)

    fun markFirstFrameRendered() {
        firstFrameRendered.value = true
    }

    fun initialize(context: Context) {
        // 幂等守卫：Application.onCreate 必先于所有组件执行；MainActivity.attachBaseContext
        // 等处的重复调用直接跳过，避免重建全套实例（旧实例泄漏 + 状态不一致窗口）
        if (this.context != null) return
        this.context = context.applicationContext
        database = AnimeDatabase.getDatabase(this.context!!)
        repository = AnimeRepositoryImpl(database!!.animeDao(), this.context!!)
        settingsRepository = SettingsRepository(this.context!!)
        authManager = AuthManager(this.context!!)
        bilibiliAuthManager = BilibiliAuthManager(this.context!!)
        userAuthManager = UserAuthManager(this.context!!)
        syncManager = BangumiSyncManager(authManager!!, repository!!)
        bilibiliSyncManager = BilibiliSyncManager(bilibiliAuthManager!!, repository!!)
        playerRepository = PlayerRepository(this.context!!)
        usageStatsRepository = UsageStatsRepository(this.context!!)
        feedbackRepository = FeedbackRepository(this.context!!)
        WebDAVAutoSyncManager.initialize(this.context!!, settingsRepository!!, database!!.animeDao())
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

    fun getUserAuthManager(): UserAuthManager {
        return userAuthManager
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    fun getBilibiliSyncManager(): BilibiliSyncManager {
        return bilibiliSyncManager
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    fun getAnimeDatabase(): AnimeDatabase {
        return database
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    fun getPlayerRepository(): PlayerRepository {
        return playerRepository
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    fun getUsageStatsRepository(): UsageStatsRepository {
        return usageStatsRepository
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }

    fun getFeedbackRepository(): FeedbackRepository {
        return feedbackRepository
            ?: throw IllegalStateException("AppContainer not initialized. Call initialize() first.")
    }
}
