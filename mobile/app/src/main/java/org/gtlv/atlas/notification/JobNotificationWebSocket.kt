package org.gtlv.atlas.notification

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.gtlv.core.job.AssignedJobNotification
import org.gtlv.core.job.JobNotification
import org.gtlv.core.job.UnassignedJobNotification
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.session.SessionManager
import org.gtlv.core.session.SessionState
import org.gtlv.core.settings.ServerSettingsRepository
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.shift.ShiftSessionState
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds

sealed interface JobNotificationEvent {
    data object Connected :
        JobNotificationEvent

    data object JobsChanged :
        JobNotificationEvent

    data class Received(
        val notification: JobNotification,
        val showInApp: Boolean
    ) : JobNotificationEvent
}

class JobNotificationWebSocket(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository:
    ServerSettingsRepository,
    private val sessionManager: SessionManager,
    private val shiftSessionManager:
    ShiftSessionManager,
    private val visibilityTracker:
    AppVisibilityTracker,
    private val systemNotificationManager:
    JobSystemNotificationManager,
    private val scope: CoroutineScope
) {
    private val _events =
        MutableSharedFlow<JobNotificationEvent>(
            extraBufferCapacity = 32
        )

    val events: SharedFlow<JobNotificationEvent> =
        _events.asSharedFlow()

    private var lifecycleJob: Job? = null

    fun start() {
        if (lifecycleJob != null) {
            return
        }

        lifecycleJob = scope.launch {
            combine(
                sessionManager.state,
                shiftSessionManager.state,
                serverSettingsRepository
                    .serverAddress
            ) {
                    sessionState,
                    shiftState,
                    serverAddress ->

                ConnectionRequirement(
                    enabled =
                        sessionState
                                is SessionState.SignedIn &&
                                shiftState
                                        is ShiftSessionState.Active,
                    serverAddress =
                        serverAddress
                            .trim()
                            .removeSuffix("/")
                )
            }
                .distinctUntilChanged()
                .collectLatest { requirement ->
                    if (requirement.enabled) {
                        connectWithRetry(
                            requirement.serverAddress
                        )
                    } else {
                        systemNotificationManager
                            .cancelAllJobNotifications()
                    }
                }
        }
    }

    suspend fun stop() {
        lifecycleJob?.cancelAndJoin()
        lifecycleJob = null
    }

    fun dismissSystemNotification(
        jobId: String
    ) {
        systemNotificationManager.cancel(jobId)
    }

    fun clearSystemNotifications() {
        systemNotificationManager
            .cancelAllJobNotifications()
    }

    private suspend fun connectWithRetry(
        serverAddress: String
    ) {
        val request =
            createRequest(serverAddress) ?: return

        var retryDelayMillis =
            INITIAL_RETRY_DELAY_MILLIS

        while (
            currentCoroutineContext().isActive
        ) {
            val wasOpened =
                connectOnce(request)

            retryDelayMillis =
                if (wasOpened) {
                    INITIAL_RETRY_DELAY_MILLIS
                } else {
                    (retryDelayMillis * 2)
                        .coerceAtMost(
                            MAX_RETRY_DELAY_MILLIS
                        )
                }

            if (
                currentCoroutineContext().isActive
            ) {
                delay(
                    retryDelayMillis.milliseconds
                )
            }
        }
    }

    private suspend fun connectOnce(
        request: Request
    ): Boolean {
        val connectionFinished =
            CompletableDeferred<Unit>()

        val socketReference =
            AtomicReference<WebSocket?>(null)

        val connectionOpened =
            AtomicBoolean(false)

        val listener =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    socketReference.set(webSocket)
                    connectionOpened.set(true)

                    _events.tryEmit(
                        JobNotificationEvent.Connected
                    )
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    handleMessage(text)
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    webSocket.close(code, reason)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    socketReference.compareAndSet(
                        webSocket,
                        null
                    )

                    connectionFinished
                        .complete(Unit)
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    throwable: Throwable,
                    response: Response?
                ) {
                    socketReference.compareAndSet(
                        webSocket,
                        null
                    )

                    val statusCode =
                        response?.code

                    response?.close()

                    connectionFinished
                        .complete(Unit)

                    Log.w(
                        TAG,
                        "Job notification WebSocket failed" +
                                if (statusCode == null) {
                                    ""
                                } else {
                                    " with HTTP $statusCode"
                                },
                        throwable
                    )
                }
            }

        val socket =
            networkClient.okHttpClient
                .newWebSocket(
                    request,
                    listener
                )

        socketReference.compareAndSet(
            null,
            socket
        )

        try {
            connectionFinished.await()
        } finally {
            socketReference
                .getAndSet(null)
                ?.close(
                    NORMAL_CLOSURE_CODE,
                    "Job notification receiver stopped"
                )
        }

        return connectionOpened.get()
    }

    private fun handleMessage(
        text: String
    ) {
        val message = runCatching {
            parseNotifyMessage(text)
        }.getOrElse { exception ->
            Log.w(
                TAG,
                "Ignoring invalid job notification",
                exception
            )

            return
        }

        if (message == null) {
            Log.w(
                TAG,
                "Ignoring unsupported or incomplete " +
                        "job notification"
            )

            return
        }

        if (message is ParsedNotifyMessage.JobsChanged) {
            _events.tryEmit(
                JobNotificationEvent.JobsChanged
            )
            return
        }

        val notification =
            (message as ParsedNotifyMessage.Job)
                .notification

        val showInApp =
            visibilityTracker.isForeground

        if (!showInApp) {
            systemNotificationManager.show(
                notification
            )
        }

        _events.tryEmit(
            JobNotificationEvent.Received(
                notification = notification,
                showInApp = showInApp
            )
        )
    }

    private fun createRequest(
        serverAddress: String
    ): Request? {
        val serverUrl =
            serverAddress.toHttpUrlOrNull()
                ?: return null

        if (
            serverUrl.scheme != "http" &&
            serverUrl.scheme != "https"
        ) {
            return null
        }

        val endpointUrl =
            serverUrl
                .newBuilder()
                .addPathSegments(REALTIME_PATH)
                .build()

        val origin =
            serverUrl
                .newBuilder()
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .build()
                .toString()
                .removeSuffix("/")

        return Request.Builder()
            .url(endpointUrl)
            .header("Origin", origin)
            .build()
    }

    private data class ConnectionRequirement(
        val enabled: Boolean,
        val serverAddress: String
    )

    private companion object {
        const val TAG =
            "JobNotificationWebSocket"

        const val REALTIME_PATH =
            "api/realtime/notify"

        const val INITIAL_RETRY_DELAY_MILLIS =
            1_000L

        const val MAX_RETRY_DELAY_MILLIS =
            30_000L

        const val NORMAL_CLOSURE_CODE = 1000
    }
}

