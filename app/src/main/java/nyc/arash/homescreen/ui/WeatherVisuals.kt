package nyc.arash.homescreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text

internal val WeatherPanelBrush = Brush.linearGradient(listOf(Color(0xFF192E46), Color(0xFF111E33), Color(0xFF111A2D)))
internal val WeatherAccent = Color(0xFFB8DEED)

@Composable
internal fun WeatherGlyph(code: Int, modifier: Modifier = Modifier, isDay: Boolean? = null) {
    val (icon, tint) = when (code) {
        0, 1 -> if (isDay == false) Icons.Outlined.NightsStay to Color(0xFFCFD7F3) else Icons.Outlined.WbSunny to Color(0xFFF4D795)
        45, 48 -> Icons.Outlined.Air to Color(0xFFB7CADB)
        71, 73, 75, 77, 85, 86 -> Icons.Outlined.AcUnit to Color(0xFFBDE7F5)
        95, 96, 97, 99 -> Icons.Outlined.FlashOn to Color(0xFFE3CEFA)
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Icons.Outlined.Grain to Color(0xFFA9D3F5)
        else -> Icons.Outlined.Cloud to Color(0xFFD1DFED)
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier)
}

@Composable
internal fun WeatherOutlook(text: String, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    Row(modifier.navigationSound().onFocusChanged { focused = it.isFocused }.clip(shape)
        .background(if (focused) Color(0xFF294B65) else Color(0x143FADC5))
        .border(if (focused) 2.dp else 1.dp, if (focused) Color(0xFFE2F5FF) else Color(0x235DC4D3), shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.Opacity, null, tint = WeatherAccent, modifier = Modifier.size(21.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("THE OUTLOOK", color = Color(0xFF8EAFBF), fontSize = 8.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
            Text(text, color = Color(0xFFE0EFF6), fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onClick != null) Icon(Icons.Outlined.ChevronRight, null, tint = WeatherAccent, modifier = Modifier.size(17.dp))
    }
}

@Composable
internal fun SettingsRow(title: String, detail: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    Row(modifier.navigationSound().onFocusChanged { focused = it.isFocused }.clip(shape)
        .background(if (focused) Color(0xFFF0F7FC) else Color(0x0DFFFFFF))
        .border(1.dp, if (focused) Color.White else Color(0x0DFFFFFF), shape)
        .clickable(onClick = onClick).padding(15.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = if (focused) Color(0xFF33566A) else WeatherAccent, modifier = Modifier.size(23.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (focused) Color(0xFF122337) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = if (focused) Color(0xFF526B7C) else Color(0xFF92A9BD), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = if (focused) Color(0xFF33566A) else Color(0xFF7896AD), modifier = Modifier.size(19.dp))
    }
}
