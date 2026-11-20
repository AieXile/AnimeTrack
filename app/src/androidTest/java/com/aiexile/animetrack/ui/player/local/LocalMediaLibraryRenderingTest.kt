package com.aiexile.animetrack.ui.player.local

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiexile.animetrack.data.media.LocalMediaEntity
import com.aiexile.animetrack.data.media.LocalMediaPermissionState
import com.aiexile.animetrack.data.media.LocalMediaSourceEntity
import com.aiexile.animetrack.data.media.LocalScanPhase
import com.aiexile.animetrack.data.media.LocalScanStatus
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
            selectedSourceId = 1
        )
        val pages = flowOf(PagingData.from(listOf(first, second)))
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
                    onScanSelectedSource = {},
                    pages = pages.collectAsLazyPagingItems()
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
        val pages = MutableStateFlow(PagingData.empty<LocalMediaEntity>())
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
                    onScanSelectedSource = {},
                    pages = pages.collectAsLazyPagingItems()
                )
            }
        }
        compose.runOnIdle {
            state.value = LocalMediaUiState(
                sources = listOf(source(1)),
                selectedSourceId = 1,
                scan = LocalScanStatus(LocalScanPhase.SCANNING)
            )
        }
        compose.onNodeWithText(source(1).displayName).assertIsDisplayed()
        compose.runOnIdle {
            pages.value = PagingData.from(listOf(media(1, 1)))
            state.value = state.value.copy(
                scan = LocalScanStatus(LocalScanPhase.COMPLETED, scannedFiles = 1, videoFiles = 1)
            )
        }
        compose.onNode(hasScrollAction()).performScrollToKey("media-1")
        compose.onNodeWithText(media(1, 1).fileName).assertIsDisplayed()
        compose.runOnIdle {
            pages.value = PagingData.from(listOf(media(2, 2)))
            state.value = state.value.copy(selectedSourceId = 2, sources = listOf(source(1), source(2)))
        }
        compose.onNode(hasScrollAction()).performScrollToKey("media-2")
        compose.onNodeWithText(media(2, 2).fileName).assertIsDisplayed()
    }

    @Test
    fun selectedSourceCanCancelAndRevokedSourceCanRestoreGrant() {
        var cancelled = false
        var reauthorized: Long? = null
        val active = source(1)
        val revoked = source(2).copy(permissionState = LocalMediaPermissionState.REVOKED)
        val pages = flowOf(PagingData.empty<LocalMediaEntity>())
        compose.setContent {
            MaterialTheme {
                LocalMediaLibraryContent(
                    state = LocalMediaUiState(
                        sources = listOf(active, revoked),
                        selectedSourceId = active.id,
                        scan = LocalScanStatus(LocalScanPhase.MATCHING, 4, 3)
                    ),
                    onBack = {}, onPlayMedia = {}, onAddFolder = {}, onChooseFolder = {},
                    onSelectSource = {}, onDeleteSource = {}, onScanSelectedSource = {},
                    onCancelScan = { cancelled = true },
                    onReauthorize = { reauthorized = it.id },
                    pages = pages.collectAsLazyPagingItems()
                )
            }
        }
        compose.onNodeWithTag("local-reauthorize").assertIsDisplayed().performClick()
        compose.onNode(hasScrollAction()).performScrollToKey("scan-1")
        compose.onNodeWithTag("local-scan-cancel").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(revoked.id, reauthorized)
            assertEquals(true, cancelled)
        }
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
