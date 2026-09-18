package org.gtlv.core.job

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Bridges an Android Auto start-job action to the phone odometer dialog. */
class StartJobOdometerRequest {
    private val _pendingJobId = MutableStateFlow<String?>(null)

    val pendingJobId: StateFlow<String?> = _pendingJobId.asStateFlow()

    fun request(jobId: String) {
        if (jobId.isNotBlank()) {
            _pendingJobId.value = jobId
        }
    }

    fun consume(jobId: String) {
        _pendingJobId.update { pendingId ->
            pendingId.takeUnless { it == jobId }
        }
    }
}

interface StartJobOdometerRequestProvider {
    val startJobOdometerRequest: StartJobOdometerRequest
}
