package com.aiexile.animetrack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.toPath
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** 用改动前的几何构造作为参考，验证进度延迟读取及 Path 复用不改变裁剪结果。 */
@RunWith(AndroidJUnit4::class)
class TopBarMorphRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun morphPixelsMatchReferenceDuringCollapseAndExpand() {
        val progress = mutableStateOf(0f)
        var spec by mutableStateOf(MorphSpec())
        val compositions = AtomicInteger()
        compose.setContent {
            Row {
                ReferenceMorph(spec, progress)
                OptimizedMorph(spec, progress, compositions)
            }
        }
        val geometries = listOf(
            MorphSpec(),
            MorphSpec(targetWidth = 97),
            MorphSpec(corner = 24),
            MorphSpec(width = 140, height = 120),
            MorphSpec(targetWidth = 200), // 目标宽度大于内容时回退为原矩形
            MorphSpec(height = 40), // 目标高度大于内容时回退为原矩形
        )
        for (geometry in geometries) {
            compose.runOnIdle { spec = geometry }
            compose.waitForIdle()
            val count = compositions.get()
            // 同一缓存路径连续经历正向、反向和端点，检查没有轮廓累积/残留。
            for (value in listOf(0f, 0.001f, 0.1f, 0.35f, 0.75f, 0.99f, 1f, 0.6f, 0.2f, 0f)) {
                compose.runOnIdle { progress.value = value }
                compose.waitForIdle()
                assertEquals("连续进度仅应刷新绘制", count, compositions.get())
                assertPixelsMatch()
            }
        }
    }

    @Composable
    private fun OptimizedMorph(spec: MorphSpec, progress: State<Float>, compositions: AtomicInteger) {
        SideEffect { compositions.incrementAndGet() }
        val readProgress = remember(progress) { { progress.value } }
        Box(
            frame(spec, OptimizedTag)
                .topBarCollapseMorph(
                    collapseProgress = readProgress,
                    targetWidth = spec.targetWidth.dp,
                    targetCornerRadius = spec.corner.dp,
                )
                .background(sampleBrush()),
        )
    }

    @Composable
    private fun ReferenceMorph(spec: MorphSpec, progress: State<Float>) {
        val density = LocalDensity.current
        val targetWidth = with(density) { spec.targetWidth.dp.toPx() }
        val targetHeight = with(density) { TopBarActionsHeight.toPx() }
        val corner = with(density) { spec.corner.dp.toPx() }
        val endInset = with(density) { 20.dp.toPx() }
        Box(
            frame(spec, ReferenceTag)
                .drawWithContent {
                    val value = progress.value
                    if (value <= 0f) {
                        drawContent()
                    } else {
                        clipPath(referencePath(size, value, targetWidth, targetHeight, corner, endInset)) {
                            this@drawWithContent.drawContent()
                        }
                    }
                }
                .background(sampleBrush()),
        )
    }

    private fun referencePath(
        size: Size,
        progress: Float,
        targetWidth: Float,
        targetHeight: Float,
        corner: Float,
        endInset: Float,
    ): Path {
        val p = progress.coerceIn(0f, 1f)
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f || targetWidth >= width || targetHeight >= height) {
            return Path().apply { addRect(Rect(0f, 0f, width, height)) }
        }
        val endRight = width - endInset
        if (p >= 1f) {
            return Path().apply {
                addRect(Rect(endRight - targetWidth, height - targetHeight, endRight, height))
            }
        }
        val start = RoundedPolygon.rectangle(
            width = width, height = height, centerX = width / 2f, centerY = height / 2f,
        )
        val rounding = CornerRounding(radius = corner, smoothing = 0.64f)
        val end = RoundedPolygon.rectangle(
            width = targetWidth,
            height = targetHeight,
            centerX = endRight - targetWidth / 2f,
            centerY = height - targetHeight / 2f,
            perVertexRounding = List(4) { rounding },
        )
        return Morph(start, end).toPath(p).asComposePath()
    }

    private fun frame(spec: MorphSpec, tag: String) = Modifier
        .requiredSize(spec.width.dp, spec.height.dp)
        .testTag(tag)

    private fun sampleBrush() = Brush.linearGradient(
        listOf(Color(0xFFFF5533), Color(0xFF55CCAA), Color(0xFF4466EE)),
    )

    private fun assertPixelsMatch() {
        val expected = compose.onNodeWithTag(ReferenceTag).captureToImage().toPixelMap()
        val actual = compose.onNodeWithTag(OptimizedTag).captureToImage().toPixelMap()
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        for (y in 0 until expected.height) {
            for (x in 0 until expected.width) {
                val reference = expected[x, y].toArgb()
                val result = actual[x, y].toArgb()
                for (shift in 0..24 step 8) {
                    val error = abs(((reference ushr shift) and 255) - ((result ushr shift) and 255))
                    if (error > 2) {
                        assertTrue("pixel=($x,$y), channel=$shift, error=$error", false)
                    }
                }
            }
        }
    }

    private data class MorphSpec(
        val width: Int = 160,
        val height: Int = 100,
        val targetWidth: Int = 48,
        val corner: Int = 14,
    )

    private companion object {
        const val ReferenceTag = "top_bar_morph_reference"
        const val OptimizedTag = "top_bar_morph_optimized"
    }
}
