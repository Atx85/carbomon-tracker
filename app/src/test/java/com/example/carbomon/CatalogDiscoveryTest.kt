package com.example.carbomon

import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress

class CatalogDiscoveryTest {
    private fun candidate(address: String = "192.168.0.181", port: Int = 8765,
        version: String = "1", id: String = "catalogue-one", expected: String = "") =
        discoveredCatalogServer("Kitchen", InetAddress.getByName(address), port,
            mapOf("version" to version, "id" to id), expected)

    @Test fun localServerUsesAdvertisedPort() {
        assertEquals("http://192.168.0.181:9876", candidate(port = 9876)?.address)
        assertEquals("catalogue-one", candidate()?.serverId)
    }

    @Test fun rememberedCatalogueCanMoveWithoutSelectingAnotherCatalogue() {
        assertNotNull(candidate(address = "192.168.0.200", expected = "catalogue-one"))
        assertNull(candidate(id = "different-catalogue", expected = "catalogue-one"))
        assertNotNull(candidate(id = "different-catalogue", expected = ""))
    }

    @Test fun malformedOrNonLocalAnnouncementsAreIgnored() {
        for (address in listOf("8.8.8.8", "127.0.0.1", "0.0.0.0", "224.0.0.251", "::1")) assertNull(candidate(address))
        assertNull(candidate(port = 0))
        assertNull(candidate(port = 65536))
        assertNull(candidate(version = "2"))
        assertNull(candidate(id = ""))
        assertNull(candidate(id = "wrong\nvalue"))
        assertNull(discoveredCatalogServer("Kitchen", InetAddress.getByName("192.168.0.1"), 8765, emptyMap(), ""))
    }
}
