package org.gtlv.atlas.newjob

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.gtlv.core.geoservice.GeoServiceRepository
import org.gtlv.core.job.JobRepository
import org.gtlv.core.role.RoleRepository

class NewJobViewModelFactory(
    private val jobRepository: JobRepository,
    private val geoServiceRepository: GeoServiceRepository,
    private val roleRepository: RoleRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NewJobViewModel::class.java)) {
            return NewJobViewModel(
                jobRepository = jobRepository,
                geoServiceRepository = geoServiceRepository,
                roleRepository = roleRepository
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
