package nyc.arash.homescreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import nyc.arash.homescreen.weather.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun WeatherForecastDialog(weather: WeatherState, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val now = currentTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val reading = weather.reading?.takeIf { it.usable(weather.settings.location, now) }
    val unit = weather.settings.unit
    LaunchedEffect(Unit) { withFrameNanos { }; first.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.width(800.dp).heightIn(max = 470.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF111D32))
            .border(.5.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(weather.settings.location?.label ?: "Your weather", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NavChip("Weather Settings", onClick = onSettings, modifier = Modifier.weight(1f).focusRequester(first))
                NavChip("Done", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
            if (reading == null) {
                Text(when {
                    weather.settings.location == null -> "Choose a city in Weather Settings to see its forecast."
                    weather.failed -> "Weather is unavailable. Check your connection and try again."
                    else -> "Loading your forecast…"
                }, color = Color(0xFFA6B6D0), fontSize = 15.sp)
            } else {
                val zone = ZoneId.of(reading.timezone)
                fun date(time: Long, pattern: String): String = Instant.ofEpochMilli(time).atZone(zone).format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
                Text("${unit.format(reading.celsius)} · ${weatherDescription(reading.code)}", color = Color.White, fontSize = 21.sp)
                Text(reading.precipitationSummary(now), color = Color(0xFFD3E3FF), fontSize = 15.sp)
                Text("${if (weather.failed) "Update unavailable · " else ""}As of ${date(reading.observedAt, "EEE h:mm a")} · Times local to this city", color = Color(0xFF899BB9), fontSize = 11.sp)
                Text("NEXT 24 HOURS", color = Color(0xFF9CACCA), fontSize = 11.sp, letterSpacing = 1.5.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(2.dp)) {
                    items(reading.hourly.filter { it.time + 3_600_000L > now }.take(24), key = { it.time }) { hour ->
                        ForecastCard(Modifier.width(104.dp)) {
                            Text(if (hour.time <= now) "Now" else date(hour.time, "EEE h a"), color = Color(0xFFA6B6D0), fontSize = 11.sp)
                            Text(hour.celsius?.let { unit.format(it) } ?: "—", color = Color.White, fontSize = 20.sp)
                            Text(weatherDescription(hour.code ?: -1), color = Color(0xFFA6B6D0), fontSize = 10.sp, maxLines = 2, modifier = Modifier.height(30.dp))
                            Text(hour.probability?.let { "$it% precip." } ?: "Chance unknown", color = Color(0xFFD3E3FF), fontSize = 10.sp)
                            Text(if ((hour.snowCm ?: 0.0) > 0) "Snow ${unit.snow(hour.snowCm)}" else unit.precipitation(hour.precipitationMm), color = Color(0xFFA6B6D0), fontSize = 10.sp)
                        }
                    }
                }
                Text("7-DAY OUTLOOK", color = Color(0xFF9CACCA), fontSize = 11.sp, letterSpacing = 1.5.sp)
                reading.daily.forEach { day ->
                    ForecastCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(date(day.time, "EEE, MMM d"), color = Color.White, fontSize = 13.sp)
                            Text("${day.highC?.let { unit.format(it) } ?: "—"} / ${day.lowC?.let { unit.format(it) } ?: "—"}", color = Color.White, fontSize = 13.sp)
                        }
                        Text("${weatherDescription(day.code ?: -1)} · ${day.probability?.let { "$it% chance" } ?: "Chance unknown"} · ${unit.precipitation(day.precipitationMm)} total${if ((day.snowCm ?: 0.0) > 0) " · Snow ${unit.snow(day.snowCm)}" else ""}", color = Color(0xFFA6B6D0), fontSize = 11.sp)
                    }
                }
                Text("Possible: at least 40% chance. Expected: forecast measurable rain or snow. Forecasts can change; daily totals include all precipitation.", color = Color(0xFF899BB9), fontSize = 10.sp)
            }
            SourceLink("Weather by Open-Meteo (CC BY 4.0)", "https://open-meteo.com/")
        }
    }
}

/** Focusable forecast cards let a TV remote scroll every hour/day into view. */
@Composable
private fun ForecastCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(modifier.navigationSound().onFocusChanged { focused = it.isFocused }
        .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0xFF344663), RoundedCornerShape(12.dp))
        .focusable().background(Color(0xFF18263C), RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
}
