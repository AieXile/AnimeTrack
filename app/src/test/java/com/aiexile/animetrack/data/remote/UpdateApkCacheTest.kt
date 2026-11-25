package com.aiexile.animetrack.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateApkCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `清理已安装及更旧的更新包但保留较新版本的续传和安装文件`() {
        val cacheDir = temp.root
        val installed = updateApk("v0.7.5")
        val older = updateApk("v0.7.4-fix")
        val nextPartial = updateApk("v0.7.6")
        val later = updateApk("v0.7.10")

        assertEquals("updates/AnimeTrack_0.7.5.apk", installed.relativeTo(cacheDir).invariantSeparatorsPath)
        assertEquals(2, UpdateApkCache.removeObsoleteApks(cacheDir, "v0.7.5"))
        assertFalse(older.exists())
        assertFalse(installed.exists())
        assertEquals("apk", nextPartial.readText())
        assertEquals("apk", later.readText())
        assertEquals(0, UpdateApkCache.removeObsoleteApks(cacheDir, "v0.7.5"))
    }

    @Test
    fun `保留无法判断先后的版本和不属于清理范围的文件`() {
        val cacheDir = temp.root
        val sameCore = updateApk("v0.7.5-fix")
        val badVersion = updateApk("v0.X.1")
        val overflowVersion = updateApk("v0.7.999999999999")
        val updates = sameCore.parentFile!!
        val unrelated = File(updates, "manual_0.7.0.apk").apply { writeText("apk") }
        val notApk = File(updates, "AnimeTrack_0.7.0.apk.part").apply { writeText("partial") }
        val nested = File(updates, "AnimeTrack_0.7.0.apk").apply { mkdir() }
        val otherCache = File(cacheDir, "AnimeTrack_0.7.0.apk").apply { writeText("apk") }

        assertEquals(0, UpdateApkCache.removeObsoleteApks(cacheDir, "v0.7.5"))
        assertTrue(listOf(sameCore, badVersion, overflowVersion, unrelated, notApk, nested, otherCache).all { it.exists() })
    }

    @Test
    fun `目录不存在或安装版本无法识别时不清理`() {
        assertEquals(0, UpdateApkCache.removeObsoleteApks(temp.root, "v0.7.5"))
        val older = updateApk("v0.7.4")

        assertEquals(0, UpdateApkCache.removeObsoleteApks(temp.root, "v1.invalid.0"))
        assertTrue(older.isFile)
    }

    private fun updateApk(versionName: String): File =
        UpdateApkCache.fileFor(temp.root, versionName).apply {
            parentFile?.mkdirs()
            writeText("apk")
        }
}
