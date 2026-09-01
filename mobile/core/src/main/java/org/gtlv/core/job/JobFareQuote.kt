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
    finishedOdometerKilometers: Double?,
    pricePerKilometer: Double?
): JobFareQuote? {
    val jobSnapshots = snapshots ?: return null
    val values = listOf(
        jobSnapshots.startedOdometerKilometers,
        jobSnapshots.passengerOdometerKilometers,
        finishedOdometerKilometers,
        pricePerKilometer
    )
    if (values.any { it == null || !it.isFinite() || it < 0.0 }) {
        return null
    }

    val started = requireNotNull(
        jobSnapshots.startedOdometerKilometers
    )
    val passenger = requireNotNull(
        jobSnapshots.passengerOdometerKilometers
    )
    val finished = requireNotNull(
        finishedOdometerKilometers
    )
    val rate = requireNotNull(pricePerKilometer)

    if (passenger !in started..finished) {
        return null
    }

    val totalDistance = finished - started
    val passengerDistance = finished - passenger

    return JobFareQuote(
        totalDistanceKilometers = totalDistance,
        passengerDistanceKilometers = passengerDistance,
        pricePerKilometer = rate,
        totalPrice = totalDistance * rate,
        passengerPrice = passengerDistance * rate
    )
}
