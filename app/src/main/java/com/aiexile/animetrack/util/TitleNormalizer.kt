package com.aiexile.animetrack.util

import java.text.Normalizer
import java.util.Locale

/**
 * 标题比较用的归一化：全半角统一、忽略大小写、去掉空白与标点。
 * 例：【我推的孩子】与「我推的孩子」视为同一标题。
 */
fun normalizeTitleForMatch(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .filter { it.isLetterOrDigit() }
