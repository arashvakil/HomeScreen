package nyc.arash.homescreen.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
    var fieldFocused by remember { mutableStateOf(false) }
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
        searchButton.requestFocus()
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
        LaunchedEffect(searching) {
            if (!searching) {
                withFrameNanos { }
                inputMode.requestInputMode(InputMode.Keyboard)
                first.requestFocus()
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
                .background(Color(0xFF111D32)).border(.5.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (searching) "Choose your city" else "Home Settings", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            if (searching) {
                Text("Search by city or postal code, then choose a match.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
                CitySearchField(
                    query = query,
                    onQueryChange = { query = it; results = emptyList(); message = null },
                    onSearch = ::search,
                    onKeyboardDismiss = { searchButton.requestFocus() },
                    onFocused = { fieldFocused = it },
                    modifier = Modifier.fillMaxWidth().navigationSound()
                        .border(if (fieldFocused) 2.dp else 1.dp, if (fieldFocused) Color.White else Color(0xFF526480), RoundedCornerShape(12.dp)),
                )
                NavChip(if (busy) "Searching…" else "Search", onClick = ::search, modifier = Modifier.fillMaxWidth().focusRequester(searchButton))
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
                Text("WEATHER", color = Color(0xFF9CACCA), fontSize = 10.sp, letterSpacing = 1.7.sp)
                NavChip(weather.settings.location?.label ?: "Choose a city", onClick = { searching = true }, modifier = Modifier.fillMaxWidth().focusRequester(first))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TemperatureUnit.entries.forEach { unit ->
                        NavChip(if (unit == TemperatureUnit.FAHRENHEIT) "Fahrenheit  °F" else "Celsius  °C", selected = weather.settings.unit == unit, onClick = {
                            weather.configure(weather.settings.copy(unit = unit))
                        }, modifier = Modifier.weight(1f))
                    }
                }
                Text("Saved on this TV. Your selected city is sent to Open-Meteo for weather. No device location access is needed.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
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
            .border(if (focused) 1.dp else 0.dp, Color.White, RoundedCornerShape(4.dp))
            .clickable {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { Toast.makeText(context, url, Toast.LENGTH_LONG).show() }
            })
}
