package nyc.arash.homescreen.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun currentTime(): LocalDateTime {
    var t by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            t = LocalDateTime.now()
        }
    }
    return t
}

fun formatHM(d: LocalDateTime, hour12: Boolean): Pair<String, String> {
    val h24 = d.hour
    val mm = "%02d".format(d.minute)
    return if (hour12) {
        val h = ((h24 + 11) % 12) + 1
        "$h:$mm" to if (h24 >= 12) "PM" else "AM"
    } else {
        "%02d:%s".format(h24, mm) to ""
    }
}

fun formatDate(d: LocalDateTime): String =
    d.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))

fun greetingFor(d: LocalDateTime): String = when (d.hour) {
    in 0..4 -> "Late night"
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
