package nyc.arash.homescreen.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// Geist is preferred per the design; falls back to system sans-serif if the
// font asset isn't bundled. To bundle Geist, drop the .ttf files into
// app/src/main/res/font/ and swap FontFamily.SansSerif for FontFamily(Font(...)).
private val Geist = FontFamily.SansSerif

val HsTypography = Typography(
    displayLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Light, fontSize = 72.sp, letterSpacing = (-0.04 * 72).sp),
    headlineLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 52.sp, letterSpacing = (-0.02 * 52).sp),
    headlineMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 36.sp, letterSpacing = (-0.015 * 36).sp),
    headlineSmall = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 32.sp, letterSpacing = (-0.01 * 32).sp),
    titleLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 24.sp, letterSpacing = (-0.005 * 24).sp),
    titleMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 22.sp, letterSpacing = (-0.005 * 22).sp),
    bodyLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 24.sp),
    bodyMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 18.sp),
    bodySmall = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 18.sp, letterSpacing = (0.16 * 18).sp),
    labelMedium = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = 14.sp, letterSpacing = (0.18 * 14).sp),
    labelSmall = TextStyle(fontFamily = Geist, fontWeight = FontWeight.Normal, fontSize = 13.sp, letterSpacing = (0.32 * 13).sp),
)
