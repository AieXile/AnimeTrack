package com.aiexile.animetrack.data.websource

import com.aiexile.animetrack.model.WebSourceApiRequest
import com.aiexile.animetrack.model.WebSourceChapterApi
import com.aiexile.animetrack.model.WebSourceEpisodePage
import com.aiexile.animetrack.model.WebSourceRule
import com.aiexile.animetrack.model.WebSourceSearchApi
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Kazumi 规则 JSON 与 [WebSourceRule] 之间的编解码。
 *
 * 支持三种输入形态：
 * 1. 单条规则对象；
 * 2. 规则数组；
 * 3. 以名称为 key 的规则映射（KazumiRules 的分发格式）。
 *
 * 不完整或运行模式不支持的条目计入 [Decoded.skipped]，通过 [Decoded.issues]
 * 给出原因；整批均不可用时，异常同样携带原因供界面显示。
 */
object WebSourceRuleCodec {

    enum class IssueReason(val rejectsRule: Boolean = true) {
        INCOMPLETE, DUPLICATE, POST, WEB_PLAYER_ONLY, LEGACY_PARSER,
        NON_WEBVIEW_PARSER, ANTI_CRAWLER, DEPRECATED, INVALID_OPTION, INVALID_HEADERS,
        AD_BLOCKING_IGNORED(false)
    }

    data class ImportIssue(val ruleName: String?, val reason: IssueReason)

    class NoUsableRulesException(val issues: List<ImportIssue>) :
        IllegalArgumentException("no usable rule")

    data class Decoded(
        val rules: List<WebSourceRule>,
        val skipped: List<String>,
        val issues: List<ImportIssue> = emptyList()
    )

    fun decode(raw: String): Result<Decoded> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty json"))

        val root = runCatching { JsonParser.parseString(trimmed) }
            .getOrElse { return Result.failure(it) }
        val candidates = collectCandidates(root)
            ?: return Result.failure(IllegalArgumentException("unsupported json shape"))
        if (candidates.isEmpty()) {
            return Result.failure(IllegalArgumentException("no rule object"))
        }

