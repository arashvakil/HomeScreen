package nyc.arash.homescreen.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Place
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nyc.arash.homescreen.weather.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WeatherSettingsDialog(weather: WeatherState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherLocation>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    val searchButton = remember { FocusRequester() }
    val firstResult = remember { FocusRequester() }
    var editing by remember { mutableStateOf(true) }
    val firstKey = remember { FocusRequester() }
    val editButton = remember { FocusRequester() }
    var searchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun leaveSearch() {
        searchJob?.cancel()
        busy = false
        searching = false
    }

    fun search() {
        if (busy) return
        if (query.trim().length < 2) {
            message = "Enter at least two characters to search."
            return
        }
        editing = false
        busy = true
        results = emptyList()
        message = null
        val text = query.trim()
        searchJob = scope.launch {
            try {
                results = withContext(Dispatchers.IO) { weather.repository.search(text) }
                if (results.isEmpty()) message = "No cities found. Try a nearby city or postal code."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "Could not search. Check your connection and try again."
            } finally {
                busy = false
            }
        }
    }

    Dialog(onDismissRequest = { if (searching) leaveSearch() else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val inputMode = LocalInputModeManager.current
        LaunchedEffect(searching, editing) {
            withFrameNanos { }
            inputMode.requestInputMode(InputMode.Keyboard)
            when {
                !searching -> first.requestFocus()
                editing -> firstKey.requestFocus()
                else -> editButton.requestFocus()
            }
        }
        LaunchedEffect(results) {
            if (results.isNotEmpty()) {
                withFrameNanos { }
                inputMode.requestInputMode(InputMode.Keyboard)
                firstResult.requestFocus()
            }
        }
        Column(
            Modifier.width(620.dp).heightIn(max = 470.dp).clip(RoundedCornerShape(24.dp))
                .background(WeatherPanelBrush).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(26.dp)
                .onPreviewKeyEvent { event ->
                    if (!searching || !editing || event.type != KeyEventType.KeyDown) false else when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_DEL -> { query = CityQuery.delete(query); true }
                        AndroidKeyEvent.KEYCODE_ENTER -> { search(); true }
                        else -> {
                            val character = event.nativeKeyEvent.unicodeChar
                            if (character >= 32 && Character.isValidCodePoint(character)) {
                                query = CityQuery.append(query, String(Character.toChars(character))); true
                            } else false
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(if (searching && editing) 7.dp else 10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(if (searching) "Choose your city" else "Home Settings", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Medium)
                if (searching) NavChip("Use New York", onClick = {
                    weather.configure(weather.settings.copy(location = WeatherLocation("New York", "New York", "United States", 40.71427, -74.00597)))
                    leaveSearch()
                }, compact = true)
            }
            if (searching) {
                Text("Choose New York above, or enter a city / ZIP below. Back returns to Settings.", color = Color(0xFFA6B6D0), fontSize = 11.sp)
                Text(query.ifEmpty { "City or postal code" }, color = if (query.isEmpty()) Color(0xFF899BB9) else Color.White, fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF526480), RoundedCornerShape(12.dp)).padding(10.dp))
                if (editing) {
                    CityKeyboard(query, firstKey, onChange = { query = it; results = emptyList(); message = null }, onSearch = ::search)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavChip("Edit city", onClick = { editing = true }, modifier = Modifier.weight(1f).focusRequester(editButton))
                        NavChip(if (busy) "Searching…" else "Search again", onClick = ::search, modifier = Modifier.weight(1f).focusRequester(searchButton))
                    }
                }
                message?.let { Text(it, color = Color(0xFFA6B6D0), fontSize = 12.sp) }
                results.forEachIndexed { index, location ->
                    NavChip(location.label, onClick = {
                        weather.configure(weather.settings.copy(location = location))
                        leaveSearch()
                    }, modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(firstResult) else Modifier))
                }
                NavChip("Back to Settings", onClick = ::leaveSearch, modifier = Modifier.fillMaxWidth())
                SourceLink("Places by GeoNames", "https://www.geonames.org/")
            } else {
                Text("Your forecast, just how you like it.", color = Color(0xFF92A9BD), fontSize = 12.sp)
                Spacer(Modifier.height(3.dp))
                Text("WEATHER", color = Color(0xFF9CACCA), fontSize = 10.sp, letterSpacing = 1.7.sp)
                SettingsRow(weather.settings.location?.name ?: "Choose a city", weather.settings.location?.label ?: "Search any city or ZIP code", Icons.Outlined.Place,
                    modifier = Modifier.fillMaxWidth().focusRequester(first), onClick = { editing = true; searching = true })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TemperatureUnit.entries.forEach { unit ->
                        NavChip((if (weather.settings.unit == unit) "✓  " else "") + if (unit == TemperatureUnit.FAHRENHEIT) "Fahrenheit  °F" else "Celsius  °C", selected = weather.settings.unit == unit, onClick = {
                            weather.configure(weather.settings.copy(unit = unit))
                        }, modifier = Modifier.weight(1f))
                    }
                }
                Text("Your chosen city and units are remembered on this TV.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
                SourceLink("Weather by Open-Meteo (CC BY 4.0)", "https://open-meteo.com/")
                Spacer(Modifier.height(3.dp))
                NavChip("Fire TV Settings", onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Toast.makeText(context, "System settings are unavailable.", Toast.LENGTH_SHORT).show() }
                }, modifier = Modifier.fillMaxWidth())
                NavChip("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun SourceLink(label: String, url: String) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Text(label, color = if (focused) Color.White else Color(0xFF899BB9), fontSize = 10.sp,
        modifier = Modifier.navigationSound().onFocusChanged { focused = it.isFocused }
            .border(1.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(4.dp))
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { Toast.makeText(context, url, Toast.LENGTH_LONG).show() }
            })
}
