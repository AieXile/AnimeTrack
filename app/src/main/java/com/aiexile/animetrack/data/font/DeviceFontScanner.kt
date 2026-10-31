package com.aiexile.animetrack.data.font

import android.content.Context
import android.os.Build
import com.aiexile.animetrack.data.AppLanguage
import com.aiexile.animetrack.data.log.AppLogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 本机字体扫描器：识别系统已安装、且能覆盖当前界面语言的字体家族。
 *
 * 数据源：
 * - API 29+：`SystemFonts#getAvailableFonts()`，可拿到系统字体配置中的全部字体
 *   （包括系统字体商店安装到 /data/fonts 的字体、OEM 自定义字体）；
 * - API 26–28：没有该公开 API，回退扫描可读的系统字体目录。
 *
 * 两步过滤，避免把系统给生僻文字/emoji 准备的兜底字体展示给用户：
 * 1. 语言过滤：只保留具名字体家族与语言标签匹配当前界面语言的 fallback 字体；
 * 2. 字形过滤：代表字面必须能渲染当前语言的样字（中文「中」/英文「A」），
 *    剔除纯数字、符号、emoji 字体。
 *
 * 结果按进程缓存（缓存 key 为界面语言），供设置页与启动时解析复用。
 */
object DeviceFontScanner {

    private const val SAMPLE_CHINESE = 0x4E2D // 「中」
    private const val SAMPLE_ENGLISH = 'A'.code

    private val mutex = Mutex()

    private var cachedLanguage: AppLanguage? = null
    private var cachedFamilies: List<DeviceFontFamily> = emptyList()

    suspend fun families(
        context: Context,
        language: AppLanguage,
        forceRefresh: Boolean = false
    ): List<DeviceFontFamily> = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!forceRefresh && cachedLanguage == language) return@withLock cachedFamilies
            val scanned = runCatching { scan(language) }.getOrElse { emptyList() }
            cachedLanguage = language
            cachedFamilies = scanned
            scanned
        }
    }

    private fun scan(language: AppLanguage): List<DeviceFontFamily> {
        val candidates = buildList {
            // API 29+ 的系统字体配置（含 /data/fonts/files 里的可更新字体）；
            // 低版本没有该系统 API，只能扫描可读字体目录。
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                addAll(SystemFontSource.candidates(language))
            }
            // 额外合并可读的 OEM/用户字体目录：主题商店字体会落在这些位置
            // （如 ColorOS 的 /data/format_unclear/font），而系统字体配置里未必有具名家族。
            addAll(
                directoryCandidates(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        EXTRA_FONT_DIRS
                    } else {
                        LEGACY_FONT_DIRS
                    }
                )
            )
        }
        if (candidates.isEmpty()) return emptyList()

        // 同一字体可能同时以系统路径和符号链接路径出现，用规范化路径去重
        val uniqueCandidates = LinkedHashMap<String, DeviceFontCandidate>()
        candidates.forEach { candidate ->
            uniqueCandidates.putIfAbsent(canonicalFaceKey(candidate.face), candidate)
        }

        val sample = languageSample(language)
        val families = uniqueCandidates.values
            .groupBy { it.familyName }
            .mapNotNull { (familyName, entries) ->
                val faces = entries.map { it.face }
                val representative = faces.minWithOrNull(representativeComparator) ?: return@mapNotNull null
                // 只要该家族有一条用户安装的字体面，就视为用户主动安装的字体，不做语言覆盖过滤
                val requireCoverage = entries.all { it.requireLanguageCoverage }
                val covered = !requireCoverage || FontNameReader.supports(
                    File(representative.filePath),
                    representative.ttcIndex,
                    sample
                )
                if (!covered) return@mapNotNull null
                val name = familyName.trim().ifBlank { return@mapNotNull null }
                DeviceFontFamily(
                    name = name,
                    faces = faces.distinctBy { it.filePath to it.ttcIndex }
                )
            }
            .sortedBy { it.name.lowercase() }
        AppLogManager.d(
            TAG,
            "本机字体扫描($language): ${families.size} 项 ${families.map { it.name }}"
        )
        return families
    }

    private fun canonicalFaceKey(face: DeviceFontFace): String {
        val path = runCatching { File(face.filePath).canonicalPath }.getOrDefault(face.filePath)
        return "$path#${face.ttcIndex}"
    }

    private val representativeComparator = compareBy<DeviceFontFace>(
        { if (it.italic) 1 else 0 },
        { kotlin.math.abs(it.weight - 400) }
    )

    private fun languageSample(language: AppLanguage): Int = when (language) {
        AppLanguage.ENGLISH -> SAMPLE_ENGLISH
        else -> SAMPLE_CHINESE
    }

    /**
     * 扫描目录中的字体文件。
     *
     * API 29+ 上系统字体配置已覆盖 /system 字体，目录扫描主要用于补齐 OEM/主题商店
     * 落在可读数据目录里的字体；该路径没有语言标签，因此完全依赖 cmap 字形过滤。
     */
    private fun directoryCandidates(dirs: List<String>): List<DeviceFontCandidate> {
        val result = mutableListOf<DeviceFontCandidate>()
        dirs.forEach dirLoop@{ dirPath ->
            val files = File(dirPath).listFiles() ?: return@dirLoop
            files.forEach fileLoop@{ file ->
                if (!file.isFile || !file.canRead() || !isFontFile(file.name)) return@fileLoop
                val faceCount = FontNameReader.faceCount(file)
                repeat(faceCount) { faceIndex ->
                    val names = FontNameReader.read(file, faceIndex) ?: return@repeat
                    val subfamily = names.subfamily.lowercase()
                    val face = DeviceFontFace(
                        filePath = file.absolutePath,
                        ttcIndex = faceIndex,
                        weight = weightFromSubfamily(subfamily),
                        italic = isItalicSubfamily(subfamily)
                    )
                    result += DeviceFontCandidate(
                        familyName = names.family,
                        face = face,
                        requireLanguageCoverage = !isUserInstalledPath(file.absolutePath)
                    )
                }
            }
        }
        return result
    }

    private val SYSTEM_FONT_DIRS = listOf(
        "/system/fonts",
        "/product/fonts",
        "/system_ext/fonts",
        "/vendor/fonts"
    )

    /** API 29+ 已由 SystemFonts 覆盖 /system 目录，只额外扫描数据分区里的 OEM 字体 */
    private val EXTRA_FONT_DIRS = listOf(
        // ColorOS 等 OEM 主题引擎的可读字体目录：主题商店字体常以真实文件/符号链接落在这里
        "/data/format_unclear/font",
        "/data/oplus/fonts",
        "/data/theme/fonts",
        "/product/fonts",
        "/system_ext/fonts",
        "/vendor/fonts"
    )

    /** API 26–28 没有 SystemFonts，需要自己扫全部可读目录 */
    private val LEGACY_FONT_DIRS = SYSTEM_FONT_DIRS + EXTRA_FONT_DIRS

    private const val TAG = "DeviceFont"
}
