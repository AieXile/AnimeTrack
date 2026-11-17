package com.aiexile.animetrack.model

/**
 * Kazumi 新式 API 规则的请求与响应映射配置。
 *
 * 规则用 `searchMode` / `chapterMode` 选择 XPath 或 API；API 模式下的请求模板
 * 支持 `@keyword`、`@source`、`@episodeUrl` 等占位符，响应按 JSONPath 取值。
 * 这里只保存配置本身，模板渲染与 JSONPath 求值在数据层完成。
 */
data class WebSourceApiRequest(
    val method: String = METHOD_GET,
    val url: String = "",
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
    /** [BODY_NONE]、[BODY_JSON] 或 [BODY_FORM]；仅 POST 请求会发送 body。 */
    val bodyType: String = BODY_NONE,
    /** body 的 JSON 原文；表单模式下既可是 JSON 对象，也可是已编码的表单串。 */
    val body: String = ""
) {
    val hasKeywordPlaceholder: Boolean
        get() = url.contains(WebSourceRule.KEYWORD_AT) ||
            query.values.any { it.contains(WebSourceRule.KEYWORD_AT) } ||
            body.contains(WebSourceRule.KEYWORD_AT)

    companion object {
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
        const val BODY_NONE = "none"
        const val BODY_JSON = "json"
        const val BODY_FORM = "form"
    }
}

/** 搜索接口：把列表节点映射为番名与详情地址。 */
data class WebSourceSearchApi(
    val request: WebSourceApiRequest = WebSourceApiRequest(),
    val listPath: String = "$.data[*]",
    val namePath: String = "$.name",
    val sourcePath: String = "$.url",
    /** 详情地址模板；为空时直接使用 [sourcePath] 的原始值（如站点 ID）。 */
    val sourceTemplate: String = ""
) {
    val isUsable: Boolean
        get() = request.isAbsoluteHttpUrl &&
            listPath.isNotBlank() &&
            namePath.isNotBlank() &&
            sourcePath.isNotBlank() &&
            request.hasKeywordPlaceholder
}

/** 选集接口的两种响应形态。 */
data class WebSourceChapterApi(
    val request: WebSourceApiRequest = WebSourceApiRequest(),
    /** [FORMAT_DELIMITED] 或 [FORMAT_NESTED]。 */
    val format: String = FORMAT_NESTED,
    val roadsPath: String = "$.data.roads[*]",
    val roadNamePath: String = "$.name",
    val episodesPath: String = "$.episodes[*]",
    val episodeNamePath: String = "$.name",
    val episodeUrlPath: String = "$.url",
    val roadNamesPath: String = "",
    val roadEpisodesPath: String = "",
    val roadSeparator: String = "$$$",
    val episodeSeparator: String = "#",
    val fieldSeparator: String = "$",
    /** 在章节响应上取值的命名变量，供 [episodePage] 模板使用。 */
    val variables: Map<String, String> = emptyMap(),
    /** 最终播放页模板；为空时直接使用响应里取到的地址。 */
    val episodePage: WebSourceEpisodePage? = null
) {
    val isDelimited: Boolean
        get() = format == FORMAT_DELIMITED

    val isUsable: Boolean
        get() {
            if (!request.isAbsoluteHttpUrl) return false
            if (isDelimited) {
                return roadNamesPath.isNotBlank() &&
                    roadEpisodesPath.isNotBlank() &&
                    roadSeparator.isNotEmpty() &&
                    episodeSeparator.isNotEmpty() &&
                    fieldSeparator.isNotEmpty()
            }
            if (episodesPath.isBlank() || episodeNamePath.isBlank()) return false
            if (episodeUrlPath.isBlank() && episodePage == null) return false
            return episodePage?.url?.isNotBlank() ?: true
        }

    companion object {
        const val FORMAT_DELIMITED = "delimited"
        const val FORMAT_NESTED = "nested"
    }
}

/** 播放页地址模板与附加查询参数。 */
data class WebSourceEpisodePage(
    val url: String = "",
    val query: Map<String, String> = emptyMap()
)

private val WebSourceApiRequest.isAbsoluteHttpUrl: Boolean
    get() = url.startsWith("http://", ignoreCase = true) ||
        url.startsWith("https://", ignoreCase = true)
