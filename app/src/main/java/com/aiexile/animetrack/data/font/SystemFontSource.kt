package com.aiexile.animetrack.data.font

import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import com.aiexile.animetrack.data.AppLanguage

/**
 * Android 10（API 29）起的系统字体来源。
 *
 * 与扫描器拆成独立文件：低版本设备上该类不会被加载，避免 API 26–28 在
 * 类校验阶段解析不存在的 `android.graphics.fonts.*` 类。
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal object SystemFontSource {

    /**
     * 系统已安装字体中，可作为界面字体的候选。
     *
     * 过滤规则：
     * - 具名字体家族（字体配置中没有语言标签，localeList 为空）属于通用 UI 字体；
     * - 其余是系统按脚本兜底的字体，只保留语言标签与当前界面语言匹配的（中文匹配 zh，
     *   英文匹配 en），排除阿拉伯语、泰语、emoji 等上百个兜底字体；
     * - 同一文件的多个字重只保留一个条目（变量字体会为每个字重列出一项）。
     */
    fun candidates(language: AppLanguage): List<DeviceFontCandidate> {
        val result = LinkedHashMap<String, DeviceFontCandidate>()
        android.graphics.fonts.SystemFonts.getAvailableFonts().forEach { font ->
            val file = font.file ?: return@forEach
            if (!file.isFile || !file.canRead()) return@forEach
            if (!isFontFile(file.name)) return@forEach
            if (!matchesLanguage(font.localeList, language)) return@forEach

            val style = font.style
            val face = DeviceFontFace(
                filePath = file.absolutePath,
                ttcIndex = font.ttcIndex,
                weight = style.weight,
                italic = style.slant != 0
            )
            val faceKey = "${face.filePath}#${face.ttcIndex}"
            val names = FontNameReader.read(file, face.ttcIndex) ?: return@forEach
            val candidate = DeviceFontCandidate(
                familyName = names.family,
                face = face,
                // 用户通过字体商店安装的字体落在数据分区：不做语言覆盖过滤，
                // 否则纯英文字体/装饰字体在中文界面下会被隐藏
                requireLanguageCoverage = !isUserInstalledPath(file.absolutePath)
            )
            val existing = result[faceKey]
            if (existing == null || weightDistance(face) < weightDistance(existing.face)) {
                result[faceKey] = candidate
            }
        }
        return result.values.toList()
    }

    private fun matchesLanguage(locales: LocaleList, language: AppLanguage): Boolean {
        // 具名字体家族没有语言标签：它们是系统默认字体族（含 OEM 正文字体），直接保留
        if (locales.isEmpty) return true
        val tags = locales.toLanguageTags()
        return when (language) {
            AppLanguage.ENGLISH -> tags.contains("en", ignoreCase = true)
            else -> tags.contains("zh", ignoreCase = true)
        }
    }

    private fun weightDistance(face: DeviceFontFace): Int =
        kotlin.math.abs(face.weight - 400) + if (face.italic) 1000 else 0
}

internal fun isFontFile(fileName: String): Boolean {
    val lower = fileName.lowercase()
    return lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc")
}

/** 字体文件是否位于数据分区的用户/主题商店字体目录 */
internal fun isUserInstalledPath(filePath: String): Boolean =
    filePath.startsWith("/data/fonts/") ||
        filePath.startsWith("/data/format_unclear/") ||
        filePath.startsWith("/data/oplus/fonts/") ||
        filePath.startsWith("/data/theme/fonts/")
