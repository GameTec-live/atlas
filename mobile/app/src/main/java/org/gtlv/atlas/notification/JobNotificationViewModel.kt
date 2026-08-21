package org.gtlv.atlas.notification

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.gtlv.core.job.AssignedJobNotification
import org.gtlv.core.job.JobActionResult
import org.gtlv.core.job.JobRepository

class JobNotificationViewModel(
    private val jobRepository: JobRepository,
    private val webSocket: JobNotificationWebSocket
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            JobNotificationUiState()
        )

    val uiState:
            StateFlow<JobNotificationUiState> =
        _uiState.asStateFlow()

    private val _refreshRequests =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1
        )

    val refreshRequests: SharedFlow<Unit> =
        _refreshRequests.asSharedFlow()

    private var declineTask: Job? = null

    init {
        viewModelScope.launch {
            webSocket.events.collect { event ->
                when (event) {
                    JobNotificationEvent.Connected -> {
                        _refreshRequests.tryEmit(Unit)
                    }

                    is JobNotificationEvent.Assigned -> {
                        if (event.showInApp) {
                            enqueue(
                                event.notification
                            )
                        }

                        _refreshRequests.tryEmit(Unit)
                    }
                }
            }
        }
    }

    fun dismissCurrentNotification() {
        val state = _uiState.value

        val currentNotification =
            state.currentNotification
                ?: return

        if (
            state.decliningJobId ==
            currentNotification.jobId
        ) {
            return
        }

        val remainingNotifications =
            state.foregroundNotifications
                .drop(1)

        _uiState.value = state.copy(
            foregroundNotifications =
                remainingNotifications,
            currentNotificationExpiresAtElapsedRealtime =
                if (
                    remainingNotifications
                        .isNotEmpty()
                ) {
                    newExpirationTime()
                } else {
                    null
                }
        )
    }

    fun declineCurrentNotification() {
        val notification =
            _uiState.value
                .currentNotification
                ?: return

        decline(notification)
    }

    fun requestDeclineConfirmation(
        notification: AssignedJobNotification
    ) {
        if (_uiState.value.isDeclining) {
            return
        }

        _uiState.update {
            it.copy(
                declineConfirmation =
                    notification,
                declineFailed = false
            )
        }
    }

    fun dismissDeclineConfirmation() {
        if (_uiState.value.isDeclining) {
            return
        }

        _uiState.update {
            it.copy(
                declineConfirmation = null
            )
        }
    }

    fun confirmDecline() {
        val notification =
            _uiState.value
                .declineConfirmation
                ?: return

        decline(notification)
    }

    fun clear() {
        declineTask?.cancel()
        declineTask = null

        webSocket.clearSystemNotifications()

        _uiState.value =
            JobNotificationUiState()
    }

    private fun enqueue(
        notification: AssignedJobNotification
    ) {
        val state = _uiState.value

        val alreadyQueued =
            state.foregroundNotifications
                .any { queued ->
                    queued.jobId ==
                            notification.jobId
                }

        if (alreadyQueued) {
            return
        }

        val wasEmpty =
            state.foregroundNotifications
                .isEmpty()

        _uiState.value = state.copy(
            foregroundNotifications =
                state.foregroundNotifications +
                        notification,
            currentNotificationExpiresAtElapsedRealtime =
                if (wasEmpty) {
                    newExpirationTime()
                } else {
                    state
                        .currentNotificationExpiresAtElapsedRealtime
                }
        )
    }

    private fun decline(
        notification: AssignedJobNotification
    ) {
        if (_uiState.value.isDeclining) {
            return
        }

        declineTask?.cancel()

        declineTask =
            viewModelScope.launch {
                _uiState.update {
                    it.copy(
                        decliningJobId =
                            notification.jobId,
                        declineFailed = false
                    )
                }

                val result =
                    jobRepository.cancelJob(
                        notification.jobId
                    )

                currentCoroutineContext()
                    .ensureActive()

                when (result) {
                    JobActionResult.Success -> {
                        webSocket
                            .dismissSystemNotification(
                                notification.jobId
                            )

                        val state =
                            _uiState.value

                        val removedCurrentNotification =
                            state
                                .currentNotification
                                ?.jobId ==
                                    notification.jobId

                        val remainingNotifications =
                            state
                                .foregroundNotifications
                                .filterNot { queued ->
                                    queued.jobId ==
                                            notification.jobId
                                }

                        _uiState.value = state.copy(
                            foregroundNotifications =
                                remainingNotifications,
                            currentNotificationExpiresAtElapsedRealtime =
                                when {
                                    !removedCurrentNotification -> {
                                        state
                                            .currentNotificationExpiresAtElapsedRealtime
                                    }

                                    remainingNotifications
                                        .isNotEmpty() -> {
                                        newExpirationTime()
                                    }

                                    else -> null
                                },
                            declineConfirmation = null,
                            decliningJobId = null,
                            declineFailed = false
                        )

                        _refreshRequests.tryEmit(Unit)
                    }

                    else -> {
                        _uiState.update {
                            it.copy(
                                decliningJobId = null,
                                declineFailed = true
                            )
                        }
                    }
                }
            }
    }

    private fun newExpirationTime(): Long {
        return SystemClock.elapsedRealtime() +
                JOB_NOTIFICATION_DURATION_MILLIS
    }
}