package com.aiexile.animetrack.data.media

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import com.aiexile.animetrack.localmedialab.LocalMediaLabActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.playback.LocalMediaAutoMatchUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 真机运行真实扫描逻辑和磁盘 Room；目录输入为可重复的合成文件元数据，不代表 SAF Binder 耗时。 */
@RunWith(AndroidJUnit4::class)
class LocalMediaScanPerformanceTest {
    @Test
    fun measureFirstUnchangedAndSmallChangeScans() {
        // ColorOS 会冻结无前台 Activity 的 debug 测试进程；用验收页保持可见，不改系统设置。
        ActivityScenario.launch(LocalMediaLabActivity::class.java).use { scenario ->
            scenario.onActivity { it.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            runBlocking {
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                repeat(2) { runDataset(context, 300, report = false) }
                for (size in listOf(1_000, 5_000, 10_000)) {
                    repeat(3) { runDataset(context, size, report = true, iteration = it + 1) }
                }
            }
        }
    }

    private suspend fun runDataset(context: Context, size: Int, report: Boolean, iteration: Int = 0) {
        val databaseName = "p6-bench-${UUID.randomUUID()}.db"
        val database = Room.databaseBuilder(context, AnimeDatabase::class.java, databaseName).build()
        try {
            val dao = database.localMediaDao()
            val reader = GeneratedTree()
            val repository = LocalMediaRepository(context, dao, reader)
            val source = repository.addOrGetSource(Uri.parse("content://benchmark/tree/test"), "P6 benchmark")
            val files = (1..size).map(::file)
            reader.visit = { emit -> files.forEach { emit(it) } }
            val first = measure(repository, source.id)
            val unchanged = measure(repository, source.id)
            assertEquals(size, first.result.parsedFiles)
            assertEquals(size, first.result.writtenFiles)
            assertEquals(0, unchanged.result.parsedFiles)
            assertEquals(0, unchanged.result.writtenFiles)

            reader.visit = { emit ->
                files.drop(1).forEachIndexed { index, file ->
                    emit(if (index == 0) file.copy(lastModified = file.lastModified + 1) else file)
                }
                emit(file(size + 1))
            }
            val changed = measure(repository, source.id)
            assertEquals(2, changed.result.parsedFiles)
            assertEquals(3, changed.result.writtenFiles)
            assertEquals(1, changed.result.missingFiles)

            // 为列表比较单独写入 20% 缺失记录，显示的有效媒体数量保持一致。
            val missingHistory = (size + 2..size + 1 + size / 5).map { number ->
                val file = file(number)
                LocalMediaEntity(sourceId = source.id, documentUri = file.documentUri, relativePath = file.relativePath,
                    fileName = file.fileName, isMissing = true)
            }
            dao.insertMedia(missingHistory)
            val listBefore = clock()
            val loaded = dao.getMedia(source.id)
            val previousVisible = withContext(Dispatchers.Main) { loaded.filterNot { it.isMissing } }
            val baselineNs = clock() - listBefore
            val listAfter = clock()
            val pageSource = dao.pagePresentMedia(source.id)
            val firstPage = pageSource.load(PagingSource.LoadParams.Refresh(null, 100, true))
                as PagingSource.LoadResult.Page<Int, LocalMediaEntity>
            val optimizedNs = clock() - listAfter
            assertEquals(size, previousVisible.size)
            assertEquals(100.coerceAtMost(size), firstPage.data.size)
            assertEquals(previousVisible.take(firstPage.data.size).map { it.id }, firstPage.data.map { it.id })
            var visibleCount = firstPage.data.size
            var next = firstPage.nextKey
            while (next != null) {
                val page = pageSource.load(PagingSource.LoadParams.Append(next, 200, true))
                    as PagingSource.LoadResult.Page<Int, LocalMediaEntity>
                assertEquals(previousVisible.drop(visibleCount).take(page.data.size).map { it.id }, page.data.map { it.id })
                visibleCount += page.data.size
                next = page.nextKey
            }
            assertEquals(size, visibleCount)
            pageSource.invalidate()

            val started = CompletableDeferred<Unit>()
            reader.visit = { emit ->
                emit(files[1])
                started.complete(Unit)
                awaitCancellation()
            }
            val cancelNs = kotlinx.coroutines.coroutineScope {
                val task = launch { repository.scanSource(source.id) }
                withTimeout(10_000) { started.await() }
                val start = clock()
                task.cancelAndJoin()
                clock() - start
            }
            assertEquals(LocalMediaScanState.CANCELLED, dao.getSource(source.id)?.scanState)
            assertFalse(dao.getMediaById(firstPage.data.first().id)!!.isMissing)

            if (report) {
                Log.i(TAG, "n=$size run=$iteration first=$first unchanged=$unchanged changed=$changed " +
                    "listBeforeMs=${ms(baselineNs)} listAfterMs=${ms(optimizedNs)} rows=${loaded.size}/${firstPage.data.size} " +
                    "cancelMs=${ms(cancelNs)}")
            }
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private suspend fun measure(repository: LocalMediaRepository, id: Long): Measurement {
        val start = clock()
        val timings = LocalMediaRepository.ScanTimings()
        val result = repository.scanSource(id, timings)
        val scanNs = clock() - start
        // 隔离真实网络延迟及账号数据：用一个只提供固定番剧列表的 Repository 代理。
        val repo = AppContainer.getAnimeRepository()
        val sample = com.aiexile.animetrack.model.Anime(
            id = 1, title = "P6 Benchmark", totalEpisodes = 100, watchedEpisodes = 0,
            status = com.aiexile.animetrack.model.AnimeStatus.WATCHING, rating = null, notes = ""
        )
        val matcher = LocalMediaAutoMatchUseCase(repository, object : com.aiexile.animetrack.data.AnimeRepository by repo {
            override fun getAllAnimes() = kotlinx.coroutines.flow.flowOf(listOf(sample))
            override suspend fun searchBangumi(query: String) = emptyList<com.aiexile.animetrack.data.network.BangumiSubject>()
        })
        val matchStart = clock()
        matcher.run(id)
        return Measurement(result, scanNs, timings, clock() - matchStart)
    }

    private class GeneratedTree : LocalMediaTreeReader {
        lateinit var visit: suspend (suspend (LocalMediaScanFile) -> Unit) -> Unit
        override fun hasPersistedReadPermission(treeUri: Uri) = true
        override fun takePersistedReadPermission(treeUri: Uri) = Unit
        override suspend fun readFiles(treeUri: Uri, onFile: suspend (LocalMediaScanFile) -> Unit) = visit(onFile)
    }

    private data class Measurement(
        val result: LocalMediaRepository.ScanResult,
        val scanNs: Long,
        val timings: LocalMediaRepository.ScanTimings,
        val matchingNs: Long
    ) {
        override fun toString() = "[scan=${ms(scanNs)},walk=${ms(timings.traversalNs)},parse=${ms(timings.parsingNs)}," +
            "db=${ms(timings.databaseNs)},match=${ms(matchingNs)},parsed=${result.parsedFiles},rows=${result.writtenFiles}]"
    }

    private companion object {
        const val TAG = "P6ScanBenchmark"
        fun clock() = SystemClock.elapsedRealtimeNanos()
        fun ms(ns: Long) = ns / 100_000 / 10.0
        fun file(number: Int): LocalMediaScanFile {
            val folder = "folder-${(number - 1) / 100}"
            val name = "P6 Benchmark EP${(number - 1) % 100 + 1}.mkv"
            return LocalMediaScanFile("$folder/$name", name, "content://benchmark/document/$number", 1, 1, folder)
        }
    }
}
