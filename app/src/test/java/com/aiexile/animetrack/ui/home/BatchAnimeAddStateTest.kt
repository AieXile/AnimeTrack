package com.aiexile.animetrack.ui.home

import com.aiexile.animetrack.model.SearchResult
import com.aiexile.animetrack.model.SearchSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatchAnimeAddStateTest {
    @Test
    fun selectionUsesSourceAndIdAcrossSearches() {
        val bangumi = result(SearchSource.BANGUMI)
        val tmdb = result(SearchSource.TMDB)
        val state = BatchAnimeAddState(isActive = true)
            .toggleSelection(bangumi)
            .toggleSelection(tmdb)

        assertEquals(listOf(bangumi, tmdb), state.selection.values.toList())
        val refreshedBangumi = bangumi.copy(title = "Updated title")
        assertEquals(listOf(tmdb), state.toggleSelection(refreshedBangumi).selection.values.toList())
    }

    @Test
    fun selectionDoesNotChangeOutsideBatchModeOrWhileSaving() {
        val anime = result(SearchSource.BANGUMI)
        val inactive = BatchAnimeAddState()
        val saving = BatchAnimeAddState(isActive = true).toggleSelection(anime).copy(isSaving = true)

        assertEquals(inactive, inactive.toggleSelection(anime))
        assertEquals(saving, saving.toggleSelection(anime))
    }

    @Test
    fun completedBatchKeepsModeOpenAndCountsConflicts() {
        val state = BatchAnimeAddState(isActive = true)
            .toggleSelection(result(SearchSource.BANGUMI))
            .toggleSelection(result(SearchSource.TMDB))
            .copy(isSaving = true)
            .complete(listOf(42L, -1L))

        assertTrue(state.isActive)
        assertFalse(state.isSaving)
        assertTrue(state.selection.isEmpty())
        assertEquals(BatchAnimeAddState.Summary(added = 1, skipped = 1), state.summary)
    }

    private fun result(source: SearchSource) = SearchResult(
        source = source,
        sourceId = 1,
        title = "Anime",
        coverUrl = null,
        episodeCount = 12,
        airDate = null,
        rating = null,
        summary = null,
        episodeCountText = "12"
    )
}
