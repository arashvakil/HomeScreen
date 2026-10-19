package nyc.arash.homescreen.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import java.io.File

/**
 * Triggers a PackageInstaller session for the downloaded APK. The user sees
 * the standard Android install confirmation when required.
 * REQUEST_INSTALL_PACKAGES requests this capability; Fire TV gates it through
 * Developer options → "Install unknown apps". No silent-install permission is assumed.
 */
object UpdateInstaller {
    private const val ACTION_COMMIT = "nyc.arash.homescreen.INSTALL_COMMIT"
    internal const val SESSION_KEY = "pending_install_session"

    fun install(context: Context, apk: File, manifest: UpdateManifest) {
        require(UpdatePolicy.isValidManifest(manifest)) { "Invalid update manifest" }
        require(UpdatePolicy.sha256(apk).equals(manifest.sha256, ignoreCase = true)) { "Update checksum mismatch" }
        require(ApkValidator.isValid(context, apk, manifest.versionCode)) { "Update identity, version or signature mismatch" }
        val pi = context.packageManager.packageInstaller
        val preferences = UpdateReadyStore(context).preferences
        val previousSession = preferences.getInt(SESSION_KEY, -1)
        if (previousSession >= 0) runCatching { pi.abandonSession(previousSession) }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)

        val sessionId = pi.createSession(params)
        preferences.edit().putInt(SESSION_KEY, sessionId).apply()
        try {
            pi.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(ACTION_COMMIT).setComponent(
                    ComponentName(context, UpdateInstallReceiver::class.java)
                )
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pending = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pending.intentSender)
            }
        } catch (failure: Exception) {
            runCatching { pi.abandonSession(sessionId) }
            if (preferences.getInt(SESSION_KEY, -1) == sessionId) {
                preferences.edit().remove(SESSION_KEY).apply()
            }
            throw failure
        }
    }
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val preferences = UpdateReadyStore(context).preferences
            if (sessionId >= 0 && preferences.getInt(UpdateInstaller.SESSION_KEY, -1) == sessionId) {
                preferences.edit().remove(UpdateInstaller.SESSION_KEY).apply()
            }
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // The user requested installation while Home was visible. If they left meanwhile,
            // Android may defer/block this activity; ready metadata remains for the next click.
            runCatching { context.startActivity(confirm) }
        }
    }
}
