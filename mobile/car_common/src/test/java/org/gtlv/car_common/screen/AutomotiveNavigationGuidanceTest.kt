package org.gtlv.car_common.screen

import org.gtlv.car_common.R
import org.gtlv.core.geoservice.Route
import org.gtlv.core.geoservice.RouteManeuver
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.geoservice.RouteProgress
import org.gtlv.core.geoservice.RouteSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomotiveNavigationGuidanceTest {
    @Test
    fun `upcoming maneuver is selected`() {
        val route = route()
        val guidance = automotiveNavigationGuidance(
            route = route,
            progress = progress(nextManeuverIndex = 1),
        )!!

        assertEquals("Turn right", guidance.maneuver.instruction)
        assertEquals(1, guidance.maneuverIndex)
        assertEquals(0.24, guidance.distanceToManeuverKilometers, 0.0)
        assertEquals(3.2, guidance.remainingRouteDistanceKilometers, 0.0)
        assertEquals(420.0, guidance.remainingRouteTimeSeconds, 0.0)
    }

    @Test
    fun `first maneuver is used before progress is available`() {
        val guidance = automotiveNavigationGuidance(route(), null)!!

        assertEquals(0, guidance.maneuverIndex)
        assertEquals("Head north", guidance.maneuver.instruction)
    }

    @Test
    fun `route without maneuvers has no guidance label`() {
        assertNull(
            automotiveNavigationGuidance(
                route().copy(maneuvers = emptyList()),
                progress = null,
            ),
        )
    }

    @Test
    fun `material icons cover turns u-turns and roundabouts`() {
        assertEquals(R.drawable.ic_maneuver_turn_right, maneuverIcon(10).drawableResource)
        assertEquals(true, maneuverIcon(15).mirrorHorizontally)
        assertEquals(R.drawable.ic_maneuver_uturn_right, maneuverIcon(12).drawableResource)
        assertEquals(R.drawable.ic_maneuver_straight, maneuverIcon(26).drawableResource)
        assertEquals(R.drawable.ic_maneuver_straight, maneuverIcon(26, 0).drawableResource)
        assertEquals(
            true,
            maneuverIcon(26, roundaboutExitCount = 1).mirrorHorizontally,
        )
        assertEquals(
            R.drawable.ic_maneuver_roundabout_left,
            maneuverIcon(26, roundaboutExitCount = 3).drawableResource,
        )
        assertEquals(0, simplifiedRoundaboutTurnDegrees(15))
        assertEquals(90, simplifiedRoundaboutTurnDegrees(80))
        assertEquals(270, simplifiedRoundaboutTurnDegrees(280))
        assertEquals(270, simplifiedRoundaboutTurnDegrees(180))
        assertEquals(R.drawable.ic_maneuver_straight, maneuverIcon(null).drawableResource)
    }

    @Test
    fun `roundabout exit keeps the entering maneuver exit number`() {
        val route = route().copy(
            maneuvers = listOf(
                maneuver(26, "Take the 3rd exit", 0.1, roundaboutExitCount = 3),
                maneuver(27, "Exit the roundabout", 0.1),
            ),
        )

        val guidance = automotiveNavigationGuidance(
            route = route,
            progress = progress(nextManeuverIndex = 1),
        )!!

        assertEquals(3, guidance.roundaboutExitCount)
    }

    @Test
    fun `roundabout icon uses the real entry to exit angle`() {
        val route = route().copy(
            maneuvers = listOf(
                maneuver(
                    type = 26,
                    instruction = "Take the 2nd exit",
                    length = 0.1,
                    roundaboutExitCount = 2,
                    bearingBeforeDegrees = 15,
                ),
                maneuver(
                    type = 27,
                    instruction = "Exit the roundabout",
                    length = 0.1,
                    bearingAfterDegrees = 20,
                ),
            ),
        )

        val entering = automotiveNavigationGuidance(
            route = route,
            progress = progress(nextManeuverIndex = 0),
        )!!
        val exiting = automotiveNavigationGuidance(
            route = route,
            progress = progress(nextManeuverIndex = 1),
        )!!

        assertEquals(5, entering.roundaboutTurnDegrees)
        assertEquals(5, exiting.roundaboutTurnDegrees)
    }

    private fun route() = Route(
        points = listOf(
            RoutePoint(48.20, 16.30),
            RoutePoint(48.21, 16.31),
        ),
        maneuvers = listOf(
            maneuver(1, "Head north", 0.5),
            maneuver(10, "Turn right", 1.0),
            maneuver(15, "Turn left", 1.7),
        ),
        summary = RouteSummary(
            timeSeconds = 420.0,
            lengthKilometers = 3.2,
        ),
        units = "kilometers",
        language = "en",
    )

    private fun maneuver(
        type: Int,
        instruction: String,
        length: Double,
        roundaboutExitCount: Int? = null,
        bearingBeforeDegrees: Int? = null,
        bearingAfterDegrees: Int? = null,
    ) =
        RouteManeuver(
            type = type,
            instruction = instruction,
            verbalPreTransitionInstruction = null,
            timeSeconds = null,
            lengthKilometers = length,
            beginShapeIndex = 0,
            endShapeIndex = 1,
            travelMode = "drive",
            travelType = "car",
            roundaboutExitCount = roundaboutExitCount,
            bearingBeforeDegrees = bearingBeforeDegrees,
            bearingAfterDegrees = bearingAfterDegrees,
        )

    private fun progress(nextManeuverIndex: Int?) = RouteProgress(
        routeShapeIndex = 0,
        currentManeuverIndex = 0,
        remainingDistanceToManeuverKilometers = 0.24,
        remainingRouteDistanceKilometers = 3.2,
        remainingRouteTimeSeconds = 420.0,
        nextManeuverIndex = nextManeuverIndex,
    )
}
