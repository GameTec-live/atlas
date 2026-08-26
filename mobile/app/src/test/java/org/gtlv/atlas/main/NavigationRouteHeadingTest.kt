package org.gtlv.atlas.main

import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.location.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationRouteHeadingTest {

    @Test
    fun `pickup route captures heading from its live location origin`() {
        val plan = NavigationRoutePlanner.plan(
            currentJob = job(),
            isPersonCollected = false,
            latestLocation = location(),
            latestHeadingDegrees = 87,
            pickupOrigin = null,
            destinationOrigin = null
        ) as NavigationRoutePlan.Request

        assertEquals(87, plan.value.headingDegrees)
        assertEquals(RoutePoint(48.2, 16.3), plan.value.origin)
    }

    @Test
    fun `captured origin heading stays stable during normal location updates`() {
        val capturedOrigin = NavigationRouteOrigin(
            point = RoutePoint(48.2, 16.3),
            headingDegrees = 87
        )
        val plan = NavigationRoutePlanner.plan(
            currentJob = job(),
            isPersonCollected = false,
            latestLocation = location(
                latitude = 48.21,
                longitude = 16.31
            ),
            latestHeadingDegrees = 180,
            pickupOrigin = capturedOrigin,
            destinationOrigin = null
        ) as NavigationRoutePlan.Request

        assertEquals(87, plan.value.headingDegrees)
        assertEquals(capturedOrigin.point, plan.value.origin)
    }

    @Test
    fun `destination route uses heading captured when person was collected`() {
        val collectedOrigin = NavigationRouteOrigin(
            point = RoutePoint(48.25, 16.35),
            headingDegrees = 225
        )
        val plan = NavigationRoutePlanner.plan(
            currentJob = job(),
            isPersonCollected = true,
            latestLocation = location(),
            latestHeadingDegrees = 90,
            pickupOrigin = null,
            destinationOrigin = collectedOrigin
        ) as NavigationRoutePlan.Request

        assertEquals(225, plan.value.headingDegrees)
        assertEquals(collectedOrigin.point, plan.value.origin)
    }

    private fun location(
        latitude: Double = 48.2,
        longitude: Double = 16.3
    ): AtlasLocation = AtlasLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 4f,
        bearingDegrees = null,
        speedMetersPerSecond = null,
        timestampMillis = 1_000L,
        source = LocationSource.PHONE
    )

    private fun job(): Job = Job(
        id = "job-1",
        assignedDriverId = "driver-1",
        vehicleId = null,
        from = JobCoordinates(48.3, 16.4),
        to = JobCoordinates(48.4, 16.5),
        fromAddress = "Pickup",
        toAddress = "Destination",
        dueDate = null,
        note = null,
        startedAt = "2026-08-24T10:00:00Z",
        completedAt = null,
        createdAt = null,
        updatedAt = null
    )
}
