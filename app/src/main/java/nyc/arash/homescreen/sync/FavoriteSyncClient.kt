package nyc.arash.homescreen.sync

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nyc.arash.homescreen.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class SyncHttpException(val status: Int, val response: JSONObject) : IOException("Sync request failed ($status)")
internal data class PairingCode(val code: String, val expiresAt: Long)

internal class FavoriteSyncClient(context: Context) {
    private val store = FavoriteSyncStore(context)
    private val api = FavoriteSyncApi()

    suspend fun sync(): Unit = inNetwork { syncLocked() }

    suspend fun link(kind: String, code: String = ""): PairingCode? = inNetwork {
        var saved = store.read()
        check(saved.token == null) { "This TV is already linked." }
        check(saved.revoking == null) { "Finish unlinking this TV before linking it again." }
        val normalizedCode = code.uppercase(Locale.ROOT).filterNot { it.isWhitespace() || it == '-' }
        if (saved.claim != null) {
            check(saved.claim.kind == kind && saved.claim.code == normalizedCode) { "Retry the unfinished link before starting a different one." }
        } else {
            if (kind == "groups") checkCapacity(saved.favorites)
            saved = store.update { it.copy(claim = LinkClaim(FavoriteSyncStore.uuid(), kind, normalizedCode, it.favorites), edits = emptyList()) }
        }
        completeClaimLocked()
    }

    private fun completeClaimLocked(): PairingCode? {
        val claim = checkNotNull(store.read().claim)
        if (claim.kind == "groups") checkCapacity(claim.favorites)
        val body = JSONObject().put("claimId", claim.id).put("deviceName", Build.MODEL.take(64))
        if (claim.kind == "groups") body.put("favorites", JSONArray(claim.favorites)) else body.put("pairCode", claim.code)
        val response = try {
            api.request("POST", "/${claim.kind}", body = body)
        } catch (error: SyncHttpException) {
            // A definitive rejected request can be replaced with a fresh code. A transport
            // failure keeps the claim so a committed request is safely recovered on retry.
            if (error.status in listOf(400, 404, 410, 422)) store.update { it.copy(claim = null, edits = emptyList()) }
            throw error
        }
        val remote = response.remote()
        store.update { current -> current.copy(
            token = response.getString("deviceToken"), revision = remote.revision,
            base = remote.favorites, favorites = FavoriteEdits.apply(remote.favorites, current.edits),
            claim = null, pending = null, lastSyncedAt = System.currentTimeMillis(),
        ) }
        return response.optString("pairCode").takeIf { it.isNotEmpty() }?.let { PairingCode(it, response.getLong("pairCodeExpiresAt")) }
    }

    suspend fun newPairCode(): PairingCode = inNetwork {
        syncLocked()
        val token = checkNotNull(store.read().token) { "Link this TV first." }
        val response = api.request("POST", "/pair-code", token)
        PairingCode(response.getString("pairCode"), response.getLong("pairCodeExpiresAt"))
    }

    suspend fun unlink(): Unit = inNetwork {
        val current = store.read()
        check(current.claim == null) { "Retry the unfinished link before unlinking." }
        store.update { it.copy(token = null, revoking = it.token ?: it.revoking, edits = emptyList(), pending = null, base = emptyList(), revision = 0) }
        revokeLocked()
    }

    private fun revokeLocked() {
        val token = store.read().revoking ?: return
        try {
            api.request("DELETE", "/device", token)
        } catch (error: SyncHttpException) {
            if (error.status != 401 && error.status != 404) throw error
        }
        store.update { if (it.revoking == token) it.copy(revoking = null) else it }
    }

    private fun syncLocked() {
        revokeLocked()
        if (store.read().claim != null) completeClaimLocked()
        if (store.read().token == null) return
        try {
            // Resolve an uncertain PUT before fetching/rebasing. A successful response may
            // have been lost while the other TV subsequently changed the shared shelf.
            sendPending()
            repeat(4) {
                val saved = store.read()
                val token = saved.token ?: return
                val remote = api.request("GET", "/favorites", token).remote()
                val merged = store.accept(remote)
                if (merged.edits.isEmpty()) return
                checkCapacity(merged.favorites)
                store.update { current ->
                    checkCapacity(current.favorites)
                    current.copy(pending = PendingWrite(
                        FavoriteSyncStore.uuid(), current.revision, current.favorites,
                        current.edits.map { edit -> edit.editId },
                    ))
                }
                if (sendPending()) return
            }
            throw IOException("Favorites changed on another TV. Retrying shortly.")
        } catch (error: SyncHttpException) {
            if (error.status == 401) {
                // Keep the visible shelf even if another action revoked this device key.
                store.update { it.copy(token = null, pending = null, edits = emptyList(), base = emptyList(), revision = 0) }
            }
            throw error
        }
    }

    /** True means the pending write was accepted, false means it needs rebasing. */
    private fun sendPending(): Boolean {
        val saved = store.read()
        val write = saved.pending ?: return true
        val token = saved.token ?: return true
        val body = JSONObject().put("mutationId", write.mutationId).put("revision", write.revision).put("favorites", JSONArray(write.favorites))
        val response = try {
            api.request("PUT", "/favorites", token, body)
        } catch (error: SyncHttpException) {
            if (error.status == 409 && error.response.optString("error") == "revision_conflict") {
                store.accept(error.response.remote())
                return false
            }
            throw error
        }
        store.accept(response.remote(), write.editIds)
        // A local edit made while the request was running remains in the outbox.
        return store.read().edits.isEmpty()
    }

    private fun checkCapacity(favorites: List<String>) {
        check(favorites.size <= 128) {
            "Shared favorites supports up to 128 apps. Remove an app to finish syncing."
        }
    }

    private suspend fun <T> inNetwork(block: () -> T): T = withContext(Dispatchers.IO) {
        networkLock.withLock { block() }
    }

    companion object {
        private val networkLock = Mutex()
    }
}

private class FavoriteSyncApi {
    fun request(method: String, path: String, token: String? = null, body: JSONObject? = null): JSONObject {
        val builder = Request.Builder().url(BuildConfig.FAVORITES_SYNC_URL.trimEnd('/') + path)
            .header("Accept", "application/json").header("Cache-Control", "no-store")
        token?.let { builder.header("Authorization", "Bearer $it") }
        val requestBody = (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType())
        builder.method(method, if (method == "GET" || method == "DELETE") null else requestBody)
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (text.length > 65_536) throw IOException("Sync response is too large.")
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (!response.isSuccessful) throw SyncHttpException(response.code, json)
            return json
        }
    }

    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build()
    }
}

private fun JSONObject.remote(): RemoteFavorites {
    val favorites = getJSONArray("favorites").strings()
    require(favorites.size <= 128 && favorites.distinct().size == favorites.size)
    require(favorites.all { it.length <= 255 && it.matches(Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) })
    val revision = getLong("revision")
    require(revision >= 1)
    return RemoteFavorites(revision, favorites)
}
