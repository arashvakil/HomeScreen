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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun WeatherForecastDialog(weather: WeatherState, onSettings: () -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    val now = currentTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val reading = weather.reading?.takeIf { it.usable(weather.settings.location, now) }
    val unit = weather.settings.unit
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
        LaunchedEffect(Unit) {
            withFrameNanos { }
            inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            first.requestFocus()
        }
        Column(Modifier.width(800.dp).heightIn(max = 470.dp).clip(RoundedCornerShape(24.dp)).background(WeatherPanelBrush)
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
            .verticalScroll(rememberScrollState()).padding(26.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(weather.settings.location?.name ?: "Your weather", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                    Text(weather.settings.location?.label ?: "A forecast for your corner of the world.", color = Color(0xFF8EA8BD), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                NavChip("Location & units", onClick = onSettings, modifier = Modifier.focusRequester(first))
                NavChip("Done", onClick = onDismiss)
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
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    WeatherGlyph(reading.code, Modifier.size(60.dp))
                    Text(unit.format(reading.celsius), color = Color.White, fontSize = 53.sp, fontWeight = FontWeight.Light, letterSpacing = (-2).sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(weatherDescription(reading.code), color = Color(0xFFE3EEF7), fontSize = 17.sp)
                        Text("${if (weather.failed) "Update unavailable · " else ""}As of ${date(reading.observedAt, "h:mm a")}", color = Color(0xFF8EA8BD), fontSize = 11.sp)
                    }
                    reading.daily.firstOrNull()?.let { today ->
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("TODAY", color = Color(0xFF8EA8BD), fontSize = 9.sp, letterSpacing = 1.4.sp)
                            Text("${today.highC?.let { unit.format(it) } ?: "—"}  /  ${today.lowC?.let { unit.format(it) } ?: "—"}", color = Color(0xFFDAE6F2), fontSize = 15.sp)
                        }
                    }
                }
                WeatherOutlook(reading.precipitationSummary(now), Modifier.fillMaxWidth())
                Text("THE NEXT 24 HOURS", color = Color(0xFF9CACCA), fontSize = 10.sp, letterSpacing = 1.6.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(2.dp)) {
                    items(reading.hourly.filter { it.time > now }.take(24), key = { it.time }) { hour ->
                        ForecastCard(Modifier.width(93.dp)) {
                            Text(date(hour.time, "EEE h a"), color = Color(0xFFA6B6D0), fontSize = 11.sp)
                            WeatherGlyph(hour.code ?: -1, Modifier.size(26.dp).padding(vertical = 2.dp))
                            Text(hour.celsius?.let { unit.format(it) } ?: "—", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Light)
                            Text(hour.probability?.let { "$it% precip." } ?: "Chance unknown", color = Color(0xFFD3E3FF), fontSize = 10.sp)
                            Text(if ((hour.snowCm ?: 0.0) > 0) "Snow ${unit.snow(hour.snowCm)}" else unit.precipitation(hour.precipitationMm), color = Color(0xFFA6B6D0), fontSize = 10.sp)
                        }
                    }
                }
                Text("THE WEEK AHEAD", color = Color(0xFF9CACCA), fontSize = 10.sp, letterSpacing = 1.6.sp)
                reading.daily.forEach { day ->
                    ForecastCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(date(day.time, "EEE, MMM d"), color = Color(0xFFE9F0F7), fontSize = 12.sp, modifier = Modifier.width(77.dp))
                            WeatherGlyph(day.code ?: -1, Modifier.size(23.dp))
                            Text(weatherDescription(day.code ?: -1), color = Color(0xFF9FB5C9), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(day.probability?.let { "$it%" } ?: "—", color = WeatherAccent, fontSize = 12.sp, modifier = Modifier.width(40.dp))
                            Text(if ((day.snowCm ?: 0.0) > 0) "Snow ${unit.snow(day.snowCm)}" else unit.precipitation(day.precipitationMm), color = Color(0xFF8EA8BD), fontSize = 11.sp, modifier = Modifier.width(70.dp))
                            Text(day.lowC?.let { unit.format(it) } ?: "—", color = Color(0xFF8399AE), fontSize = 12.sp)
                            Box(Modifier.width(60.dp).height(4.dp).clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF83AEC7), Color(0xFFE5C69B)))))
                            Text(day.highC?.let { unit.format(it) } ?: "—", color = Color(0xFFE9F0F7), fontSize = 12.sp)
                        }
                    }
                }
                Text("Times are local to this city. Percentages show precipitation chance; amounts include rain and snow. Hourly totals cover the hour ending at the shown time. Forecasts can change.", color = Color(0xFF899BB9), fontSize = 10.sp)
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
        .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFD5EBFA) else Color(0x16FFFFFF), RoundedCornerShape(16.dp))
        .focusable().background(if (focused) Color(0xFF263F58) else Color(0x09FFFFFF), RoundedCornerShape(16.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp), content = content)
}
