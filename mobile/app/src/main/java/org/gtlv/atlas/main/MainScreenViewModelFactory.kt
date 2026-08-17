package org.gtlv.atlas.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.job.JobRepository

class MainScreenViewModelFactory(
    private val jobRepository: JobRepository,
    private val geoServiceRepository: GeoServiceRepository
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
                    geoServiceRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}