        val rules = mutableListOf<WebSourceRule>()
        val skipped = mutableListOf<String>()
        val issues = mutableListOf<ImportIssue>()
        val seen = mutableSetOf<String>()
        candidates.forEach { (fallbackName, obj) ->
            val name = obj.firstString("name")?.trim()?.takeIf { it.isNotBlank() } ?: fallbackName
            val reasons = compatibilityIssues(obj).toMutableList()
            val rule = parseRule(obj, fallbackName)
            if (rule == null) reasons += IssueReason.INCOMPLETE
            if (rule != null && !validHeaders(rule)) reasons += IssueReason.INVALID_HEADERS
            if (reasons.any { it.rejectsRule }) {
                skipped += name.orEmpty()
                issues += reasons.filter { it.rejectsRule }.distinct().map { ImportIssue(name, it) }
                return@forEach
            }
            when {
                rule != null && seen.add(rule.name) -> {
                    rules += rule
                    issues += reasons.map { ImportIssue(name, it) }
                }
                rule != null -> {
                    skipped += rule.name
                    issues += ImportIssue(rule.name, IssueReason.DUPLICATE)
                }
            }
        }
        if (rules.isEmpty()) {
            return Result.failure(NoUsableRulesException(issues))
        }
        return Result.success(Decoded(rules, skipped, issues))
    }

    private fun compatibilityIssues(obj: JsonObject): List<IssueReason> = buildList {
        fun option(key: String, unsupported: Boolean, reason: IssueReason) {
            val value = obj.get(key) ?: return
            val flag = value.takeIf { it.isJsonPrimitive }?.asString?.toBooleanStrictOrNull()
            when (flag) {
                null -> add(IssueReason.INVALID_OPTION)
                unsupported -> add(reason)
                else -> Unit
            }
        }

        // API 规则的 GET/POST 由 searchApiConfig.request.method 决定，usePost 只作用于 XPath。
        if (obj.firstString("searchMode")?.trim() != WebSourceRule.MODE_API) {
            option("usePost", true, IssueReason.POST)
        }
        option("useNativePlayer", false, IssueReason.WEB_PLAYER_ONLY)
        option("useLegacyParser", true, IssueReason.LEGACY_PARSER)
        option("useWebview", false, IssueReason.NON_WEBVIEW_PARSER)
        option("antiCrawlerEnabled", true, IssueReason.ANTI_CRAWLER)
        option("deprecated", true, IssueReason.DEPRECATED)
        option("adBlocker", true, IssueReason.AD_BLOCKING_IGNORED)
        // 新版规则的防爬开关写在 antiCrawlerConfig.enabled 里。
        if (!obj.has("antiCrawlerEnabled")) {
            val enabled = obj.get("antiCrawlerConfig")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject?.get("enabled")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString?.toBooleanStrictOrNull()
            when (enabled) {
                null -> Unit
                true -> add(IssueReason.ANTI_CRAWLER)
                false -> Unit
            }
        }
    }

    private fun validHeaders(rule: WebSourceRule): Boolean = runCatching {
        val headers = Headers.Builder().add("User-Agent", rule.effectiveUserAgent)
        rule.effectiveReferer?.let {
            require(it.toHttpUrlOrNull() != null)
            headers.add("Referer", it)
        }
        listOfNotNull(rule.searchApi?.request, rule.chapterApi?.request).forEach { request ->
            request.headers.forEach { (key, value) -> headers.add(key, value) }
        }
        headers.build()
        true
    }.getOrDefault(false)

    fun encode(rules: List<WebSourceRule>): String {
        val array = JsonArray()
        rules.forEach { rule -> array.add(encodeRule(rule)) }
        return array.toString()
    }

    private fun collectCandidates(root: JsonElement): List<Pair<String?, JsonObject>>? = when {
        root.isJsonArray -> root.asJsonArray.mapNotNull { element ->
            element.takeIf { it.isJsonObject }?.asJsonObject?.let { null to it }
        }

        root.isJsonObject && looksLikeRule(root.asJsonObject) ->
            listOf(null to root.asJsonObject)

        root.isJsonObject -> root.asJsonObject.entrySet().mapNotNull { (key, value) ->
            value.takeIf { it.isJsonObject }?.asJsonObject?.let { key to it }
        }

        else -> null
    }

    private fun parseRule(obj: JsonObject, fallbackName: String?): WebSourceRule? {
        val rule = WebSourceRule(
            name = obj.firstString("name")?.trim().takeUnless { it.isNullOrBlank() }
                ?: fallbackName.orEmpty().trim(),
            version = obj.firstString("version").orEmpty().trim(),
            baseUrl = obj.firstString("baseURL", "baseUrl").orEmpty().trim(),
            searchUrl = obj.firstString("searchURL", "searchUrl").orEmpty().trim(),
            searchList = obj.firstString("searchList").orEmpty().trim(),
            searchName = obj.firstString("searchName").orEmpty().trim(),
            searchResult = obj.firstString("searchResult").orEmpty().trim(),
            chapterRoads = obj.firstString("chapterRoads").orEmpty().trim(),
            chapterResult = obj.firstString("chapterResult").orEmpty().trim(),
            userAgent = obj.firstString("userAgent").orEmpty().trim(),
            referer = obj.firstString("referer").orEmpty().trim(),
            searchMode = modeOf(obj.firstString("searchMode")),
            chapterMode = modeOf(obj.firstString("chapterMode")),
            searchApi = obj.objectOrNull("searchApiConfig")?.let(::parseSearchApi),
            chapterApi = obj.objectOrNull("chapterApiConfig")?.let(::parseChapterApi)
        )
        return rule.takeIf { it.isUsable && apiConfigsValid(it) }
    }

    private fun modeOf(value: String?): String =
        if (value?.trim() == WebSourceRule.MODE_API) WebSourceRule.MODE_API
        else WebSourceRule.MODE_XPATH

    private fun parseSearchApi(obj: JsonObject): WebSourceSearchApi = WebSourceSearchApi(
        request = parseRequest(obj.objectOrNull("request")),
        listPath = obj.defaulted("listPath", "$.data[*]"),
        namePath = obj.defaulted("namePath", "$.name"),
        sourcePath = obj.defaulted("sourcePath", "$.url"),
        sourceTemplate = obj.firstString("sourceTemplate")?.trim().orEmpty()
    )

    private fun parseChapterApi(obj: JsonObject): WebSourceChapterApi {
        val format = if (obj.firstString("format")?.trim() == WebSourceChapterApi.FORMAT_DELIMITED) {
            WebSourceChapterApi.FORMAT_DELIMITED
        } else {
            WebSourceChapterApi.FORMAT_NESTED
        }
        return WebSourceChapterApi(
            request = parseRequest(obj.objectOrNull("request")),
            format = format,
            roadsPath = obj.defaulted("roadsPath", "$.data.roads[*]"),
            roadNamePath = obj.defaulted("roadNamePath", "$.name"),
            episodesPath = obj.defaulted("episodesPath", "$.episodes[*]"),
            episodeNamePath = obj.defaulted("episodeNamePath", "$.name"),
            episodeUrlPath = obj.defaulted("episodeUrlPath", "$.url"),
            roadNamesPath = obj.firstString("roadNamesPath")?.trim().orEmpty(),
            roadEpisodesPath = obj.firstString("roadEpisodesPath")?.trim().orEmpty(),
            roadSeparator = obj.firstString("roadSeparator") ?: "$$$",
            episodeSeparator = obj.firstString("episodeSeparator") ?: "#",
            fieldSeparator = obj.firstString("fieldSeparator") ?: "$",
            variables = obj.stringMap("variables"),
            episodePage = obj.objectOrNull("episodePage")?.let {
                WebSourceEpisodePage(
                    url = it.firstString("url")?.trim().orEmpty(),
                    query = it.stringMap("query")
                )
            }
        )
    }

    private fun parseRequest(obj: JsonObject?): WebSourceApiRequest {
        if (obj == null) return WebSourceApiRequest()
        val bodyType = when (obj.firstString("bodyType")?.trim()?.lowercase()) {
            WebSourceApiRequest.BODY_JSON -> WebSourceApiRequest.BODY_JSON
            WebSourceApiRequest.BODY_FORM -> WebSourceApiRequest.BODY_FORM
            else -> WebSourceApiRequest.BODY_NONE
        }
        val body = obj.get("body")?.takeUnless { it.isJsonNull }?.let { element ->
            if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
                element.asString
            } else {
                element.toString()
            }
        }.orEmpty()
        return WebSourceApiRequest(
            method = obj.firstString("method")?.trim()?.uppercase().orEmpty()
                .ifEmpty { WebSourceApiRequest.METHOD_GET },
            url = obj.firstString("url")?.trim().orEmpty(),
            headers = obj.stringMap("headers"),
            query = obj.stringMap("query"),
            bodyType = bodyType,
            body = body
        )
    }

    /** 只校验规则实际启用的模式；未启用的一侧允许保留给编辑器。 */
    private fun apiConfigsValid(rule: WebSourceRule): Boolean = runCatching {
        if (rule.isApiSearch) {
            val api = rule.searchApi ?: return false
            if (!methodSupported(api.request)) return false
            paths(api.listPath, api.namePath, api.sourcePath)
            if (api.sourceTemplate.isNotBlank() && !api.sourceTemplate.contains("@source")) {
                return false
            }
        }
        if (rule.isApiChapter) {
            val api = rule.chapterApi ?: return false
            if (!methodSupported(api.request)) return false
            api.variables.values.forEach(JsonPathLite::validate)
            if (api.isDelimited) {
                paths(api.roadNamesPath, api.roadEpisodesPath)
            } else {
                paths(api.episodesPath, api.episodeNamePath)
                if (api.roadsPath.isNotBlank()) JsonPathLite.validate(api.roadsPath)
                if (api.roadNamePath.isNotBlank()) JsonPathLite.validate(api.roadNamePath)
                if (api.episodeUrlPath.isNotBlank()) JsonPathLite.validate(api.episodeUrlPath)
            }
        }
        true
    }.getOrDefault(false)

    private fun methodSupported(request: WebSourceApiRequest): Boolean =
        request.method.uppercase() in setOf(
            WebSourceApiRequest.METHOD_GET,
            WebSourceApiRequest.METHOD_POST
        )

    private fun paths(vararg expressions: String) {
        expressions.forEach(JsonPathLite::validate)
    }

    private fun encodeRule(rule: WebSourceRule): JsonObject = JsonObject().apply {
        addProperty("name", rule.name)
        addProperty("version", rule.version)
        addProperty("baseURL", rule.baseUrl)
        addProperty("searchURL", rule.searchUrl)
        addProperty("searchList", rule.searchList)
        addProperty("searchName", rule.searchName)
        addProperty("searchResult", rule.searchResult)
        addProperty("chapterRoads", rule.chapterRoads)
        addProperty("chapterResult", rule.chapterResult)
        addProperty("userAgent", rule.userAgent)
        addProperty("referer", rule.referer)
        addProperty("searchMode", rule.searchMode)
        addProperty("chapterMode", rule.chapterMode)
        rule.searchApi?.let { add("searchApiConfig", encodeSearchApi(it)) }
        rule.chapterApi?.let { add("chapterApiConfig", encodeChapterApi(it)) }
        addProperty("useWebview", true)
        addProperty("useNativePlayer", true)
    }

    private fun encodeSearchApi(api: WebSourceSearchApi): JsonObject = JsonObject().apply {
        add("request", encodeRequest(api.request))
        addProperty("listPath", api.listPath)
        addProperty("namePath", api.namePath)
        addProperty("sourcePath", api.sourcePath)
        addProperty("sourceTemplate", api.sourceTemplate)
    }

    private fun encodeChapterApi(api: WebSourceChapterApi): JsonObject = JsonObject().apply {
        add("request", encodeRequest(api.request))
        addProperty("format", api.format)
        addProperty("roadsPath", api.roadsPath)
        addProperty("roadNamePath", api.roadNamePath)
        addProperty("episodesPath", api.episodesPath)
        addProperty("episodeNamePath", api.episodeNamePath)
        addProperty("episodeUrlPath", api.episodeUrlPath)
        addProperty("roadNamesPath", api.roadNamesPath)
        addProperty("roadEpisodesPath", api.roadEpisodesPath)
        addProperty("roadSeparator", api.roadSeparator)
        addProperty("episodeSeparator", api.episodeSeparator)
        addProperty("fieldSeparator", api.fieldSeparator)
        add("variables", stringMapToJson(api.variables))
        api.episodePage?.let { page ->
            add("episodePage", JsonObject().apply {
                addProperty("url", page.url)
                add("query", stringMapToJson(page.query))
            })
        }
    }

    private fun encodeRequest(request: WebSourceApiRequest): JsonObject = JsonObject().apply {
        addProperty("method", request.method)
        addProperty("url", request.url)
        if (request.headers.isNotEmpty()) add("headers", stringMapToJson(request.headers))
        if (request.query.isNotEmpty()) add("query", stringMapToJson(request.query))
        if (request.bodyType != WebSourceApiRequest.BODY_NONE) {
            addProperty("bodyType", request.bodyType)
        }
        if (request.body.isNotBlank()) {
            val parsed = runCatching { JsonParser.parseString(request.body) }.getOrNull()
            if (parsed != null && (parsed.isJsonObject || parsed.isJsonArray)) {
                add("body", parsed)
            } else {
                addProperty("body", request.body)
            }
        }
    }

    private fun JsonObject.defaulted(key: String, default: String): String =
        firstString(key)?.trim() ?: default

    private fun stringMapToJson(map: Map<String, String>): JsonObject = JsonObject().apply {
        map.forEach { (key, value) -> addProperty(key, value) }
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.stringMap(key: String): Map<String, String> {
        val obj = objectOrNull(key) ?: return emptyMap()
        return obj.entrySet().mapNotNull { (name, value) ->
            value.takeIf { it.isJsonPrimitive }?.let { name to it.asString }
        }.toMap()
    }

    private fun looksLikeRule(obj: JsonObject): Boolean =
        obj.has("searchURL") || obj.has("searchUrl") ||
            obj.has("searchList") || obj.has("chapterResult") ||
            obj.has("searchApiConfig") || obj.has("chapterApiConfig")

    private fun JsonObject.firstString(vararg keys: String): String? {
        keys.forEach { key ->
            val element = get(key) ?: return@forEach
            if (element.isJsonPrimitive) return element.asString
        }
        return null
    }
}
