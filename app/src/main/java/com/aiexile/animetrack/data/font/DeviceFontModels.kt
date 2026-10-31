package com.aiexile.animetrack.data.font

/**
 * 本机字体中的一个字体面（一个文件 + TTC 内的 face 索引）。
 *
 * @param filePath 字体文件绝对路径
 * @param ttcIndex TTC 集合内的 face 索引；非 TTC 字体为 0
 * @param weight 字重（100–1000），仅用于挑选代表字面（Regular 优先）
 * @param italic 是否斜体
 */
data class DeviceFontFace(
    val filePath: String,
    val ttcIndex: Int,
    val weight: Int,
    val italic: Boolean
)

/**
 * 本机字体家族：同名家族下的多个字面（Regular/Bold/Italic 等）合并为一项。
 *
 * @param name TTF name 表中的家族名（如「OPPO Sans 4.0 SC」）；导入字体使用文件名
 * @param faces 该家族包含的字体面
 * @param isImported 是否为用户从文件导入并保存在应用私有目录的字体
 * @param contentTag 导入字体的内容标记（大小 + 修改时间），用于覆盖后刷新预览缓存
 */
data class DeviceFontFamily(
    val name: String,
    val faces: List<DeviceFontFace>,
    val isImported: Boolean = false,
    val contentTag: String = ""
) {
    /** 代表字面：非斜体、字重最接近 400 的字面，用于预览与全局应用 */
    val representativeFace: DeviceFontFace = faces.minWithOrNull(
        compareBy<DeviceFontFace>({ if (it.italic) 1 else 0 }, { kotlin.math.abs(it.weight - 400) })
    ) ?: faces.first()

    /** 稳定的预览缓存 key；导入字体附带内容标记，文件被覆盖后 key 变化并重新加载 */
    val key: String = buildString {
        append(representativeFace.filePath)
        append('#')
        append(representativeFace.ttcIndex)
        if (contentTag.isNotBlank()) {
            append('#')
            append(contentTag)
        }
    }
}

/**
 * 扫描候选：字体面 + 真实家族名。
 *
 * @param requireLanguageCoverage 是否必须覆盖当前界面语言。预装系统字体为 true，
 *   避免把 emoji/生僻文字兜底字体列出来；用户通过主题商店安装的字体为 false，
 *   即使是不含中文的装饰字体也应让用户看到并可选。
 */
internal data class DeviceFontCandidate(
    val familyName: String,
    val face: DeviceFontFace,
    val requireLanguageCoverage: Boolean
)

/** 从子家族名推断字重（扫描目录与导入字体共用） */
internal fun weightFromSubfamily(subfamily: String): Int {
    val lower = subfamily.lowercase()
    return when {
        lower.contains("thin") -> 100
        lower.contains("extralight") || lower.contains("extra light") -> 200
        lower.contains("light") -> 300
        lower.contains("medium") -> 500
        lower.contains("semibold") || lower.contains("semi bold") ||
            lower.contains("demibold") || lower.contains("demi bold") -> 600
        lower.contains("extrabold") || lower.contains("extra bold") ||
            lower.contains("black") || lower.contains("heavy") -> 900
        lower.contains("bold") -> 700
        else -> 400
    }
}

/** 子家族名是否表示斜体 */
internal fun isItalicSubfamily(subfamily: String): Boolean {
    val lower = subfamily.lowercase()
    return lower.contains("italic") || lower.contains("oblique")
}

/**
 * 已选择的本机字体（DataStore 持久化值）。
 *
 * 同时保存「家族名 + 文件 + ttcIndex」：文件路径可优先直接加载；
 * 系统更新导致路径变化时，再用家族名重新扫描解析。
 */
data class DeviceFontSelection(
    val familyName: String = "",
    val filePath: String = "",
    val ttcIndex: Int = 0
) {
    val isConfigured: Boolean
        get() = familyName.isNotBlank()

    fun toFace(): DeviceFontFace = DeviceFontFace(
        filePath = filePath,
        ttcIndex = ttcIndex,
        weight = 400,
        italic = false
    )
}
