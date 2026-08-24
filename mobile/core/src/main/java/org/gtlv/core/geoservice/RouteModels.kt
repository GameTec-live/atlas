package org.gtlv.core.geoservice

data class RoutePoint(
    val latitude: Double,
    val longitude: Double
) {
    fun isValid(): Boolean =
        latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
}

data class RouteSummary(
    val timeSeconds: Double?,
    val lengthKilometers: Double?
)

data class RouteManeuver(
    val type: Int?,
    val instruction: String,
    val verbalPreTransitionInstruction: String?,
    val timeSeconds: Double?,
    val lengthKilometers: Double?,
    val beginShapeIndex: Int?,
    val endShapeIndex: Int?,
    val travelMode: String?,
    val travelType: String?
)

data class Route(
    val points: List<RoutePoint>,
    val maneuvers: List<RouteManeuver>,
    val summary: RouteSummary,
    val units: String?,
    val language: String?
)

data class RouteProgress(
    val routeShapeIndex: Int,
    val currentManeuverIndex: Int?,
    val remainingDistanceToManeuverKilometers: Double?,
    val remainingRouteDistanceKilometers: Double,
    val remainingRouteTimeSeconds: Double,
    val snappedRoutePoint: RoutePoint? = null,
    val routePosition: Double = routeShapeIndex.toDouble(),
    val distanceFromRouteKilometers: Double? = null,
    val isMovingAgainstRoute: Boolean = false
)
