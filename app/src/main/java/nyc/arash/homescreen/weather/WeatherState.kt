package nyc.arash.homescreen.weather

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal class WeatherState(private val store: WeatherStore) {
    var settings by mutableStateOf(store.settings())
        private set
    var reading by mutableStateOf(store.cached())
    var failed by mutableStateOf(false)
    val repository = WeatherRepository()
    var lastAttemptAt = 0L
    var lastAttemptKey: String? = null

    fun configure(value: WeatherSettings) {
        store.save(value)
        settings = value
    }

    fun received(value: WeatherReading) {
        store.cache(value)
        reading = value
    }
}

@Composable
internal fun rememberWeather(resumeGeneration: Int): WeatherState {
    val context = LocalContext.current.applicationContext
    val state = remember(context) { WeatherState(WeatherStore(context)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(state.settings.location, resumeGeneration, lifecycle) {
        val location = state.settings.location ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                val cached = state.reading
                val fresh = cached?.let { it.usable(location, now) && it.fresh(now) && it.isDay != null && it.hourly.all { hour -> hour.isDay != null } } == true
                val recentlyAttempted = state.lastAttemptKey == location.key && now - state.lastAttemptAt in 0L until 60_000L
                if (!fresh && !recentlyAttempted) {
                    state.lastAttemptKey = location.key
                    state.lastAttemptAt = now
                    state.failed = false
                    try {
                        val reading = withContext(Dispatchers.IO) { state.repository.current(location) }
                        state.received(reading)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        state.failed = true
                    }
                }
                delay(60_000L)
            }
        }
    }
    return state
}
