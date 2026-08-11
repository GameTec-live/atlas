package org.gtlv.core.location

import android.location.Location

internal fun Location.toAtlasLocation(): AtlasLocation {
    return AtlasLocation(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracy.takeIf { hasAccuracy() },
        bearingDegrees = bearing.takeIf { hasBearing() },
        speedMetersPerSecond = speed.takeIf { hasSpeed() },
        timestampMillis = time,
        source = LocationSource.PHONE
    )
}