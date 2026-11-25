package com.aiexile.animetrack.data.player.webdav

import com.thegrizzlylabs.sardineandroid.DavResource
import okhttp3.HttpUrl
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
internal object WebDavDirectoryCache {

    /** 缓存条目存活时长：字幕文件极少变动，但也不宜长期失效 */
    private const val TTL_MS = 10 * 60 * 1000L

    /** 最多缓存的目录数（防遍历型访问无限膨胀） */
    private const val MAX_ENTRIES = 64

    private data class Key(val config: PlayerWebDavDirectory.Config, val directory: HttpUrl)
    private class Entry(val resources: List<DavResource>, val savedAt: Long)

    private val cache = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun put(config: PlayerWebDavDirectory.Config, directory: HttpUrl, resources: List<DavResource>) {
        cache[Key(config, directory)] = Entry(resources, System.currentTimeMillis())
    }

    /** 命中且未过期返回目录列表；否则 null（不主动刷新） */
    @Synchronized
    fun get(config: PlayerWebDavDirectory.Config, directory: HttpUrl): List<DavResource>? {
        val key = Key(config, directory)
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

}
