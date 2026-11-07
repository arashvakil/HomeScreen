package nyc.arash.homescreen.sync

import java.net.URI

/** Credentials remain bound to the service on which this TV was linked. */
internal object SyncServiceEndpoint {
    fun choose(saved: String, configured: String, hasCredentials: Boolean): String {
        val existing = normalize(saved)
        val buildDefault = normalize(configured)
        return when {
            hasCredentials && existing.isNotEmpty() -> existing
            buildDefault.isNotEmpty() -> buildDefault
            else -> existing
        }
    }

    private fun normalize(value: String): String {
        val url = value.trim().trimEnd('/')
        if (url.isEmpty()) return ""
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "The sync service must use an HTTPS base URL without credentials, query, or fragment."
        }
        return url
    }
}
