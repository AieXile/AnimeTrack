package com.aiexile.animetrack.data.font

import android.graphics.Typeface
import java.io.File

/**
 * 本机字体面 → [Typeface] 的加载入口。
 *
 * 用 [Typeface.Builder] 指定 TTC 的 face 索引（`Typeface.createFromFile` 只会取第一个
 * face，CJK TTC 会错误地显示成日文字形）；`setFallback("sans-serif")` 保证所选字体
 * 缺少标点/特殊字符时回退到系统字体，而不是显示豆腐块。
 */
object DeviceFontTypefaceLoader {

    fun load(face: DeviceFontFace): Typeface? {
        if (face.filePath.isBlank()) return null
        val file = File(face.filePath)
        if (!file.isFile || !file.canRead()) return null
        return runCatching {
            Typeface.Builder(file)
                .setTtcIndex(face.ttcIndex)
                .setFallback("sans-serif")
                .build()
        }.getOrNull()
    }
}
