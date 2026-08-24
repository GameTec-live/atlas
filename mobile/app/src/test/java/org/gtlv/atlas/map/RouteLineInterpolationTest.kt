package org.gtlv.atlas.map

import org.gtlv.core.geoservice.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteLineInterpolationTest {

    @Test
    fun consumedRouteHeadMovesProgressivelyToTarget() {
        val previous = listOf(
            RoutePoint(0.0, 0.0),
            RoutePoint(0.0, 0.001),
            RoutePoint(0.0, 0.002),
            RoutePoint(0.0, 0.003)
        )
        val target = listOf(
            RoutePoint(0.0, 0.0015),
            RoutePoint(0.0, 0.002),
            RoutePoint(0.0, 0.003)
        )

        val halfway = interpolateRemainingRoute(
            previous = previous,
            target = target,
            fraction = 0.5
        )

        assertTrue(canAnimateRouteTransition(previous, target))
        assertEquals(0.00075, halfway.first().longitude, 0.000001)
        assertEquals(target, interpolateRemainingRoute(
            previous,
            target,
            1.0
        ))
    }

    @Test
    fun replacementRouteDoesNotMorphThroughUnrelatedGeometry() {
        val previous = listOf(
            RoutePoint(0.0, 0.0),
            RoutePoint(0.0, 0.001)
        )
        val target = listOf(
            RoutePoint(1.0, 1.0),
            RoutePoint(1.0, 1.001)
        )

        assertFalse(canAnimateRouteTransition(previous, target))
        assertEquals(
            target,
            interpolateRemainingRoute(previous, target, 0.5)
        )
    }
}
