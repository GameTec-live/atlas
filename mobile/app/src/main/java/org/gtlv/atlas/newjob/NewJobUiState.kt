package org.gtlv.atlas.newjob

import org.gtlv.atlas.address.AddressSearchUiState
import org.gtlv.core.geoservice.Route
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobCoordinates
import org.gtlv.core.job.JobLocationField

data class NewJobUiState(
    val from: JobCoordinates? = null,
    val fromAddress: String? = null,
    val to: JobCoordinates? = null,
    val toAddress: String? = null,
    val dueDate: String = java.time.Instant.now().toString(),
    val note: String = "",
    val isAddressEditorOpen: Boolean = false,
    val editedLocationField: JobLocationField? = null,
    val addressSearch: AddressSearchUiState = AddressSearchUiState(),
    val route: Route? = null,
    val isLoadingRoute: Boolean = false,
    val routeFailed: Boolean = false,
    val cameraFocusPoints: List<RoutePoint> = emptyList(),
    val cameraFocusRequestId: Int = 0,
    val candidates: List<JobCandidate> = emptyList(),
    val isLoadingCandidates: Boolean = false,
    val candidatesFailed: Boolean = false,
    val allDrivers: List<JobCandidate> = emptyList(),
    val isLoadingDrivers: Boolean = false,
    val driversFailed: Boolean = false,
    val pendingCandidate: JobCandidate? = null,
    val isSelectingUnassignedDueDate: Boolean = false,
    val dueDatePickerRequestId: Int = 0,
    val isConfirmingUnassigned: Boolean = false,
    val isCreating: Boolean = false,
    val creationFailed: Boolean = false,
    val createdJob: Job? = null
) {
    val canCreate: Boolean
        get() = from != null && !isCreating

    val creationCompleted: Boolean
        get() = createdJob != null
}

internal const val NEW_JOB_NOTE_MAX_LENGTH = 100
