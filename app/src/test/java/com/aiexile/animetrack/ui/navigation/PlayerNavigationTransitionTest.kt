package com.aiexile.animetrack.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerNavigationTransitionTest {
    @Test
    fun playerAndLibraryBranchesReverseThePushAnimationOnReturn() {
        val routes = listOf(
            Routes.PLAYER_SETTINGS to Routes.LOCAL_MEDIA,
            Routes.LOCAL_MEDIA to Routes.PLAYER,
            Routes.WEB_PLAYBACK to Routes.PLAYER,
            Routes.PLAYER_SETTINGS to Routes.PLAYER,
            Routes.PLAYER_SETTINGS to Routes.WEBDAV_BROWSE,
            Routes.PLAYER to Routes.WEBDAV_BROWSE,
            Routes.ANIME_DETAIL to Routes.WEB_PLAYBACK
        )
        routes.forEach { (parent, child) ->
            assertEquals("$parent → $child", NavigationPushPull.FORWARD, navigationPushPull(parent, child))
            assertEquals("$child → $parent", NavigationPushPull.BACKWARD, navigationPushPull(child, parent))
        }
    }

    @Test
    fun detailAndPlayerFallBackToPushPullWhenSharedCoverDoesNotMatch() {
        assertTrue(canMorphPlayerCover(10, 10, null, ""))
        assertTrue(canMorphPlayerCover(10, 10, "fav_", "fav_"))
        assertFalse(canMorphPlayerCover(10, 10, "fav_", null))
        assertFalse(canMorphPlayerCover(10, 10, "fav_", ""))
        assertFalse(canMorphPlayerCover(10, 20, "fav_", "fav_"))
        assertEquals(NavigationPushPull.FORWARD, navigationPushPull(Routes.ANIME_DETAIL, Routes.PLAYER))
        assertEquals(NavigationPushPull.BACKWARD, navigationPushPull(Routes.PLAYER, Routes.ANIME_DETAIL))
        assertNull(navigationPushPull(Routes.ANIME_DETAIL, Routes.PLAYER, playerCoverMorph = true))
        assertNull(navigationPushPull(Routes.PLAYER, Routes.ANIME_DETAIL, playerCoverMorph = true))
    }
}
