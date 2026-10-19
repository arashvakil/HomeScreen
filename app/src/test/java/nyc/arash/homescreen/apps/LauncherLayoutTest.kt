package nyc.arash.homescreen.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLayoutTest {
    @Test fun defaultsMatchRequestedFavoriteShelf() {
        assertEquals(listOf(
            "com.plexapp.android", "com.spotify.tv.android", "com.amazon.firetv.youtube",
            "com.amazon.firetv.youtube.tv", "com.bamnetworks.mobile.android.gameday.atbat", "org.smarttube.stable",
        ), LauncherLayout.DEFAULT_FAVORITES)
    }

    @Test fun normalizationPreservesOrderAndUnavailablePackages() {
        assertEquals(listOf("plex", "uninstalled.app", "spotify"),
            LauncherLayout.normalizeFavorites(listOf(" plex ", "", "uninstalled.app", "plex", "spotify", "  ")))
    }

    @Test fun moveRetainsOtherAppsInRelativeOrder() {
        assertEquals(listOf("spotify", "youtube", "plex", "mlb"),
            LauncherLayout.moveFavorite(listOf("plex", "spotify", "youtube", "mlb"), 0, 2))
        assertEquals(listOf("mlb", "plex", "spotify", "youtube"),
            LauncherLayout.moveFavorite(listOf("plex", "spotify", "youtube", "mlb"), 3, 0))
    }

    @Test fun invalidMovesLeaveOrderIntact() {
        val ids = listOf("plex", "spotify")
        assertEquals(ids, LauncherLayout.moveFavorite(ids, -1, 0))
        assertEquals(ids, LauncherLayout.moveFavorite(ids, 0, 2))
        assertEquals(ids, LauncherLayout.moveFavorite(ids, 1, 1))
        assertEquals(emptyList<String>(), LauncherLayout.moveFavorite(emptyList(), 0, 0))
    }

    @Test fun helpersAndVpnsAreUtilitiesButStreamingAppsAreNot() {
        listOf("tv.dumbtv.home", "io.github.toolicious.homeonfire", "nyc.arash.homescreen.debug",
            "com.nordvpn.android", "io.privado.android", "com.windscribe.vpn", "com.esaba.downloader",
            "com.amazon.whasettings", "com.amazon.tv.ambient").forEach { assertTrue(it, AppRepository.isUtility(it)) }
        (LauncherLayout.DEFAULT_FAVORITES + listOf("tv.twitch.android.app", "com.amazon.avod", "com.amazon.bueller.music"))
            .forEach { assertFalse(it, AppRepository.isUtility(it)) }
    }
}
