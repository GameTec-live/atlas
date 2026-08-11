package org.gtlv.core.location

data class AtlasLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val bearingDegrees: Float?,
    val speedMetersPerSecond: Float?,
    val timestampMillis: Long,
    val source: LocationSource
)