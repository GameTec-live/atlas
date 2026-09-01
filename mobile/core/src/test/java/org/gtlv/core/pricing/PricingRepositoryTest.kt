package org.gtlv.core.pricing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.gtlv.core.network.NetworkClient
import org.gtlv.core.settings.ServerSettingsRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PricingRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PricingRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = PricingRepositoryImpl(
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
    fun requestsPriceEndpointAndParsesRate() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"pricePerKilometer":2.75}"""
                )
        )

        assertEquals(
            PriceResult.Success(2.75),
            repository.getPricePerKilometer()
        )
        assertEquals(
            "/api/config/price",
            server.takeRequest().path
        )
    }

    @Test
    fun invalidPriceIsUnavailable() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """{"pricePerKilometer":-1}"""
                )
        )

        assertEquals(
            PriceResult.Unavailable,
            repository.getPricePerKilometer()
        )
    }

    private class FakeServerSettingsRepository(
        initialAddress: String
    ) : ServerSettingsRepository {
        private val address = MutableStateFlow(initialAddress)

        override val serverAddress: StateFlow<String> =
            address

        override suspend fun setServerAddress(
            address: String
        ) {
            this.address.value = address
        }
    }
}
