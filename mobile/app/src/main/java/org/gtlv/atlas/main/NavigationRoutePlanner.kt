package org.gtlv.atlas.main

import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.job.Job
import org.gtlv.core.location.AtlasLocation

data class NavigationRouteRequest(
    val jobId: String,
    val phase: NavigationPhase,
    val origin: RoutePoint,
    val destination: RoutePoint
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
        pickupOrigin: RoutePoint?,
        destinationOrigin: RoutePoint?
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
                ?.takeIf(RoutePoint::isValid)
                ?: latestLocation?.toRoutePoint()
                ?: return NavigationRoutePlan.WaitingForLocation(
                    NavigationPhase.ToPickup
                )

            return NavigationRoutePlan.Request(
                NavigationRouteRequest(
                    jobId = job.id,
                    phase = NavigationPhase.ToPickup,
                    origin = origin,
                    destination = pickup
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
            ?.takeIf(RoutePoint::isValid)
            ?: return NavigationRoutePlan.WaitingForLocation(
                NavigationPhase.ToDestination
            )

        return NavigationRoutePlan.Request(
            NavigationRouteRequest(
                jobId = job.id,
                phase = NavigationPhase.ToDestination,
                origin = origin,
                destination = destination
            )
        )
    }

    private fun AtlasLocation.toRoutePoint(): RoutePoint? =
        RoutePoint(
            latitude = latitude,
            longitude = longitude
        ).takeIf(RoutePoint::isValid)
}
