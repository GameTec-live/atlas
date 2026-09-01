package org.gtlv.core.job

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class JobFareQuoteTest {

    @Test
    fun calculatesTotalAndPassengerDistancesAndPrices() {
        val quote = calculateJobFareQuote(
            snapshots = JobMileageSnapshots(
                jobId = "job-1",
                startedOdometerKilometers = 100.0,
                passengerOdometerKilometers = 102.0
            ),
            finishedOdometerKilometers = 110.0,
            pricePerKilometer = 1.5
        )

        assertNotNull(quote)
        assertEquals(
            10.0,
            requireNotNull(quote).totalDistanceKilometers,
            0.0
        )
        assertEquals(
            8.0,
            quote.passengerDistanceKilometers,
            0.0
        )
        assertEquals(15.0, quote.totalPrice, 0.0)
        assertEquals(12.0, quote.passengerPrice, 0.0)
    }

    @Test
    fun missingAnyReadingUsesFallback() {
        val snapshots = JobMileageSnapshots(
            jobId = "job-1",
            startedOdometerKilometers = 100.0,
            passengerOdometerKilometers = null
        )

        assertNull(
            calculateJobFareQuote(
                snapshots = snapshots,
                finishedOdometerKilometers = 110.0,
                pricePerKilometer = 1.0
            )
        )
    }

    @Test
    fun decreasingOdometerUsesFallback() {
        val snapshots = JobMileageSnapshots(
            jobId = "job-1",
            startedOdometerKilometers = 100.0,
            passengerOdometerKilometers = 99.0
        )

        assertNull(
            calculateJobFareQuote(
                snapshots = snapshots,
                finishedOdometerKilometers = 110.0,
                pricePerKilometer = 1.0
            )
        )
    }
}
