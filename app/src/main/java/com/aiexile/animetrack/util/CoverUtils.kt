package com.aiexile.animetrack.util

import android.content.Context
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.aiexile.animetrack.model.AnimeStatus
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.net.ssl.SSLHandshakeException

/**
 * 将 coverUrl 转换为 Coil 可加载的模型号。
 *
 * 数据库中存储的本地封面路径是裸绝对路径（如 /data/data/.../files/anime_covers/12345.jpg），
 * Coil 的 String→Uri→Fetcher 链路无法处理 scheme 为 null 的裸路径，
 * 需要转为 File 对象才能由 FileFetcher 正确加载。
 */
fun resolveCoverModel(coverUrl: String?): Any? {
    if (coverUrl == null) return null
    return if (coverUrl.startsWith("/") || coverUrl.startsWith("file://")) {
        File(coverUrl.removePrefix("file://"))
    } else if (coverUrl.contains("lain.bgm.tv")) {
        "https://wsrv.nl/?url=$coverUrl"
    } else {
        coverUrl
    }
}

/**
 * 生成稳定的封面内存缓存 key。
 *
 * 列表卡片与详情页共用同一 key，使详情页在共享元素转场时能以内存缓存图作占位，
 * 消除首帧空白导致的封面闪烁。
 */
fun coverMemoryCacheKey(coverUrl: String?): String? {
    if (coverUrl == null) return null
    return "cover_$coverUrl"
}

/**
 * 构建列表/详情页封面加载用的 [ImageRequest]，统一处理本地文件与网络图片的缓存策略。
 *
 * 缓存策略：
 * - 本地 File（由 [com.aiexile.animetrack.data.network.CoverDownloader] 持久化到
 *   `filesDir/anime_covers/`）：禁用 Coil diskCache，避免与 CoverDownloader 自管理目录
 *   形成双份存储；仅用内存缓存加速列表滑动。
 * - 网络 URL：走 Coil diskCache（`cacheDir/image_cache/`）。
 *
 * 调用方迁移：AnimeCard / AnimeCardStack / ScheduleScreen / AnimeDetailScreen 等
 * 可逐步替换内联的 ImageRequest.Builder 链为此函数（由各对应任务执行，本函数不强制调用方变更）。
 */
fun coverImageRequestForList(context: Context, coverUrl: String?): ImageRequest {
    val model = resolveCoverModel(coverUrl)
    val isLocalFile = model is File
    return ImageRequest.Builder(context)
        .data(model)
        .memoryCacheKey(coverMemoryCacheKey(coverUrl))
        .placeholderMemoryCacheKey(coverMemoryCacheKey(coverUrl))
        .apply {
            if (isLocalFile) {
                diskCachePolicy(CachePolicy.DISABLED)
            }
        }
        .build()
}

/**
 * 根据开播日期和总集数判断番剧是否已完结。
 *
 * 判定逻辑：
 * - 状态为 COMPLETED → 已完结
 * - 缺少开播日期或总集数 → 未完结
 * - 当前日期距开播日期超过 (总集数+1) 周 → 已完结
 */
fun computeIsFinished(
    airDate: String?,
    totalEpisodes: Int,
    localStatus: AnimeStatus
): Boolean {
    if (localStatus == AnimeStatus.COMPLETED) return true

    if (airDate == null || totalEpisodes <= 0) return false

    return try {
        // 兼容 UTC ISO 8601 格式（如 2020-01-10T16:00:00.000Z），统一转为 yyyy-MM-dd
        val normalizedDate = formatAirDate(airDate) ?: airDate
        val startDate = LocalDate.parse(normalizedDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val today = LocalDate.now()
        val diffWeeks = ChronoUnit.WEEKS.between(startDate, today)
        diffWeeks > (totalEpisodes + 1)
    } catch (e: Exception) {
        false
    }
}

/**
 * 根据异常类型生成友好的搜索错误提示。
 *
 * 优先判断 Bangumi 被墙：当请求目标是 Bangumi 域名且异常属于底层网络异常
 * （超时/连接失败/SSL握手失败/DNS解析失败）时，提示被墙。
 * 正常的 HTTP 错误（404/500 等 HttpException）不会被误判。
 */
fun resolveSearchError(e: Exception): String {
    val isBangumiHost = e.message?.let {
        it.contains("api.bgm.tv") || it.contains("bgm.tv")
    } ?: false

    // Bangumi 域名 + 底层网络异常 → 被墙
    if (isBangumiHost && (e is SocketTimeoutException || e is ConnectException
        || e is SSLHandshakeException || e is UnknownHostException)
    ) {
        return "Bangumi被墙，请挂代理后搜索"
    }

    // 非Bangumi域名的通用提示
    return when (e) {
        is UnknownHostException -> "网络未连接"
        is SocketTimeoutException -> "连接超时"
        else -> e.message?.takeIf { it.isNotBlank() } ?: "未知错误"
    }
}
