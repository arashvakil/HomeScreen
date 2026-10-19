package nyc.arash.homescreen.update

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.security.MessageDigest

/** Pure checks shared by download and installation validation. */
internal object UpdatePolicy {
    fun isSecureUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.username.isEmpty() && url.password.isEmpty()
    }

    fun isValidManifest(manifest: UpdateManifest): Boolean =
        manifest.versionCode > 0 && isSecureUrl(manifest.apkUrl) &&
            manifest.sha256.matches(Regex("[a-fA-F0-9]{64}"))

    fun acceptsApk(
        installedPackage: String,
        installedVersion: Long,
        installedSigners: Set<String>,
        candidatePackage: String,
        candidateVersion: Long,
        candidateSigners: Set<String>,
        expectedVersion: Int? = null,
    ): Boolean = candidatePackage == installedPackage && candidateVersion > installedVersion &&
        (expectedVersion == null || candidateVersion == expectedVersion.toLong()) &&
        installedSigners.isNotEmpty() && installedSigners == candidateSigners

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
