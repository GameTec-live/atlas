package org.gtlv.core.telemetry

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.session.SessionManager
import org.gtlv.core.session.SessionState
import org.gtlv.core.settings.ServerSettingsRepository
import org.gtlv.core.shift.ShiftSessionManager
import org.gtlv.core.shift.ShiftSessionState
import org.json.JSONObject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Sends local telemetry and tracks live users received from the
 * configured telemetry WebSocket.
 */
class TelemetryWebSocketSender(
    private val networkClient: NetworkClient,
    private val serverSettingsRepository: ServerSettingsRepository,
    private val sessionManager: SessionManager,
    private val shiftSessionManager: ShiftSessionManager,
    private val telemetryProvider: TelemetryProvider,
    private val scope: CoroutineScope
) {
    private var lifecycleJob: Job? = null

    private val _liveMapUsers =
        MutableStateFlow<Map<String, LiveMapUser>>(
            emptyMap()
        )

    val liveMapUsers:
            StateFlow<Map<String, LiveMapUser>> =
        _liveMapUsers.asStateFlow()

    fun start() {
        if (lifecycleJob != null) return

        lifecycleJob = scope.launch {
            combine(
                sessionManager.state,
                shiftSessionManager.state,
                serverSettingsRepository.serverAddress
            ) { sessionState, shiftState, serverAddress ->
                ConnectionRequirement(
                    enabled =
                        sessionState is SessionState.SignedIn &&
                                shiftState is ShiftSessionState.Active,
                    serverAddress = serverAddress
                        .trim()
                        .removeSuffix("/")
                )
            }
                .distinctUntilChanged()
                .collectLatest { requirement ->
                    if (requirement.enabled) {
                        connectWithRetry(
                            serverAddress =
                                requirement.serverAddress
                        )
                    } else {
                        clearLiveMapUsers()
                    }
                }
        }
    }

    suspend fun stop() {
        lifecycleJob?.cancelAndJoin()
        lifecycleJob = null
    }

    private suspend fun connectWithRetry(
        serverAddress: String
    ) {
        val request = createRequest(serverAddress) ?: return
        var retryDelayMillis = INITIAL_RETRY_DELAY_MILLIS

        while (currentCoroutineContext().isActive) {
            val wasOpened = connectOnce(request)

            retryDelayMillis = if (wasOpened) {
                INITIAL_RETRY_DELAY_MILLIS
            } else {
                (retryDelayMillis * 2)
                    .coerceAtMost(MAX_RETRY_DELAY_MILLIS)
            }

            if (currentCoroutineContext().isActive) {
                delay(retryDelayMillis.milliseconds)
            }
        }
    }

    private suspend fun connectOnce(
        request: Request
    ): Boolean = coroutineScope {
        val connectionFinished = CompletableDeferred<Unit>()
        val socketReference = AtomicReference<WebSocket?>(null)
        val connectionOpened = AtomicBoolean(false)

        val listener = object : WebSocketListener() {
            override fun onOpen(
                webSocket: WebSocket,
                response: Response
            ) {
                socketReference.set(webSocket)
                connectionOpened.set(true)
            }

            override fun onMessage(
                webSocket: WebSocket,
                text: String
            ) {
                handleIncomingMessage(text)
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
                connectionFinished.complete(Unit)
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: Response?
            ) {
                socketReference.compareAndSet(
                    webSocket,
                    null
                )

                val statusCode = response?.code
                response?.close()
                connectionFinished.complete(Unit)

                Log.w(
                    TAG,
                    "Telemetry WebSocket failed" +
                            if (statusCode == null) {
                                ""
                            } else {
                                " with HTTP $statusCode"
                            },
                    t
                )
            }
        }

        val createdSocket = networkClient.okHttpClient
            .newWebSocket(request, listener)

        socketReference.compareAndSet(
            null,
            createdSocket
        )

        val sendJob = launch {
            while (isActive) {
                delay(SEND_INTERVAL_MILLIS.milliseconds)

                if (!connectionOpened.get()) {
                    continue
                }

                val telemetry = telemetryProvider
                    .telemetry
                    .value
                    ?: continue

                val activeSocket =
                    socketReference.get() ?: continue

                val accepted = activeSocket.send(
                    telemetry.toWebSocketJson()
                )

                if (!accepted) {
                    Log.w(
                        TAG,
                        "Telemetry update was rejected by " +
                                "the WebSocket queue"
                    )
                    connectionFinished.complete(Unit)
                }
            }
        }

        try {
            connectionFinished.await()
        } finally {
            socketReference
                .getAndSet(null)
                ?.close(
                    NORMAL_CLOSURE_CODE,
                    "Telemetry sender stopped"
                )

            withContext(NonCancellable) {
                sendJob.cancelAndJoin()
                clearLiveMapUsers()
            }
        }

        connectionOpened.get()
    }

    private fun handleIncomingMessage(
        text: String
    ) {
        val message = runCatching {
            JSONObject(text)
        }.getOrElse { exception ->
            Log.w(
                TAG,
                "Ignoring invalid WebSocket message",
                exception
            )

            return
        }

        when (message.optString("type")) {
            UPDATE_MESSAGE_TYPE -> {
                handleLiveUserUpdate(message)
            }

            CONNECTION_CHANGE_MESSAGE_TYPE -> {
                handleConnectionChange(message)
            }
        }
    }

    private fun handleLiveUserUpdate(
        message: JSONObject
    ) {
        val userId = message
            .optString("userId")
            .trim()

        val userName = message
            .optString("userName")
            .trim()

        val latitude = message.optDouble(
            "latitude",
            Double.NaN
        )

        val longitude = message.optDouble(
            "longitude",
            Double.NaN
        )

        val vehicleState =
            TelemetryVehicleState.entries
                .firstOrNull { state ->
                    state.wireValue ==
                            message.optString("state")
                }

        if (
            userId.isBlank() ||
            userName.isBlank() ||
            !latitude.isFinite() ||
            !longitude.isFinite() ||
            latitude !in TelemetryData.LATITUDE_RANGE ||
            longitude !in TelemetryData.LONGITUDE_RANGE ||
            vehicleState == null
        ) {
            Log.w(
                TAG,
                "Ignoring incomplete telemetry update"
            )

            return
        }

        val liveMapUser = LiveMapUser(
            userId = userId,
            userName = userName,
            latitude = latitude,
            longitude = longitude,
            state = vehicleState
        )

        _liveMapUsers.update { currentUsers ->
            currentUsers + (
                    userId to liveMapUser
                    )
        }
    }

    private fun handleConnectionChange(
        message: JSONObject
    ) {
        if (
            message.optString("state") !=
            DISCONNECTED_STATE
        ) {
            return
        }

        val userId = message
            .optString("userId")
            .trim()

        if (userId.isBlank()) {
            return
        }

        _liveMapUsers.update { currentUsers ->
            currentUsers - userId
        }
    }

    private fun clearLiveMapUsers() {
        _liveMapUsers.value = emptyMap()
    }

    private fun createRequest(
        serverAddress: String
    ): Request? {
        val serverUrl = serverAddress.toHttpUrlOrNull()

        if (serverUrl == null || serverUrl.scheme != "https") {
            return null
        }

        val endpointUrl = serverUrl
            .newBuilder()
            .addPathSegments(REALTIME_PATH)
            .build()

        val origin = serverUrl
            .newBuilder()
            .encodedPath("/")
            .query(null)
            .fragment(null)
            .build()
            .toString()
            .removeSuffix("/")

        /*
         * OkHttp's newWebSocket performs a secure WebSocket upgrade
         * when the request URL uses HTTPS. ...
         */
        return Request.Builder()
            .url(endpointUrl)
            .header("Origin", origin)
            .build()
    }

    private fun TelemetryData.toWebSocketJson(): String {
        return JSONObject()
            .put("type", type)
            .put("latitude", latitude)
            .put("longitude", longitude)
            .put("state", state.wireValue)
            .apply {
                vehicleId?.let { value ->
                    put("vehicleId", value)
                }

                fuelLevel?.let { value ->
                    put("fuelLevel", value)
                }

                odometer?.let { value ->
                    put("odometer", value)
                }
            }
            .toString()
    }

    private data class ConnectionRequirement(
        val enabled: Boolean,
        val serverAddress: String
    )

    private companion object {
        const val TAG = "TelemetryWebSocket"
        const val REALTIME_PATH = "api/realtime/track"
        const val UPDATE_MESSAGE_TYPE = "update"

        const val CONNECTION_CHANGE_MESSAGE_TYPE =
            "connectionChange"

        const val DISCONNECTED_STATE = "disconnected"
        const val SEND_INTERVAL_MILLIS = 1_000L
        const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRY_DELAY_MILLIS = 30_000L
        const val NORMAL_CLOSURE_CODE = 1000
    }
}
