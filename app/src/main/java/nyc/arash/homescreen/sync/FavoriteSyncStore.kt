package nyc.arash.homescreen.sync

import android.content.Context
import nyc.arash.homescreen.apps.LauncherLayout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class RemoteFavorites(val revision: Long, val favorites: List<String>)
internal data class LinkClaim(val id: String, val kind: String, val code: String, val favorites: List<String>)
internal data class PendingWrite(val mutationId: String, val revision: Long, val favorites: List<String>, val editIds: List<String>)
internal data class SavedSync(
    val favorites: List<String>,
    val token: String? = null,
    val revision: Long = 0,
    val base: List<String> = emptyList(),
    val edits: List<FavoriteEdit> = emptyList(),
    val claim: LinkClaim? = null,
    val pending: PendingWrite? = null,
    val revoking: String? = null,
    val lastSyncedAt: Long = 0,
) {
    fun withLocalFavorites(
        next: List<String>,
        nextId: () -> String,
        displayedFavorites: List<String>? = null,
    ): SavedSync {
        val rebased = if (displayedFavorites == null) next else FavoriteEdits.apply(
            favorites, FavoriteEdits.between(displayedFavorites, next, nextId),
        )
        val pendingIds = pending?.editIds.orEmpty()
        val submitted = edits.filter { it.editId in pendingIds }
        val baseline = pending?.favorites ?: claim?.favorites ?: base
        val nextEdits = if (token != null || claim != null) FavoriteEdits.between(baseline, rebased, nextId) else emptyList()
        return copy(favorites = rebased, edits = submitted + nextEdits)
    }

    fun accepted(remote: RemoteFavorites, acknowledged: List<String>, now: Long): SavedSync {
        val remaining = edits.filterNot { it.editId in acknowledged }
        return copy(
            revision = remote.revision, base = remote.favorites,
            edits = remaining, favorites = FavoriteEdits.apply(remote.favorites, remaining),
            pending = null, lastSyncedAt = now,
        )
    }
}

internal class FavoriteSyncStore(context: Context) {
    val preferences = context.applicationContext.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)

    fun read(): SavedSync = synchronized(lock) { readLocked() }

    fun update(transform: (SavedSync) -> SavedSync): SavedSync = synchronized(lock) {
        val next = transform(readLocked())
        writeLocked(next)
        next
    }

    fun saveLocal(ids: List<String>, displayedFavorites: List<String>? = null) = update { old ->
        val next = LauncherLayout.normalizeFavorites(ids)
        val displayed = displayedFavorites?.let { LauncherLayout.normalizeFavorites(it) }
        old.withLocalFavorites(next, ::uuid, displayed)
    }

    fun accept(remote: RemoteFavorites, acknowledged: List<String> = emptyList()): SavedSync = update { old ->
        old.accepted(remote, acknowledged, System.currentTimeMillis())
    }

    private fun readLocked(): SavedSync {
        val favorites = if (preferences.contains(FAVORITES)) LauncherLayout.normalizeFavorites(
            preferences.getString(FAVORITES, "").orEmpty().split('\n'),
        ) else LauncherLayout.DEFAULT_FAVORITES.toList()
        val json = JSONObject(preferences.getString(STATE, "{}").orEmpty())
        val claim = json.optJSONObject("claim")?.let {
            LinkClaim(
                it.getString("id"), it.getString("kind"), it.getString("code"),
                it.getJSONArray("favorites").strings(),
            )
        }
        val pending = json.optJSONObject("pending")?.let {
            PendingWrite(
                it.getString("mutationId"), it.getLong("revision"),
                it.getJSONArray("favorites").strings(), it.getJSONArray("editIds").strings(),
            )
        }
        val edits = json.optJSONArray("edits")?.let { array ->
            (0 until array.length()).map { index ->
                val edit = array.getJSONObject(index)
                FavoriteEdit(
                    edit.getString("editId"), edit.getString("packageId"),
                    edit.optBoolean("remove"), edit.optBoolean("add"),
                    edit.nullableString("before"), edit.nullableString("after"),
                )
            }
        }.orEmpty()
        return SavedSync(
            favorites, json.nullableString("token"), json.optLong("revision"),
            json.optJSONArray("base")?.strings().orEmpty(), edits, claim, pending,
            json.nullableString("revoking"), json.optLong("lastSyncedAt"),
        )
    }

    private fun writeLocked(value: SavedSync) {
        val json = JSONObject().put("token", value.token).put("revision", value.revision)
            .put("base", JSONArray(value.base)).put("lastSyncedAt", value.lastSyncedAt).put("revoking", value.revoking)
            .put("edits", JSONArray(value.edits.map { edit ->
                JSONObject().put("editId", edit.editId).put("packageId", edit.packageId)
                    .put("remove", edit.remove).put("add", edit.add).put("before", edit.before).put("after", edit.after)
            }))
        value.claim?.let { json.put("claim", JSONObject().put("id", it.id).put("kind", it.kind).put("code", it.code).put("favorites", JSONArray(it.favorites))) }
        value.pending?.let { json.put("pending", JSONObject().put("mutationId", it.mutationId).put("revision", it.revision).put("favorites", JSONArray(it.favorites)).put("editIds", JSONArray(it.editIds))) }
        check(preferences.edit().putString(FAVORITES, value.favorites.joinToString("\n")).putString(STATE, json.toString()).commit()) { "Could not save favorites on this TV." }
    }

    companion object {
        private val lock = Any()
        private const val FAVORITES = "favorite_packages_v1"
        private const val STATE = "favorites_sync_v1"
        fun uuid(): String = UUID.randomUUID().toString()
    }
}

internal fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
private fun JSONObject.nullableString(key: String): String? = if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
