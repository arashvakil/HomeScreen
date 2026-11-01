package nyc.arash.homescreen.weather

import android.content.Context
import org.json.JSONObject

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
        WeatherReading(json.getString("locationKey"), json.getDouble("celsius"), json.getInt("code"), json.getLong("observedAt"), json.getLong("fetchedAt"))
    }.getOrNull()

    fun cache(reading: WeatherReading) {
        preferences.edit().putString("reading", JSONObject().put("locationKey", reading.locationKey)
            .put("celsius", reading.celsius).put("code", reading.code).put("observedAt", reading.observedAt)
            .put("fetchedAt", reading.fetchedAt).toString()).apply()
    }
}
