package nyc.arash.homescreen.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nyc.arash.homescreen.ui.theme.HsColors

/** Static focus treatment: no scale animations, focus loops, or layout movement. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun Modifier.focusRing(
    focused: Boolean,
    cornerRadius: Dp = 18.dp,
    scaleOnFocus: Float = 1f,
    borderWidth: Dp = 2.dp,
    accent: Color = HsColors.Accent,
    glow: Color = HsColors.AccentRing,
): Modifier = border(
    if (focused) borderWidth else 0.5.dp,
    if (focused) accent else HsColors.Hairline,
    RoundedCornerShape(cornerRadius),
)
