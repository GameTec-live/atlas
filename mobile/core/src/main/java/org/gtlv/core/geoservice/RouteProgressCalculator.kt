package org.gtlv.core.geoservice

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RouteProgressCalculator {

    fun initial(route: Route): RouteProgress {
        if (route.points.isEmpty()) {
            return emptyProgress()
        }

        return progressAt(
            route = route,
            routeShapeIndex = 0,
            snappedPoint = route.points.first()
        )
    }

    fun calculate(
        route: Route,
        location: RoutePoint,
        previousShapeIndex: Int = 0,
        previousProgress: RouteProgress? = null
    ): RouteProgress {
        if (route.points.isEmpty()) {
            return emptyProgress()
        }
        if (route.points.size == 1) {
            return progressAt(
                route = route,
                routeShapeIndex = 0,
                snappedPoint = route.points.first()
            )
        }

        val previousIndex = previousShapeIndex.coerceIn(
            0,
            route.points.lastIndex - 1
        )
        val firstSegmentIndex = max(
            0,
            previousIndex - MAX_BACKWARD_SEGMENTS
        )
        val lastCandidateSegment = min(
            route.points.lastIndex - 1,
            previousIndex + MAX_FORWARD_SEGMENTS
        )
        var nearest = projectOntoSegment(
            location = location,
            start = route.points[firstSegmentIndex],
            end = route.points[firstSegmentIndex + 1],
            segmentIndex = firstSegmentIndex
        )

        for (
            segmentIndex in
            (firstSegmentIndex + 1)..lastCandidateSegment
        ) {
            val candidate = projectOntoSegment(
                location = location,
                start = route.points[segmentIndex],
                end = route.points[segmentIndex + 1],
                segmentIndex = segmentIndex
            )
            if (candidate.distanceKilometers < nearest.distanceKilometers) {
                nearest = candidate
            }
        }

        val rawRoutePosition =
            nearest.segmentIndex + nearest.fraction
        val previousRoutePosition = previousProgress
            ?.routePosition
            ?: previousShapeIndex.toDouble()
        val movingAgainstRoute =
            rawRoutePosition < previousRoutePosition &&
                    previousProgress?.snappedRoutePoint?.let {
                        previousPoint ->
                        distanceKilometers(
                            nearest.point,
                            previousPoint
                        ) >= WRONG_WAY_DISTANCE_KILOMETERS
                    } == true
        val canAdvance = rawRoutePosition >= previousRoutePosition
        val effectivePosition = if (canAdvance) {
            rawRoutePosition
        } else {
            previousRoutePosition
        }
        val effectivePoint = if (canAdvance) {
            nearest.point
        } else {
            previousProgress?.snappedRoutePoint
                ?: route.points[previousShapeIndex.coerceIn(
                    0,
                    route.points.lastIndex
                )]
        }
        val progressedIndex = if (
            effectivePosition - effectivePosition.toInt() >= 0.999
        ) {
            effectivePosition.toInt() + 1
        } else {
            effectivePosition.toInt()
        }.coerceAtLeast(previousShapeIndex)
            .coerceAtMost(route.points.lastIndex)

        return progressAt(
            route = route,
            routeShapeIndex = progressedIndex,
            snappedPoint = effectivePoint,
            routePosition = effectivePosition,
            distanceFromRouteKilometers =
                nearest.distanceKilometers,
            isMovingAgainstRoute = movingAgainstRoute
        )
    }

    fun remainingRoutePoints(
        route: Route,
        progress: RouteProgress?
    ): List<RoutePoint> {
        if (route.points.isEmpty()) {
            return emptyList()
        }
        if (progress == null) {
            return route.points
        }

        val shapeIndex = progress.routeShapeIndex.coerceIn(
            0,
            route.points.lastIndex
        )
        val firstPoint = progress.snappedRoutePoint
            ?.takeIf(RoutePoint::isValid)
            ?: route.points[shapeIndex]
        val remaining = buildList {
            add(firstPoint)
            addAll(route.points.drop(shapeIndex + 1))
        }

        return remaining.filterIndexed { index, point ->
            index == 0 || point != remaining[index - 1]
        }
    }

    private fun progressAt(
        route: Route,
        routeShapeIndex: Int,
        snappedPoint: RoutePoint,
        routePosition: Double = routeShapeIndex.toDouble(),
        distanceFromRouteKilometers: Double? = null,
        isMovingAgainstRoute: Boolean = false
    ): RouteProgress {
        val shapeIndex = routeShapeIndex.coerceIn(
            0,
            route.points.lastIndex
        )
        val currentManeuverIndex = route.maneuvers
            .indexOfFirst { maneuver ->
                val endIndex = maneuver.endShapeIndex
                    ?: maneuver.beginShapeIndex
                    ?: Int.MAX_VALUE
                endIndex.toDouble() > routePosition
            }
            .takeIf { it >= 0 }
        val nextManeuverIndex = route.maneuvers
            .indexOfFirst { maneuver ->
                maneuver.beginShapeIndex
                    ?.toDouble()
                    ?.let {
                        it > routePosition +
                                MANEUVER_POSITION_EPSILON
                    } == true
            }
            .takeIf { it >= 0 }
            ?: route.maneuvers
                .lastIndex
                .takeIf { index ->
                    val finalManeuver = route.maneuvers
                        .getOrNull(index)
                        ?: return@takeIf false
                    val beginIndex = finalManeuver.beginShapeIndex
                        ?: return@takeIf false
                    finalManeuver.endShapeIndex == beginIndex &&
                            routePosition >= beginIndex.toDouble()
                }

        val geometryTotal = distanceAlongRoute(
            route.points,
            0,
            route.points.lastIndex
        )
        val geometryRemaining = distanceFromSnappedPoint(
            points = route.points,
            snappedPoint = snappedPoint,
            shapeIndex = shapeIndex,
            targetIndex = route.points.lastIndex
        )
        val summaryDistance = route.summary.lengthKilometers
            ?.takeIf { it.isFinite() && it >= 0.0 }
        val distanceScale = if (
            summaryDistance != null && geometryTotal > 0.0
        ) {
            summaryDistance / geometryTotal
        } else {
            1.0
        }
        val remainingDistance = geometryRemaining * distanceScale

        val remainingTime = route.summary.timeSeconds
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { totalTime ->
                if (geometryTotal > 0.0) {
                    totalTime * geometryRemaining / geometryTotal
                } else {
                    0.0
                }
            }
            ?: route.maneuvers
                .drop(currentManeuverIndex ?: route.maneuvers.size)
                .mapNotNull { it.timeSeconds }
                .sum()

        val currentManeuverDistance =
            currentManeuverIndex?.let { index ->
                val maneuver = route.maneuvers[index]
                val targetIndex = (
                        maneuver.endShapeIndex
                            ?: maneuver.beginShapeIndex
                            ?: shapeIndex
                        ).coerceIn(
                            shapeIndex,
                            route.points.lastIndex
                        )
                distanceFromSnappedPoint(
                    points = route.points,
                    snappedPoint = snappedPoint,
                    shapeIndex = shapeIndex,
                    targetIndex = targetIndex
                ) * distanceScale
            }

        val displayedManeuverIndex =
            nextManeuverIndex ?: currentManeuverIndex
        val maneuverDistance = displayedManeuverIndex?.let { index ->
            val maneuver = route.maneuvers[index]
            val targetIndex = (
                    if (nextManeuverIndex != null) {
                        maneuver.beginShapeIndex
                    } else {
                        maneuver.endShapeIndex
                            ?: maneuver.beginShapeIndex
                    }
                        ?: shapeIndex
                    ).coerceIn(
                        shapeIndex,
                        route.points.lastIndex
                    )
            distanceFromSnappedPoint(
                points = route.points,
                snappedPoint = snappedPoint,
                shapeIndex = shapeIndex,
                targetIndex = targetIndex
            ) * distanceScale
        }

        return RouteProgress(
            routeShapeIndex = shapeIndex,
            currentManeuverIndex = currentManeuverIndex,
            remainingDistanceToManeuverKilometers =
                maneuverDistance,
            remainingRouteDistanceKilometers =
                max(0.0, remainingDistance),
            remainingRouteTimeSeconds =
                max(0.0, remainingTime),
            snappedRoutePoint = snappedPoint,
            routePosition = routePosition,
            distanceFromRouteKilometers =
                distanceFromRouteKilometers,
            isMovingAgainstRoute = isMovingAgainstRoute,
            nextManeuverIndex = nextManeuverIndex,
            remainingDistanceInCurrentManeuverKilometers =
                currentManeuverDistance
        )
    }

    private fun distanceFromSnappedPoint(
        points: List<RoutePoint>,
        snappedPoint: RoutePoint,
        shapeIndex: Int,
        targetIndex: Int
    ): Double {
        if (shapeIndex >= targetIndex || shapeIndex >= points.lastIndex) {
            return 0.0
        }

        return distanceKilometers(
            snappedPoint,
            points[shapeIndex + 1]
        ) + distanceAlongRoute(
            points,
            shapeIndex + 1,
            targetIndex
        )
    }

    private fun projectOntoSegment(
        location: RoutePoint,
        start: RoutePoint,
        end: RoutePoint,
        segmentIndex: Int
    ): SegmentProjection {
        val latitudeScale = cos(
            Math.toRadians(
                (start.latitude + end.latitude + location.latitude) /
                        3.0
            )
        )
        val segmentX = (end.longitude - start.longitude) * latitudeScale
        val segmentY = end.latitude - start.latitude
        val locationX =
            (location.longitude - start.longitude) * latitudeScale
        val locationY = location.latitude - start.latitude
        val segmentLengthSquared =
            segmentX * segmentX + segmentY * segmentY
        val fraction = if (segmentLengthSquared <= 0.0) {
            0.0
        } else {
            ((locationX * segmentX + locationY * segmentY) /
                    segmentLengthSquared).coerceIn(0.0, 1.0)
        }
        val point = RoutePoint(
            latitude = start.latitude +
                    (end.latitude - start.latitude) * fraction,
            longitude = start.longitude +
                    (end.longitude - start.longitude) * fraction
        )

        return SegmentProjection(
            segmentIndex = segmentIndex,
            fraction = fraction,
            point = point,
            distanceKilometers = distanceKilometers(location, point)
        )
    }

    private fun distanceAlongRoute(
        points: List<RoutePoint>,
        startIndex: Int,
        endIndex: Int
    ): Double {
        if (points.size < 2 || endIndex <= startIndex) {
            return 0.0
        }

        var distance = 0.0
        val safeStart = startIndex.coerceIn(0, points.lastIndex)
        val safeEnd = endIndex.coerceIn(safeStart, points.lastIndex)
        for (index in safeStart until safeEnd) {
            distance += distanceKilometers(
                points[index],
                points[index + 1]
            )
        }
        return distance
    }

    private fun distanceKilometers(
        first: RoutePoint,
        second: RoutePoint
    ): Double {
        val latitudeDelta = Math.toRadians(
            second.latitude - first.latitude
        )
        val longitudeDelta = Math.toRadians(
            second.longitude - first.longitude
        )
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val haversine = sin(latitudeDelta / 2.0).pow(2) +
                cos(firstLatitude) * cos(secondLatitude) *
                sin(longitudeDelta / 2.0).pow(2)
        val centralAngle = 2.0 * asin(
            min(1.0, sqrt(max(0.0, haversine)))
        )
        return EARTH_RADIUS_KILOMETERS * centralAngle
    }

    private fun emptyProgress() = RouteProgress(
        routeShapeIndex = 0,
        currentManeuverIndex = null,
        remainingDistanceToManeuverKilometers = null,
        remainingRouteDistanceKilometers = 0.0,
        remainingRouteTimeSeconds = 0.0,
        snappedRoutePoint = null,
        routePosition = 0.0,
        distanceFromRouteKilometers = null,
        isMovingAgainstRoute = false,
        nextManeuverIndex = null,
        remainingDistanceInCurrentManeuverKilometers = null
    )

    private data class SegmentProjection(
        val segmentIndex: Int,
        val fraction: Double,
        val point: RoutePoint,
        val distanceKilometers: Double
    )

    private const val EARTH_RADIUS_KILOMETERS = 6371.0088
    private const val MAX_FORWARD_SEGMENTS = 250
    private const val MAX_BACKWARD_SEGMENTS = 40
    private const val WRONG_WAY_DISTANCE_KILOMETERS = 0.05
    private const val MANEUVER_POSITION_EPSILON = 0.001
}
