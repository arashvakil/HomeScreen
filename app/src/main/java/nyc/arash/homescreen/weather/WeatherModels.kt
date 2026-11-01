package nyc.arash.homescreen.weather

import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

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

internal data class WeatherReading(
    val locationKey: String, val celsius: Double, val code: Int, val observedAt: Long, val fetchedAt: Long,
    val timezone: String = "UTC", val hourly: List<HourlyForecast> = emptyList(), val daily: List<DailyForecast> = emptyList(), val isDay: Boolean? = null,
) {
    init {
        require(celsius.isFinite() && celsius in -100.0..70.0)
        require(observedAt > 0 && fetchedAt > 0)
        ZoneId.of(timezone)
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

internal data class HourlyForecast(
    val time: Long, val celsius: Double?, val probability: Int?, val precipitationMm: Double?,
    val snowCm: Double?, val code: Int?, val isDay: Boolean? = null,
)

internal data class DailyForecast(
    val time: Long, val lowC: Double?, val highC: Double?, val probability: Int?,
    val precipitationMm: Double?, val snowCm: Double?, val code: Int?,
)

internal data class PrecipitationOutlook(val hour: HourlyForecast? = null, val expected: Boolean = false, val dryThrough: Long? = null)

internal fun WeatherReading.precipitationOutlook(now: Long): PrecipitationOutlook {
    val upcoming = hourly.filter { it.time > now && it.time < now + 7 * 86_400_000L }.sortedBy { it.time }
    for (hour in upcoming) {
        val expected = (hour.precipitationMm ?: 0.0) >= 0.1 || (hour.snowCm ?: 0.0) >= 0.1
        if (expected || (hour.probability ?: 0) >= 40) return PrecipitationOutlook(hour, expected)
    }
    val complete = upcoming.isNotEmpty() && upcoming.first().time - 3_600_000L <= now && upcoming.all {
        it.precipitationMm != null && it.probability != null && it.snowCm != null
    } && upcoming.zipWithNext().all { (a, b) -> b.time - a.time == 3_600_000L }
    return PrecipitationOutlook(dryThrough = if (complete) upcoming.last().time else null)
}

internal fun precipitationKind(hour: HourlyForecast): String = when {
    hour.code in listOf(56, 57, 66, 67) -> "Freezing precipitation"
    (hour.snowCm ?: 0.0) >= 0.1 || hour.code in listOf(71, 73, 75, 77, 85, 86) -> "Snow"
    hour.code in listOf(95, 96, 97, 99) -> "Thunderstorms"
    (hour.precipitationMm ?: 0.0) >= 0.1 || hour.code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82) -> "Rain"
    else -> "Precipitation"
}

internal fun WeatherReading.precipitationSummary(now: Long): String {
    val outlook = precipitationOutlook(now)
    val zone = ZoneId.of(timezone)
    val hour = outlook.hour
    if (hour != null) {
        // Open-Meteo precipitation amounts/probabilities cover the preceding hour.
        val startsAt = hour.time - 3_600_000L
        val date = Instant.ofEpochMilli(startsAt).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val whenText = if (startsAt <= now) "within the hour" else {
            val day = when (date.toLocalDate()) {
                today -> "today"
                today.plusDays(1) -> "tomorrow"
                else -> date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
            }
            "$day around ${date.format(DateTimeFormatter.ofPattern("h a", Locale.getDefault()))}"
        }
        val chance = hour.probability?.let { " · $it% chance" }.orEmpty()
        return "${precipitationKind(hour)} ${if (outlook.expected) "forecast" else "possible"} $whenText$chance"
    }
    return outlook.dryThrough?.let {
        val through = Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("EEE h a", Locale.getDefault()))
        "No precipitation indicated through $through"
    } ?: "Precipitation forecast unavailable"
}

internal fun TemperatureUnit.precipitation(mm: Double?): String = mm?.let {
    if (this == TemperatureUnit.FAHRENHEIT) String.format(Locale.getDefault(), "%.2f in", it / 25.4)
    else String.format(Locale.getDefault(), "%.1f mm", it)
} ?: "—"

internal fun TemperatureUnit.snow(cm: Double?): String = cm?.let {
    if (this == TemperatureUnit.FAHRENHEIT) String.format(Locale.getDefault(), "%.1f in", it / 2.54)
    else String.format(Locale.getDefault(), "%.1f cm", it)
} ?: "—"
