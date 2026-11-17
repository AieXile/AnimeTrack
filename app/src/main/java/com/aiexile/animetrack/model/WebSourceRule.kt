package com.aiexile.animetrack.model

/**
 * 用户导入的网页数据源规则。
 *
 * 字段与 Kazumi 规则 JSON（Predidit/KazumiRules）保持一致，并兼容 Animeko Selector
 * 的 `{keyword}` 占位符写法。不支持的运行模式由导入器检查并说明原因。
 */
data class WebSourceRule(
    val name: String,
    val version: String = "",
    val baseUrl: String = "",
    val searchUrl: String = "",
    /** 搜索结果列表容器 */
    val searchList: String = "",
    /** 相对列表容器的条目名称 */
    val searchName: String = "",
    /** 相对列表容器的条目链接 */
    val searchResult: String = "",
    /** 详情页的线路列表容器；为空时 chapterResult 直接作用于详情页 */
    val chapterRoads: String = "",
    /** 相对线路容器的剧集链接 */
    val chapterResult: String = "",
    val userAgent: String = "",
    val referer: String = "",
    /** [MODE_XPATH] 或 [MODE_API]，与 Kazumi 的 `searchMode` 一致。 */
    val searchMode: String = MODE_XPATH,
    /** [MODE_XPATH] 或 [MODE_API]，与 Kazumi 的 `chapterMode` 一致。 */
    val chapterMode: String = MODE_XPATH,
    val searchApi: WebSourceSearchApi? = null,
    val chapterApi: WebSourceChapterApi? = null
) {
    val isApiSearch: Boolean get() = searchMode == MODE_API
    val isApiChapter: Boolean get() = chapterMode == MODE_API

    /** 搜索词占位符：Kazumi 用 `@keyword`，Animeko Selector 用 `{keyword}`。 */
    val hasKeywordPlaceholder: Boolean
        get() = searchUrl.contains(KEYWORD_AT) || searchUrl.contains(KEYWORD_BRACED) ||
            searchApi?.request?.hasKeywordPlaceholder == true

    /** 是否具备完成「搜索 → 选集」链路的最小字段。 */
    val isUsable: Boolean
        get() {
            if (name.isBlank()) return false
            val searchReady = if (isApiSearch) {
                searchApi?.isUsable == true
            } else {
                hasKeywordPlaceholder &&
                    searchList.isNotBlank() &&
                    searchName.isNotBlank() &&
                    searchResult.isNotBlank()
            }
            val chapterReady = if (isApiChapter) {
                chapterApi?.isUsable == true
            } else {
                chapterResult.isNotBlank()
            }
            return searchReady && chapterReady
        }

    /** 搜索与播放使用同一 UA；空规则字段沿用普通移动浏览器 UA。 */
    val effectiveUserAgent: String
        get() = userAgent.ifBlank { DEFAULT_USER_AGENT }

    /** 显式 Referer 优先，缺省使用数据源首页。 */
    val effectiveReferer: String?
        get() = referer.ifBlank { baseUrl }.takeIf { it.isNotBlank() }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
        const val KEYWORD_AT = "@keyword"
        const val KEYWORD_BRACED = "{keyword}"
        const val MODE_XPATH = "xpath"
        const val MODE_API = "api"
    }
}
