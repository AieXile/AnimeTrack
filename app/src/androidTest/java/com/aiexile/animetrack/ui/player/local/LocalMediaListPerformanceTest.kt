package com.aiexile.animetrack.ui.player.local

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.flowOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiexile.animetrack.data.media.LocalMediaAnimeGroup
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaSourceEntity
import com.aiexile.animetrack.localmedialab.LocalMediaLabActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentLinkedQueue

/** 真机 Compose 渲染；首屏从送入已加载的列表算起，不包含 Room 和应用冷启动。 */
@RunWith(AndroidJUnit4::class)
class LocalMediaListPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<LocalMediaLabActivity>()

    @Test
    fun measureFirstVisibleRowAndGestureFramesAtEachSize() {
        val state = mutableStateOf(LocalMediaUiState())
        val pageFlow = mutableStateOf(flowOf(PagingData.empty<LocalMediaEntity>()))
        val groups = flowOf(PagingData.empty<LocalMediaAnimeGroup>())
        val source = LocalMediaSourceEntity(id = 1, treeUri = "content://test/tree/root", displayName = "P6 list sample")
        val activity = compose.activity
        compose.runOnUiThread {
            activity.setContentView(android.widget.FrameLayout(activity).apply {
                addView(androidx.compose.ui.platform.ComposeView(activity).also { view ->
                    view.setContent {
                        MaterialTheme {
                            LocalMediaLibraryContent(
                                state = state.value,
                                onBack = {}, onPlayMedia = {}, onAddFolder = {},
                                onSelectSource = {}, onDeleteSource = {}, onScanSelectedSource = {},
                                groups = groups.collectAsLazyPagingItems(),
                                unclassified = pageFlow.value.collectAsLazyPagingItems()
                            )
                        }
                    }
                })
            })
        }
        val collectorThread = HandlerThread("P6 frames").apply { start() }
        val frameTimes = ConcurrentLinkedQueue<Long>()
        val listener = Window.OnFrameMetricsAvailableListener { _, frame, _ ->
            val nanos = frame.getMetric(FrameMetrics.TOTAL_DURATION)
            if (nanos >= 0) frameTimes.add(nanos)
        }
        try {
            compose.runOnUiThread {
                activity.window.addOnFrameMetricsAvailableListener(listener, Handler(collectorThread.looper))
            }
            for (size in listOf(300, 1_000, 5_000, 10_000)) {
                val rows = (1..size).map { id ->
                    val name = "P6 Performance $size - $id.mkv"
                    LocalMediaEntity(id.toLong(), source.id, "content://test/document/$id", name, name)
                }
                val start = SystemClock.elapsedRealtimeNanos()
                val pager = Pager(PagingConfig(50, initialLoadSize = 100, maxSize = 250, jumpThreshold = 150)) { SamplePages(rows) }
                compose.runOnUiThread {
                    state.value = LocalMediaUiState(listOf(source), source.id)
                    pageFlow.value = pager.flow
                }
                compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(rows.first().fileName)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(rows.first().fileName).assertIsDisplayed()
                val firstVisible = SystemClock.elapsedRealtimeNanos() - start
                frameTimes.clear()
                val display = activity.resources.displayMetrics
                repeat(5) {
                    val x = display.widthPixels / 2
                    shell("input swipe $x ${display.heightPixels * 3 / 4} $x ${display.heightPixels / 3} 280")
                }
                compose.waitForIdle()
                val samples = frameTimes.toList().sorted()
                // 添加按钮、来源、扫描卡、媒体标题和未归类标题各占一项。
                compose.onNodeWithTag("local-media-list").performScrollToIndex(size + 4)
                compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasText(rows.last().fileName)).fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithText(rows.last().fileName).assertIsDisplayed()
                assertTrue("No rendered frames at size $size", samples.isNotEmpty())
                if (size != 300) {
                    val p95 = samples[(samples.lastIndex * 0.95).toInt()]
                    Log.i("P6ListBenchmark", "n=$size firstVisibleMs=${ms(firstVisible)} frames=${samples.size} " +
                        "p95Ms=${ms(p95)} over16_7ms=${samples.count { it > 16_700_000L }} " +
                        "over33_3ms=${samples.count { it > 33_300_000L }}")
                }
                // 下一轮从同一列表顶部开始，而不是沿用上轮的滚动位置。
                compose.runOnUiThread { state.value = LocalMediaUiState() }
                compose.waitForIdle()
            }
        } finally {
            compose.runOnUiThread { activity.window.removeOnFrameMetricsAvailableListener(listener) }
            collectorThread.quitSafely()
        }
    }

    private class SamplePages(private val rows: List<LocalMediaEntity>) : PagingSource<Int, LocalMediaEntity>() {
        override val jumpingSupported: Boolean get() = true

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, LocalMediaEntity> {
            val (start, end) = if (params is LoadParams.Prepend) {
                val end = requireNotNull(params.key)
                (end - params.loadSize).coerceAtLeast(0) to end
            } else {
                val start = (params.key ?: 0).coerceIn(0, rows.lastIndex)
                start to (start + params.loadSize).coerceAtMost(rows.size)
            }
            return LoadResult.Page(rows.subList(start, end), start.takeIf { it > 0 }, end.takeIf { it < rows.size }, start, rows.size - end)
        }

        override fun getRefreshKey(state: PagingState<Int, LocalMediaEntity>): Int? =
            state.anchorPosition?.let { (it - state.config.initialLoadSize / 2).coerceAtLeast(0) }
    }

    private fun shell(command: String) {
        val result = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(result).use { it.readBytes() }
    }

    private fun ms(ns: Long) = ns / 100_000 / 10.0
}
