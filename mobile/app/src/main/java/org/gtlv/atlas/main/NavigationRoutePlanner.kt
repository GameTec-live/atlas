package org.gtlv.atlas.main

import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.job.Job
import org.gtlv.core.location.AtlasLocation

data class NavigationRouteRequest(
    val jobId: String,
    val phase: NavigationPhase,
    val origin: RoutePoint,
    val destination: RoutePoint,
    val headingDegrees: Int?
)

data class NavigationRouteOrigin(
    val point: RoutePoint,
    val headingDegrees: Int?
)

sealed interface NavigationRoutePlan {
    data object None : NavigationRoutePlan

    data class WaitingForLocation(
        val phase: NavigationPhase
    ) : NavigationRoutePlan

    data object PickupUnavailable : NavigationRoutePlan

    data object WaitingForDestination : NavigationRoutePlan

    data class Request(
        val value: NavigationRouteRequest
    ) : NavigationRoutePlan
}

object NavigationRoutePlanner {

    fun plan(
        currentJob: Job?,
        isPersonCollected: Boolean,
        latestLocation: AtlasLocation?,
        latestHeadingDegrees: Int?,
        pickupOrigin: NavigationRouteOrigin?,
        destinationOrigin: NavigationRouteOrigin?
    ): NavigationRoutePlan {
        val job = currentJob ?: return NavigationRoutePlan.None
        val pickup = job.from?.let { coordinates ->
            RoutePoint(
                latitude = coordinates.latitude,
                longitude = coordinates.longitude
            )
        }?.takeIf(RoutePoint::isValid)
            ?: return NavigationRoutePlan.PickupUnavailable

        if (!isPersonCollected) {
            val origin = pickupOrigin
                ?.takeIf { it.point.isValid() }
                ?: latestLocation?.toRouteOrigin(
                    latestHeadingDegrees
                )
                ?: return NavigationRoutePlan.WaitingForLocation(
                    NavigationPhase.ToPickup
                )

            return NavigationRoutePlan.Request(
                NavigationRouteRequest(
                    jobId = job.id,
                    phase = NavigationPhase.ToPickup,
                    origin = origin.point,
                    destination = pickup,
                    headingDegrees = origin.headingDegrees
                )
            )
        }

        val destination = job.to?.let { coordinates ->
            RoutePoint(
                latitude = coordinates.latitude,
                longitude = coordinates.longitude
            )
        }?.takeIf(RoutePoint::isValid)
            ?: return NavigationRoutePlan.WaitingForDestination

        val origin = destinationOrigin
            ?.takeIf { it.point.isValid() }
            ?: return NavigationRoutePlan.WaitingForLocation(
                NavigationPhase.ToDestination
            )

        return NavigationRoutePlan.Request(
            NavigationRouteRequest(
                jobId = job.id,
                phase = NavigationPhase.ToDestination,
                origin = origin.point,
                destination = destination,
                headingDegrees = origin.headingDegrees
            )
        )
    }

    private fun AtlasLocation.toRouteOrigin(
        headingDegrees: Int?
    ): NavigationRouteOrigin? {
        val point = RoutePoint(
            latitude = latitude,
            longitude = longitude
        ).takeIf(RoutePoint::isValid) ?: return null

        return NavigationRouteOrigin(
            point = point,
            headingDegrees = headingDegrees
        )
    }
}
