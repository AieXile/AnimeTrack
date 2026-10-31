package com.aiexile.animetrack.data

import android.content.Context
import com.aiexile.animetrack.data.log.AppLogManager
import com.aiexile.animetrack.data.network.BangumiApiService
import com.aiexile.animetrack.data.network.BangumiCalendarDay
import com.aiexile.animetrack.data.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * 一次每日放送读取的结果。
 *
 * @param fetchedAt 数据写入缓存的时间戳（毫秒），用于界面上「更新于 …」的提示
 * @param fromCache 本次是否直接命中当天缓存、没有发起网络请求
 * @param stale 网络刷新失败后回退到旧缓存（数据可能已跨天）
 */
data class BangumiCalendarSnapshot(
    val days: List<BangumiCalendarDay>,
    val fetchedAt: Long,
    val fromCache: Boolean,
    val stale: Boolean = false
)

/**
 * 每日放送的拉取与缓存。
 *
 * 缓存分两级：
 * - 内存：[memoryCache]，进程存活期间直接命中；
 * - 磁盘：`filesDir/bangumi_calendar.json`，跨进程有效，**当天**写入的缓存不再请求网络。
 *
 * 每日放送一周内只在新番追加/移出时变化，因此「当天有效」比固定小时 TTL 更直观，
 * 也保证跨天后一定重新拉取。手动刷新（[load] 的 forceRefresh）无视缓存时效。
 */
class BangumiCalendarRepository(
    context: Context,
    private val api: BangumiApiService = RetrofitClient.bangumiApi,
    private val gson: Gson = Gson()
) {

    companion object {
        private const val TAG = "BangumiCalendar"
        private const val CACHE_FILE_NAME = "bangumi_calendar.json"
    }

    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)

    // 串行化「读缓存 → 请求 → 写缓存」，避免并发进入时重复请求与互相覆盖
    private val lock = Mutex()

    @Volatile
    private var memoryCache: CachedCalendar? = null

    /**
     * 读取每日放送。
     *
     * - 当天缓存有效且非强制刷新：直接返回缓存，不发请求；
     * - 否则请求接口；成功则替换两级缓存；
     * - 请求失败但有任意缓存：返回旧缓存并标记 [BangumiCalendarSnapshot.stale]；
     * - 请求失败且无缓存：抛出异常，由界面展示错误态。
     */
    suspend fun load(forceRefresh: Boolean = false): BangumiCalendarSnapshot = lock.withLock {
        val cached = memoryCache ?: readDiskCache()?.also { memoryCache = it }

        if (!forceRefresh && cached != null && cached.isFromToday()) {
            return@withLock BangumiCalendarSnapshot(
                days = cached.days,
                fetchedAt = cached.savedAt,
                fromCache = true
            )
        }

        try {
            val days = withContext(Dispatchers.IO) { api.getCalendar() }
                .filter { it.items.orEmpty().isNotEmpty() }
                .sortedBy { it.weekdayId }
            val fresh = CachedCalendar(
                savedDate = LocalDate.now().toString(),
                savedAt = System.currentTimeMillis(),
                days = days
            )
            memoryCache = fresh
            writeDiskCache(fresh)
            AppLogManager.i(TAG, "每日放送刷新: ${days.sumOf { it.items.size }} 部")
            BangumiCalendarSnapshot(days = days, fetchedAt = fresh.savedAt, fromCache = false)
        } catch (e: Exception) {
            AppLogManager.w(TAG, "每日放送拉取失败", e)
            if (cached != null) {
                BangumiCalendarSnapshot(
                    days = cached.days,
                    fetchedAt = cached.savedAt,
                    fromCache = true,
                    stale = true
                )
            } else {
                throw e
            }
        }
    }

    private suspend fun readDiskCache(): CachedCalendar? = withContext(Dispatchers.IO) {
        if (!cacheFile.exists()) return@withContext null
        runCatching { gson.fromJson(cacheFile.readText(), CachedCalendar::class.java) }
            .onFailure { AppLogManager.w(TAG, "每日放送缓存解析失败", it) }
            .getOrNull()
            ?.takeIf { it.days.orEmpty().isNotEmpty() }
    }

    /** 临时文件 + rename 原子替换，避免写入中途被杀进程留下半截 JSON */
    private suspend fun writeDiskCache(cache: CachedCalendar) = withContext(Dispatchers.IO) {
        runCatching {
            val temp = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
            temp.writeText(gson.toJson(cache))
            if (cacheFile.exists()) cacheFile.delete()
            temp.renameTo(cacheFile)
        }.onFailure { AppLogManager.w(TAG, "每日放送缓存写入失败", it) }
    }

    /** 磁盘缓存结构：日期字符串按本地时区判定「当天」，避免跨时区/改时钟导致的边界问题 */
    private data class CachedCalendar(
        val savedDate: String,
        val savedAt: Long,
        val days: List<BangumiCalendarDay>
    ) {
        fun isFromToday(): Boolean = savedDate == LocalDate.now().toString()
    }
}
