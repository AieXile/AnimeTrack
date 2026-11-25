package com.aiexile.animetrack.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FabLocationTest {

    @Test
    fun `传统沉底将导航栏旁回退到右下角`() {
        assertEquals(FabLocation.BOTTOM_RIGHT, FabLocation.NAV_BAR.resolveFor(NavigationStyle.BOTTOM))
        assertEquals(FabLocation.BOTTOM_RIGHT, FabLocation.BOTTOM_RIGHT.resolveFor(NavigationStyle.BOTTOM))
        assertEquals(FabLocation.TOP_BAR, FabLocation.TOP_BAR.resolveFor(NavigationStyle.BOTTOM))
    }

    @Test
    fun `悬浮胶囊沿用选定的按钮位置`() {
        for (location in FabLocation.entries) {
            assertEquals(location, location.resolveFor(NavigationStyle.CAPSULE))
        }
    }
}
