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
import org.gtlv.core.job.JobNotification
import org.gtlv.core.job.JobNotificationResolution
import org.gtlv.core.job.JobNotificationType
import org.gtlv.core.job.JobRepository
import org.gtlv.core.job.UnassignedJobNotification
import org.gtlv.core.job.matches
import org.gtlv.core.job.hasSameIdentity
import org.gtlv.core.job.type

class JobNotificationViewModel(
    private val jobRepository: JobRepository,
    private val notificationSync: JobNotificationSync
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

    private val _unassignedRefreshRequests =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1
        )

    val unassignedRefreshRequests: SharedFlow<Unit> =
        _unassignedRefreshRequests.asSharedFlow()

    private var declineTask: Job? = null

    init {
        viewModelScope.launch {
            notificationSync.events.collect { event ->
                when (event) {
                    JobNotificationEvent.Connected -> {
                        _refreshRequests.tryEmit(Unit)
                    }

                    JobNotificationEvent.JobsChanged -> {
                        _unassignedRefreshRequests
                            .tryEmit(Unit)
                    }

                    is JobNotificationEvent.Received -> {
                        if (event.showInApp) {
                            enqueue(
                                event.notification
                            )
                        }

                        _refreshRequests.tryEmit(Unit)

                        if (
                            event.notification
                                is UnassignedJobNotification
                        ) {
                            _unassignedRefreshRequests
                                .tryEmit(Unit)
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            notificationSync.resolvedJobNotifications.collect { resolution ->
                applyResolution(resolution)
                _refreshRequests.tryEmit(Unit)
                _unassignedRefreshRequests.tryEmit(Unit)
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

        resolve(currentNotification)
    }

    fun declineCurrentNotification() {
        val notification =
            _uiState.value
                .currentNotification
                as? AssignedJobNotification
                    ?: return

        decline(notification)
    }

    fun assignCurrentNotification() {
        val notification =
            _uiState.value
                .currentNotification
                as? UnassignedJobNotification
                    ?: return

        requestAssignment(notification.jobId)
    }

    fun requestAssignment(jobId: String) {
        if (jobId.isBlank()) {
            return
        }

        notificationSync.dismissSystemNotification(jobId)

        val notification = _uiState.value.foregroundNotifications
            .filterIsInstance<UnassignedJobNotification>()
            .firstOrNull { queued -> queued.jobId == jobId }

        if (notification != null) {
            resolve(notification)
        }

        _uiState.update { state ->
            state.copy(
                assignmentJobId = jobId
            )
        }

        _unassignedRefreshRequests.tryEmit(Unit)
    }

    fun assignmentNavigationHandled() {
        _uiState.update {
            it.copy(assignmentJobId = null)
        }
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

        notificationSync.clearSystemNotifications()

        _uiState.value =
            JobNotificationUiState()
    }

    private fun enqueue(
        notification: JobNotification
    ) {
        _uiState.update { state ->
            state.withEnqueuedNotification(
                notification = notification,
                newExpirationTime =
                    ::newExpirationTime
            )
        }
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
                        resolve(notification)

                        _refreshRequests.tryEmit(Unit)
                    }

                    else -> {
                        val state = _uiState.value
                        val notificationIsStillPending =
                            state.foregroundNotifications.any { queued ->
                                queued.hasSameIdentity(notification)
                            } || state.declineConfirmation
                                ?.hasSameIdentity(notification) == true
                        if (!notificationIsStillPending) {
                            return@launch
                        }

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

    private fun resolve(notification: JobNotification) {
        val resolution = JobNotificationResolution(
            jobId = notification.jobId,
            type = notification.type
        )
        applyResolution(resolution)
        notificationSync.resolveJobNotification(notification)
    }

    private fun applyResolution(resolution: JobNotificationResolution) {
        _uiState.update { state ->
            val removedCurrentNotification =
                state.currentNotification?.matches(resolution) == true
            val remainingNotifications =
                state.foregroundNotifications.filterNot { notification ->
                    notification.matches(resolution)
                }
            val resolvedAssignedNotification =
                resolution.type == JobNotificationType.ASSIGNED

            state.copy(
                foregroundNotifications = remainingNotifications,
                currentNotificationExpiresAtElapsedRealtime = when {
                    !removedCurrentNotification ->
                        state.currentNotificationExpiresAtElapsedRealtime

                    remainingNotifications.isNotEmpty() -> newExpirationTime()
                    else -> null
                },
                declineConfirmation = state.declineConfirmation
                    ?.takeUnless { notification ->
                        notification.matches(resolution)
                    },
                decliningJobId = if (
                    resolvedAssignedNotification &&
                    state.decliningJobId == resolution.jobId
                ) {
                    null
                } else {
                    state.decliningJobId
                },
                declineFailed = if (resolvedAssignedNotification) {
                    false
                } else {
                    state.declineFailed
                }
            )
        }
    }
}

internal fun JobNotification.hasSameQueueIdentity(
    other: JobNotification
): Boolean = hasSameIdentity(other)

internal fun JobNotificationUiState.withEnqueuedNotification(
    notification: JobNotification,
    newExpirationTime: () -> Long
): JobNotificationUiState {
    val alreadyQueued =
        foregroundNotifications.any { queued ->
            queued.hasSameQueueIdentity(notification)
        }

    if (alreadyQueued) {
        return this
    }

    return copy(
        foregroundNotifications =
            foregroundNotifications + notification,
        currentNotificationExpiresAtElapsedRealtime =
            if (foregroundNotifications.isEmpty()) {
                newExpirationTime()
            } else {
                currentNotificationExpiresAtElapsedRealtime
            }
    )
}
