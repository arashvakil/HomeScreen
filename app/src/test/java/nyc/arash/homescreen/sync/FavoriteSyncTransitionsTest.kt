package nyc.arash.homescreen.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FavoriteSyncTransitionsTest {
    private var sequence = 0
    private fun nextId() = (++sequence).toString()
    private fun linked(favorites: List<String>) = SavedSync(favorites, token = "device", revision = 1, base = favorites)

    @Test fun laterLocalEditSurvivesAcknowledgementOfInFlightWrite() {
        val edited = linked(listOf("a", "b")).withLocalFavorites(listOf("b", "a"), ::nextId)
        val write = PendingWrite("mutation", 1, edited.favorites, edited.edits.map { it.editId })
        val inFlight = edited.copy(pending = write)
        val newer = inFlight.withLocalFavorites(listOf("b", "new", "a"), ::nextId)
        val acknowledged = newer.accepted(RemoteFavorites(2, listOf("b", "a")), write.editIds, 100)
        assertEquals(listOf("b", "new", "a"), acknowledged.favorites)
        assertEquals(1, acknowledged.edits.size)
        assertEquals("new", acknowledged.edits.single().packageId)
        assertNull(acknowledged.pending)
    }

    @Test fun lostAcknowledgementDoesNotReplayOldMoveOverNewerRemoteEdit() {
        val edited = linked(listOf("a", "b")).withLocalFavorites(listOf("b", "a"), ::nextId)
        val write = PendingWrite("mutation", 1, edited.favorites, edited.edits.map { it.editId })
        val uncertain = edited.copy(pending = write)
        // Server recognises the mutation ID and returns the shelf another TV saved later.
        val recovered = uncertain.accepted(RemoteFavorites(3, listOf("a", "b", "remote")), write.editIds, 100)
        assertEquals(listOf("a", "b", "remote"), recovered.favorites)
        assertEquals(emptyList<FavoriteEdit>(), recovered.edits)
    }

    @Test fun staleOfflineRemovalRebasesWithoutDroppingRemoteAddition() {
        val edited = linked(listOf("a", "b")).withLocalFavorites(listOf("a"), ::nextId)
        val rebased = edited.accepted(RemoteFavorites(2, listOf("a", "remote", "b")), emptyList(), 100)
        assertEquals(listOf("a", "remote"), rebased.favorites)
        assertEquals(listOf("a", "remote", "b"), rebased.base)
        assertEquals(1, rebased.edits.size)
    }

    @Test fun localChangesBeforeFirstLinkRemainDeviceLocal() {
        val edited = SavedSync(listOf("a", "b")).withLocalFavorites(listOf("b"), ::nextId)
        assertEquals(listOf("b"), edited.favorites)
        assertEquals(emptyList<FavoriteEdit>(), edited.edits)
    }

    @Test fun staleDisplayedReorderPreservesAppJustAddedRemotely() {
        val latest = linked(listOf("a", "b", "remote"))
        val edited = latest.withLocalFavorites(listOf("b", "a"), ::nextId, listOf("a", "b"))
        assertEquals(listOf("b", "a", "remote"), edited.favorites)
        assertEquals(listOf("b", "a", "remote"), FavoriteEdits.apply(latest.base, edited.edits))
    }

    @Test fun staleDisplayedMoveDoesNotResurrectAppJustRemovedRemotely() {
        val latest = linked(listOf("a", "remote"))
        val edited = latest.withLocalFavorites(listOf("b", "a"), ::nextId, listOf("a", "b"))
        assertEquals(listOf("a", "remote"), edited.favorites)
        assertEquals(emptyList<FavoriteEdit>(), edited.edits)
    }

    @Test fun staleDisplayedRemovalPreservesRemoteAdditionAndPendingLocalEdit() {
        val latest = linked(listOf("a", "b", "remote"))
            .withLocalFavorites(listOf("b", "a", "remote"), ::nextId)
        val edited = latest.withLocalFavorites(listOf("a"), ::nextId, listOf("a", "b"))
        assertEquals(listOf("a", "remote"), edited.favorites)
        assertEquals(listOf("a", "remote"), FavoriteEdits.apply(latest.base, edited.edits))
    }

    @Test fun concurrentAdditionsRetainEveryAppWhenSharedCapacityIsExceeded() {
        val base = (1..127).map { "app.$it" }
        val edited = linked(base).withLocalFavorites(base + "app.local", ::nextId)
        val rebased = edited.accepted(RemoteFavorites(2, base + "app.remote"), emptyList(), 100)
        assertEquals(129, rebased.favorites.size)
        assertEquals((base + "app.local" + "app.remote").toSet(), rebased.favorites.toSet())
        val reduced = rebased.withLocalFavorites(rebased.favorites - "app.1", ::nextId)
        assertEquals(128, reduced.favorites.size)
    }

    @Test fun repeatedOfflineEditsAreCompactedAndCancellationLeavesNoOutbox() {
        val initial = linked(listOf("a", "b"))
        var edited = initial
        repeat(1000) {
            edited = edited.withLocalFavorites(listOf("b", "a"), ::nextId)
            edited = edited.withLocalFavorites(listOf("a", "b"), ::nextId)
        }
        assertEquals(initial.favorites, edited.favorites)
        assertEquals(emptyList<FavoriteEdit>(), edited.edits)
    }
}
