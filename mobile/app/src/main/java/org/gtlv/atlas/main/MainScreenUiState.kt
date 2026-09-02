package org.gtlv.atlas.main

import org.gtlv.atlas.address.AddressSearchUiState
import org.gtlv.core.job.Job
import org.gtlv.core.job.JobFareQuote
import org.gtlv.core.job.JobLocationField

data class MainScreenUiState(
    val isLoading: Boolean = true,
    val currentJob: Job? = null,
    val queuedJobs: List<Job> = emptyList(),
    val isJobListExpanded: Boolean = false,
    val hasError: Boolean = false,
    val isStartingNextJob: Boolean = false,
    val startNextJobFailed: Boolean = false,
    val isStartKilometerDialogVisible: Boolean = false,
    val startKilometerInput: String = "",
    val isStartKilometerInputInvalid: Boolean = false,
    val isCancellingCurrentJob: Boolean = false,
    val cancelCurrentJobFailed: Boolean = false,
    val isCancelConfirmationVisible: Boolean = false,
    val isFinishingCurrentJob: Boolean = false,
    val finishCurrentJobFailed: Boolean = false,
    val isPreparingFinishConfirmation: Boolean = false,
    val finishConfirmation:
        FinishJobConfirmation? = null,
    val isPersonCollected: Boolean = false,
    val isAddressEditorOpen: Boolean = false,
    val editedLocationField: JobLocationField? = null,
    val addressSearch: AddressSearchUiState =
        AddressSearchUiState(),
    val navigation: NavigationUiState =
        NavigationUiState()
)

data class FinishJobConfirmation(
    val quote: JobFareQuote?
)
