package nyc.arash.homescreen.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncServiceEndpointTest {
    private val first = "https://sync.example.com/v1"
    private val second = "https://other.example.com/sync/v1"

    @Test fun publicFreshInstallHasNoService() {
        assertEquals("", SyncServiceEndpoint.choose("", "", false))
    }
    @Test fun selfHostedBuildSuppliesInitialService() {
        assertEquals(first, SyncServiceEndpoint.choose("", "$first/", false))
    }
    @Test fun publicUpdateRetainsLinkedService() {
        assertEquals(first, SyncServiceEndpoint.choose(first, "", true))
    }
    @Test fun linkedCredentialsCannotMoveToAnotherBuildDefault() {
        assertEquals(first, SyncServiceEndpoint.choose(first, second, true))
    }
    @Test fun unlinkedTvCanUseAnotherConfiguredService() {
        assertEquals(second, SyncServiceEndpoint.choose(first, second, false))
        assertEquals(first, SyncServiceEndpoint.choose(first, "", false))
    }
    @Test fun existingVersionFiveLinkCanPinItsOriginalService() {
        assertEquals(first, SyncServiceEndpoint.choose("", first, true))
        assertEquals("", SyncServiceEndpoint.choose("", "", true))
    }
    @Test(expected = IllegalArgumentException::class) fun cleartextIsRejected() {
        SyncServiceEndpoint.choose("", "http://sync.example.com/v1", false)
    }
    @Test(expected = IllegalArgumentException::class) fun embeddedCredentialsAreRejected() {
        SyncServiceEndpoint.choose("", "https://name:password@sync.example.com/v1", false)
    }
    @Test(expected = IllegalArgumentException::class) fun queryIsRejected() {
        SyncServiceEndpoint.choose("", "$first?token=value", false)
    }
}
