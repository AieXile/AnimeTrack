package com.aiexile.animetrack.ui.player.components

import com.aiexile.animetrack.ui.player.coordinator.FullscreenVideoOrientation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDisplaySizeTest {
    @Test
    fun `竖屏视频保持竖屏`() {
        val size = VideoDisplaySize.from(1080, 1920, 1f)

        assertTrue(size != null)
        assertFalse(size!!.isLandscape)
    }

    @Test
    fun `旋转九十度的视频按显示方向判断`() {
        val size = VideoDisplaySize.from(1920, 1080, 1f, rotationDegrees = 90)

        assertTrue(size != null)
        assertFalse(size!!.isLandscape)
    }

    @Test
    fun `像素宽高比也参与方向判断`() {
        val size = VideoDisplaySize.from(720, 576, 1.5f)

        assertTrue(size != null)
        assertTrue(size!!.isLandscape)
    }

    @Test
    fun `无效尺寸不触发方向切换`() {
        assertTrue(VideoDisplaySize.from(0, 1080, 1f) == null)
        assertTrue(VideoDisplaySize.from(1080, 1920, 0f) != null)
    }

    @Test
    fun `轨道旋转后的比例和解码上报的比例一致`() {
        val track = VideoDisplaySize.from(720, 576, 0.75f, rotationDegrees = 270)
        val rendered = VideoDisplaySize.from(576, 720, 1f / 0.75f)

        assertEquals(rendered!!.aspectRatio, track!!.aspectRatio, 0.0001f)
        assertTrue(track.isLandscape)
    }

    @Test
    fun `轨道候选存在不同方向或未知尺寸时等待渲染尺寸`() {
        val portrait = VideoDisplaySize.from(1080, 1920, 1f)
        val landscape = VideoDisplaySize.from(1920, 1080, 1f)

        assertNull(commonVideoLandscape(sequenceOf(portrait, landscape)))
        assertNull(commonVideoLandscape(sequenceOf(landscape, null)))
        assertEquals(true, commonVideoLandscape(sequenceOf(landscape, VideoDisplaySize.from(640, 360, 1f))))
    }

    @Test
    fun `渲染尺寸到达后优先使用实际方向`() {
        val orientation = FullscreenVideoOrientation()
        orientation.trackLandscape = true
        assertEquals(true, orientation.landscape)

        orientation.renderedLandscape = false
        assertEquals(false, orientation.landscape)
    }

    @Test
    fun `切集清除旧方向并使用下一集的尺寸`() {
        val orientation = FullscreenVideoOrientation()
        orientation.trackLandscape = true
        orientation.renderedLandscape = true

        orientation.onMediaItemTransition()
        assertNull(orientation.landscape)

        orientation.trackLandscape = false
        assertEquals(false, orientation.landscape)
    }
}
