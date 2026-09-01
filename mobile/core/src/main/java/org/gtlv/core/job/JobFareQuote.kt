package org.gtlv.core.job

data class JobFareQuote(
    val totalDistanceKilometers: Double,
    val passengerDistanceKilometers: Double,
    val pricePerKilometer: Double,
    val totalPrice: Double,
    val passengerPrice: Double
)

fun calculateJobFareQuote(
    snapshots: JobMileageSnapshots?,
    finishedOdometerMeters: Double?,
    pricePerKilometer: Double?
): JobFareQuote? {
    val jobSnapshots = snapshots ?: return null
    val values = listOf(
        jobSnapshots.startedOdometerMeters,
        jobSnapshots.passengerOdometerMeters,
        finishedOdometerMeters,
        pricePerKilometer
    )
    if (values.any { it == null || !it.isFinite() || it < 0.0 }) {
        return null
    }

    val started = requireNotNull(
        jobSnapshots.startedOdometerMeters
    )
    val passenger = requireNotNull(
        jobSnapshots.passengerOdometerMeters
    )
    val finished = requireNotNull(finishedOdometerMeters)
    val rate = requireNotNull(pricePerKilometer)

    if (passenger < started || finished < passenger) {
        return null
    }

    val totalDistance = (finished - started) / METERS_PER_KILOMETER
    val passengerDistance =
        (finished - passenger) / METERS_PER_KILOMETER

    return JobFareQuote(
        totalDistanceKilometers = totalDistance,
        passengerDistanceKilometers = passengerDistance,
        pricePerKilometer = rate,
        totalPrice = totalDistance * rate,
        passengerPrice = passengerDistance * rate
    )
}

private const val METERS_PER_KILOMETER = 1_000.0
