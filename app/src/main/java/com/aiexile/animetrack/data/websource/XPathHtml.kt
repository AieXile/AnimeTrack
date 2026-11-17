package com.aiexile.animetrack.data.websource

import org.jsoup.Jsoup
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathFactory

/**
 * 把 HTML 转成 W3C DOM，并按 XPath 取值。
 *
 * 这里没有用 jsoup 自带的 `W3CDom`：它会把元素放进 XHTML 命名空间，
 * 而 Kazumi 规则使用不带前缀的 XPath（如 `//div`），在 XPath 1.0 里
 * 匹配不到带命名空间的元素。自己建一个 namespace-unaware 的 DOM 即可。
 *
 * 另外，规则选择器普遍以 `/` 开头。XPath 里以 `/` 开头的路径是绝对路径，
 * 直接作用在条目节点上会从文档根重新匹配，导致每条结果都取到同一个元素；
 * 因此求值前给每条以 `/` 开头的并集分支补一个 `.`，让选择器相对当前节点生效
 * （`//div` → `.//div`，`/self::*` → `./self::*`）。
 */
object XPathHtml {

    private val factory: XPathFactory by lazy { XPathFactory.newInstance() }

    // XPath 对象不是线程安全的，搜索与选集可能并发，按线程各持一个
    private val xpath = ThreadLocal.withInitial { factory.newXPath() }

    private val whitespace = Regex("\\s+")

    // 并集分支开头的 `/`：包括整串开头和 `|` 之后，补 `.` 变成相对路径
    private val leadingAbsolute = Regex("(^|\\|)\\s*/")

    // 个别规则沿用 CSS 的 `attr*="value"`（子串匹配），XPath 1.0 没有该运算符
    private val cssSubstringMatch = Regex("@([A-Za-z_][\\w.-]*)\\s*\\*=\\s*(['\"])(.*?)\\2")

    fun parse(bytes: ByteArray, baseUrl: String): Document {
        val source = Jsoup.parse(ByteArrayInputStream(bytes), null, baseUrl)
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
        }.newDocumentBuilder().newDocument()

        val root = source.children().firstOrNull() ?: return document
        convert(document, root)?.let { document.appendChild(it) }
        return document
    }

    fun select(context: Node, expression: String): List<Node> {
        val trimmed = expression.trim()
        if (trimmed.isEmpty()) return emptyList()
        // 部分 Kazumi 规则用 "//" / "/" 表示「当前节点本身」（整页容器）
        if (trimmed == "//" || trimmed == "/") return listOf(context)
        val normalized = trimmed.replace(leadingAbsolute) { match ->
            match.groupValues[1] + "./"
        }.replace(cssSubstringMatch) { match ->
            val (name, quote, value) = match.destructured
            "contains(@$name, $quote$value$quote)"
        }
        val nodes = runCatching {
            val evaluator = xpath.get() ?: return emptyList()
            evaluator.evaluate(normalized, context, XPathConstants.NODESET) as NodeList
        }.getOrNull() ?: return emptyList()
        return (0 until nodes.length).mapNotNull { nodes.item(it) }
    }

    fun text(node: Node?): String =
        node?.textContent?.replace(whitespace, " ")?.trim().orEmpty()

    fun href(node: Node?): String? = when (node) {
        null -> null
        is Attr -> node.value?.trim()?.takeIf { it.isNotEmpty() }
        is Element -> node.getAttribute("href").trim().takeIf { it.isNotEmpty() }
        else -> null
    }

    private fun convert(target: Document, source: org.jsoup.nodes.Node): Node? = when (source) {
        is org.jsoup.nodes.Element -> {
            val element = target.createElement(source.tagName())
            source.attributes().forEach { attribute ->
                element.setAttribute(attribute.key, attribute.value)
            }
            source.childNodes().forEach { child ->
                convert(target, child)?.let { element.appendChild(it) }
            }
            element
        }

        is org.jsoup.nodes.TextNode -> target.createTextNode(source.text())
        is org.jsoup.nodes.DataNode -> target.createTextNode(source.wholeData)
        is org.jsoup.nodes.Comment -> target.createComment(source.data)
        else -> null
    }
}
