package nyc.arash.homescreen

import android.os.Bundle
import android.content.SharedPreferences
import android.widget.Toast
import java.io.File
import nyc.arash.homescreen.update.UpdateManifest
import nyc.arash.homescreen.update.UpdateReadyStore
import nyc.arash.homescreen.update.UpdateInstaller
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nyc.arash.homescreen.apps.AppRepository
import nyc.arash.homescreen.apps.TvApp
import nyc.arash.homescreen.ui.HomeScreen
import nyc.arash.homescreen.ui.theme.HomeScreenTheme
import nyc.arash.homescreen.update.UpdateScheduler

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<TvApp>>(emptyList())
    private var loadJob: Job? = null
    private var resumeGeneration by mutableIntStateOf(0)
    private var pendingFocusRestore = false
    private val updateStore by lazy { UpdateReadyStore(applicationContext) }
    private var readyUpdate by mutableStateOf<Pair<UpdateManifest, File>?>(null)
    private var installingUpdate by mutableStateOf(false)
    private var updateLoadJob: Job? = null
    private val updateListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == UpdateReadyStore.READY_KEY) refreshReadyUpdate()
    }

    private fun refreshReadyUpdate() {
        if (BuildConfig.DEBUG || !BuildConfig.AUTO_UPDATE_ENABLED) return
        updateLoadJob?.cancel()
        updateLoadJob = lifecycleScope.launch {
            readyUpdate = withContext(Dispatchers.IO) { updateStore.loadReady() }
        }
    }

    private fun installReadyUpdate() {
        val ready = readyUpdate ?: return
        if (installingUpdate || !hasWindowFocus()) return
        installingUpdate = true
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { UpdateInstaller.install(applicationContext, ready.second, ready.first) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, "Could not install update. Allow Home to install unknown apps, then try again.", Toast.LENGTH_LONG).show()
            } finally {
                installingUpdate = false
                refreshReadyUpdate()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.AUTO_UPDATE_ENABLED && !BuildConfig.DEBUG) UpdateScheduler.schedule(applicationContext)
        updateStore.preferences.registerOnSharedPreferenceChangeListener(updateListener)
        setContent {
            HomeScreenTheme {
                HomeScreen(apps = apps, resumeGeneration = resumeGeneration, updateVersion = readyUpdate?.first?.versionName, updateInstalling = installingUpdate, onInstallUpdate = ::installReadyUpdate, onLaunchApp = { app -> runCatching { startActivity(app.launchIntent) } })
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) restoreFocusWhenReady()
    }

    private fun restoreFocusWhenReady() {
        if (pendingFocusRestore && hasWindowFocus()) {
            pendingFocusRestore = false
            resumeGeneration++
        }
    }

    override fun onDestroy() {
        updateStore.preferences.unregisterOnSharedPreferenceChangeListener(updateListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refreshReadyUpdate()
        pendingFocusRestore = true
        restoreFocusWhenReady()
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            apps = withContext(Dispatchers.IO) { AppRepository(applicationContext).listApps() }
        }
    }
}
