package com.aiexile.animetrack.util

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/** 拼音输出格式：小写、无声调（如「主」→ "zhu"） */
private val pinyinFormat = HanyuPinyinOutputFormat().apply {
    caseType = HanyuPinyinCaseType.LOWERCASE
    toneType = HanyuPinyinToneType.WITHOUT_TONE
}

/** 单字拼音缓存：设置项与标题文本重复率高，避免每次转换都走 pinyin4j */
private val charPinyinCache = ConcurrentHashMap<Char, String>()

/** 取单字拼音（多音字取第一个读音）；非汉字（含假名）返回 null */
internal fun charPinyin(c: Char): String? {
    charPinyinCache[c]?.let { return it }
    val py = try {
        // 假名等字符会返回空数组，firstOrNull 一并归一为 null
        PinyinHelper.toHanyuPinyinStringArray(c, pinyinFormat)?.firstOrNull()
    } catch (_: Exception) {
        null
    }
    if (py != null) charPinyinCache[c] = py
    return py
}

/** 标题排序键缓存：主界面每次重排都会重新取键，标题集合变化不频繁 */
private val titleSortKeyCache = ConcurrentHashMap<String, String>()

/**
 * 番剧标题的首字母排序键：汉字转无声调拼音全拼，字母数字转小写，空白与标点跳过。
 *
 * 跳过标点避免「【我推的孩子】」这类带装饰前缀的标题被排到列表头尾；
 * 全角字符先做 NFKC 归一（「ＴＲＩＧＵＮ」按 trigun 参与排序）。
 * pinyin4j 不支持的假名保留原字符，排在拉丁字母之后。
 */
fun titleSortKey(title: String): String = titleSortKeyCache[title] ?: buildString {
    Normalizer.normalize(title, Normalizer.Form.NFKC).forEach { c ->
        val py = charPinyin(c)
        when {
            py != null -> append(py)
            c.isLetterOrDigit() -> append(c.lowercaseChar())
            // 其余：空白、标点、符号一律跳过
        }
    }
}.also { titleSortKeyCache[title] = it }
