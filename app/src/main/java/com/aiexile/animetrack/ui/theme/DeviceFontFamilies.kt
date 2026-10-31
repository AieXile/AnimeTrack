package com.aiexile.animetrack.ui.theme

import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.text.font.FontFamily
import com.aiexile.animetrack.data.font.DeviceFontFace
import com.aiexile.animetrack.data.font.DeviceFontFamily
import com.aiexile.animetrack.data.font.DeviceFontTypefaceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本机字体的 Compose 适配与预览缓存。
 *
 * CJK 字体常为 30MB+ 的 TTC，预览列表按可见项懒加载；缓存只保留少量项，
 * 避免快速滚动时把大量字体文件映射常驻内存。
 */
object DeviceFontFamilies {

    private const val PREVIEW_CACHE_SIZE = 4

    private val previewCache = LruCache<String, FontFamily>(PREVIEW_CACHE_SIZE)

    fun toFontFamily(face: DeviceFontFace): FontFamily? =
        DeviceFontTypefaceLoader.load(face)?.let { FontFamily(it) }

    fun cachedPreview(key: String): FontFamily? = previewCache.get(key)

    fun loadPreview(family: DeviceFontFamily): FontFamily? {
        previewCache.get(family.key)?.let { return it }
        val loaded = toFontFamily(family.representativeFace) ?: return null
        previewCache.put(family.key, loaded)
        return loaded
    }
}

/** 用字体自身渲染预览；加载完成前回退默认字体，避免首帧空白 */
@Composable
fun rememberDeviceFontPreview(family: DeviceFontFamily): FontFamily? {
    val initial = DeviceFontFamilies.cachedPreview(family.key)
    val familyState = produceState(initial, family.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { DeviceFontFamilies.loadPreview(family) }
        }
    }
    return familyState.value
}
