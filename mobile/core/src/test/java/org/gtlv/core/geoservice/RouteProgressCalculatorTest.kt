package org.gtlv.core.geoservice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressCalculatorTest {

    @Test
    fun passedManeuverAdvancesAndConsumedGeometryIsRemoved() {
        val route = routeWithManeuvers()

        val progress = RouteProgressCalculator.calculate(
            route = route,
            location = RoutePoint(
                latitude = 0.0,
                longitude = 0.0015
            )
        )
        val remaining = RouteProgressCalculator
            .remainingRoutePoints(route, progress)

        assertEquals(1, progress.routeShapeIndex)
        assertEquals(1, progress.currentManeuverIndex)
        assertEquals(3, remaining.size)
        assertEquals(0.0015, remaining.first().longitude, 0.000001)
        assertTrue(remaining.none { it.longitude < 0.0015 })
    }

    @Test
    fun upcomingTurnUsesDistanceToItsBeginIndex() {
        val route = routeWithUpcomingTurns()

        val progress = RouteProgressCalculator.initial(route)

        assertEquals(0, progress.currentManeuverIndex)
        assertEquals(1, progress.nextManeuverIndex)
        assertEquals(
            0.111,
            progress.remainingDistanceToManeuverKilometers ?: 0.0,
            0.002
        )
        assertEquals(
            0.111,
            progress.remainingDistanceInCurrentManeuverKilometers
                ?: 0.0,
            0.002
        )
        assertTrue(
            (progress.remainingDistanceToManeuverKilometers ?: 0.0) <
                    (route.maneuvers[1].lengthKilometers ?: 0.0)
        )
    }

    @Test
    fun nextTurnAdvancesAfterCurrentTurnWasPassed() {
        val route = routeWithUpcomingTurns()

        val progress = RouteProgressCalculator.calculate(
            route = route,
            location = RoutePoint(0.0, 0.00275)
        )

        assertEquals(1, progress.currentManeuverIndex)
        assertEquals(2, progress.nextManeuverIndex)
        assertEquals(
            0.028,
            progress.remainingDistanceToManeuverKilometers ?: 0.0,
            0.002
        )
    }

    @Test
    fun arrivalIsPresentedAsTheFinalUpcomingManeuver() {
        val route = routeWithUpcomingTurns()
        val arrival = maneuver(
            instruction = "You have arrived",
            beginIndex = 4,
            endIndex = 4
        )
        val routeWithArrival = route.copy(
            maneuvers = route.maneuvers + arrival
        )

        val approaching = RouteProgressCalculator.calculate(
            route = routeWithArrival,
            location = RoutePoint(0.0, 0.00375)
        )
        val arrived = RouteProgressCalculator.calculate(
            route = routeWithArrival,
            location = RoutePoint(0.0, 0.004)
        )

        assertEquals(3, approaching.nextManeuverIndex)
        assertEquals(
            0.028,
            approaching.remainingDistanceToManeuverKilometers ?: 0.0,
            0.002
        )
        assertEquals(3, arrived.nextManeuverIndex)
        assertEquals(
            0.0,
            arrived.remainingDistanceToManeuverKilometers ?: -1.0,
            0.000001
        )
    }

    @Test
    fun progressNeverMovesBackwardsOnTheSameRoute() {
        val route = routeWithManeuvers()

        val progress = RouteProgressCalculator.calculate(
            route = route,
            location = RoutePoint(0.0, 0.0002),
            previousShapeIndex = 2
        )

        assertTrue(progress.routeShapeIndex >= 2)
        assertEquals(1, progress.currentManeuverIndex)
    }

    @Test
    fun routeWithoutManeuversStillProducesRemainingGeometry() {
        val route = routeWithManeuvers().copy(
            maneuvers = emptyList()
        )

        val progress = RouteProgressCalculator.calculate(
            route = route,
            location = RoutePoint(0.0, 0.0005)
        )

        assertNull(progress.currentManeuverIndex)
        assertTrue(
            RouteProgressCalculator
                .remainingRoutePoints(route, progress)
                .size >= 2
        )
    }

    @Test
    fun nearestMatchIsLimitedToAReasonableForwardWindow() {
        val points = (0..400).map { index ->
            RoutePoint(0.0, index / 1_000_000.0)
        }
        val route = routeWithManeuvers().copy(
            points = points,
            maneuvers = emptyList()
        )

        val progress = RouteProgressCalculator.calculate(
            route = route,
            location = points.last(),
            previousShapeIndex = 0
        )

        assertTrue(progress.routeShapeIndex <= 251)
    }

    @Test
    fun distanceFromRouteDetectsAnOffRouteLocation() {
        val route = routeWithManeuvers()

        val progress = RouteProgressCalculator.calculate(
            route = route,
            location = RoutePoint(0.001, 0.0015)
        )

        assertTrue(
            (progress.distanceFromRouteKilometers ?: 0.0) > 0.1
        )
    }

    @Test
    fun reversingAlongRouteIsReportedWithoutMovingProgressBackwards() {
        val route = routeWithManeuvers()
        val forwardProgress = RouteProgressCalculator.calculate(
            route = route,
            location = RoutePoint(0.0, 0.0025)
        )

        val reverseProgress = RouteProgressCalculator.calculate(
            route = route,
            location = RoutePoint(0.0, 0.0015),
            previousShapeIndex = forwardProgress.routeShapeIndex,
            previousProgress = forwardProgress
        )

        assertTrue(reverseProgress.isMovingAgainstRoute)
        assertTrue(
            reverseProgress.routePosition >=
                    forwardProgress.routePosition
        )
        assertEquals(
            forwardProgress.snappedRoutePoint,
            reverseProgress.snappedRoutePoint
        )
    }

    private fun routeWithManeuvers(): Route = Route(
        points = listOf(
            RoutePoint(0.0, 0.0),
            RoutePoint(0.0, 0.001),
            RoutePoint(0.0, 0.002),
            RoutePoint(0.0, 0.003)
        ),
        maneuvers = listOf(
            maneuver(
                instruction = "Turn right",
                beginIndex = 0,
                endIndex = 1
            ),
            maneuver(
                instruction = "Continue straight",
                beginIndex = 1,
                endIndex = 3
            )
        ),
        summary = RouteSummary(
            timeSeconds = 180.0,
            lengthKilometers = 0.333
        ),
        units = "kilometers",
        language = "en-US"
    )

    private fun routeWithUpcomingTurns(): Route = Route(
        points = listOf(
            RoutePoint(0.0, 0.0),
            RoutePoint(0.0, 0.001),
            RoutePoint(0.0, 0.002),
            RoutePoint(0.0, 0.003),
            RoutePoint(0.0, 0.004)
        ),
        maneuvers = listOf(
            maneuver("Drive north", 0, 1).copy(
                lengthKilometers = 0.111
            ),
            maneuver("Turn right", 1, 3).copy(
                lengthKilometers = 0.222
            ),
            maneuver("Turn left", 3, 4).copy(
                lengthKilometers = 0.111
            )
        ),
        summary = RouteSummary(
            timeSeconds = null,
            lengthKilometers = null
        ),
        units = "kilometers",
        language = "en-US"
    )

    private fun maneuver(
        instruction: String,
        beginIndex: Int,
        endIndex: Int
    ) = RouteManeuver(
        type = 8,
        instruction = instruction,
        verbalPreTransitionInstruction = null,
        timeSeconds = 60.0,
        lengthKilometers = 0.1,
        beginShapeIndex = beginIndex,
        endShapeIndex = endIndex,
        travelMode = "drive",
        travelType = "car"
    )
}
