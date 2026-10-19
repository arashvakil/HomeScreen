package nyc.arash.homescreen.update

import android.content.Context
import nyc.arash.homescreen.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val notes: String,
)

/** Synchronous network/file I/O: the worker calls this on Dispatchers.IO. */
class UpdateChecker(
    private val context: Context,
    private val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.MINUTES)
        .followSslRedirects(false) // Never follow HTTPS to HTTP redirects.
        .build()

    /** Invalid configuration, manifests and APKs are ignored; transport failures can be retried. */
    fun checkForUpdate(): Pair<UpdateManifest, File>? {
        if (!UpdatePolicy.isSecureUrl(manifestUrl)) return null
        val manifest = fetchManifest() ?: return null
        if (manifest.versionCode <= BuildConfig.VERSION_CODE) return null

        val target = File(updatesDir(), "homescreen-${manifest.versionCode}.apk")
        if (target.exists()) {
            if (isVerified(target, manifest)) return manifest to target
            if (!target.delete()) throw IOException("Cannot replace cached update")
        }
        val partial = File(target.parentFile, "${target.name}.part")
        try {
            downloadTo(manifest.apkUrl, partial)
            if (!isVerified(partial, manifest)) return null
            if (!partial.renameTo(target)) throw IOException("Cannot finalize verified update")
            return manifest to target
        } finally {
            partial.delete()
        }
    }

    private fun isVerified(apk: File, manifest: UpdateManifest): Boolean =
        UpdatePolicy.sha256(apk).equals(manifest.sha256, ignoreCase = true) &&
            ApkValidator.isValid(context, apk, manifest.versionCode)

    private fun fetchManifest(): UpdateManifest? {
        val request = Request.Builder().url(manifestUrl).build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful || !response.request.url.isHttps) return null
            val body = response.body ?: return null
            if (body.contentLength() > MAX_MANIFEST_BYTES) return null
            val text = body.byteStream().use { input ->
                val bytes = input.readBytesBounded(MAX_MANIFEST_BYTES) ?: return null
                bytes.toString(Charsets.UTF_8)
            }
            return try {
                val json = JSONObject(text)
                UpdateManifest(
                    versionCode = json.getInt("versionCode"),
                    versionName = json.optString("versionName", ""),
                    apkUrl = json.getString("apkUrl"),
                    sha256 = json.getString("sha256"),
                    notes = json.optString("notes", ""),
                ).takeIf(UpdatePolicy::isValidManifest)
            } catch (_: JSONException) {
                null
            }
        }
    }

    private fun downloadTo(url: String, destination: File) {
        require(UpdatePolicy.isSecureUrl(url))
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful || !response.request.url.isHttps) throw IOException("Update download failed")
            val body = response.body ?: throw IOException("Empty update response")
            if (body.contentLength() > MAX_APK_BYTES) throw IOException("Update exceeds size limit")
            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("Update exceeds size limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
    }

    private fun updatesDir(): File = File(context.filesDir, "updates").apply {
        if (!isDirectory && !mkdirs()) throw IOException("Cannot create update directory")
    }

    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray? {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) return null
            output.write(buffer, 0, count)
        }
    }

    companion object {
        private const val MAX_MANIFEST_BYTES = 1024 * 1024
        private const val MAX_APK_BYTES = 250L * 1024 * 1024
    }
}
