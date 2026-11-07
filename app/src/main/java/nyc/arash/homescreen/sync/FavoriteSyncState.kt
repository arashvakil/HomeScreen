package nyc.arash.homescreen.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal class FavoriteSyncState(
    private val store: FavoriteSyncStore,
    private val client: FavoriteSyncClient,
    private val scope: CoroutineScope,
) {
    private var saved by mutableStateOf(store.read())
    val favoriteIds: List<String> get() = saved.favorites
    val linked: Boolean get() = saved.token != null
    val revoking: Boolean get() = saved.revoking != null
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var pairCode by mutableStateOf<String?>(null)
        private set
    var pairCodeExpiresAt by mutableLongStateOf(0L)
        private set
    val status: String get() = when {
        revoking -> "Unlinking when online"
        saved.claim != null -> "Link unfinished. Retry to finish."
        !linked -> "This TV only"
        saved.edits.isNotEmpty() -> "Changes saved on this TV. Waiting to sync."
        saved.lastSyncedAt > 0 -> "Favorites are linked"
        else -> "Waiting to sync"
    }

    fun refresh() { saved = store.read() }
    fun clearError() { error = null }
    fun closeCode() { pairCode = null; pairCodeExpiresAt = 0L }

    fun saveFavorites(ids: List<String>) {
        try {
            saved = store.saveLocal(ids)
            syncNow()
        } catch (_: Exception) {
            error = "Could not save favorites on this TV. Try again."
        }
    }

    fun createGroup() = perform {
        client.link("groups")?.let { pairCode = it.code; pairCodeExpiresAt = it.expiresAt }
    }

    fun join(code: String) = perform { client.link("join", code); closeCode() }
    fun newPairCode() = perform {
        client.newPairCode().let { pairCode = it.code; pairCodeExpiresAt = it.expiresAt }
    }
    fun unlink() = perform { closeCode(); client.unlink() }
    fun syncNow() {
        if (!linked && !revoking && saved.claim == null) return
        perform(clearExistingError = false) { client.sync() }
    }

    private fun perform(clearExistingError: Boolean = true, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        if (clearExistingError) error = null
        scope.launch {
            try {
                action()
                error = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = when (failure) {
                    is SyncHttpException -> when (failure.status) {
                        400, 404, 410, 422 -> "That code is invalid or expired. Ask the other TV for a new code."
                        401 -> "This TV's link was removed. Link it again to sync."
                        429 -> "Too many attempts. Please wait a minute and try again."
                        else -> "Could not sync. Your favorites are saved on this TV."
                    }
                    is IllegalStateException -> failure.message
                    else -> "Could not connect. Your favorites are saved on this TV."
                }
            } finally {
                refresh()
                busy = false
            }
        }
    }
}

@Composable
internal fun rememberFavoriteSync(resumeGeneration: Int): FavoriteSyncState {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val store = remember(context) { FavoriteSyncStore(context) }
    val state = remember(context) { FavoriteSyncState(store, FavoriteSyncClient(context), scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(store, state) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> state.refresh() }
        store.preferences.registerOnSharedPreferenceChangeListener(listener)
        FavoriteSyncScheduler.schedule(context)
        onDispose { store.preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(state, resumeGeneration, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                state.refresh()
                state.syncNow()
                delay(10_000L)
            }
        }
    }
    return state
}

class FavoriteSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            FavoriteSyncClient(applicationContext).sync()
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            if (runAttemptCount < 4) Result.retry() else Result.failure()
        }
    }
}

internal object FavoriteSyncScheduler {
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<FavoriteSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("homescreen-favorites-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
