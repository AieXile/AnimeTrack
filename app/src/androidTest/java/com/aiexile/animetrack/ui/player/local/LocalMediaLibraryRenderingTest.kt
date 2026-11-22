package com.aiexile.animetrack.ui.player.local

import android.app.Application
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.media.LocalMediaRepository
import com.aiexile.animetrack.data.media.LocalMediaScanScheduler
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import com.aiexile.animetrack.data.media.LocalMediaAnimeGroup
import com.aiexile.animetrack.data.media.LocalMediaMatchState
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
    private val emptyGroups = flowOf(PagingData.empty<LocalMediaAnimeGroup>())

    @Test
    fun returningFromPlayerRestoresPagedLibraryScrollPosition() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val database = Room.inMemoryDatabaseBuilder(application, AnimeDatabase::class.java).build()
        try {
            val repository = LocalMediaRepository(application, database.localMediaDao())
            val targetIndex = 150
            runBlocking {
                val source = repository.addOrGetSource(Uri.parse("content://test/tree/scroll"), "Scroll fixture")
                database.localMediaDao().insertMedia((1..240).map { index ->
                    // 让相对路径顺序与 ID 一致，目标文件在第一批分页之外。
                    val name = "Scroll clip ${index.toString().padStart(3, '0')}.mkv"
                    media(index.toLong(), source.id).copy(id = 0, fileName = name, relativePath = name)
                })
            }
            compose.setContent {
                MaterialTheme {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "library") {
                        composable("library") {
                            val library: LocalMediaViewModel = viewModel {
                                LocalMediaViewModel(application, repository, AppContainer.getAnimeRepository(),
                                    LocalMediaScanScheduler(WorkManager.getInstance(application)), SavedStateHandle())
                            }
                            LocalMediaLibraryScreen(onBack = {}, onPlayMedia = { nav.navigate("player") }, viewModel = library)
                        }
                        composable("player") {
                            TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("return-to-library")) {
                                Text("Return")
                            }
                        }
                    }
                }
            }
            val grid = compose.onNodeWithTag("local-media-list")
            val targetLabel = "Scroll clip $targetIndex.mkv"
            val target = compose.onNodeWithText(targetLabel)
            compose.waitUntil(5_000) {
                grid.fetchSemanticsNode().config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                    ?.maxValue?.invoke()?.let { it > targetIndex } == true
            }
            // 先滚到分页占位附近，等真实 key 加载后再精确定位。
            grid.performScrollToIndex(targetIndex + 4)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(targetLabel).fetchSemanticsNodes().isNotEmpty() }
            grid.performScrollToKey("media-$targetIndex")
            val before = target.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top
            target.performClick()
            compose.onNodeWithTag("return-to-library").assertIsDisplayed()
            // 允许出场转场结束，确保媒体库离开组合，而不是在转场重叠期取巧。
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithTag("return-to-library").performClick()
            assertEquals(before, target.assertIsDisplayed().fetchSemanticsNode().boundsInRoot.top, 1f)
        } finally {
            database.close()
        }
    }

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
                    onSelectSource = {},
                    onDeleteSource = {},
                    onScanSelectedSource = {},
                    groups = emptyGroups.collectAsLazyPagingItems(),
                    unclassified = pages.collectAsLazyPagingItems()
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
                    onSelectSource = {},
                    onDeleteSource = {},
                    onScanSelectedSource = {},
                    groups = emptyGroups.collectAsLazyPagingItems(),
                    unclassified = pages.collectAsLazyPagingItems()
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
                    onBack = {}, onPlayMedia = {}, onAddFolder = {},
                    onSelectSource = {}, onDeleteSource = {}, onScanSelectedSource = {},
                    onCancelScan = { cancelled = true },
                    onReauthorize = { reauthorized = it.id },
                    groups = emptyGroups.collectAsLazyPagingItems(),
                    unclassified = pages.collectAsLazyPagingItems()
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

    @Test
    fun rescanAndReidentifyShareAnActionRow() {
        val source = source(1)
        val pages = flowOf(PagingData.empty<LocalMediaEntity>())
        var rescans = 0
        var reidentifications = 0
        compose.setContent {
            MaterialTheme {
                LocalMediaLibraryContent(
                    state = LocalMediaUiState(sources = listOf(source), selectedSourceId = source.id),
                    onBack = {}, onPlayMedia = {}, onAddFolder = {}, onSelectSource = {}, onDeleteSource = {},
                    onScanSelectedSource = { rescans++ }, onReidentify = { reidentifications++ },
                    groups = emptyGroups.collectAsLazyPagingItems(), unclassified = pages.collectAsLazyPagingItems()
                )
            }
        }
        compose.onNodeWithTag("local-media-list").performScrollToKey("scan-1")
        val rescan = compose.onNodeWithTag("local-scan-start").assertIsDisplayed()
        val reidentify = compose.onNodeWithTag("local-scan-reidentify").assertIsDisplayed()
        val summary = compose.onNodeWithTag("local-scan-summary").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val first = rescan.fetchSemanticsNode().boundsInRoot
        val second = reidentify.fetchSemanticsNode().boundsInRoot
        assertEquals(first.center.y, second.center.y, 1f)
        assertEquals(summary.center.y, first.center.y, 1f)
        org.junit.Assert.assertTrue(summary.right <= first.left && first.right <= second.left)
        rescan.performClick()
        reidentify.performClick()
        compose.runOnIdle {
            assertEquals(1, rescans)
            assertEquals(1, reidentifications)
        }
    }

    @Test
    fun mediaGroupCardsHaveEqualHeightAcrossRecognitionStates() {
        val confirmed = LocalMediaAnimeGroup(1, 0, "咒术回战", null, 1, 0, archiveKey = "bgm:1")
        val automatic = confirmed.copy(title = "紫罗兰永恒花园", archiveKey = "bgm:2", pendingCount = 1)
        val review = confirmed.copy(title = "SPY FAMILY", archiveKey = null, reviewKey = "review")
        val waiting = review.copy(title = "等待在线元数据的番剧", reviewKey = "waiting", waiting = true)
        val cards = listOf(confirmed, automatic, review, waiting)
        val groups = flowOf(PagingData.from(cards))
        val unclassified = flowOf(PagingData.empty<LocalMediaEntity>())
        compose.setContent {
            MaterialTheme {
                LocalMediaLibraryContent(
                    state = LocalMediaUiState(sources = listOf(source(1)), selectedSourceId = 1),
                    onBack = {}, onPlayMedia = {}, onAddFolder = {}, onSelectSource = {},
                    onDeleteSource = {}, onScanSelectedSource = {},
                    groups = groups.collectAsLazyPagingItems(), unclassified = unclassified.collectAsLazyPagingItems()
                )
            }
        }
        val heights = cards.map { card ->
            compose.onNodeWithTag("local-media-list").performScrollToKey("group-${card.displayKey}")
            compose.onNodeWithTag("local-group-card-${card.displayKey}", useUnmergedTree = true)
                .assertIsDisplayed().fetchSemanticsNode().boundsInRoot.height
        }
        heights.drop(1).forEach { assertEquals(heights.first(), it, 1f) }
    }

    @Test
    fun cardMenuCanConfirmChangeAndClearWithoutOpeningFiles() {
        val archived = LocalMediaAnimeGroup(1, 0, "Archived series", null, 4, 4, archiveKey = "bgm:7")
        val waiting = LocalMediaAnimeGroup(1, 0, "Waiting series", null, 2, 0, reviewKey = "waiting", waiting = true)
        val review = waiting.copy(title = "Review series", reviewKey = "review", waiting = false)
        val groups = flowOf(PagingData.from(listOf(archived, waiting, review)))
        val unclassified = flowOf(PagingData.empty<LocalMediaEntity>())
        val actions = mutableListOf<Pair<String, String>>()
        var opened = false
        compose.setContent {
            MaterialTheme {
                LocalMediaLibraryContent(
                    state = LocalMediaUiState(sources = listOf(source(1)), selectedSourceId = 1),
                    onBack = {}, onPlayMedia = {}, onAddFolder = {}, onSelectSource = {},
                    onDeleteSource = {}, onScanSelectedSource = {}, onOpenGroup = { opened = true },
                    onMatchGroup = { actions += "match" to it.displayKey },
                    onConfirmGroup = { actions += "confirm" to it.displayKey },
                    onClearGroup = { actions += "clear" to it.displayKey },
                    groups = groups.collectAsLazyPagingItems(), unclassified = unclassified.collectAsLazyPagingItems()
                )
            }
        }
        val resources = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources
        fun select(group: LocalMediaAnimeGroup, label: Int) {
            compose.onNodeWithTag("local-media-list").performScrollToKey("group-${group.displayKey}")
            compose.onNodeWithTag("local-group-menu-${group.displayKey}").performClick()
            compose.onNodeWithText(resources.getString(label)).assertIsDisplayed().performClick()
        }
        select(archived, com.aiexile.animetrack.R.string.local_media_confirm_group)
        select(archived, com.aiexile.animetrack.R.string.local_media_change_group)
        select(archived, com.aiexile.animetrack.R.string.local_media_clear_group)
        select(waiting, com.aiexile.animetrack.R.string.local_media_match_group)
        select(waiting, com.aiexile.animetrack.R.string.local_media_clear_group)
        select(review, com.aiexile.animetrack.R.string.local_media_clear_group)
        val card = compose.onNodeWithTag("local-group-card-${review.displayKey}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val menu = compose.onNodeWithTag("local-group-menu-${review.displayKey}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val count = compose.onNodeWithTag("local-group-count-${review.displayKey}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(card.top, menu.top, 1f)
        assertEquals(card.right, menu.right, 1f)
        org.junit.Assert.assertTrue(count.right > menu.left && count.top >= menu.bottom - 1f)
        compose.runOnIdle {
            assertEquals(false, opened)
            assertEquals(listOf("confirm" to archived.displayKey, "match" to archived.displayKey, "clear" to archived.displayKey,
                "match" to waiting.displayKey, "clear" to waiting.displayKey, "clear" to review.displayKey), actions)
        }
    }

    @Test
    fun groupedFilesAreHiddenUntilTheirCardIsOpened() {
        val group = LocalMediaAnimeGroup(1, 7, "Series title", null, 1, 1)
        val media = media(1, 1).copy(matchState = LocalMediaMatchState.AUTO_MATCHED, matchedAnimeId = group.animeId)
        val groups = flowOf(PagingData.from(listOf(group)))
        val unclassified = flowOf(PagingData.empty<LocalMediaEntity>())
        val files = flowOf(PagingData.from(listOf(media)))
        val opened = mutableStateOf<LocalMediaAnimeGroup?>(null)
        var editId: Long? = null
        compose.setContent {
            MaterialTheme {
                LocalMediaLibraryContent(
                    state = LocalMediaUiState(sources = listOf(source(1)), selectedSourceId = 1),
                    onBack = {}, onPlayMedia = {}, onAddFolder = {}, onSelectSource = {},
                    onDeleteSource = {}, onScanSelectedSource = {}, onOpenGroup = { opened.value = it },
                    groups = groups.collectAsLazyPagingItems(),
                    unclassified = unclassified.collectAsLazyPagingItems()
                )
                opened.value?.let {
                    LocalMediaGroupSheet(
                        group = it,
                        files = files.collectAsLazyPagingItems(),
                        onDismiss = { opened.value = null },
                        onPlayMedia = {},
                        onMatchMedia = { item -> editId = item.id }
                    )
                }
            }
        }
        compose.onNodeWithText(media.fileName).assertDoesNotExist()
        compose.onNodeWithTag("local-media-list").performScrollToKey("group-${group.displayKey}")
        compose.onNodeWithText(group.title).assertIsDisplayed().performClick()
        compose.onNodeWithText(media.fileName).assertIsDisplayed()
        val resources = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources
        compose.onNodeWithTag("local-file-menu-${media.id}").performClick()
        compose.onNodeWithText(resources.getString(com.aiexile.animetrack.R.string.local_media_match_change)).performClick()
        compose.runOnIdle { assertEquals(media.id, editId) }
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
