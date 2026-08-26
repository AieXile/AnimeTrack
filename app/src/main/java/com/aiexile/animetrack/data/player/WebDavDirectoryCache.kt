package com.aiexile.animetrack.data.player

import com.thegrizzlylabs.sardineandroid.DavResource
import java.util.LinkedHashMap

/**
 * WebDAV 目录列表内存缓存（LRU + TTL）。
 *
 * 背景：播放前外挂字幕扫描（[SubtitleLocator]）需要对视频所在目录发一次
 * PROPFIND，而用户通常刚在浏览页（WebDAVBrowseViewModel）列过同一目录——
 * 重复请求白白增加点击到起播的延迟。
 *
 * 写入方：浏览页每次列目录成功后（始终携带最新数据）；
 * 读取方：字幕扫描优先读缓存，命中则零网络请求起播。
 *
 * TTL 防止列表数据无限期过期；LRU 防止遍历大量目录时内存膨胀。
 */
object WebDavDirectoryCache {

    /** 缓存条目存活时长：字幕文件极少变动，但也不宜长期失效 */
    private const val TTL_MS = 10 * 60 * 1000L

    /** 最多缓存的目录数（防遍历型访问无限膨胀） */
    private const val MAX_ENTRIES = 64

    private class Entry(val resources: List<DavResource>, val savedAt: Long)

    private val cache = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun put(dirUrl: String, resources: List<DavResource>) {
        cache[keyOf(dirUrl)] = Entry(resources, System.currentTimeMillis())
    }

    /** 命中且未过期返回目录列表；否则 null（不主动刷新） */
    @Synchronized
    fun get(dirUrl: String): List<DavResource>? {
        val key = keyOf(dirUrl)
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.savedAt > TTL_MS) {
            cache.remove(key)
            return null
        }
        return entry.resources
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }

    /** 统一 key：写入方（浏览页）路径可能带尾斜杠，读取方（字幕扫描）不带，归一后才能命中 */
    private fun keyOf(dirUrl: String): String = dirUrl.trimEnd('/')
}
