package org.gtlv.atlas.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class NavigationCameraPaddingTest {

    @Test
    fun portraitNavigationPlacesPuckBelowScreenCenter() {
        assertEquals(
            420.0,
            navigationCameraTopPaddingPixels(
                mapHeightPixels = 1_000,
                isLandscape = false,
                hasActiveRoute = true
            ),
            0.0
        )
    }

    @Test
    fun landscapeNavigationUsesSmallerOffset() {
        assertEquals(
            240.0,
            navigationCameraTopPaddingPixels(
                mapHeightPixels = 1_000,
                isLandscape = true,
                hasActiveRoute = true
            ),
            0.0
        )
    }

    @Test
    fun landscapeNavigationPlacesPuckInRightHalf() {
        assertEquals(
            280.0,
            navigationCameraLeftPaddingPixels(
                mapWidthPixels = 1_000,
                isLandscape = true,
                hasActiveRoute = true
            ),
            0.0
        )
    }

    @Test
    fun portraitNavigationKeepsPuckHorizontallyCentered() {
        assertEquals(
            0.0,
            navigationCameraLeftPaddingPixels(
                mapWidthPixels = 1_000,
                isLandscape = false,
                hasActiveRoute = true
            ),
            0.0
        )
    }

    @Test
    fun navigationCameraUsesLeftPaddingForRightSidePuck() {
        assertArrayEquals(
            doubleArrayOf(280.0, 240.0, 0.0, 0.0),
            navigationCameraPadding(
                topPaddingPixels = 240.0,
                leftPaddingPixels = 280.0
            ),
            0.0
        )
    }

    @Test
    fun cameraReturnsToCenteredPaddingWithoutRoute() {
        assertEquals(
            0.0,
            navigationCameraTopPaddingPixels(
                mapHeightPixels = 1_000,
                isLandscape = false,
                hasActiveRoute = false
            ),
            0.0
        )
    }
}
