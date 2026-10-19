package nyc.arash.homescreen.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object HsColors {
    val Bg0 = Color(0xFF0A0B0E)
    val Bg1 = Color(0xFF111317)
    val Surface = Color(0xFF15181D)
    val SurfaceHi = Color(0xFF1C2027)
    val Text1 = Color(0xFFF4F7FF)
    val Text2 = Color(0xFFBDC9DF)
    val Text3 = Color(0xFF8C9CB7)
    val Accent = Color(0xFFF4F7FF)
    val AccentSoft = Color(0x2E41628F)
    val AccentRing = Color(0x8C7BA4DC)
    val Hairline = Color(0x0FFFFFFF)
    val HairlineHi = Color(0x1FFFFFFF)
    val SidebarBg = Color(0xB814161B)
    val SidebarHi = Color(0x1AFFFFFF)
}

val LocalAccent = staticCompositionLocalOf { HsColors.Accent }

@Composable
fun HomeScreenTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = HsColors.Accent,
        onPrimary = HsColors.Bg0,
        background = HsColors.Bg0,
        onBackground = HsColors.Text1,
        surface = HsColors.Surface,
        onSurface = HsColors.Text1,
    )
    CompositionLocalProvider(LocalAccent provides HsColors.Accent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HsTypography,
            content = content,
        )
    }
}
