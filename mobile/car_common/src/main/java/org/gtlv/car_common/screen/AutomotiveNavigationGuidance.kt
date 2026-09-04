package org.gtlv.car_common.screen

import org.gtlv.core.geoservice.Route
import org.gtlv.core.geoservice.RouteManeuver
import org.gtlv.core.geoservice.RouteProgress
import org.gtlv.car_common.R
import kotlin.math.roundToInt

internal data class AutomotiveManeuverIcon(
    val drawableResource: Int,
    val mirrorHorizontally: Boolean = false,
)

internal data class AutomotiveNavigationGuidance(
    val maneuverIndex: Int,
    val maneuver: RouteManeuver,
    val distanceToManeuverKilometers: Double,
    val remainingRouteDistanceKilometers: Double,
    val remainingRouteTimeSeconds: Double,
    val roundaboutExitCount: Int?,
    val roundaboutTurnDegrees: Int?,
)

internal data class AutomotiveNavigationDisplayState(
    val maneuverIndex: Int,
    val instruction: String,
    val maneuverType: Int?,
    val distanceToManeuverDecameters: Int,
    val remainingRouteHectometers: Int,
    val remainingRouteMinutes: Int,
    val roundaboutExitCount: Int?,
    val roundaboutTurnDegrees: Int?,
)

internal fun automotiveNavigationGuidance(
    route: Route,
    progress: RouteProgress?,
): AutomotiveNavigationGuidance? {
    val maneuverIndex = progress?.nextManeuverIndex
        ?: progress?.currentManeuverIndex
        ?: route.maneuvers.indices.firstOrNull()
        ?: return null
    val maneuver = route.maneuvers.getOrNull(maneuverIndex)
        ?: return null

    return AutomotiveNavigationGuidance(
        maneuverIndex = maneuverIndex,
        maneuver = maneuver,
        distanceToManeuverKilometers = (
            progress?.remainingDistanceToManeuverKilometers
                ?: maneuver.lengthKilometers
                ?: 0.0
            ).validDistance(),
        remainingRouteDistanceKilometers = (
            progress?.remainingRouteDistanceKilometers
                ?: route.summary.lengthKilometers
                ?: 0.0
            ).validDistance(),
        remainingRouteTimeSeconds = (
            progress?.remainingRouteTimeSeconds
                ?: route.summary.timeSeconds
                ?: 0.0
            ).validDistance(),
        roundaboutExitCount = if (maneuver.type == 26 || maneuver.type == 27) {
            maneuver.roundaboutExitCount
                ?: route.maneuvers.getOrNull(maneuverIndex - 1)
                    ?.takeIf { previous -> previous.type == 26 }
                    ?.roundaboutExitCount
        } else {
            null
        },
        roundaboutTurnDegrees = roundaboutTurnDegrees(
            route = route,
            maneuverIndex = maneuverIndex,
        ),
    )
}

internal fun AutomotiveNavigationGuidance.displayState() =
    AutomotiveNavigationDisplayState(
        maneuverIndex = maneuverIndex,
        instruction = maneuver.instruction,
        maneuverType = maneuver.type,
        distanceToManeuverDecameters =
            (distanceToManeuverKilometers * 100.0).roundToInt(),
        remainingRouteHectometers =
            (remainingRouteDistanceKilometers * 10.0).roundToInt(),
        remainingRouteMinutes =
            (remainingRouteTimeSeconds / 60.0).roundToInt(),
        roundaboutExitCount = roundaboutExitCount,
        roundaboutTurnDegrees = roundaboutTurnDegrees,
    )

/** Maps Valhalla maneuvers to Apache-licensed Google Material Symbols. */
internal fun maneuverIcon(
    valhallaType: Int?,
    roundaboutTurnDegrees: Int? = null,
    roundaboutExitCount: Int? = null,
): AutomotiveManeuverIcon =
    when (valhallaType) {
        2, 10 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_turn_right)
        3, 15 -> AutomotiveManeuverIcon(
            R.drawable.ic_maneuver_turn_right,
            mirrorHorizontally = true,
        )
        9 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_slight_right)
        16 -> AutomotiveManeuverIcon(
            R.drawable.ic_maneuver_slight_right,
            mirrorHorizontally = true,
        )
        11 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_sharp_right)
        14 -> AutomotiveManeuverIcon(
            R.drawable.ic_maneuver_sharp_right,
            mirrorHorizontally = true,
        )
        12 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_uturn_right)
        13 -> AutomotiveManeuverIcon(
            R.drawable.ic_maneuver_uturn_right,
            mirrorHorizontally = true,
        )
        18, 20 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_ramp_right)
        19, 21 -> AutomotiveManeuverIcon(
            R.drawable.ic_maneuver_ramp_right,
            mirrorHorizontally = true,
        )
        23 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_fork_right)
        24 -> AutomotiveManeuverIcon(
            R.drawable.ic_maneuver_fork_right,
            mirrorHorizontally = true,
        )
        25 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_merge)
        26, 27 -> when (
            simplifiedRoundaboutTurnDegrees(
                roundaboutTurnDegrees
                    ?: approximateRoundaboutTurnDegrees(roundaboutExitCount),
            )
        ) {
            90 -> AutomotiveManeuverIcon(
                R.drawable.ic_maneuver_roundabout_left,
                mirrorHorizontally = true,
            )
            270 -> AutomotiveManeuverIcon(
                R.drawable.ic_maneuver_roundabout_left,
            )
            else -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_straight)
        }
        4, 5, 6 -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_destination)
        else -> AutomotiveManeuverIcon(R.drawable.ic_maneuver_straight)
    }

private fun approximateRoundaboutTurnDegrees(exitCount: Int?): Int =
    when (exitCount) {
        1 -> 90
        2, null -> 0
        3 -> 270
        else -> 270
    }

internal fun simplifiedRoundaboutTurnDegrees(turnDegrees: Int): Int {
    val normalized = ((turnDegrees % 360) + 360) % 360
    val signed = if (normalized < 180) normalized else normalized - 360
    return when {
        signed > ROUNDABOUT_STRAIGHT_THRESHOLD_DEGREES -> 90
        signed < -ROUNDABOUT_STRAIGHT_THRESHOLD_DEGREES -> 270
        else -> 0
    }
}

private fun roundaboutTurnDegrees(
    route: Route,
    maneuverIndex: Int,
): Int? {
    val maneuver = route.maneuvers.getOrNull(maneuverIndex) ?: return null
    if (maneuver.type != 26 && maneuver.type != 27) return null

    val enterManeuver = if (maneuver.type == 26) {
        maneuver
    } else {
        route.maneuvers.getOrNull(maneuverIndex - 1)
            ?.takeIf { it.type == 26 }
    } ?: return null
    val exitManeuver = if (maneuver.type == 27) {
        maneuver
    } else {
        route.maneuvers.getOrNull(maneuverIndex + 1)
            ?.takeIf { it.type == 27 }
    } ?: return null

    val approachBearing = enterManeuver.bearingBeforeDegrees ?: return null
    val exitBearing = exitManeuver.bearingAfterDegrees ?: return null
    return (exitBearing - approachBearing + 360) % 360
}

private fun Double.validDistance(): Double =
    takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

private const val ROUNDABOUT_STRAIGHT_THRESHOLD_DEGREES = 35
