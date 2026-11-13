package com.aiexile.animetrack.ui.timeline

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiexile.animetrack.ui.components.SquircleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.roundToInt

/** 原版索引器作为参照：高亮位置、文字切换时机和透明度动画都不能改变。 */
@RunWith(AndroidJUnit4::class)
class MonthIndexerRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun indexerMatchesOriginalAtIntermediateFramesInBothThemes() {
        var current by mutableIntStateOf(-1)
        var dark by mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Row {
                    ReferenceIndexer(Months, current, frame(ReferenceTag))
                    MonthIndexer(Months, current, {}, {}, frame(OptimizedTag))
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        for (darkTheme in listOf(false, true)) {
            compose.runOnIdle { dark = darkTheme }
            compose.mainClock.advanceTimeByFrame()
            for (target in listOf(0, 4, 2, 5, 0, -1)) {
                compose.runOnIdle { current = target }
                // 包括跨越多个文字高亮阈值的中间帧，以及文字淡入淡出的尾部。
                repeat(22) {
                    compose.mainClock.advanceTimeByFrame()
                    assertPixelsMatch()
                }
            }
        }
    }

    @Test
    fun clickAndDragStillReportTheRequestedMonth() {
        var current by mutableIntStateOf(0)
        val clicks = mutableListOf<Int>()
        val seeks = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                MonthIndexer(
                    months = Months,
                    currentIndex = current,
                    onIndexClick = { clicks += it; current = it },
                    onIndexSeek = { seeks += it; current = it },
                    modifier = frame(OptimizedTag),
                )
            }
        }
        compose.onNode(hasText("3月") and hasAnyAncestor(hasTestTag(OptimizedTag))).performClick()
        compose.runOnIdle { assertEquals(listOf(2), clicks) }
        compose.onNodeWithTag(OptimizedTag).performTouchInput {
            swipe(
                start = Offset(center.x, height * 0.15f),
                end = Offset(center.x, height * 0.9f),
                durationMillis = 300,
            )
        }
        compose.runOnIdle {
            assertTrue("拖动应继续发出月份定位请求", seeks.isNotEmpty())
            assertTrue(seeks.all { it in Months.indices })
            assertEquals(Months.lastIndex, seeks.last())
        }
    }

    @Test
    fun pressingMonthDoesNotDrawARipple() {
        val clicks = mutableListOf<Int>()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                MonthIndexer(Months, 0, { clicks += it }, {}, frame(OptimizedTag))
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val before = compose.onNodeWithTag(OptimizedTag).captureToImage()
        val target = compose.onNode(hasText("3月") and hasAnyAncestor(hasTestTag(OptimizedTag)))
        target.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(100)
        assertImagesMatch(before, compose.onNodeWithTag(OptimizedTag).captureToImage())
        target.performTouchInput { up() }
        compose.runOnIdle { assertEquals(listOf(2), clicks) }
    }

    @Test
    fun emptyMonthsRemainSafeToDisplayAndDrag() {
        val seeks = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                MonthIndexer(emptyList(), -1, {}, { seeks += it }, frame(OptimizedTag))
            }
        }
        compose.onNodeWithTag(OptimizedTag).performTouchInput {
            swipe(start = topCenter, end = bottomCenter, durationMillis = 200)
        }
        compose.runOnIdle { assertTrue(seeks.isEmpty()) }
    }

    // 以下保留优化前非拖动状态的渲染路径；气泡只在拖动时出现，不参与此像素对照。
    @Composable
    private fun ReferenceIndexer(months: List<String>, currentIndex: Int, modifier: Modifier) {
        var contentHeightPx by remember { mutableIntStateOf(0) }
        Box(
            modifier = modifier
                .width(32.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    SquircleShape(topStart = 16.dp, bottomStart = 16.dp),
                )
                .padding(vertical = 8.dp)
                .onSizeChanged { contentHeightPx = it.height },
        ) {
            if (months.isNotEmpty() && contentHeightPx > 0) {
                val density = LocalDensity.current
                val slotPx = contentHeightPx.toFloat() / months.size
                val slotDp = with(density) { slotPx.toDp() }
                val animatedIndex by animateFloatAsState(
                    targetValue = currentIndex.toFloat(),
                    animationSpec = tween(durationMillis = 150),
                    label = "referenceIndex",
                )
                if (currentIndex >= 0) {
                    val highlightPx = minOf(slotPx, with(density) { 28.dp.toPx() })
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset {
                                IntOffset(0, (animatedIndex * slotPx + (slotPx - highlightPx) / 2f).roundToInt())
                            }
                            .size(with(density) { highlightPx.toDp() })
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    )
                }
                months.forEachIndexed { index, month ->
                    val isCurrent = abs(index - animatedIndex) < 0.5f
                    val alpha by animateFloatAsState(
                        targetValue = if (isCurrent) 1f else 0.5f,
                        animationSpec = tween(durationMillis = 150),
                        label = "referenceAlpha",
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset { IntOffset(0, (index * slotPx).roundToInt()) }
                            .size(width = 32.dp, height = slotDp)
                            .clickable {},
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = month,
                            fontSize = 10.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.alpha(alpha),
                        )
                    }
                }
            }
        }
    }

    private fun frame(tag: String): Modifier = Modifier.requiredSize(32.dp, 300.dp).testTag(tag)

    private fun assertPixelsMatch() {
        assertImagesMatch(
            compose.onNodeWithTag(ReferenceTag).captureToImage(),
            compose.onNodeWithTag(OptimizedTag).captureToImage(),
        )
    }

    private fun assertImagesMatch(expectedImage: ImageBitmap, actualImage: ImageBitmap) {
        val expected = expectedImage.toPixelMap()
        val actual = actualImage.toPixelMap()
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val reference = expected[x, y].toArgb()
                val result = actual[x, y].toArgb()
                for (shift in 0..24 step 8) {
                    val error = abs(((reference ushr shift) and 255) - ((result ushr shift) and 255))
                    assertTrue("pixel=($x,$y), channel=$shift, error=$error", error <= 2)
                }
            }
        }
    }

    private companion object {
        const val ReferenceTag = "month_index_reference"
        const val OptimizedTag = "month_index_optimized"
        val Months = listOf("1月", "2月", "3月", "4月", "5月", "6月")
    }
}
