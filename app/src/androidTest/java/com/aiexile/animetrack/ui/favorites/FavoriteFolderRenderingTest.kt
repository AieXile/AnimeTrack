package com.aiexile.animetrack.ui.favorites

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.AnimeDatabase
import com.aiexile.animetrack.data.AnimeRepositoryImpl
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.data.favorites.FavoriteFolderRepository
import com.aiexile.animetrack.model.Anime
import com.aiexile.animetrack.model.AnimeStatus
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoriteFolderRenderingTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun openingFromTheSecondTileMovesAllFavoritesDownAndKeepsTheFolderAsClickSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AnimeDatabase::class.java).build()
        val repository = FavoriteFolderRepository(database) {}
        val settings = SettingsRepository(context)
        val guideWasSeen = runBlocking { settings.favoriteFolderPinchGuideSeen.first() }
        val viewModel = FavoritesViewModel(AnimeRepositoryImpl(database.animeDao(), context), repository, settings)
        try {
            val folderId = runBlocking {
                settings.setFavoriteFolderPinchGuideSeen(false)
                repository.create("First folder")
                repository.create("Weekend anime")
                val second = database.favoriteFolderDao().getFolderIds().last()
                val animeId = database.insertFavorite("Paper Moon")
                repository.addAnimes(second, setOf(animeId))
                second
            }
            var clickSource: Pair<String, String>? = null
            val gridState = LazyGridState()
            val pagerState = PagerState { 2 }
            compose.activityRule.scenario.onActivity {
                it.setShowWhenLocked(true)
                it.setTurnScreenOn(true)
            }
            compose.setContent {
                MaterialTheme {
                    HorizontalPager(state = pagerState) { page ->
                        if (page == 0) {
                            FavoritesScreen(
                                onAnimeClick = { anime, prefix -> clickSource = anime.title to prefix },
                                gridState = gridState,
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
            compose.waitUntilAtLeastOneExists(hasText("Weekend anime"), timeoutMillis = 5_000)
            val folder = compose.onNodeWithText("Weekend anime")
            val all = compose.onNodeWithText(context.getString(R.string.favorite_folder_all))
            val grid = compose.onNode(
                hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            )
            val manageLabel = context.getString(R.string.favorite_folder_manage)
            val pinchGuideTitle = context.getString(R.string.favorite_folder_pinch_guide_title)
            val pinchGuide = compose.onNodeWithText(pinchGuideTitle)
            val closedFolder = folder.getUnclippedBoundsInRoot()
            val originalAllTop = all.getUnclippedBoundsInRoot().top
            assertTrue(originalAllTop >= closedFolder.bottom)
            pinchGuide.assertDoesNotExist()

            folder.performClick()
            compose.waitUntilAtLeastOneExists(hasContentDescription(manageLabel), timeoutMillis = 5_000)
            compose.onNodeWithContentDescription(manageLabel).assertIsDisplayed()
            pinchGuide.assertIsDisplayed()
            assertTrue(folder.getUnclippedBoundsInRoot().left < closedFolder.left)
            assertTrue(all.getUnclippedBoundsInRoot().top > originalAllTop)
            val firstMember = compose.onAllNodesWithText("Paper Moon")[0]
            firstMember.assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("Paper Moon" to "folder_${folderId}_", clickSource) }

            grid.pinchAt(all.centerY(), all.centerY())
            compose.onNodeWithContentDescription(manageLabel).assertIsDisplayed()
            pinchGuide.assertIsDisplayed()
            grid.pinchAt(pinchGuide.centerY(), pinchGuide.centerY())
            compose.onNodeWithText("First folder").assertIsDisplayed()
            assertEquals(originalAllTop, all.getUnclippedBoundsInRoot().top)
            compose.waitUntil(timeoutMillis = 5_000) { viewModel.folderPinchGuideSeen.value == true }
            runBlocking { assertTrue(SettingsRepository(context).favoriteFolderPinchGuideSeen.first()) }

            val members = runBlocking {
                val ids = (2..7).map { database.insertFavorite("Folder member $it") }
                repository.addAnimes(folderId, ids.toSet())
                ids
            }
            compose.waitUntil(timeoutMillis = 5_000) { viewModel.favorites.value.size == 7 }
            compose.waitForIdle()
            val closedItems = gridState.layoutInfo.totalItemsCount
            folder.performClick()
            compose.waitUntil(timeoutMillis = 5_000) { gridState.layoutInfo.totalItemsCount > closedItems }
            pinchGuide.assertDoesNotExist()
            grid.performTouchInput { swipeUp() }
            compose.runOnIdle {
                assertTrue(gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)
            }
            grid.performScrollToKey("folder_flap_$folderId")
            assertTrue(all.getUnclippedBoundsInRoot().top >= folder.getUnclippedBoundsInRoot().bottom)

            val columns = gridState.layoutInfo.maxSpan
            val lastRowStart = members.size / columns * columns
            grid.performScrollToKey("folder_${folderId}_${members[lastRowStart - 1]}")
            val lastMember = compose.onAllNodesWithText("Folder member 7")[0]
            assertTrue(lastMember.getUnclippedBoundsInRoot().bottom <= folder.getUnclippedBoundsInRoot().top)
            lastMember.assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("Folder member 7" to "folder_${folderId}_", clickSource) }
            pinchWithTranslation(grid, lastMember.centerY(), folder.centerY(), gridState, pagerState, closedItems)
            grid.performScrollToIndex(0)
            compose.onNodeWithText("First folder").assertIsDisplayed()
            compose.runOnIdle { assertEquals("Folder member 7" to "folder_${folderId}_", clickSource) }
            folder.performClick()
            compose.waitUntil(timeoutMillis = 5_000) { gridState.layoutInfo.totalItemsCount > closedItems }
            grid.performTouchInput { swipeLeft() }
            compose.runOnIdle { assertEquals(1, pagerState.settledPage) }
        } finally {
            viewModel.viewModelScope.cancel()
            runBlocking { settings.setFavoriteFolderPinchGuideSeen(guideWasSeen) }
            database.close()
        }
    }

    private fun SemanticsNodeInteraction.centerY(): Dp =
        getUnclippedBoundsInRoot().let { (it.top + it.bottom) / 2 }

    private fun SemanticsNodeInteraction.pinchAt(firstY: Dp, secondY: Dp) {
        val gridTop = getUnclippedBoundsInRoot().top
        performTouchInput {
            val start0 = Offset(width * 0.25f, (firstY - gridTop).toPx())
            val start1 = Offset(width * 0.75f, (secondY - gridTop).toPx())
            val midpoint = (start0 + start1) / 2f
            val endOffset = (start1 - start0) * 0.15f
            pinch(start0 = start0, end0 = midpoint - endOffset, start1 = start1, end1 = midpoint + endOffset)
        }
    }

    private fun pinchWithTranslation(
        grid: SemanticsNodeInteraction,
        firstY: Dp,
        secondY: Dp,
        gridState: LazyGridState,
        pagerState: PagerState,
        closedItems: Int
    ) {
        fun scrollPosition() = compose.runOnIdle {
            assertEquals(0, pagerState.currentPage)
            assertEquals(0f, pagerState.currentPageOffsetFraction)
            gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset
        }

        val openPosition = scrollPosition()
        val gridTop = grid.getUnclippedBoundsInRoot().top
        grid.performTouchInput {
            down(0, Offset(width * 0.35f, (firstY - gridTop).toPx()))
            down(1, Offset(width * 0.8f, (secondY - gridTop).toPx()))
            val translation = Offset(-width * 0.2f, 0f)
            updatePointerBy(0, translation)
            updatePointerBy(1, translation)
            move()
        }
        assertEquals(openPosition, scrollPosition())

        grid.performTouchInput {
            val translation = Offset(0f, -width * 0.16f)
            updatePointerBy(0, translation)
            updatePointerBy(1, translation)
            move()
        }
        assertEquals(openPosition, scrollPosition())

        grid.performTouchInput {
            val towardCenter = Offset(width * 0.15f, 0f)
            updatePointerBy(0, towardCenter)
            updatePointerBy(1, -towardCenter)
            move()
        }
        compose.waitUntil(timeoutMillis = 5_000) { gridState.layoutInfo.totalItemsCount == closedItems }
        val closedPosition = scrollPosition()
        grid.performTouchInput {
            up(1)
            moveBy(0, Offset(-width * 0.1f, -width * 0.26f))
            up(0)
        }
        assertEquals(closedPosition, scrollPosition())
    }

    private suspend fun AnimeDatabase.insertFavorite(title: String): Int =
        animeDao().insertAnime(
            Anime(
                title = title,
                totalEpisodes = 12,
                watchedEpisodes = 0,
                status = AnimeStatus.PLANNED,
                rating = null,
                notes = "",
                isFavorite = true
            )
        ).toInt()
}
