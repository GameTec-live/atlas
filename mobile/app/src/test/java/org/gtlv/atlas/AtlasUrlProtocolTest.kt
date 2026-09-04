 package org.gtlv.atlas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtlasUrlProtocolTest {

    @Test
    fun `plain host uses https`() {
        assertEquals(
            "https://atlas.example.com",
            AtlasUrlProtocol.serverAddressFrom(
                "atlas://atlas.example.com"
            )
        )
    }

    @Test
    fun `percent encoded https url is decoded`() {
        assertEquals(
            "https://atlas.example.com/public",
            AtlasUrlProtocol.serverAddressFrom(
                "atlas://https%3A%2F%2Fatlas.example.com%2Fpublic"
            )
        )
    }

    @Test
    fun `explicit http url is supported`() {
        assertEquals(
            "http://192.168.1.12:3000",
            AtlasUrlProtocol.serverAddressFrom(
                "atlas://http%3A%2F%2F192.168.1.12%3A3000"
            )
        )
    }

    @Test
    fun `provided qr payload is supported`() {
        assertEquals(
            "http://localhost:3001",
            AtlasUrlProtocol.serverAddressFrom(
                "atlas://http%3A%2F%2Flocalhost%3A3001"
            )
        )
    }

    @Test
    fun `query plus is preserved`() {
        assertEquals(
            "https://atlas.example.com/public?token=a+b",
            AtlasUrlProtocol.serverAddressFrom(
                "atlas://https%3A%2F%2Fatlas.example.com%2Fpublic%3Ftoken%3Da+b"
            )
        )
    }

    @Test
    fun `empty and invalid urls are rejected`() {
        assertNull(AtlasUrlProtocol.serverAddressFrom("atlas://"))
        assertNull(AtlasUrlProtocol.serverAddressFrom("mailto:test@example.com"))
        assertNull(AtlasUrlProtocol.serverAddressFrom("atlas://https%3A%2F%2F"))
    }
}
