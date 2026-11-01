package nyc.arash.homescreen.weather

import kotlin.math.roundToInt

internal data class WeatherLocation(val name: String, val region: String, val country: String, val latitude: Double, val longitude: Double) {
    init {
        require(name.isNotBlank() && latitude.isFinite() && longitude.isFinite())
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0)
    }
    val label: String get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
    val key: String get() = "$latitude,$longitude"
}

internal enum class TemperatureUnit(val label: String) {
    FAHRENHEIT("°F"), CELSIUS("°C");
    fun format(celsius: Double): String = "${(if (this == FAHRENHEIT) celsius * 9 / 5 + 32 else celsius).roundToInt()}$label"
}

internal data class WeatherSettings(val location: WeatherLocation? = null, val unit: TemperatureUnit = TemperatureUnit.FAHRENHEIT)

internal data class WeatherReading(val locationKey: String, val celsius: Double, val code: Int, val observedAt: Long, val fetchedAt: Long) {
    init {
        require(celsius.isFinite() && celsius in -100.0..70.0)
        require(observedAt > 0 && fetchedAt > 0)
    }
    fun usable(location: WeatherLocation?, now: Long): Boolean = location?.key == locationKey && now - observedAt in -300_000L..7_200_000L
    fun fresh(now: Long): Boolean = now - fetchedAt in 0L until 900_000L
}

internal fun weatherDescription(code: Int): String = when (code) {
    0 -> "Clear skies"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73, 77 -> "Snow"
    75 -> "Heavy snow"
    80, 81, 82 -> "Rain showers"
    85, 86 -> "Snow showers"
    95, 97 -> "Thunderstorms"
    96, 99 -> "Thunderstorms with hail"
    else -> "Conditions unavailable"
}
