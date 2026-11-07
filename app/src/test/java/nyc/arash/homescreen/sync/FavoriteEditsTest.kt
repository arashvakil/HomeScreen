package nyc.arash.homescreen.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteEditsTest {
    private var sequence = 0
    private fun edit(old: List<String>, new: List<String>) = FavoriteEdits.between(old, new) { (++sequence).toString() }

    @Test fun allSmallReordersAndMembershipChangesProduceRequestedShelf() {
        val variants = permutations(listOf("a", "b", "c", "d")).flatMap { ordered ->
            (0..ordered.size).map { count -> ordered.take(count) }
        }.distinct()
        for (old in variants) for (new in variants) {
            assertEquals("$old -> $new", new, FavoriteEdits.apply(old, edit(old, new)))
        }
    }

    @Test fun remoteAdditionSurvivesLocalRemovalAndMove() {
        val old = listOf("a", "b", "c")
        val local = listOf("c", "a")
        assertEquals(listOf("c", "a", "remote"), FavoriteEdits.apply(listOf("a", "b", "remote", "c"), edit(old, local)))
    }

    @Test fun moveDoesNotResurrectAppRemovedRemotely() {
        assertEquals(listOf("a", "c"), FavoriteEdits.apply(listOf("a", "c"), edit(listOf("a", "b", "c"), listOf("b", "a", "c"))))
    }

    @Test fun addingNewAppAndReplayingEditDoesNotDuplicateIt() {
        val edits = edit(listOf("a", "b"), listOf("a", "new", "b"))
        val once = FavoriteEdits.apply(listOf("a", "remote", "b"), edits)
        assertEquals(listOf("a", "remote", "new", "b"), once)
        assertEquals(once, FavoriteEdits.apply(once, edits))
    }

    @Test fun disjointMovesAreRetained() {
        assertEquals(listOf("b", "a", "d", "c"), FavoriteEdits.apply(
            listOf("a", "b", "d", "c"), edit(listOf("a", "b", "c", "d"), listOf("b", "a", "c", "d")),
        ))
    }

    private fun permutations(items: List<String>): List<List<String>> = if (items.isEmpty()) listOf(emptyList())
        else items.flatMap { first -> permutations(items - first).map { listOf(first) + it } }
}
