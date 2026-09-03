package org.gtlv.core.logbook

import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LogbookRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: LogbookRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = LogbookRepositoryImpl(
            networkClient = NetworkClient(),
            serverSettingsRepository = FakeServerSettingsRepository(
                server.url("/").toString()
            )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `submits the completed shift`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val submission = LogbookSubmission(
            vehicleId = "7bb0de4d-bcdd-4c99-a852-a17a4bbdb3de",
            startedAt = Instant.parse("2026-09-03T07:30:00Z"),
            startOdometer = 12_345,
            endOdometer = 12_695,
            endedAt = Instant.parse("2026-09-03T17:00:00Z"),
            revenue = 325.5
        )

        assertEquals(SubmitLogbookResult.Success, repository.submit(submission))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/logbooks/submit", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals(submission.vehicleId, body.getString("vehicleId"))
        assertEquals(12_345L, body.getLong("startOdometer"))
        assertEquals(12_695L, body.getLong("endOdometer"))
        assertEquals(325.5, body.getDouble("revenue"), 0.0)
    }

    private class FakeServerSettingsRepository(
        initialAddress: String
    ) : ServerSettingsRepository {
        private val address = MutableStateFlow(initialAddress)
        override val serverAddress: StateFlow<String> = address
        override suspend fun setServerAddress(address: String) {
            this.address.value = address
        }
    }
}
