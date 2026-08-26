package org.gtlv.core.geoservice

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class GeoServiceRepositoryHeadingTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: GeoServiceRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        repository = GeoServiceRepositoryImpl(
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
    fun `route request sends heading for a live origin`() = runBlocking {
        server.enqueue(routerErrorResponse())

        repository.requestRoute(
            origin = RoutePoint(48.2082, 16.3738),
            destination = RoutePoint(48.3069, 16.437),
            headingDegrees = 93
        )

        val url = server.takeRequest().requestUrl
        assertEquals("93", url?.queryParameter("heading"))
        assertEquals("48.2082", url?.queryParameter("fromlat"))
        assertEquals("16.3738", url?.queryParameter("fromlon"))
    }

    @Test
    fun `route request omits an unavailable heading`() = runBlocking {
        server.enqueue(routerErrorResponse())

        repository.requestRoute(
            origin = RoutePoint(48.2082, 16.3738),
            destination = RoutePoint(48.3069, 16.437)
        )

        assertNull(
            server.takeRequest().requestUrl
                ?.queryParameter("heading")
        )
    }

    @Test
    fun `invalid heading is rejected without a network request`() =
        runBlocking {
            val result = repository.requestRoute(
                origin = RoutePoint(48.2082, 16.3738),
                destination = RoutePoint(48.3069, 16.437),
                headingDegrees = 361
            )

            assertEquals(RouteResult.InvalidResponse, result)
            assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        }

    private fun routerErrorResponse(): MockResponse =
        MockResponse()
            .setResponseCode(400)
            .setBody(
                """{
                    "error_code":171,
                    "error":"No suitable edges near location",
                    "status_code":400,
                    "status":"Bad Request"
                }""".trimIndent()
            )

    private class FakeServerSettingsRepository(
        initialAddress: String
    ) : ServerSettingsRepository {
        private val address = MutableStateFlow(initialAddress)

        override val serverAddress: Flow<String> = address

        override suspend fun setServerAddress(address: String) {
            this.address.value = address
        }
    }
}
