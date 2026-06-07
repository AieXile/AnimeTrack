package com.aiexile.animetrack.util

import java.net.URLEncoder
import java.security.MessageDigest

object WbiUtils {

    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    private fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (i in mixinKeyEncTab) {
            if (i < orig.length) sb.append(orig[i])
        }
        return sb.toString().substring(0, 32)
    }

    private fun filterIllegalChars(value: String): String {
        return value.replace(Regex("[!'()*]"), "")
    }

    private fun encodeURIComponent(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    private fun md5(str: String): String {
        return MessageDigest.getInstance("MD5").digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun sign(
        params: Map<String, String>,
        imgKey: String,
        subKey: String
    ): Map<String, String> {
        val mixinKey = getMixinKey(imgKey + subKey)
        val currTime = System.currentTimeMillis() / 1000

        val rawParams = mutableMapOf<String, String>()
        for ((key, value) in params) {
            rawParams[key] = filterIllegalChars(value)
        }
        rawParams["wts"] = currTime.toString()

        val sortedKeys = rawParams.keys.sorted()

        val queryBuilder = StringBuilder()
        for (key in sortedKeys) {
            val value = rawParams[key]
            if (value != null) {
                val encodedValue = encodeURIComponent(value)
                if (queryBuilder.isNotEmpty()) queryBuilder.append("&")
                queryBuilder.append(key).append("=").append(encodedValue)
            }
        }

        val strToHash = queryBuilder.toString() + mixinKey
        val wRid = md5(strToHash)

        rawParams["w_rid"] = wRid

        return rawParams
    }
}
