package nyc.arash.homescreen.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import nyc.arash.homescreen.apps.LauncherLayout
import nyc.arash.homescreen.apps.TvApp
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(apps: List<TvApp>, resumeGeneration: Int = 0, updateVersion: String? = null, updateInstalling: Boolean = false, onInstallUpdate: () -> Unit = {}, onLaunchApp: (TvApp) -> Unit) {
    val context = LocalContext.current
    val inputMode = LocalInputModeManager.current
    val layout = remember { LauncherLayout(context) }
    var favoriteIds by remember { mutableStateOf(layout.favoriteIds()) }
    var allApps by remember(resumeGeneration) { mutableStateOf(false) }
    var arranging by remember(resumeGeneration) { mutableStateOf<TvApp?>(null) }
    val firstTile = remember { FocusRequester() }
    val gridState = rememberLazyGridState()
    var focusRequest by remember { mutableIntStateOf(0) }
    val favorites = favoriteIds.mapNotNull { id -> apps.find { it.id == id } }
    val watchOrder = listOf("tv.twitch.android.viewer", "com.netflix.ninja", "com.amazon.avod", "com.disney.disneyplus", "com.apple.atve.amazon.appletv", "org.jellyfin.androidtv")
    val more = apps.filter { !it.isUtility && it.id !in favoriteIds }.sortedBy {
        watchOrder.indexOf(it.id).let { rank -> if (rank < 0) Int.MAX_VALUE else rank }
    }
    val now = currentTime()
    val (time, period) = formatHM(now, hour12 = true)
    fun save(ids: List<String>) { layout.saveFavorites(ids); favoriteIds = layout.favoriteIds() }

    LaunchedEffect(apps.isNotEmpty(), allApps, focusRequest, resumeGeneration) {
        if (apps.isNotEmpty()) {
            gridState.scrollToItem(0)
            // Lazy grids dispose off-screen tiles. Wait for the first tile to be placed.
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.any { it.index == 1 } }.first { it }
            withFrameNanos { }
            // Fire OS may retain touch mode after settings/helper setup. TV tiles need keyboard focus.
            inputMode.requestInputMode(InputMode.Keyboard)
            firstTile.requestFocus()
        }
    }
    BackHandler {
        if (arranging != null) arranging = null
        else if (allApps) allApps = false
        else if (apps.isNotEmpty()) focusRequest++
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF070D19))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color(0xFF203D64), Color.Transparent), Offset(size.width * .25f, 0f), size.width * .82f))
            drawRect(Brush.radialGradient(listOf(Color(0xFF251D43), Color.Transparent), Offset(size.width * .94f, size.height * .85f), size.width * .8f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99040912))))
        }
        Column(Modifier.fillMaxSize().padding(horizontal = 44.dp).padding(top = 25.dp, bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NavChip("Home", selected = !allApps, onClick = { allApps = false })
                    NavChip("All Apps", selected = allApps, onClick = { allApps = true })
                    if (updateVersion != null) {
                        NavChip(if (updateInstalling) "Installing…" else "Update $updateVersion", onClick = onInstallUpdate, enabled = !updateInstalling)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("$time $period", color = Color(0xFFE9EFFA), fontSize = 16.sp, fontWeight = FontWeight.Light)
                    SettingsButton {
                        runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory("android.intent.category.LEANBACK_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text(now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())).uppercase(Locale.getDefault()), color = Color(0xFF9CACCA), fontSize = 10.sp, letterSpacing = 1.7.sp)
            Spacer(Modifier.height(5.dp))
            Text(if (allApps) "All your apps." else "Make yourself at home.", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Light, letterSpacing = (-.7).sp)
            Spacer(Modifier.height(22.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeading(if (allApps) "App Library" else "Favorites", if (allApps) "${apps.size} apps" else "Hold OK or tap ☰ to arrange")
                }
                if (allApps) {
                    itemsIndexed(apps, key = { _, app -> app.id }) { index, app ->
                        AppTile(app, { onLaunchApp(app) }, { arranging = app }, if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                    }
                } else {
                    itemsIndexed(favorites, key = { _, app -> "favorite:${app.id}" }) { index, app ->
                        AppTile(app, { onLaunchApp(app) }, { arranging = app }, if (index == 0) Modifier.focusRequester(firstTile) else Modifier)
                    }
                    if (apps.isEmpty()) {
                        items(6) {
                            Column {
                                Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(18.dp)).background(Color(0x13FFFFFF)))
                                Spacer(Modifier.height(23.dp))
                            }
                        }
                    }
                    if (apps.isNotEmpty() && favorites.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            NavChip("Choose favorites in All Apps", onClick = { allApps = true }, modifier = Modifier.focusRequester(firstTile))
                        }
                    }
                    if (more.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column { Spacer(Modifier.height(8.dp)); SectionHeading("More to watch", "") }
                        }
                        items(more, key = { "more:${it.id}" }) { app -> AppTile(app, { onLaunchApp(app) }, { arranging = app }) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Your apps. Nothing extra.", color = Color(0xFF7486A3), fontSize = 10.sp)
                Text("← ↑ ↓ →  Navigate     OK  Open     ☰  Arrange", color = Color(0xFF7486A3), fontSize = 10.sp)
            }
        }
        arranging?.let { app ->
            ArrangeDialog(app, favoriteIds, onSave = ::save, onDismiss = { arranging = null })
        }
    }
}

@Composable
private fun SectionHeading(title: String, hint: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color(0xFFE8EDF7), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (hint.isNotEmpty()) Text(hint, color = Color(0xFF899BB9), fontSize = 10.sp)
    }
}

@Composable
private fun NavChip(label: String, selected: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) Color.White else if (selected) Color(0x22FFFFFF) else Color.Transparent)
            .border(if (focused) 1.dp else 0.dp, Color(0x66FFFFFF), shape)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 19.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (!enabled) Color(0xFF617089) else if (focused) Color(0xFF111C2F) else Color(0xFFD7E0EF), fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.size(34.dp).onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(50))
            .background(if (focused) Color.White else Color(0x18FFFFFF))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Settings, "Settings", tint = if (focused) Color(0xFF122139) else Color(0xFFD6E1F4), modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun ArrangeDialog(app: TvApp, favorites: List<String>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val firstAction = remember { FocusRequester() }
    val index = favorites.indexOf(app.id)
    LaunchedEffect(app.id) { firstAction.requestFocus() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(370.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF111D32))
                .border(.5.dp, Color(0x33FFFFFF), RoundedCornerShape(24.dp)).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(app.name, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Medium)
            Text(if (index >= 0) "Favorite ${index + 1} of ${favorites.size}" else "Add this app to your first row.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (index >= 0) {
                NavChip("Move earlier", onClick = { onSave(LauncherLayout.moveFavorite(favorites, index, index - 1)) }, modifier = Modifier.fillMaxWidth().focusRequester(firstAction))
                NavChip("Move later", onClick = { onSave(LauncherLayout.moveFavorite(favorites, index, index + 1)) }, modifier = Modifier.fillMaxWidth())
                NavChip("Remove from favorites", onClick = { onSave(favorites.filterNot { it == app.id }); onDismiss() }, modifier = Modifier.fillMaxWidth())
            } else {
                NavChip("Add to favorites", onClick = { onSave(favorites + app.id); onDismiss() }, modifier = Modifier.fillMaxWidth().focusRequester(firstAction))
            }
            NavChip("Done", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
