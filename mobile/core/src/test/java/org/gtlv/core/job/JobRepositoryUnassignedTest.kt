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
import org.json.JSONObject

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

    @Test
    fun updateJobDetails_sendsDestinationAndDueDate() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(
                        "Content-Type",
                        "application/json"
                    )
                    .setBody("{}")
            )

            val result = repository.updateJobDetails(
                jobId = "job-1",
                destination = JobCoordinates(
                    latitude = 48.3,
                    longitude = 14.4
                ),
                dueDate = "2026-08-26T17:11:00Z"
            )

            assertEquals(JobActionResult.Success, result)

            val request = server.takeRequest()
            assertEquals("/api/jobs/job-1", request.path)
            assertEquals("PUT", request.method)

            val body = JSONObject(
                request.body.readUtf8()
            )
            assertEquals(
                "2026-08-26T17:11:00Z",
                body.getString("dueDate")
            )
            assertEquals(
                48.3,
                body.getJSONArray("to").getDouble(0),
                0.0
            )
            assertEquals(
                14.4,
                body.getJSONArray("to").getDouble(1),
                0.0
            )
        }

    @Test
    fun getJobCandidates_parsesRankedDrivers() =
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
                            "driverId": "driver-1",
                            "driverName": "Hermann",
                            "rankingTrace": {
                              "rank": 1,
                              "summary": "Best available driver"
                            }
                          }
                        ]
                        """.trimIndent()
                    )
            )

            val result = repository
                .getJobCandidates("job-1")

            assertTrue(
                result is JobCandidatesResult.Success
            )

            val candidate =
                (result as JobCandidatesResult.Success)
                    .candidates
                    .single()

            assertEquals("driver-1", candidate.driverId)
            assertEquals("Hermann", candidate.driverName)
            assertEquals(1, candidate.rank)

            val request = server.takeRequest()
            assertEquals(
                "/api/jobs/job-1/candidates",
                request.path
            )
            assertEquals("GET", request.method)
        }

    @Test
    fun assignJob_postsSelectedDriver() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(
                    "Content-Type",
                    "application/json"
                )
                .setBody("{}")
        )

        val result = repository.assignJob(
            jobId = "job-1",
            driverId = "driver-1"
        )

        assertEquals(JobActionResult.Success, result)

        val request = server.takeRequest()
        assertEquals(
            "/api/jobs/job-1/assign",
            request.path
        )
        assertEquals("POST", request.method)
        assertTrue(
            request.body.readUtf8()
                .contains(
                    "\"assignedDriverId\":\"driver-1\""
                )
        )
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
