package org.gtlv.atlas.main

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LandscapeNavigationLayoutTest {

    @Test
    fun controlsStayBetweenPanelsWhenHeightIsAvailable() {
        assertFalse(
            shouldPlaceLandscapeControlsBesideNavigation(
                viewportWidthPixels = 900,
                viewportHeightPixels = 400,
                navigationHeightPixels = 136,
                jobPanelHeightPixels = 110,
                verticalPaddingPixels = 12,
                gapPixels = 12,
                controlSizePixels = 56,
                minimumSideLayoutWidthPixels = 500
            )
        )
    }

    @Test
    fun controlsMoveBesideNavigationOnShortDisplays() {
        assertTrue(
            shouldPlaceLandscapeControlsBesideNavigation(
                viewportWidthPixels = 900,
                viewportHeightPixels = 300,
                navigationHeightPixels = 136,
                jobPanelHeightPixels = 110,
                verticalPaddingPixels = 12,
                gapPixels = 12,
                controlSizePixels = 56,
                minimumSideLayoutWidthPixels = 500
            )
        )
    }

    @Test
    fun controlsDoNotMoveSidewaysWithoutEnoughWidth() {
        assertFalse(
            shouldPlaceLandscapeControlsBesideNavigation(
                viewportWidthPixels = 480,
                viewportHeightPixels = 300,
                navigationHeightPixels = 136,
                jobPanelHeightPixels = 110,
                verticalPaddingPixels = 12,
                gapPixels = 12,
                controlSizePixels = 56,
                minimumSideLayoutWidthPixels = 500
            )
        )
    }
}
