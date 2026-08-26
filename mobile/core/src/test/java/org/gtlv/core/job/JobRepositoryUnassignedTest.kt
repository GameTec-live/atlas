package org.gtlv.core.job

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class JobRepositoryUnassignedTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: JobRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        repository = JobRepositoryImpl(
            networkClient = NetworkClient(),
            serverSettingsRepository =
                FakeServerSettingsRepository(
                    server.url("/").toString()
                )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun getUnassignedJobs_requestsEndpointAndParsesJobs() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(
                        "Content-Type",
                        "application/json"
                    )
                    .setBody(
                        """
                        [
                          {
                            "id": "job-1",
                            "assignedDriverId": null,
                            "vehicleId": null,
                            "from": [48.2082, 16.3738],
                            "to": [48.1947, 16.3122],
                            "fromAddress": "Origin",
                            "toAddress": "Destination",
                            "dueDate": "2026-08-01T12:00:00.000Z",
                            "note": "For Franz",
                            "startedAt": null,
                            "completedAt": null,
                            "createdAt": "2026-07-01T12:00:00.000Z",
                            "updatedAt": "2026-07-01T12:00:00.000Z"
                          }
                        ]
                        """.trimIndent()
                    )
            )

            val result = repository.getUnassignedJobs()

            assertTrue(
                result is UnassignedJobsResult.Success
            )

            val jobs =
                (result as UnassignedJobsResult.Success)
                    .jobs

            assertEquals(1, jobs.size)
            assertEquals("job-1", jobs.single().id)
            assertEquals(
                "Origin",
                jobs.single().fromAddress
            )
            assertEquals(
                "For Franz",
                jobs.single().note
            )

            val request = server.takeRequest()
            assertEquals(
                "/api/jobs/unassigned?geocode",
                request.path
            )
            assertEquals("GET", request.method)
        }

    @Test
    fun deleteUnassignedJob_sendsDeleteToJobEndpoint() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(
                        "Content-Type",
                        "application/json"
                    )
                    .setBody(
                        """{"message":"Job deleted successfully"}"""
                    )
            )

            val result = repository
                .deleteUnassignedJob("job-1")

            assertEquals(
                JobActionResult.Success,
                result
            )

            val request = server.takeRequest()
            assertEquals(
                "/api/jobs/job-1",
                request.path
            )
            assertEquals("DELETE", request.method)
        }

    private class FakeServerSettingsRepository(
        initialAddress: String
    ) : ServerSettingsRepository {

        private val address = MutableStateFlow(
            initialAddress
        )

        override val serverAddress: StateFlow<String> =
            address

        override suspend fun setServerAddress(
            address: String
        ) {
            this.address.value = address
        }
    }
}
