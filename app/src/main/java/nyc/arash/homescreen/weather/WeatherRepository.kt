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
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("hourly", "temperature_2m,precipitation_probability,precipitation,snowfall,weather_code,is_day")
            .addQueryParameter("daily", "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum,snowfall_sum")
            .addQueryParameter("forecast_days", "7").addQueryParameter("timezone", "auto")
            .addQueryParameter("temperature_unit", "celsius").addQueryParameter("timeformat", "unixtime").build()
        val json = request(url)
        check(json.getJSONObject("current_units").getString("temperature_2m") == "°C")
        val current = json.getJSONObject("current")
        val now = System.currentTimeMillis()
        val hourly = json.getJSONObject("hourly")
        val daily = json.getJSONObject("daily")
        val hours = (0 until hourly.getJSONArray("time").length()).map { index ->
            HourlyForecast(hourly.getJSONArray("time").getLong(index) * 1000, hourly.number("temperature_2m", index),
                hourly.number("precipitation_probability", index)?.toInt()?.takeIf { it in 0..100 },
                hourly.number("precipitation", index)?.takeIf { it >= 0 }, hourly.number("snowfall", index)?.takeIf { it >= 0 },
                hourly.number("weather_code", index)?.toInt(), hourly.number("is_day", index)?.let { it == 1.0 })
        }
        val days = (0 until daily.getJSONArray("time").length()).map { index ->
            DailyForecast(daily.getJSONArray("time").getLong(index) * 1000, daily.number("temperature_2m_min", index), daily.number("temperature_2m_max", index),
                daily.number("precipitation_probability_max", index)?.toInt()?.takeIf { it in 0..100 },
                daily.number("precipitation_sum", index)?.takeIf { it >= 0 }, daily.number("snowfall_sum", index)?.takeIf { it >= 0 },
                daily.number("weather_code", index)?.toInt())
        }
        return WeatherReading(location.key, current.getDouble("temperature_2m"), current.getInt("weather_code"), current.getLong("time") * 1000, now,
            json.getString("timezone"), hours, days, current.getInt("is_day") == 1)
            .also { check(it.usable(location, now)) { "Weather is out of date" } }
    }

    private fun JSONObject.number(key: String, index: Int): Double? = optJSONArray(key)?.optDouble(index)?.takeIf { it.isFinite() }

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
