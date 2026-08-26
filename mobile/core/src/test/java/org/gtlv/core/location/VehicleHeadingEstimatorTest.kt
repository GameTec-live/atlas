package org.gtlv.core.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleHeadingEstimatorTest {

    @Test
    fun `travel bearing is preferred while vehicle is moving`() {
        val estimator = VehicleHeadingEstimator()

        val heading = estimator.update(
            location(
                bearingDegrees = 91f,
                speedMetersPerSecond = 12f
            )
        )

        assertEquals(91, heading)
    }

    @Test
    fun `bearing is calculated from timestamped movement when unavailable`() {
        val estimator = VehicleHeadingEstimator()

        assertNull(estimator.update(location(
            longitude = 0.0,
            timestampMillis = 1_000L
        )))

        val heading = estimator.update(location(
            longitude = 0.0002,
            timestampMillis = 3_000L
        ))

        assertEquals(90, heading)
    }

    @Test
    fun `slow provider bearing and small gps jitter are ignored`() {
        val estimator = VehicleHeadingEstimator()

        assertNull(estimator.update(location(
            longitude = 0.0,
            bearingDegrees = 180f,
            speedMetersPerSecond = 0.5f,
            timestampMillis = 1_000L
        )))
        assertNull(estimator.update(location(
            longitude = 0.00001,
            timestampMillis = 3_000L
        )))
    }

    @Test
    fun `movement smaller than gps uncertainty is ignored`() {
        val estimator = VehicleHeadingEstimator()

        assertNull(estimator.update(location(
            longitude = 0.0,
            accuracyMeters = 30f,
            timestampMillis = 1_000L
        )))
        assertNull(estimator.update(location(
            longitude = 0.0002,
            accuracyMeters = 30f,
            timestampMillis = 3_000L
        )))
    }

    @Test
    fun `heading smoothing crosses north instead of rotating south`() {
        val estimator = VehicleHeadingEstimator()

        assertEquals(350, estimator.update(location(
            bearingDegrees = 350f,
            speedMetersPerSecond = 10f,
            timestampMillis = 1_000L
        )))
        assertEquals(0, estimator.update(location(
            bearingDegrees = 10f,
            speedMetersPerSecond = 10f,
            timestampMillis = 2_000L
        )))
    }

    @Test
    fun `stale heading is not blended into new movement`() {
        val estimator = VehicleHeadingEstimator()

        assertEquals(90, estimator.update(location(
            bearingDegrees = 90f,
            speedMetersPerSecond = 10f,
            timestampMillis = 1_000L
        )))
        assertEquals(180, estimator.update(location(
            bearingDegrees = 180f,
            speedMetersPerSecond = 10f,
            timestampMillis = 20_000L
        )))
    }

    private fun location(
        latitude: Double = 0.0,
        longitude: Double = 0.0,
        accuracyMeters: Float? = 3f,
        bearingDegrees: Float? = null,
        speedMetersPerSecond: Float? = null,
        timestampMillis: Long = 1_000L
    ): AtlasLocation = AtlasLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        bearingDegrees = bearingDegrees,
        speedMetersPerSecond = speedMetersPerSecond,
        timestampMillis = timestampMillis,
        source = LocationSource.PHONE
    )
}
