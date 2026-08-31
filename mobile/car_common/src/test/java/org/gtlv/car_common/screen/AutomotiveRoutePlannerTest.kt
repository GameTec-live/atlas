package org.gtlv.car_common.screen

import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.location.AtlasLocation
import org.gtlv.core.location.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutomotiveRoutePlannerTest {
    @Test
    fun `before pickup route leads from driver to pickup`() {
        val target = AutomotiveRoutePlanner.target(
            job = job(),
            isPersonCollected = false,
        )!!
        val request = AutomotiveRoutePlanner.request(
            target = target,
            location = location(),
            headingDegrees = 91,
        )!!

        assertEquals(AutomotiveRoutePhase.TO_PICKUP, request.target.phase)
        assertEquals(48.20, request.origin.latitude, 0.0)
        assertEquals(16.30, request.origin.longitude, 0.0)
        assertEquals(48.21, request.target.destination.latitude, 0.0)
        assertEquals(16.31, request.target.destination.longitude, 0.0)
        assertEquals(91, request.headingDegrees)
    }

    @Test
    fun `after pickup route switches to final destination`() {
        val target = AutomotiveRoutePlanner.target(
            job = job(),
            isPersonCollected = true,
        )!!

        assertEquals(AutomotiveRoutePhase.TO_DESTINATION, target.phase)
        assertEquals(48.22, target.destination.latitude, 0.0)
        assertEquals(16.32, target.destination.longitude, 0.0)
    }

    @Test
    fun `route waits until a current driver location is available`() {
        val target = AutomotiveRoutePlanner.target(job(), false)!!

        assertNull(AutomotiveRoutePlanner.request(target, null, null))
    }

    @Test
    fun `missing coordinates do not produce an invalid target`() {
        assertNull(
            AutomotiveRoutePlanner.target(
                job = job().copy(to = null),
                isPersonCollected = true,
            ),
        )
    }

    private fun job() = Job(
        id = "job-1",
        assignedDriverId = "driver-1",
        vehicleId = "vehicle-1",
        from = JobCoordinates(48.21, 16.31),
        to = JobCoordinates(48.22, 16.32),
        fromAddress = "Pickup",
        toAddress = "Destination",
        dueDate = null,
        note = null,
        startedAt = null,
        completedAt = null,
        createdAt = null,
        updatedAt = null,
    )

    private fun location() = AtlasLocation(
        latitude = 48.20,
        longitude = 16.30,
        accuracyMeters = 5f,
        bearingDegrees = 91f,
        speedMetersPerSecond = 8f,
        timestampMillis = 1_000L,
        source = LocationSource.CAR,
    )
}
