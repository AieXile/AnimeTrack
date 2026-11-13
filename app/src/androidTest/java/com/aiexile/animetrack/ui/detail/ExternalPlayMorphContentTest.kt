package com.aiexile.animetrack.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiexile.animetrack.R
import com.aiexile.animetrack.ui.icons.AppIcon
import com.aiexile.animetrack.ui.icons.rememberAppIconPainter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** 只对照内容层，不改动或替代玻璃材质的渲染链路。 */
@RunWith(AndroidJUnit4::class)
class ExternalPlayMorphContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun contentMatchesOriginalAcrossFadeBoundariesAndThemes() {
        val progress = mutableStateOf(0f)
        var dark by mutableStateOf(false)
        var episode by mutableStateOf("第 2 集")
        compose.setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                Row {
                    Box(frame(ReferenceTag)) { ReferenceContent(progress.value, episode) }
                    OptimizedContent(progress, episode)
                }
            }
        }
        for (theme in listOf(false, true)) {
            for (text in listOf("第 2 集", "")) {
                compose.runOnIdle { dark = theme; episode = text }
                for (value in listOf(0f, 0.1f, 0.349f, 0.35f, 0.351f, 0.6f, 1f, 0.6f, 0.2f, 0f)) {
                    compose.runOnIdle { progress.value = value }
                    compose.waitForIdle()
                    assertPixelsMatch()
                }
            }
        }
    }

    @Test
    fun hiddenCardContentRemainsAbsentFromSemantics() {
        val progress = mutableStateOf(0f)
        compose.setContent {
            MaterialTheme { OptimizedContent(progress, "Episode 2") }
        }
        compose.onNodeWithText("Episode 2").assertDoesNotExist()
        compose.runOnIdle { progress.value = 0.35f }
        compose.onNodeWithText("Episode 2").assertDoesNotExist()
        compose.runOnIdle { progress.value = 0.351f }
        compose.onNodeWithText("Episode 2").assertExists()
        compose.runOnIdle { progress.value = 1f }
        compose.onNodeWithText("Episode 2").assertExists()
        compose.runOnIdle { progress.value = 0f }
        compose.onNodeWithText("Episode 2").assertDoesNotExist()
    }

    @Composable
    private fun OptimizedContent(progress: State<Float>, episode: String) {
        Box(frame(OptimizedTag)) {
            ExternalPlayMorphContent(progress.value, false, episode)
        }
    }

    // 改动前的纯图标 → 卡片内容淡出/淡入实现，作为固定进度像素参考。
    @Composable
    private fun ReferenceContent(progress: Float, episodeText: String) {
        val fabAlpha = (1f - progress / 0.35f).coerceIn(0f, 1f)
        val cardAlpha = ((progress - 0.35f) / (1f - 0.35f)).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize()) {
            if (fabAlpha > 0f) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer { alpha = fabAlpha },
                    contentAlignment = Alignment.Center,
                ) { PlayIcon(24) }
            }
            if (cardAlpha > 0f) {
                Row(
                    Modifier.fillMaxSize().graphicsLayer { alpha = cardAlpha }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) { PlayIcon(22) }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            stringResource(R.string.external_player_watch),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (episodeText.isNotBlank()) {
                            Text(
                                episodeText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Icon(
                        painter = rememberAppIconPainter(AppIcon.KEYBOARD_ARROW_RIGHT),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun PlayIcon(size: Int) {
        Icon(
            painter = rememberAppIconPainter(AppIcon.PLAY_ARROW),
            contentDescription = stringResource(R.string.external_player_watch),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size.dp),
        )
    }

    private fun frame(tag: String) = Modifier.requiredSize(160.dp, 68.dp).testTag(tag)

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

    private companion object {
        const val ReferenceTag = "external_play_content_reference"
        const val OptimizedTag = "external_play_content_optimized"
    }
}
