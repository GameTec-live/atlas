package org.gtlv.atlas.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.job.CollectedJobStateStore
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.JobMileageStateStore
import org.gtlv.core.pricing.PricingRepository
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.telemetry.TelemetryProvider

class MainScreenViewModelFactory(
    private val jobRepository: JobRepository,
    private val geoServiceRepository: GeoServiceRepository,
    private val telemetryProvider: TelemetryProvider,
    private val collectedJobStore: CollectedJobStateStore,
    private val jobMileageStore: JobMileageStateStore,
    private val pricingRepository: PricingRepository,
    private val shiftSessionManager: ShiftSessionManager
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (
            modelClass.isAssignableFrom(
                MainScreenViewModel::class.java
            )
        ) {
            return MainScreenViewModel(
                jobRepository = jobRepository,
                geoServiceRepository =
                    geoServiceRepository,
                telemetryProvider =
                    telemetryProvider,
                collectedJobStore =
                    collectedJobStore,
                jobMileageStore = jobMileageStore,
                pricingRepository = pricingRepository,
                shiftSessionManager = shiftSessionManager
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}
