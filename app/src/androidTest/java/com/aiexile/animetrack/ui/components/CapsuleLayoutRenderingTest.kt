package com.aiexile.animetrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiexile.animetrack.ui.home.BesideNavFabSize
import com.aiexile.animetrack.ui.home.BesideNavFabSlotWidth
import com.aiexile.animetrack.ui.home.besideNavFabSquish
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** 对照原来的 Dp padding/offset/缩放公式，检查延迟读取没有改变布局或动画形状。 */
@RunWith(AndroidJUnit4::class)
class CapsuleLayoutRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun deferredPaddingAndMotionMatchOriginalInBothDirections() {
        val reserve = mutableStateOf(0.dp)
        var spec by mutableStateOf(LayoutSpec())
        val compositions = AtomicInteger()
        compose.setContent {
            CompositionLocalProvider(
                LocalLayoutDirection provides spec.direction,
                LocalDensity provides Density(spec.density),
            ) {
                Column {
                    ReferenceLayout(reserve)
                    DeferredLayout(reserve, compositions)
                }
            }
        }
        for (density in listOf(1f, 1.25f)) {
            for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                compose.runOnIdle { spec = LayoutSpec(direction, density) }
                compose.waitForIdle()
                val count = compositions.get()
                for (fraction in listOf(0f, 0.01f, 0.125f, 0.35f, 0.75f, 1f, 0.55f, 0f)) {
                    compose.runOnIdle { reserve.value = BesideNavFabSlotWidth * fraction }
                    compose.waitForIdle()
                    assertEquals("让位进度变化不应重组延迟读取的组件", count, compositions.get())
                    assertPixelsMatch()
                }
            }
        }
    }

    @Composable
    private fun ReferenceLayout(reserve: State<Dp>) {
        val width = reserve.value
        val progress = (width / BesideNavFabSlotWidth).coerceIn(0f, 1f)
        val squash = (1f - progress) * 0.35f
        Frame(
            tag = ReferenceTag,
            capsulePadding = Modifier.padding(end = width),
            slotMotion = Modifier
                .offset(x = (BesideNavFabSize + 32.dp) * (1f - width / BesideNavFabSlotWidth))
                .graphicsLayer {
                    scaleX = 1f - squash
                    scaleY = 1f + squash * 0.5f
                },
        )
    }

    @Composable
    private fun DeferredLayout(reserve: State<Dp>, compositions: AtomicInteger) {
        SideEffect { compositions.incrementAndGet() }
        val readReserve = remember(reserve) { { reserve.value } }
        val endPadding = rememberCapsuleEndPadding(readReserve)
        Frame(
            tag = DeferredTag,
            capsulePadding = Modifier.padding(endPadding),
            slotMotion = Modifier
                .offset {
                    IntOffset(
                        ((BesideNavFabSize + 32.dp) *
                            (1f - reserve.value / BesideNavFabSlotWidth)).roundToPx(),
                        0,
                    )
                }
                .besideNavFabSquish(readReserve),
        )
    }

    @Composable
    private fun Frame(tag: String, capsulePadding: Modifier, slotMotion: Modifier) {
        Box(
            Modifier
                .requiredSize(260.dp, 100.dp)
                .background(Color(0xFFEEEEEE))
                .testTag(tag),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .then(capsulePadding)
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFFEE5533), Color(0xFF3355DD)))
                    ),
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 25.dp)
                    .then(slotMotion)
                    .requiredSize(BesideNavFabSize)
                    .background(Color(0xFF226655)),
            )
        }
    }

    private fun assertPixelsMatch() {
        val expected = compose.onNodeWithTag(ReferenceTag).captureToImage().toPixelMap()
        val actual = compose.onNodeWithTag(DeferredTag).captureToImage().toPixelMap()
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

    private data class LayoutSpec(
        val direction: LayoutDirection = LayoutDirection.Ltr,
        val density: Float = 1f,
    )

    private companion object {
        const val ReferenceTag = "capsule_layout_reference"
        const val DeferredTag = "capsule_layout_deferred"
    }
}
