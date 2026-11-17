package com.aiexile.animetrack.data.websource

import com.aiexile.animetrack.model.WebSourceApiRequest
import com.aiexile.animetrack.model.WebSourceChapterApi
import com.aiexile.animetrack.model.WebSourceEntry
import com.aiexile.animetrack.model.WebSourceEpisode
import com.aiexile.animetrack.model.WebSourceRule
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

/** API 规则配置或响应无法处理。 */
class WebSourceApiException(message: String) : Exception(message)

/** 模板已经渲染完成、可直接交给 OkHttp 的 API 请求。 */
data class WebSourceApiCall(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val query: Map<String, String>,
    val body: String?,
    val contentType: String?
)

/**
 * Kazumi API 规则的纯计算部分：渲染请求模板、解析 JSON 响应。
 *
 * 这里不发起网络请求，便于单元测试；HTTP 由 [WebSourceHttpClient.executeApi] 完成。
 */
object WebSourceApiParser {

    private const val MAX_ENTRIES = 60
    private const val MAX_EPISODES = 500

    private val missingVariable = Regex("(?<![A-Za-z0-9_])@([A-Za-z_][A-Za-z0-9_]*)")
    private val exactVariable = Regex("^@([A-Za-z_][A-Za-z0-9_]*)$")

    fun buildSearchCall(rule: WebSourceRule, keyword: String): WebSourceApiCall {
        val api = rule.searchApi ?: throw WebSourceApiException("missing searchApiConfig")
        return buildCall(api.request, mapOf("keyword" to keyword))
    }

    fun buildChapterCall(rule: WebSourceRule, source: String): WebSourceApiCall {
        val api = rule.chapterApi ?: throw WebSourceApiException("missing chapterApiConfig")
        // 章节源既可能是站点 ID，也可能是详情页地址；整值替换不做转义。
        return buildCall(api.request, mapOf("source" to source))
    }

    fun parseSearch(json: String, rule: WebSourceRule): List<WebSourceEntry> {
        val api = rule.searchApi ?: throw WebSourceApiException("missing searchApiConfig")
        val document = parseJson(json)
        val seen = HashSet<String>()
        return JsonPathLite.read(document, api.listPath)
            .asSequence()
            .mapNotNull { node ->
                val name = stringOf(JsonPathLite.readFirst(node, api.namePath)).trim()
                val source = stringOf(JsonPathLite.readFirst(node, api.sourcePath)).trim()
                if (name.isEmpty() || source.isEmpty()) return@mapNotNull null
                // 与 Kazumi 一致：模板存在时整体替换 @source，否则原样保留（通常是站点 ID）。
                val url = if (api.sourceTemplate.isBlank()) {
                    source
                } else {
                    val rendered = api.sourceTemplate.replace("@source", encodeComponent(source))
                    WebSourceParser.resolve(rule.baseUrl, rendered) ?: rendered
                }
                WebSourceEntry(name, url)
            }
            .filter { seen.add(it.url) }
            .take(MAX_ENTRIES)
            .toList()
    }

    fun parseEpisodes(json: String, rule: WebSourceRule, source: String): List<WebSourceEpisode> {
        val api = rule.chapterApi ?: throw WebSourceApiException("missing chapterApiConfig")
        val document = parseJson(json)
        val variables = linkedMapOf("source" to source)
        api.variables.forEach { (name, path) ->
            val value = JsonPathLite.readFirst(document, path)
                ?: throw WebSourceApiException("missing chapter variable @$name")
            variables[name] = stringOf(value)
        }
        val roads = if (api.isDelimited) {
            parseDelimited(document, api, variables, rule.baseUrl)
        } else {
            parseNested(document, api, variables, rule.baseUrl)
        }
        val channelCount = roads.size
        val seen = HashSet<String>()
        return roads.flatMapIndexed { index, road ->
            val channelIndex = if (channelCount > 1) index else null
            road.mapNotNull { episode ->
                if (seen.add(episode.url)) episode.copy(channelIndex = channelIndex) else null
            }
        }.take(MAX_EPISODES)
    }

