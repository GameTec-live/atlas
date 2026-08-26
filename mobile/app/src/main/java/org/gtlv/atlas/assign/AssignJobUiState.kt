package org.gtlv.atlas.assign

import org.gtlv.atlas.address.AddressSearchUiState
import org.gtlv.core.geoservice.Route
import org.gtlv.core.geoservice.RoutePoint
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobCandidate
import org.gtlv.core.job.JobLocationField

data class AssignJobUiState(
    val job: Job? = null,
    val candidates: List<JobCandidate> = emptyList(),
    val isLoadingCandidates: Boolean = false,
    val candidatesFailed: Boolean = false,
    val allDrivers: List<JobCandidate> = emptyList(),
    val isLoadingDrivers: Boolean = false,
    val driversFailed: Boolean = false,
    val isAddressEditorOpen: Boolean = false,
    val editedLocationField: JobLocationField? = null,
    val addressSearch: AddressSearchUiState =
        AddressSearchUiState(),
    val route: Route? = null,
    val isLoadingRoute: Boolean = false,
    val routeFailed: Boolean = false,
    val cameraFocusPoints: List<RoutePoint> = emptyList(),
    val cameraFocusRequestId: Int = 0,
    val hasUnsavedChanges: Boolean = false,
    val isSavingChanges: Boolean = false,
    val saveChangesFailed: Boolean = false,
    val pendingCandidate: JobCandidate? = null,
    val isAssigning: Boolean = false,
    val assignmentFailed: Boolean = false,
    val assignmentCompleted: Boolean = false
) {
    val otherDrivers: List<JobCandidate>
        get() {
            val recommendedIds = candidates
                .mapTo(mutableSetOf()) {
                    candidate -> candidate.driverId
                }

            return allDrivers.filterNot { driver ->
                driver.driverId in recommendedIds
            }
        }
}
