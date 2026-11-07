package nyc.arash.homescreen.sync

/** A local intent can be replayed on a newer shelf without replacing unrelated edits. */
internal data class FavoriteEdit(
    val editId: String,
    val packageId: String,
    val remove: Boolean = false,
    val add: Boolean = false,
    val before: String? = null,
    val after: String? = null,
)

internal object FavoriteEdits {
    fun between(old: List<String>, new: List<String>, nextId: () -> String): List<FavoriteEdit> {
        val edits = old.filter { it !in new }.map { FavoriteEdit(nextId(), it, remove = true) }.toMutableList()
        val working = old.filter { it in new }.toMutableList()
        for (index in new.indices) {
            val id = new[index]
            if (working.getOrNull(index) == id) continue
            val added = id !in old
            working.remove(id)
            edits += FavoriteEdit(
                nextId(), id, add = added,
                before = working.getOrNull(index), after = working.getOrNull(index - 1),
            )
            working.add(index, id)
        }
        return edits
    }

    fun apply(remote: List<String>, edits: List<FavoriteEdit>): List<String> {
        val result = remote.toMutableList()
        for (edit in edits) {
            if (edit.remove) {
                result.remove(edit.packageId)
                continue
            }
            // Moving an existing app must not resurrect an app removed on another TV.
            if (!edit.add && edit.packageId !in result) continue
            result.remove(edit.packageId)
            val before = result.indexOf(edit.before)
            val after = result.indexOf(edit.after)
            val position = when {
                edit.before == null -> result.size
                before >= 0 -> before
                after >= 0 -> after + 1
                else -> result.size
            }
            result.add(position, edit.packageId)
        }
        return result
    }

}
