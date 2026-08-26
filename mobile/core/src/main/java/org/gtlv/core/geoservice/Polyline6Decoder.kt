package org.gtlv.core.geoservice

object Polyline6Decoder {

    private const val PRECISION_FACTOR = 1_000_000.0
    private const val MAX_SHIFT = 60

    fun decode(encoded: String): List<RoutePoint>? {
        if (encoded.isEmpty()) {
            return emptyList()
        }

        val points = mutableListOf<RoutePoint>()
        var cursor = 0
        var latitude = 0L
        var longitude = 0L

        while (cursor < encoded.length) {
            val latitudeValue = decodeValue(encoded, cursor)
                ?: return null
            cursor = latitudeValue.nextIndex

            val longitudeValue = decodeValue(encoded, cursor)
                ?: return null
            cursor = longitudeValue.nextIndex

            latitude = addSafely(latitude, latitudeValue.delta)
                ?: return null
            longitude = addSafely(longitude, longitudeValue.delta)
                ?: return null

            val point = RoutePoint(
                latitude = latitude / PRECISION_FACTOR,
                longitude = longitude / PRECISION_FACTOR
            )

            if (!point.isValid()) {
                return null
            }

            points += point
        }

        return points
    }

    private fun decodeValue(
        encoded: String,
        startIndex: Int
    ): DecodedValue? {
        var index = startIndex
        var result = 0L
        var shift = 0

        while (true) {
            if (index >= encoded.length || shift > MAX_SHIFT) {
                return null
            }

            val raw = encoded[index].code
            if (raw !in 63..126) {
                return null
            }

            val value = raw - 63
            result = result or
                    ((value and 0x1f).toLong() shl shift)
            index += 1

            if (value < 0x20) {
                break
            }

            shift += 5
        }

        val delta = if ((result and 1L) == 1L) {
            -(result shr 1) - 1L
        } else {
            result shr 1
        }

        return DecodedValue(
            delta = delta,
            nextIndex = index
        )
    }

    private fun addSafely(
        value: Long,
        delta: Long
    ): Long? = runCatching {
        Math.addExact(value, delta)
    }.getOrNull()

    private data class DecodedValue(
        val delta: Long,
        val nextIndex: Int
    )
}
