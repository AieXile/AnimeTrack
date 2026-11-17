package com.aiexile.animetrack.data.websource

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Kazumi API 规则所需的 JSONPath 子集。
 *
 * 只支持规则里实际出现的写法，避免引入完整 JSONPath 依赖：
 * - `$` 根节点；
 * - `$.a.b` / `$.a-b` 子字段；
 * - `[*]` 数组元素（对象上取全部值）；
 * - `[0]` 数组下标；
 * - `['name']` / `["name"]` 带引号的字段。
 *
 * 表达式非法时抛出 [IllegalArgumentException]；取值缺失返回空列表。
 */
object JsonPathLite {

    private sealed interface Step {
        data class Child(val name: String) : Step
        data class Index(val index: Int) : Step
        data object Wildcard : Step
    }

    fun validate(expression: String) {
        parse(expression)
    }

    fun read(root: JsonElement, expression: String): List<JsonElement> {
        var current = listOf(root)
        parse(expression).forEach { step ->
            current = current.flatMap { node -> apply(node, step) }
        }
        return current
    }

    fun readFirst(root: JsonElement, expression: String): JsonElement? =
        read(root, expression).firstOrNull()

    private fun apply(node: JsonElement, step: Step): List<JsonElement> = when (step) {
        is Step.Child -> {
            val value = (node as? JsonObject)?.get(step.name)
            if (value == null) emptyList() else listOf(value)
        }

        is Step.Index -> {
            val array = node as? JsonArray
            if (array != null && step.index < array.size()) listOf(array[step.index])
            else emptyList()
        }

        Step.Wildcard -> when {
            node.isJsonArray -> node.asJsonArray.toList()
            node.isJsonObject -> node.asJsonObject.entrySet().map { it.value }
            else -> emptyList()
        }
    }

    private fun parse(expression: String): List<Step> {
        val path = expression.trim()
        require(path.startsWith("$")) { "JSONPath 必须以 $ 开头: $expression" }
        val steps = mutableListOf<Step>()
        var index = 1
        while (index < path.length) {
            when (val char = path[index]) {
                '.' -> {
                    index++
                    val start = index
                    while (index < path.length && isNameChar(path[index])) index++
                    require(index > start) { "不支持的 JSONPath: $expression" }
                    steps += Step.Child(path.substring(start, index))
                }

                '[' -> {
                    val end = findBracketEnd(path, index, expression)
                    val content = path.substring(index + 1, end).trim()
                    steps += parseBracket(content, expression)
                    index = end + 1
                }

                else -> throw IllegalArgumentException("不支持的 JSONPath 片段 '$char': $expression")
            }
        }
        return steps
    }

    private fun parseBracket(content: String, expression: String): Step {
        if (content == "*") return Step.Wildcard
        content.toIntOrNull()?.let { value ->
            require(value >= 0) { "JSONPath 下标不能为负: $expression" }
            return Step.Index(value)
        }
        val quoted = content.length >= 2 &&
            ((content.startsWith("'") && content.endsWith("'")) ||
                (content.startsWith("\"") && content.endsWith("\"")))
        require(quoted) { "不支持的 JSONPath 片段: [$content]" }
        return Step.Child(content.substring(1, content.length - 1))
    }

    /** 方括号内允许引号，引号里的 `]` 不结束片段。 */
    private fun findBracketEnd(path: String, start: Int, expression: String): Int {
        var quote: Char? = null
        var escaped = false
        for (index in start + 1 until path.length) {
            val char = path[index]
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                quote != null -> if (char == quote) quote = null
                char == '\'' || char == '"' -> quote = char
                char == ']' -> return index
            }
        }
        throw IllegalArgumentException("JSONPath 缺少 ]: $expression")
    }

    private fun isNameChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '_' || char == '-' || char == '$'
}