    /** 章节响应不是合法 JSON。 */
    fun parseJson(raw: String): JsonElement {
        val element = runCatching { JsonParser.parseString(raw) }
            .getOrElse { throw WebSourceApiException("invalid json response: ${it.message}") }
        // Gson 宽松模式会把挑战页等纯文本解析成字符串，接口响应必须落在对象或数组上。
        if (!element.isJsonObject && !element.isJsonArray) {
            throw WebSourceApiException("api response is not a JSON object")
        }
        return element
    }

    private fun buildCall(
        config: WebSourceApiRequest,
        variables: Map<String, String>
    ): WebSourceApiCall {
        val method = config.method.trim().uppercase().ifEmpty { WebSourceApiRequest.METHOD_GET }
        if (method != WebSourceApiRequest.METHOD_GET && method != WebSourceApiRequest.METHOD_POST) {
            throw WebSourceApiException("unsupported method $method")
        }
        if (config.url.isBlank()) throw WebSourceApiException("missing api url")
        val url = renderTemplate(config.url.trim(), variables, encode = true)
        if (url.toHttpUrlOrNull() == null) throw WebSourceApiException("invalid api url: $url")
        val isPost = method == WebSourceApiRequest.METHOD_POST
        val body = if (isPost && config.bodyType != WebSourceApiRequest.BODY_NONE) {
            renderBody(config.body, config.bodyType, variables)
        } else {
            null
        }
        val contentType = when {
            body == null -> null
            config.bodyType == WebSourceApiRequest.BODY_JSON -> "application/json; charset=utf-8"
            config.bodyType == WebSourceApiRequest.BODY_FORM ->
                "application/x-www-form-urlencoded; charset=utf-8"
            else -> null
        }
        return WebSourceApiCall(
            method = method,
            url = url,
            headers = config.headers.mapValues { (_, value) -> renderTemplate(value, variables) },
            query = config.query.mapValues { (_, value) -> renderValue(value, variables) },
            body = body,
            contentType = contentType
        )
    }

    private fun renderBody(
        raw: String,
        bodyType: String,
        variables: Map<String, String>
    ): String? {
        if (raw.isBlank()) return null
        val parsed = runCatching { JsonParser.parseString(raw) }.getOrNull()
        if (bodyType == WebSourceApiRequest.BODY_FORM) {
            if (parsed?.isJsonObject == true) {
                return parsed.asJsonObject.entrySet().joinToString("&") { (key, value) ->
                    val rendered = renderJson(value, variables)
                    "${formEncode(renderTemplate(key, variables))}=${formEncode(stringOf(rendered))}"
                }
            }
            return renderTemplate(raw, variables)
        }
        if (parsed == null) throw WebSourceApiException("invalid json body")
        return renderJson(parsed, variables).toString()
    }

    /** 整值占位符保留原始字符串，嵌入占位符按字符串插值。 */
    private fun renderJson(element: JsonElement, variables: Map<String, String>): JsonElement =
        when {
            element.isJsonObject -> JsonObject().apply {
                element.asJsonObject.entrySet().forEach { (key, value) ->
                    add(key, renderJson(value, variables))
                }
            }

            element.isJsonArray -> JsonArray().apply {
                element.asJsonArray.forEach { add(renderJson(it, variables)) }
            }

            element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                val text = element.asString
                val exact = exactVariable.matchEntire(text)
                if (exact != null) {
                    JsonPrimitive(variable(exact.groupValues[1], variables))
                } else {
                    JsonPrimitive(renderTemplate(text, variables))
                }
            }