internal fun parseJobNotification(
    text: String
): JobNotification? =
    (parseNotifyMessage(text) as? ParsedNotifyMessage.Job)
        ?.notification

internal sealed interface ParsedNotifyMessage {
    data class Job(
        val notification: JobNotification
    ) : ParsedNotifyMessage

    data object JobsChanged : ParsedNotifyMessage
}

internal fun parseNotifyMessage(
    text: String
): ParsedNotifyMessage? {
    val json = JSONObject(text)

    val type = json.optString("type").trim()

    if (type == "jobs_changed") {
        return ParsedNotifyMessage.JobsChanged
    }

    val jobId = json.optString("jobId").trim()
    val from = json.optString("from").trim()
    val to = json.optString("to")
        .trim()
        .takeIf(String::isNotEmpty)
    val note = json.optString("note")
        .trim()
        .takeIf(String::isNotEmpty)

    if (
        jobId.isBlank() ||
        from.isBlank()
    ) {
        return null
    }

    val notification = when (type) {
        "assigned" ->
            AssignedJobNotification(
                jobId = jobId,
                from = from,
                to = to,
                note = note
            )

        "unassigned" ->
            UnassignedJobNotification(
                jobId = jobId,
                from = from,
                to = to,
                note = note
            )

        else -> null
    } ?: return null

    return ParsedNotifyMessage.Job(notification)
}
