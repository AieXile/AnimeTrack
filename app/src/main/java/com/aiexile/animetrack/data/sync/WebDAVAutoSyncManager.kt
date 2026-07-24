package com.aiexile.animetrack.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.data.AnimeDao
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.backup.BackupManager
import com.aiexile.animetrack.data.backup.WebDAVClient
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class WebDAVAutoSyncManager(
    private val settingsRepository: SettingsRepository,
    private val animeDao: AnimeDao,
    private val context: Context
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isSyncing = AtomicBoolean(false)

    /** onAppOpen 决策所需字段的合并读取结果 */
    private data class AppOpenSyncDecision(
        val enabled: Boolean,
        val onAppOpen: Boolean,
        val scheduled: Boolean,
        val interval: Int,
        val lastScheduledTime: Long
    )

    /**
     * 番剧数据发生变化时调用
     */
    fun notifyDataChanged() {
        scope.launch {
            val enabled = settingsRepository.webdavAutoSyncEnabled.first()
            if (!enabled) return@launch

            val onDataChange = settingsRepository.webdavAutoSyncOnDataChange.first()
            if (!onDataChange) return@launch

            performAutoSync("数据变化")
        }
    }

    /**
     * App 启动时调用。
     * 内部启动协程：先等待首帧渲染完成（避免与启动链路争抢资源），
     * 再合并读取启动同步决策所需的 5 个字段，单次 DataStore 访问后执行原逻辑。
     */
    fun onAppOpen() {
        scope.launch {
            // Task 8.1: 等待首帧渲染完成
            AppContainer.firstFrameRendered.first { it }

            // Task 8.3: 合并读取 onAppOpen + checkScheduledSync 决策字段
            val decision = combine(
                settingsRepository.webdavAutoSyncEnabled,
                settingsRepository.webdavAutoSyncOnAppOpen,
                settingsRepository.webdavAutoSyncScheduled,
                settingsRepository.webdavAutoSyncInterval,
                settingsRepository.webdavAutoSyncLastScheduledTime
            ) { enabled, onAppOpen, scheduled, interval, lastScheduledTime ->
                AppOpenSyncDecision(enabled, onAppOpen, scheduled, interval, lastScheduledTime)
            }.first()

            if (!decision.enabled) return@launch

            if (decision.onAppOpen) {
                performAutoSync("App启动")
            }

            checkScheduledSync(decision.scheduled, decision.interval, decision.lastScheduledTime)
        }
    }

    /**
     * 根据系统时间判断定时同步是否到期
     */
    private suspend fun checkScheduledSync(
        scheduled: Boolean,
        interval: Int,
        lastScheduledTime: Long
    ) {
        if (!scheduled) return

        val intervalHours = when (interval) {
            0 -> 6
            1 -> 12
            else -> 24
        }
        val intervalMs = intervalHours * 60 * 60 * 1000L

        val now = System.currentTimeMillis()

        if (lastScheduledTime == 0L || now - lastScheduledTime >= intervalMs) {
            val success = performAutoSync("定时同步")
            if (success) {
                settingsRepository.setWebdavAutoSyncLastScheduledTime(now)
            }
        }
    }

    /**
     * 执行自动备份，返回是否成功
     */
    private suspend fun performAutoSync(reason: String): Boolean {
        if (!isSyncing.compareAndSet(false, true)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "自动同步正在进行中，跳过（触发原因：$reason）")
            return false
        }

        try {
            val url = settingsRepository.webdavUrl.first()
            val username = settingsRepository.webdavUsername.first()
            val password = settingsRepository.webdavPassword.first()

            if (url.isBlank()) {
                Log.w(TAG, "WebDAV 地址为空，跳过自动同步")
                return false
            }

            val wifiOnly = settingsRepository.webdavAutoSyncWifiOnly.first()
            if (wifiOnly && !isWifiConnected()) {
                if (BuildConfig.DEBUG) Log.d(TAG, "非 Wi-Fi 网络，跳过自动同步（触发原因：$reason）")
                return false
            }

            val useCustom = settingsRepository.webdavAutoSyncUseCustomStrategy.first()
            val strategy = if (useCustom) {
                settingsRepository.webdavAutoSyncBackupStrategy.first()
            } else {
                settingsRepository.webdavBackupStrategy.first()
            }

            if (BuildConfig.DEBUG) Log.d(TAG, "开始自动同步（触发原因：$reason，策略：${if (strategy == 0) "JSON" else "ZIP"}）")

            val backupFile = BackupManager.backup(context, strategy, animeDao)

            val result = WebDAVClient.upload(url, username, password, backupFile, strategy)
            if (result.isSuccess) {
                settingsRepository.setWebdavLastAutoSyncTime(System.currentTimeMillis())
                if (BuildConfig.DEBUG) Log.d(TAG, "自动同步成功（触发原因：$reason）")
                return true
            } else {
                Log.e(TAG, "自动同步上传失败（触发原因：$reason）", result.exceptionOrNull())
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动同步异常（触发原因：$reason）", e)
            return false
        } finally {
            isSyncing.set(false)
        }
    }

    private fun isWifiConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    companion object {
        private const val TAG = "WebDAVAutoSync"

        @Volatile
        private var instance: WebDAVAutoSyncManager? = null

        fun getInstance(): WebDAVAutoSyncManager {
            return instance ?: throw IllegalStateException("WebDAVAutoSyncManager not initialized")
        }

        fun initialize(context: Context, settingsRepository: SettingsRepository, animeDao: AnimeDao) {
            if (instance != null) return
            instance = WebDAVAutoSyncManager(settingsRepository, animeDao, context.applicationContext)
        }
    }
}