            else -> element
        }

    private fun renderValue(value: String, variables: Map<String, String>): String {
        val exact = exactVariable.matchEntire(value)
        return if (exact != null) {
            variable(exact.groupValues[1], variables)
        } else {
            renderTemplate(value, variables)
        }
    }

    private fun renderTemplate(
        template: String,
        variables: Map<String, String>,
        encode: Boolean = false
    ): String = missingVariable.replace(template) { match ->
        val value = variable(match.groupValues[1], variables)
        if (encode) encodeComponent(value) else value
    }

    private fun variable(name: String, variables: Map<String, String>): String =
        variables[name] ?: throw WebSourceApiException("missing template variable @$name")

    private fun parseDelimited(
        document: JsonElement,
        api: WebSourceChapterApi,
        variables: Map<String, String>,
        baseUrl: String
    ): List<List<WebSourceEpisode>> {
        val episodesValue = stringOf(JsonPathLite.readFirst(document, api.roadEpisodesPath))
        if (episodesValue.isBlank()) return emptyList()
        // 线路标题统一由界面按序号渲染，这里只消费 roadEpisodesPath 的分组。
        return episodesValue.split(api.roadSeparator).mapIndexedNotNull { roadIndex, group ->
            val episodes = group.split(api.episodeSeparator).mapIndexedNotNull { episodeIndex, entry ->
                val trimmed = entry.trim()
                if (trimmed.isEmpty()) return@mapIndexedNotNull null
                val separator = trimmed.indexOf(api.fieldSeparator)
                if (separator < 0) return@mapIndexedNotNull null
                val name = trimmed.substring(0, separator).trim()
                    .ifEmpty { defaultEpisodeName(episodeIndex) }
                val rawUrl = trimmed.substring(separator + api.fieldSeparator.length).trim()
                val url = episodeUrl(api, variables, rawUrl, roadIndex, episodeIndex, baseUrl)
                    ?: return@mapIndexedNotNull null
                WebSourceEpisode(name, url)
            }
            episodes.ifEmpty { null }
        }
    }

    private fun parseNested(
        document: JsonElement,
        api: WebSourceChapterApi,
        variables: Map<String, String>,
        baseUrl: String
    ): List<List<WebSourceEpisode>> {
        val roads = if (api.roadsPath.isBlank()) {
            listOf(document)
        } else {
            JsonPathLite.read(document, api.roadsPath)
        }
        return roads.mapIndexedNotNull { roadIndex, road ->
            JsonPathLite.read(road, api.episodesPath)
                .mapIndexedNotNull { episodeIndex, node ->
                    val name = stringOf(JsonPathLite.readFirst(node, api.episodeNamePath)).trim()
                        .ifEmpty { defaultEpisodeName(episodeIndex) }
                    val rawUrl = if (api.episodeUrlPath.isBlank()) {
                        ""
                    } else {
                        stringOf(JsonPathLite.readFirst(node, api.episodeUrlPath)).trim()
                    }
                    val url = episodeUrl(api, variables, rawUrl, roadIndex, episodeIndex, baseUrl)
                        ?: return@mapIndexedNotNull null
                    WebSourceEpisode(name, url)
                }
                .ifEmpty { null }
        }
    }

    /** 模板为空时使用响应地址；否则渲染播放页模板并合并查询参数。 */
    private fun episodeUrl(
        api: WebSourceChapterApi,
        variables: Map<String, String>,
        rawUrl: String,
        roadIndex: Int,
        episodeIndex: Int,
        baseUrl: String
    ): String? {
        val page = api.episodePage ?: return WebSourceParser.resolve(baseUrl, rawUrl)
        if (page.url.isBlank()) throw WebSourceApiException("missing episode page url")
        val pageVariables = variables + mapOf(
            "episodeUrl" to rawUrl,
            "roadIndex" to roadIndex.toString(),
            "roadNumber" to (roadIndex + 1).toString(),
            "episodeIndex" to episodeIndex.toString(),
            "episodeNumber" to (episodeIndex + 1).toString()
        )
        val rendered = renderTemplate(page.url.trim(), pageVariables, encode = true)
        val url = rendered.toHttpUrlOrNull()
            ?: WebSourceParser.resolve(baseUrl, rendered)?.toHttpUrlOrNull()
            ?: return null
        val builder = url.newBuilder()
        page.query.forEach { (key, value) ->
            builder.setQueryParameter(
                renderTemplate(key, pageVariables),
                renderValue(value, pageVariables)
            )
        }
        return builder.build().toString()
    }

    private fun defaultEpisodeName(index: Int): String = "第${index + 1}集"

    private fun stringOf(element: JsonElement?): String = when {
        element == null || element.isJsonNull -> ""
        element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString
        element.isJsonPrimitive -> element.asString
        else -> element.toString()
    }

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun formEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
