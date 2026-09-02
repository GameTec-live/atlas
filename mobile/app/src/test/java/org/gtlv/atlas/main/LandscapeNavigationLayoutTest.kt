package org.gtlv.atlas.main

import org.junit.Assert.assertEquals
import org.junit.Test

class LandscapeNavigationLayoutTest {

    @Test
    fun controlsStayBetweenPanelsWhenHeightIsAvailable() {
        assertEquals(
            LandscapeControlsPlacement(
                besideNavigation = false,
                topPixels = 160
            ),
            calculateLandscapeControlsPlacement(
                viewportWidthPixels = 900,
                viewportHeightPixels = 400,
                navigationHeightPixels = 136,
                jobPanelHeightPixels = 110,
                verticalPaddingPixels = 12,
                preferredGapPixels = 12,
                minimumGapPixels = 4,
                controlSizePixels = 56,
                minimumSideLayoutWidthPixels = 500
            )
        )
    }

    @Test
    fun controlsUseSmallerBalancedGapsBeforeMovingSideways() {
        assertEquals(
            LandscapeControlsPlacement(
                besideNavigation = false,
                topPixels = 155
            ),
            calculateLandscapeControlsPlacement(
                viewportWidthPixels = 900,
                viewportHeightPixels = 340,
                navigationHeightPixels = 136,
                jobPanelHeightPixels = 110,
                verticalPaddingPixels = 12,
                preferredGapPixels = 12,
                minimumGapPixels = 4,
                controlSizePixels = 56,
                minimumSideLayoutWidthPixels = 500
            )
        )
    }

    @Test
    fun controlsMoveBesideNavigationOnShortDisplays() {
        assertEquals(
            LandscapeControlsPlacement(
                besideNavigation = true,
                topPixels = 12
            ),
            calculateLandscapeControlsPlacement(
                viewportWidthPixels = 900,
                viewportHeightPixels = 300,
                navigationHeightPixels = 136,
                jobPanelHeightPixels = 110,
                verticalPaddingPixels = 12,
                preferredGapPixels = 12,
                minimumGapPixels = 4,
                controlSizePixels = 56,
                minimumSideLayoutWidthPixels = 500
            )
        )
    }

    @Test
    fun controlsDoNotMoveSidewaysWithoutEnoughWidth() {
        assertEquals(
            LandscapeControlsPlacement(
                besideNavigation = false,
                topPixels = 152
            ),
            calculateLandscapeControlsPlacement(
                viewportWidthPixels = 480,
                viewportHeightPixels = 300,
                navigationHeightPixels = 136,
                jobPanelHeightPixels = 110,
                verticalPaddingPixels = 12,
                preferredGapPixels = 12,
                minimumGapPixels = 4,
                controlSizePixels = 56,
                minimumSideLayoutWidthPixels = 500
            )
        )
    }
}
