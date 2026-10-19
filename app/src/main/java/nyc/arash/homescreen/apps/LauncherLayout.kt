package nyc.arash.homescreen.apps

import android.content.Context

/** Device-local ordering. Missing packages retain their position if installed again later. */
class LauncherLayout(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("launcher_layout", Context.MODE_PRIVATE)

    fun favoriteIds(): List<String> {
        if (!preferences.contains(KEY_FAVORITES)) return DEFAULT_FAVORITES.toList()
        return normalizeFavorites(preferences.getString(KEY_FAVORITES, "").orEmpty().split('\n'))
    }

    fun saveFavorites(ids: List<String>) {
        preferences.edit().putString(KEY_FAVORITES, normalizeFavorites(ids).joinToString("\n")).apply()
    }

    companion object {
        private const val KEY_FAVORITES = "favorite_packages_v1"

        val DEFAULT_FAVORITES: List<String> = listOf(
            "com.plexapp.android",
            "com.spotify.tv.android",
            "com.amazon.firetv.youtube",
            "com.amazon.firetv.youtube.tv",
            "com.bamnetworks.mobile.android.gameday.atbat",
            "org.smarttube.stable",
        )

        /** Keep the first occurrence; never discard an ID merely because it is not installed. */
        fun normalizeFavorites(ids: List<String>): List<String> = ids
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .distinct()

        /** Move an item without changing any other item's relative order. Invalid moves are ignored. */
        fun moveFavorite(ids: List<String>, fromIndex: Int, toIndex: Int): List<String> {
            val ordered = normalizeFavorites(ids)
            if (fromIndex !in ordered.indices || toIndex !in ordered.indices || fromIndex == toIndex) {
                return ordered
            }
            return ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }
}
