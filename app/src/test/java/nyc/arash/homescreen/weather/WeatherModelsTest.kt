package nyc.arash.homescreen.weather

import org.junit.Assert.*
import org.junit.Test

class WeatherModelsTest {
    private val location = WeatherLocation("New York", "New York", "United States", 40.71, -74.0)
    private val now = 10_000_000L
    private val reading = WeatherReading(location.key, 0.0, 0, now - 900_000, now - 60_000)

    @Test fun `units convert negative zero and warm temperatures`() {
        assertEquals("32°F", TemperatureUnit.FAHRENHEIT.format(0.0))
        assertEquals("68°F", TemperatureUnit.FAHRENHEIT.format(20.0))
        assertEquals("-40°F", TemperatureUnit.FAHRENHEIT.format(-40.0))
        assertEquals("-4°C", TemperatureUnit.CELSIUS.format(-4.2))
    }

    @Test fun `cache never labels another city or old observations as current`() {
        assertTrue(reading.usable(location, now))
        assertFalse(reading.usable(location.copy(latitude = 41.0), now))
        assertFalse(reading.usable(null, now))
        assertFalse(reading.copy(observedAt = now - 7_200_001).usable(location, now))
        assertFalse(reading.copy(observedAt = now + 300_001).usable(location, now))
    }

    @Test fun `fresh cache lasts fifteen minutes and rejects clock rollback`() {
        assertTrue(reading.fresh(now))
        assertFalse(reading.copy(fetchedAt = now - 900_000).fresh(now))
        assertFalse(reading.copy(fetchedAt = now + 1).fresh(now))
    }

    @Test fun `weather codes include frozen rain storms and unknown values`() {
        assertEquals("Freezing rain", weatherDescription(67))
        assertEquals("Thunderstorms with hail", weatherDescription(99))
        assertEquals("Snow showers", weatherDescription(86))
        assertEquals("Conditions unavailable", weatherDescription(-1))
    }

    @Test fun `city label includes disambiguating region without duplicates`() {
        assertEquals("New York, United States", location.label)
        assertEquals("Springfield, Illinois, United States", location.copy(name = "Springfield", region = "Illinois").label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid city coordinates are rejected`() {
        location.copy(latitude = Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid temperatures are rejected`() {
        reading.copy(celsius = Double.NaN)
    }

    @Test fun `next precipitation distinguishes a chance from measurable snowfall`() {
        val hour = HourlyForecast(now + 3_599_000, 2.0, 45, 0.0, 0.0, 61)
        val possible = reading.copy(hourly = listOf(hour)).precipitationOutlook(now)
        assertEquals(hour, possible.hour)
        assertFalse(possible.expected)
        val snow = hour.copy(probability = 20, precipitationMm = 0.1, snowCm = 0.2, code = 73)
        val expected = reading.copy(hourly = listOf(snow)).precipitationOutlook(now)
        assertTrue(expected.expected)
        assertEquals("Snow", precipitationKind(expected.hour!!))
        assertEquals("Freezing precipitation", precipitationKind(snow.copy(code = 67)))
    }

    @Test fun `missing hourly data never becomes a confident dry forecast`() {
        val hour = HourlyForecast(now + 3_599_000, 2.0, 0, 0.0, 0.0, 0)
        assertNotNull(reading.copy(hourly = listOf(hour)).precipitationOutlook(now).dryThrough)
        assertNull(reading.copy(hourly = listOf(hour.copy(probability = null))).precipitationOutlook(now).dryThrough)
        assertNull(reading.copy(hourly = emptyList()).precipitationOutlook(now).dryThrough)
        assertNull(reading.copy(hourly = listOf(hour, hour.copy(time = now + 7_200_000))).precipitationOutlook(now).dryThrough)
    }

    @Test fun `summary uses city timezone and omits past events`() {
        val event = HourlyForecast(now + 3_599_000, 2.0, 80, 0.5, 0.0, 61)
        val value = reading.copy(timezone = "America/New_York", hourly = listOf(event))
        assertTrue(value.precipitationSummary(now).contains("Rain expected within the hour"))
        assertNull(value.precipitationOutlook(now + 3_600_000).hour)
        assertEquals("1.00 in", TemperatureUnit.FAHRENHEIT.precipitation(25.4))
        assertEquals("1.0 in", TemperatureUnit.FAHRENHEIT.snow(2.54))
    }
}
