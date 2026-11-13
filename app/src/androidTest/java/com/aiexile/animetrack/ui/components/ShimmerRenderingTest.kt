package com.aiexile.animetrack.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiexile.animetrack.data.GreetingEffectStyle
import com.aiexile.animetrack.ui.home.GreetingText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** 固定动画进度比较原画笔与复用 shader 的输出，同时检查进度更新不触发组合。 */
@RunWith(AndroidJUnit4::class)
class ShimmerRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun translatedShaderMatchesOriginalGradient() {
        val sizes = listOf(IntSize(240, 48), IntSize(48, 240))
        val colors = listOf(Color(0xFF242424), Color(0xFFF4F4F4), Color(0x80446688))
        val progressValues = listOf(-1f, -0.4f, 0f, 0.37f, 1f, 1.4f, 2f)
        for (size in sizes) {
            for (base in colors) {
                val glow = Color.White
                val animated = AnimatedShimmerBrush(size, base, glow)
                val shader = animated.createShader(Size(size.width.toFloat(), size.height.toFloat()))
                for (progress in progressValues) {
                    animated.updateProgress(progress)
                    assertSame(shader, animated.createShader(Size(1f, 1f)))
                    val expected = renderGradient(shimmerBrush(size, progress, base, glow), size)
                    val actual = renderGradient(animated, size)
                    try {
                        assertBitmapPixelsMatch(expected, actual, "$size / $base / $progress")
                    } finally {
                        expected.recycle()
                        actual.recycle()
                    }
                }
            }
        }
    }

    @Test
    fun textOutputMatchesAcrossProgressSizeColorAndOverflow() {
        val progress = mutableStateOf(ShimmerProgressStart)
        var spec by mutableStateOf(SampleSpec())
        val compositions = AtomicInteger()
        compose.setContent {
            MaterialTheme(colorScheme = if (spec.dark) darkColorScheme() else lightColorScheme()) {
                Row {
                    ReferenceSample(spec, progress)
                    AnimatedSample(spec, progress, compositions)
                }
            }
        }
        val samples = listOf(
            SampleSpec(),
            SampleSpec(text = "多行微光\nSecond line", maxLines = 2),
            SampleSpec(text = "这是一段需要省略的很长文字 Long shimmer text", width = 120),
            SampleSpec(base = Color(0x80446688), dark = true, width = 170),
            SampleSpec(base = Color(0xFFF4F4F4), dark = true, width = 180),
        )
        for (sample in samples) {
            compose.runOnIdle { spec = sample }
            compose.waitForIdle()
            val count = compositions.get()
            for (value in listOf(-1f, -0.4f, 0.15f, 0.55f, 1f, 1.4f, 2f)) {
                compose.runOnIdle { progress.value = value }
                compose.waitForIdle()
                assertEquals("只有进度改变时不应重新组合", count, compositions.get())
                assertImagesMatch(
                    compose.onNodeWithTag(ReferenceTag).captureToImage(),
                    compose.onNodeWithTag(AnimatedTag).captureToImage(),
                )
            }
        }
    }

    @Test
    fun shimmerTextAnimationMovesWithoutRelayout() {
        val layouts = AtomicInteger()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                ShimmerText(
                    text = "Loading 动画进度",
                    color = Color(0xFF242424),
                    fontSize = 24.sp,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            layouts.incrementAndGet()
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                        }
                        .requiredSize(260.dp, 60.dp)
                        .background(Color.White)
                        .testTag(AnimatedTag),
                )
            }
        }
        // 首帧测量尺寸后让 shader 进入文字样式，再固定到两处不同的扫光位置。
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(600)
        val before = compose.onNodeWithTag(AnimatedTag).captureToImage().toPixelMap()
        val layoutCount = layouts.get()
        compose.mainClock.advanceTimeBy(450)
        val after = compose.onNodeWithTag(AnimatedTag).captureToImage().toPixelMap()
        assertEquals("光带移动不应触发重新测量", layoutCount, layouts.get())
        var changed = false
        for (y in 0 until before.height) {
            for (x in 0 until before.width) {
                if (before[x, y] != after[x, y]) changed = true
            }
        }
        assertTrue("循环微光必须在实际 Text 上持续移动", changed)
    }

    @Test
    fun greetingSweepReturnsToOriginalAppearance() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                GreetingText(
                    greetingText = "你好 AnimeTrack",
                    style = GreetingEffectStyle.NONE,
                    shouldAnimate = false,
                    onAnimated = {},
                    modifier = Modifier
                        .requiredSize(260.dp, 60.dp)
                        .background(Color.White)
                        .testTag(AnimatedTag),
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        val before = compose.onNodeWithTag(AnimatedTag).captureToImage()
        compose.onNodeWithTag(AnimatedTag).performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(ShimmerSweepDurationMs.toLong() + 100)
        assertImagesMatch(before, compose.onNodeWithTag(AnimatedTag).captureToImage())
    }

    private data class SampleSpec(
        val text: String = "Loading 微光",
        val width: Int = 150,
        val base: Color = Color(0xFF242424),
        val maxLines: Int = 1,
        val dark: Boolean = false,
    )

    @Composable
    private fun ReferenceSample(spec: SampleSpec, progress: State<Float>) {
        var size by remember { mutableStateOf(IntSize.Zero) }
        val glow = shimmerGlowColor(spec.base)
        val brush = if (size == IntSize.Zero) null else shimmerBrush(size, progress.value, spec.base, glow)
        SampleText(spec, brush, sampleModifier(spec, ReferenceTag).onSizeChanged { size = it })
    }

    @Composable
    private fun AnimatedSample(spec: SampleSpec, progress: State<Float>, compositions: AtomicInteger) {
        SideEffect { compositions.incrementAndGet() }
        var size by remember { mutableStateOf(IntSize.Zero) }
        val brush = rememberAnimatedShimmerBrush(size, spec.base)
        SampleText(
            spec,
            brush,
            sampleModifier(spec, AnimatedTag)
                .onSizeChanged { size = it }
                .drawWithContent {
                    brush?.updateProgress(progress.value)
                    drawContent()
                },
        )
    }

    private fun sampleModifier(spec: SampleSpec, tag: String): Modifier = Modifier
        .requiredSize(spec.width.dp, 84.dp)
        .background(Color(0xFF888888))
        .testTag(tag)

    @Composable
    private fun SampleText(spec: SampleSpec, brush: Brush?, modifier: Modifier) {
        Text(
            text = spec.text,
            modifier = modifier,
            color = spec.base,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = spec.maxLines,
            overflow = TextOverflow.Ellipsis,
            style = if (brush == null) TextStyle.Default else TextStyle(brush = brush),
        )
    }

    private fun renderGradient(brush: Brush, size: IntSize): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = (brush as ShaderBrush).createShader(Size(size.width.toFloat(), size.height.toFloat()))
        }
        android.graphics.Canvas(bitmap).drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), paint)
        return bitmap
    }

    private fun assertBitmapPixelsMatch(expected: Bitmap, actual: Bitmap, label: String) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val referencePixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(referencePixels.size)
        expected.getPixels(referencePixels, 0, expected.width, 0, 0, expected.width, expected.height)
        actual.getPixels(actualPixels, 0, actual.width, 0, 0, actual.width, actual.height)
        for (index in referencePixels.indices) {
            assertPixelMatches(referencePixels[index], actualPixels[index], "$label / pixel=$index")
        }
    }

    private fun assertImagesMatch(expected: ImageBitmap, actual: ImageBitmap) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        val reference = expected.toPixelMap()
        val result = actual.toPixelMap()
        for (y in 0 until reference.height) {
            for (x in 0 until reference.width) {
                assertPixelMatches(reference[x, y].toArgb(), result[x, y].toArgb(), "pixel=($x,$y)")
            }
        }
    }

    private fun assertPixelMatches(expected: Int, actual: Int, label: String) {
        // 平移矩阵与直接计算端点可能有浮点舍入差，最多允许每个 8-bit 通道相差 2。
        for (shift in listOf(0, 8, 16, 24)) {
            val error = abs(((expected ushr shift) and 255) - ((actual ushr shift) and 255))
            assertTrue("$label / channel=$shift / error=$error", error <= 2)
        }
    }

    private companion object {
        const val ReferenceTag = "shimmer_reference"
        const val AnimatedTag = "shimmer_animated"
    }
}
