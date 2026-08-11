package org.gtlv.atlas.location

import android.location.Location
import android.os.SystemClock
import org.gtlv.core.location.AtlasLocation

internal fun AtlasLocation.toAndroidLocation(): Location {
    return Location(source.name).apply {
        latitude = this@toAndroidLocation.latitude
        longitude = this@toAndroidLocation.longitude
        time = timestampMillis
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

        accuracyMeters?.let {
            accuracy = it
        }

        bearingDegrees?.let {
            bearing = it
        }

        speedMetersPerSecond?.let {
            speed = it
        }
    }
}