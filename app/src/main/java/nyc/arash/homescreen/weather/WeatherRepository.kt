package nyc.arash.homescreen.weather

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public, non-commercial Open-Meteo endpoints; no API key or device location permission. */
internal class WeatherRepository {
    private val http = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS)
        .followSslRedirects(false).build()

    fun search(query: String): List<WeatherLocation> {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query.trim()).addQueryParameter("count", "5")
            .addQueryParameter("language", "en").addQueryParameter("format", "json").build()
        val results = request(url).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            runCatching {
                val item = results.getJSONObject(index)
                WeatherLocation(item.getString("name"), item.optString("admin1"), item.optString("country"), item.getDouble("latitude"), item.getDouble("longitude"))
            }.getOrNull()
        }.distinctBy { it.key }
    }

    fun current(location: WeatherLocation): WeatherReading {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", location.latitude.toString())
            .addQueryParameter("longitude", location.longitude.toString())
            .addQueryParameter("current", "temperature_2m,weather_code")
            .addQueryParameter("temperature_unit", "celsius").addQueryParameter("timeformat", "unixtime").build()
        val json = request(url)
        check(json.getJSONObject("current_units").getString("temperature_2m") == "°C")
        val current = json.getJSONObject("current")
        val now = System.currentTimeMillis()
        return WeatherReading(location.key, current.getDouble("temperature_2m"), current.getInt("weather_code"), current.getLong("time") * 1000, now)
            .also { check(it.usable(location, now)) { "Weather is out of date" } }
    }

    private fun request(url: HttpUrl): JSONObject {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful || !response.request.url.isHttps) throw IOException("Weather service unavailable")
            val body = response.body ?: throw IOException("Empty weather response")
            // City searches can include many postal codes, but remain bounded.
            val bytes = body.byteStream().use { it.readBytesLimited(256 * 1024) }
            return JSONObject(bytes.toString(Charsets.UTF_8)).also {
                if (it.optBoolean("error")) throw IOException("Weather request rejected")
            }
        }
    }

    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            if (output.size() + count > limit) throw IOException("Weather response too large")
            output.write(buffer, 0, count)
        }
    }
}
