package org.gtlv.core.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Estimates the vehicle's course over the ground without relying on the
 * physical orientation of the phone.
 */
class VehicleHeadingEstimator {

    private var movementAnchor: AtlasLocation? = null
    private var lastProcessedLocation: AtlasLocation? = null
    private var lastResult: Int? = null
    private var smoothedHeadingDegrees: Double? = null
    private var lastHeadingTimestampMillis: Long? = null

    fun update(location: AtlasLocation): Int? {
        if (!location.hasUsablePosition()) {
            return null
        }

        if (location.matches(lastProcessedLocation)) {
            return lastResult
        }

        val lastProcessedTimestamp = lastProcessedLocation
            ?.timestampMillis
        if (
            lastProcessedTimestamp != null &&
            location.timestampMillis < lastProcessedTimestamp
        ) {
            return null
        }

        resetStaleSmoothing(location.timestampMillis)

        val providerHeading = location.bearingDegrees
            ?.toDouble()
            ?.takeIf(Double::isFinite)
            ?.takeIf {
                (location.speedMetersPerSecond ?: 0f) >=
                        MIN_PROVIDER_BEARING_SPEED_METERS_PER_SECOND
            }
            ?.takeIf { location.hasAcceptableAccuracy() }
            ?.normalizeHeading()

        val movementHeading = if (providerHeading == null) {
            calculateMovementHeading(location)
        } else {
            movementAnchor = location
            null
        }

        val candidate = providerHeading ?: movementHeading
        val result = candidate?.let {
            smooth(it).roundToInt().normalizeHeading()
        }

        lastProcessedLocation = location
        lastResult = result

        if (candidate != null) {
            lastHeadingTimestampMillis = location.timestampMillis
        }

        return result
    }

    fun reset() {
        movementAnchor = null
        lastProcessedLocation = null
        lastResult = null
        smoothedHeadingDegrees = null
        lastHeadingTimestampMillis = null
    }

    private fun calculateMovementHeading(
        location: AtlasLocation
    ): Double? {
        if (!location.hasAcceptableAccuracy()) {
            return null
        }

        val anchor = movementAnchor
        if (anchor == null) {
            movementAnchor = location
            return null
        }

        val elapsedMillis =
            location.timestampMillis - anchor.timestampMillis
        if (
            elapsedMillis < MIN_MOVEMENT_INTERVAL_MILLIS ||
            elapsedMillis > MAX_MOVEMENT_INTERVAL_MILLIS
        ) {
            if (elapsedMillis > MAX_MOVEMENT_INTERVAL_MILLIS) {
                movementAnchor = location
            }
            return null
        }

        val distanceMeters = distanceMeters(anchor, location)
        val requiredDistanceMeters = max(
            MIN_MOVEMENT_DISTANCE_METERS,
            max(
                anchor.accuracyMeters ?: 0f,
                location.accuracyMeters ?: 0f
            ) * ACCURACY_DISTANCE_MULTIPLIER
        )
        val inferredSpeedMetersPerSecond =
            distanceMeters / (elapsedMillis / 1_000.0)

        if (
            distanceMeters < requiredDistanceMeters ||
            inferredSpeedMetersPerSecond <
            MIN_INFERRED_SPEED_METERS_PER_SECOND
        ) {
            return null
        }

        movementAnchor = location
        return initialBearingDegrees(anchor, location)
    }

    private fun smooth(candidateDegrees: Double): Double {
        val previousDegrees = smoothedHeadingDegrees
        if (previousDegrees == null) {
            smoothedHeadingDegrees = candidateDegrees
            return candidateDegrees
        }

        val previousRadians = Math.toRadians(previousDegrees)
        val candidateRadians = Math.toRadians(candidateDegrees)
        val x =
            (1.0 - SMOOTHING_WEIGHT) * cos(previousRadians) +
                    SMOOTHING_WEIGHT * cos(candidateRadians)
        val y =
            (1.0 - SMOOTHING_WEIGHT) * sin(previousRadians) +
                    SMOOTHING_WEIGHT * sin(candidateRadians)
        val smoothed = Math.toDegrees(atan2(y, x))
            .normalizeHeading()

        smoothedHeadingDegrees = smoothed
        return smoothed
    }

