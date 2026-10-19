package nyc.arash.homescreen.update

import android.content.Context
import android.content.SharedPreferences
import nyc.arash.homescreen.BuildConfig
import org.json.JSONObject
import java.io.File

/** Durable handoff from the background download to an explicit foreground installation. */
class UpdateReadyStore(context: Context) {
    private val appContext = context.applicationContext
    val preferences: SharedPreferences = appContext.getSharedPreferences("launcher_updates", Context.MODE_PRIVATE)

    fun save(manifest: UpdateManifest) {
        require(UpdatePolicy.isValidManifest(manifest)) { "Invalid update manifest" }
        val json = JSONObject()
            .put("versionCode", manifest.versionCode)
            .put("versionName", manifest.versionName)
            .put("apkUrl", manifest.apkUrl)
            .put("sha256", manifest.sha256)
            .put("notes", manifest.notes)
        synchronized(LOCK) {
            preferences.edit().putString(READY_KEY, json.toString()).apply()
        }
    }

    /** File hashing and APK parsing perform disk I/O; call on a background dispatcher. */
    fun loadReady(): Pair<UpdateManifest, File>? = synchronized(LOCK) { loadReadyLocked() }

    private fun loadReadyLocked(): Pair<UpdateManifest, File>? {
        val raw = preferences.getString(READY_KEY, null) ?: return null
        var staleApk: File? = null
        val ready = runCatching {
            val json = JSONObject(raw)
            val manifest = UpdateManifest(
                json.getInt("versionCode"), json.optString("versionName", ""),
                json.getString("apkUrl"), json.getString("sha256"), json.optString("notes", ""),
            )
            if (!UpdatePolicy.isValidManifest(manifest)) return@runCatching null
            val apk = File(File(appContext.filesDir, "updates"), "homescreen-${manifest.versionCode}.apk")
            if (manifest.versionCode <= BuildConfig.VERSION_CODE) {
                staleApk = apk
                return@runCatching null
            }
            if (!apk.isFile) return@runCatching null
            if (!UpdatePolicy.sha256(apk).equals(manifest.sha256, ignoreCase = true)) return@runCatching null
            if (!ApkValidator.isValid(appContext, apk, manifest.versionCode)) return@runCatching null
            manifest to apk
        }.getOrNull()
        if (ready == null) {
            // Avoid deleting a newer worker result if a concurrent save replaced what we read.
            synchronized(LOCK) {
                if (preferences.getString(READY_KEY, null) == raw) {
                    preferences.edit().remove(READY_KEY).apply()
                    staleApk?.delete()
                }
            }
        }
        return ready
    }

    companion object {
        const val READY_KEY = "ready_manifest_v1"
        private val LOCK = Any()
    }
}
