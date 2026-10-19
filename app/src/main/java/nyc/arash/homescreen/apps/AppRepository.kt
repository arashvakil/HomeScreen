package nyc.arash.homescreen.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.Locale

data class TvApp(
    val id: String,           // package name
    val name: String,         // display label
    val banner: Drawable?,    // wide banner (preferred for TV)
    val icon: Drawable,       // fallback square icon
    val launchIntent: Intent,
    val isUtility: Boolean = AppRepository.isUtility(id),
) {
    val hasBanner: Boolean get() = banner != null
}

class AppRepository(private val context: Context) {

    /** Enumerate installed leanback (TV) apps, plus phone apps as a fallback. */
    fun listApps(): List<TvApp> {
        val pm = context.packageManager
        val seen = mutableSetOf<String>()
        val result = mutableListOf<TvApp>()

        fun addFromIntent(category: String) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val infos = pm.queryIntentActivities(intent, 0)
            for (info in infos) {
                val pkg = info.activityInfo.packageName ?: continue
                if (pkg == context.packageName) continue
                if (!seen.add(pkg)) continue
                val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
                    ?: pm.getLaunchIntentForPackage(pkg)
                    ?: continue
                val appInfo = info.activityInfo.applicationInfo
                val label = info.loadLabel(pm)?.toString()
                    ?: appInfo.loadLabel(pm).toString()
                val banner = info.activityInfo.loadBanner(pm) ?: appInfo.loadBanner(pm)
                val icon = appInfo.loadIcon(pm) ?: appInfo.loadLogo(pm) ?: continue
                result += TvApp(
                    id = pkg,
                    name = label,
                    banner = banner,
                    icon = icon,
                    launchIntent = launch.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                )
            }
        }

        addFromIntent(Intent.CATEGORY_LEANBACK_LAUNCHER)
        addFromIntent(Intent.CATEGORY_LAUNCHER) // pick up phone apps that work on Fire TV (e.g. Plexamp)

        // Stable, slightly opinionated sort: banner apps first (look better as tiles), then alpha.
        return result.sortedWith(compareByDescending<TvApp> { it.hasBanner }.thenBy { it.name.lowercase(Locale.ROOT) })
    }
    companion object {
        /** Utilities remain available under All Apps instead of taking streaming shelf positions. */
        fun isUtility(packageName: String): Boolean {
            val id = packageName.lowercase(Locale.ROOT)
            return id in UTILITY_PACKAGES || UTILITY_PREFIXES.any(id::startsWith) ||
                (id.startsWith("com.amazon.") && AMAZON_UTILITY_NAMES.any(id::contains))
        }

        private val UTILITY_PACKAGES = setOf(
            "tv.dumbtv.home",
            "io.github.toolicious.homeonfire",
            "nyc.arash.homescreen",
            "nyc.arash.homescreen.debug",
            "io.privado.android",
            "com.windscribe.vpn",
            "com.amazon.smarthomemapviewapp",
            "com.amazon.shoptv.firetv.client",
            "com.amazon.whasettings",
            "com.amazon.tv.launcher",
            "com.amazon.tv.settings",
            "com.amazon.tv.nimh",
            "com.amazon.venezia",
            "com.esaba.downloader",
            "com.sween.laancher",
            "com.markus.firetools",
            "com.toolicious.homeonfire",
            "com.toolicious.home.on.fire",
            "com.humblemuzzu.dumbtv",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.settings",
        )
        private val AMAZON_UTILITY_NAMES = listOf(
            "minitv", "gamehub", "spiderpork", "earlyaccess", "troubleshoot", "profilepicker", "screensaver", "ambient",
        )
        private val UTILITY_PREFIXES = listOf(
            "com.wolf.",
            "com.wolfapps.",
            "com.hal9000.",
            "com.toolicious.",
            "com.humblemuzzu.",
            "com.nordvpn.",
            "com.expressvpn.",
            "com.surfshark.",
            "com.privateinternetaccess.",
            "de.blinkt.openvpn",
            "net.openvpn.",
            "com.wireguard.",
        )
    }
}
