package org.gtlv.atlas.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.job.CollectedJobStore
import org.gtlv.core.job.JobRepository
import org.gtlv.core.telemetry.TelemetryProvider

class MainScreenViewModelFactory(
    private val jobRepository: JobRepository,
    private val geoServiceRepository: GeoServiceRepository,
    private val telemetryProvider: TelemetryProvider,
    private val collectedJobStore: CollectedJobStore
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
                    collectedJobStore
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}