    private fun resetStaleSmoothing(timestampMillis: Long) {
        val lastTimestamp = lastHeadingTimestampMillis ?: return
        if (
            timestampMillis - lastTimestamp >
            MAX_HEADING_AGE_MILLIS
        ) {
            smoothedHeadingDegrees = null
            lastHeadingTimestampMillis = null
        }
    }

    private fun AtlasLocation.hasUsablePosition(): Boolean =
        latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0

    private fun AtlasLocation.hasAcceptableAccuracy(): Boolean =
        accuracyMeters
            ?.let {
                it.isFinite() &&
                        it >= 0f &&
                        it <= MAX_LOCATION_ACCURACY_METERS
            }
            ?: true

    private fun AtlasLocation.matches(
        other: AtlasLocation?
    ): Boolean =
        other != null &&
                timestampMillis == other.timestampMillis &&
                latitude == other.latitude &&
                longitude == other.longitude

    private fun distanceMeters(
        first: AtlasLocation,
        second: AtlasLocation
    ): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val latitudeDelta = secondLatitude - firstLatitude
        val longitudeDelta = Math.toRadians(
            second.longitude - first.longitude
        )
        val haversine =
            sin(latitudeDelta / 2.0) *
                    sin(latitudeDelta / 2.0) +
                    cos(firstLatitude) *
                    cos(secondLatitude) *
                    sin(longitudeDelta / 2.0) *
                    sin(longitudeDelta / 2.0)
        val centralAngle = 2.0 * atan2(
            sqrt(haversine),
            sqrt((1.0 - haversine).coerceAtLeast(0.0))
        )
        return EARTH_RADIUS_METERS * centralAngle
    }

    private fun initialBearingDegrees(
        first: AtlasLocation,
        second: AtlasLocation
    ): Double {
        val firstLatitude = Math.toRadians(first.latitude)
        val secondLatitude = Math.toRadians(second.latitude)
        val longitudeDelta = Math.toRadians(
            second.longitude - first.longitude
        )
        val y = sin(longitudeDelta) * cos(secondLatitude)
        val x =
            cos(firstLatitude) * sin(secondLatitude) -
                    sin(firstLatitude) * cos(secondLatitude) *
                    cos(longitudeDelta)
        return Math.toDegrees(atan2(y, x)).normalizeHeading()
    }

    private fun Double.normalizeHeading(): Double =
        ((this % FULL_CIRCLE_DEGREES) +
                FULL_CIRCLE_DEGREES) % FULL_CIRCLE_DEGREES

    private fun Int.normalizeHeading(): Int =
        ((this % FULL_CIRCLE_DEGREES.toInt()) +
                FULL_CIRCLE_DEGREES.toInt()) %
                FULL_CIRCLE_DEGREES.toInt()

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
        const val FULL_CIRCLE_DEGREES = 360.0
        const val MIN_PROVIDER_BEARING_SPEED_METERS_PER_SECOND = 2f
        const val MIN_INFERRED_SPEED_METERS_PER_SECOND = 1.5
        const val MIN_MOVEMENT_DISTANCE_METERS = 8.0
        const val ACCURACY_DISTANCE_MULTIPLIER = 1.5
        const val MAX_LOCATION_ACCURACY_METERS = 50f
        const val MIN_MOVEMENT_INTERVAL_MILLIS = 500L
        const val MAX_MOVEMENT_INTERVAL_MILLIS = 15_000L
        const val MAX_HEADING_AGE_MILLIS = 15_000L
        const val SMOOTHING_WEIGHT = 0.5
    }
}
