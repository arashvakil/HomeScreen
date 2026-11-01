package nyc.arash.homescreen.weather

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray

internal class WeatherStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun settings(): WeatherSettings {
        val location = runCatching {
            val json = JSONObject(preferences.getString("location", "").orEmpty())
            WeatherLocation(json.getString("name"), json.optString("region"), json.optString("country"), json.getDouble("latitude"), json.getDouble("longitude"))
        }.getOrNull()
        val unit = runCatching { TemperatureUnit.valueOf(preferences.getString("unit", "").orEmpty()) }.getOrDefault(TemperatureUnit.FAHRENHEIT)
        return WeatherSettings(location, unit)
    }

    fun save(settings: WeatherSettings) {
        val location = settings.location?.let {
            JSONObject().put("name", it.name).put("region", it.region).put("country", it.country)
                .put("latitude", it.latitude).put("longitude", it.longitude).toString()
        }
        preferences.edit().putString("location", location).putString("unit", settings.unit.name).apply()
    }

    fun cached(): WeatherReading? = runCatching {
        val json = JSONObject(preferences.getString("reading", "").orEmpty())
        check(json.getInt("schema") == 2)
        val hours = json.getJSONArray("hourly")
        val days = json.getJSONArray("daily")
        WeatherReading(json.getString("locationKey"), json.getDouble("celsius"), json.getInt("code"), json.getLong("observedAt"), json.getLong("fetchedAt"),
            json.getString("timezone"), (0 until hours.length()).map { index ->
                val h = hours.getJSONObject(index)
                HourlyForecast(h.getLong("time"), h.number("celsius"), h.number("probability")?.toInt(), h.number("precipitationMm"), h.number("snowCm"), h.number("code")?.toInt(), if (h.isNull("isDay")) null else h.getBoolean("isDay"))
            }, (0 until days.length()).map { index ->
                val d = days.getJSONObject(index)
                DailyForecast(d.getLong("time"), d.number("lowC"), d.number("highC"), d.number("probability")?.toInt(), d.number("precipitationMm"), d.number("snowCm"), d.number("code")?.toInt())
            }, if (json.isNull("isDay")) null else json.getBoolean("isDay"))
    }.getOrNull()

    private fun JSONObject.number(key: String): Double? = optDouble(key).takeIf { it.isFinite() }

    fun cache(reading: WeatherReading) {
        val hours = JSONArray()
        reading.hourly.forEach { h -> hours.put(JSONObject().put("time", h.time).put("celsius", h.celsius)
            .put("probability", h.probability).put("precipitationMm", h.precipitationMm).put("snowCm", h.snowCm).put("code", h.code).put("isDay", h.isDay)) }
        val days = JSONArray()
        reading.daily.forEach { d -> days.put(JSONObject().put("time", d.time).put("lowC", d.lowC).put("highC", d.highC)
            .put("probability", d.probability).put("precipitationMm", d.precipitationMm).put("snowCm", d.snowCm).put("code", d.code)) }
        preferences.edit().putString("reading", JSONObject().put("schema", 2).put("isDay", reading.isDay).put("locationKey", reading.locationKey)
            .put("celsius", reading.celsius).put("code", reading.code).put("observedAt", reading.observedAt)
            .put("fetchedAt", reading.fetchedAt).put("timezone", reading.timezone).put("hourly", hours).put("daily", days).toString()).apply()
    }
}
