package com.aiexile.animetrack.data.remote

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

internal object UpdateApkCache {
    private const val APK_DIR = "updates"
    private const val APK_PREFIX = "AnimeTrack_"
    private const val APK_SUFFIX = ".apk"
    private val versionPattern = Regex(
        "(\\d+\\.\\d+\\.\\d+)(?:-[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?" +
            "(?:\\+[A-Za-z0-9]+(?:[.-][A-Za-z0-9]+)*)?"
    )

    fun directory(cacheDir: File): File = File(cacheDir, APK_DIR)

    fun fileFor(cacheDir: File, versionName: String): File =
        File(directory(cacheDir), "$APK_PREFIX${versionName.removePrefix("v")}$APK_SUFFIX")

    /** 以已安装版本为准，不依赖联网，也不删除更新版本的待安装包和续传文件。 */
    fun removeObsoleteApks(cacheDir: File, installedVersionName: String): Int {
        val installedVersion = validatedVersion(installedVersionName) ?: return 0
        val updateDir = directory(cacheDir)
        if (!Files.isDirectory(updateDir.toPath(), NOFOLLOW_LINKS)) return 0
        return updateDir.listFiles().orEmpty().count { apk ->
            val name = apk.name
            if (!name.startsWith(APK_PREFIX) || !name.endsWith(APK_SUFFIX) ||
                !Files.isRegularFile(apk.toPath(), NOFOLLOW_LINKS)
            ) {
                return@count false
            }
            val version = validatedVersion(name.removePrefix(APK_PREFIX).removeSuffix(APK_SUFFIX))
                ?: return@count false
            // 版本比较器会忽略后缀，同一数字版本的不同后缀无法可靠判定先后，因此保留。
            val obsolete = version == installedVersion ||
                VersionComparator.isNewerVersion(installedVersion, version)
            obsolete && apk.delete()
        }
    }

    private fun validatedVersion(versionName: String): String? {
        val version = versionName.removePrefix("v").removePrefix("V")
        // 先校验，避免比较器把无法解析的数字段跳过后误判为旧版本。
        val core = versionPattern.matchEntire(version)?.groupValues?.get(1) ?: return null
        return version.takeIf { core.split('.').all { part -> part.toIntOrNull() != null } }
    }
}
