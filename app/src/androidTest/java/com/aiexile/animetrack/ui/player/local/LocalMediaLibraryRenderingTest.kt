package com.aiexile.animetrack.ui.player.local

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaSourceEntity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 回归真实闪退：同一列表内，来源表与媒体表的自增 ID 可能相同。 */
@RunWith(AndroidJUnit4::class)
class LocalMediaLibraryRenderingTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sameSourceAndMediaIdsRenderAndRemainPlayable() {
        var played: Long? = null
        val first = media(1, 1)
        val second = media(2, 1)
        val state = LocalMediaUiState(
            sources = listOf(source(1), source(2)),
            selectedSourceId = 1,
            media = listOf(first, second)
        )
        compose.setContent {
            MaterialTheme {
                LocalMediaLibraryContent(
                    state = state,
                    onBack = {},
                    onPlayMedia = { mediaId -> played = mediaId },
                    onAddFolder = {},
                    onChooseFolder = {},
                    onSelectSource = {},
                    onDeleteSource = {},
                    onScanSelectedSource = {}
                )
            }
        }
        compose.onNodeWithText(source(1).displayName).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToKey("media-1")
        compose.onNodeWithText(first.fileName).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(first.id, played)
        }
        compose.onNode(hasScrollAction()).performScrollToKey("media-2")
        compose.onNodeWithText(second.fileName).assertIsDisplayed()
    }

    @Test
    fun scanResultsCanAppearAfterFolderIsAdded() {
        val state = mutableStateOf(LocalMediaUiState())
        compose.setContent {
            MaterialTheme {
                LocalMediaLibraryContent(
                    state = state.value,
                    onBack = {},
                    onPlayMedia = { _ -> },
                    onAddFolder = {},
                    onChooseFolder = {},
                    onSelectSource = {},
                    onDeleteSource = {},
                    onScanSelectedSource = {}
                )
            }
        }
        compose.runOnIdle {
            state.value = LocalMediaUiState(
                sources = listOf(source(1)),
                selectedSourceId = 1,
                isScanning = true
            )
        }
        compose.onNodeWithText(source(1).displayName).assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(
                media = listOf(media(1, 1)),
                scannedFiles = 1,
                videoFiles = 1,
                isScanning = false
            )
        }
        compose.onNode(hasScrollAction()).performScrollToKey("media-1")
        compose.onNodeWithText(media(1, 1).fileName).assertIsDisplayed()
        compose.runOnIdle {
            state.value = state.value.copy(
                selectedSourceId = 2,
                sources = listOf(source(1), source(2)),
                media = listOf(media(2, 2))
            )
        }
        compose.onNode(hasScrollAction()).performScrollToKey("media-2")
        compose.onNodeWithText(media(2, 2).fileName).assertIsDisplayed()
    }

    private fun source(id: Long) = LocalMediaSourceEntity(
        id = id,
        treeUri = "content://test/tree/$id",
        displayName = "Regression Folder $id",
        videoCount = 2
    )

    private fun media(id: Long, sourceId: Long) = LocalMediaEntity(
        id = id,
        sourceId = sourceId,
        documentUri = "content://test/tree/$sourceId/document/$id",
        relativePath = "Regression - $id.mkv",
        fileName = "Regression - $id.mkv",
        parsedTitle = "Regression",
        episodeNumber = id.toInt()
    )
}
