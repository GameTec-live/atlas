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

/**
 * Sends the latest available telemetry snapshot to the configured server.
 *
 * The connection is active only while the user is signed in and has an
 * active role. Incoming application messages are intentionally ignored.
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
                Log.d(
                    TAG,
                    "Reconnecting in $retryDelayMillis ms"
                )
                delay(retryDelayMillis)
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

                Log.d(
                    TAG,
                    "Telemetry WebSocket connected"
                )
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

                Log.d(
                    TAG,
                    "Telemetry WebSocket closed: " +
                            "code=$code, reason=$reason"
                )
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
                    throwable
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
            }
        }

        connectionOpened.get()
    }

    private fun createRequest(
        serverAddress: String
    ): Request? {
        val serverUrl = serverAddress.toHttpUrlOrNull()

        if (serverUrl == null || serverUrl.scheme != "https") {
            Log.e(
                TAG,
                "Telemetry requires a valid HTTPS server address"
            )
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
         * when the request URL uses HTTPS.
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
        const val SEND_INTERVAL_MILLIS = 1_000L
        const val INITIAL_RETRY_DELAY_MILLIS = 1_000L
        const val MAX_RETRY_DELAY_MILLIS = 30_000L
        const val NORMAL_CLOSURE_CODE = 1000
    }
}
