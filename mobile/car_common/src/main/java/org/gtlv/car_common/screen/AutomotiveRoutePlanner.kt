package org.gtlv.car_common.screen

import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.job.Job
import org.gtlv.core.location.AtlasLocation

internal enum class AutomotiveRoutePhase {
    TO_PICKUP,
    TO_DESTINATION,
}

internal data class AutomotiveRouteTarget(
    val jobId: String,
    val phase: AutomotiveRoutePhase,
    val destination: RoutePoint,
)

internal data class AutomotiveRouteRequest(
    val target: AutomotiveRouteTarget,
    val origin: RoutePoint,
    val headingDegrees: Int?,
)

internal object AutomotiveRoutePlanner {
    fun target(
        job: Job?,
        isPersonCollected: Boolean,
    ): AutomotiveRouteTarget? {
        job ?: return null
        val coordinates = if (isPersonCollected) job.to else job.from
        val destination = coordinates?.let {
            RoutePoint(
                latitude = it.latitude,
                longitude = it.longitude,
            )
        }?.takeIf(RoutePoint::isValid) ?: return null

        return AutomotiveRouteTarget(
            jobId = job.id,
            phase = if (isPersonCollected) {
                AutomotiveRoutePhase.TO_DESTINATION
            } else {
                AutomotiveRoutePhase.TO_PICKUP
            },
            destination = destination,
        )
    }

    fun request(
        target: AutomotiveRouteTarget,
        location: AtlasLocation?,
        headingDegrees: Int?,
    ): AutomotiveRouteRequest? {
        val origin = location?.let {
            RoutePoint(
                latitude = it.latitude,
                longitude = it.longitude,
            )
        }?.takeIf(RoutePoint::isValid) ?: return null

        return AutomotiveRouteRequest(
            target = target,
            origin = origin,
            headingDegrees = headingDegrees,
        )
    }
}
