package org.gtlv.atlas.map

import org.junit.Assert.assertEquals
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
