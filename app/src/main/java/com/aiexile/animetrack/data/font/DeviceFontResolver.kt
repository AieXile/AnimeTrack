package com.aiexile.animetrack.data.font

import android.content.Context
import android.graphics.Typeface
import com.aiexile.animetrack.data.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把持久化的本机字体选择解析为可用的 [Typeface]。
 *
 * 优先直接加载记录的文件与 ttcIndex；系统更新/主题切换导致文件失效时，
 * 再按家族名重新扫描（扫描结果有进程级缓存）。都失败则返回 null，由调用方
 * 回退系统默认字体。
 */
object DeviceFontResolver {

    suspend fun resolve(
        context: Context,
        selection: DeviceFontSelection,
        language: AppLanguage
    ): Typeface? = withContext(Dispatchers.IO) {
        if (!selection.isConfigured) return@withContext null

        DeviceFontTypefaceLoader.load(selection.toFace())?.let { return@withContext it }

        DeviceFontScanner.families(context, language)
            .firstOrNull { it.name.equals(selection.familyName, ignoreCase = true) }
            ?.let { DeviceFontTypefaceLoader.load(it.representativeFace) }
    }
}
