package nyc.arash.homescreen.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import nyc.arash.homescreen.sync.FavoriteSyncState
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun FavoriteSyncDialog(sync: FavoriteSyncState, onDismiss: () -> Unit) {
    var joining by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current
    val codeAlphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
    val busy = sync.busy || sync.revoking
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(sync.pairCode) {
        while (sync.pairCode != null) {
            now = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }

    fun updateCode(value: String) {
        code = value.uppercase(Locale.ROOT).filter { it in codeAlphabet }.take(8)
        sync.clearError()
    }
    fun join() {
        if (code.length == 8 && !busy) sync.join(code)
    }
    fun back() {
        when {
            joining -> { joining = false; sync.clearError() }
            confirmUnlink -> confirmUnlink = false
            else -> { sync.closeCode(); onDismiss() }
        }
    }

    LaunchedEffect(sync.linked) {
        if (sync.linked) joining = false
        if (!sync.linked) confirmUnlink = false
    }
    LaunchedEffect(joining, confirmUnlink, sync.linked) {
        snapshotFlow { sync.busy || sync.revoking }.first { !it }
        withFrameNanos { }
        inputMode.requestInputMode(InputMode.Keyboard)
        first.requestFocus()
    }
    Dialog(onDismissRequest = ::back, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.width(620.dp).heightIn(max = 470.dp).clip(RoundedCornerShape(24.dp))
                .background(WeatherPanelBrush).border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(26.dp)
                .onPreviewKeyEvent { event ->
                    if (!joining || busy) false else when (event.nativeKeyEvent.keyCode) {
                        AndroidKeyEvent.KEYCODE_ENTER -> {
                            if (event.type == KeyEventType.KeyDown) join()
                            true
                        }
                        AndroidKeyEvent.KEYCODE_DEL -> {
                            if (event.type == KeyEventType.KeyDown) updateCode(code.dropLast(1))
                            true
                        }
                        else -> {
                            val character = event.nativeKeyEvent.unicodeChar
                            if (character >= 32 && Character.isValidCodePoint(character)) {
                                if (event.type == KeyEventType.KeyDown) updateCode(code + String(Character.toChars(character)))
                                true
                            } else false
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Favorites sync", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            Text("Link your TVs once. Your favorites and their order follow you between homes.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
            if (sync.revoking) Text("Unlinking when this TV is online.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
            when {
                confirmUnlink -> {
                    Text("Unlink this TV? Its current favorites will stay here. Future changes will be saved on this TV only.", color = Color.White, fontSize = 14.sp)
                    NavChip("Keep this TV linked", onClick = { confirmUnlink = false }, modifier = Modifier.fillMaxWidth().focusRequester(first), enabled = !busy)
                    NavChip("Unlink this TV", onClick = { sync.unlink() }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                }
                joining -> {
                    Text("Enter the code displayed on your other TV.", color = Color.White, fontSize = 14.sp)
                    Text(code.padEnd(8, '–').chunked(4).joinToString("  "), color = Color.White, fontSize = 27.sp,
                        modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF526480), RoundedCornerShape(12.dp)).padding(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        codeAlphabet.chunked(8).forEachIndexed { row, keys ->
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                keys.forEachIndexed { column, character ->
                                    NavChip(character.toString(), onClick = { updateCode(code + character) }, enabled = !busy,
                                        modifier = Modifier.weight(1f).then(if (row == 0 && column == 0) Modifier.focusRequester(first) else Modifier), compact = true)
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NavChip("Delete", onClick = { updateCode(code.dropLast(1)) }, modifier = Modifier.weight(1f), enabled = !busy, compact = true)
                        NavChip("Clear", onClick = { updateCode("") }, modifier = Modifier.weight(1f), enabled = !busy, compact = true)
                    }
                    Text("Joining replaces this TV’s favorites with the shared list. Weather stays local.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
                    NavChip(if (busy) "Joining…" else "Join and use shared favorites", onClick = ::join, modifier = Modifier.fillMaxWidth(), enabled = code.length == 8 && !busy)
                }
                sync.linked -> {
                    Text(sync.status, color = Color(0xFFC6E8DE), fontSize = 14.sp)
                    if (sync.pairCode != null && sync.pairCodeExpiresAt > now) {
                        Text(sync.pairCode.orEmpty().chunked(4).joinToString("  "), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium)
                        Text("On your other TV, open Favorites sync → Join an existing setup and enter this code. It works once and expires in ${((sync.pairCodeExpiresAt - now + 59) / 60).coerceAtLeast(1)} minutes.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
                    }
                    if (sync.pairCode != null && sync.pairCodeExpiresAt <= now) {
                        Text("Pairing code expired. Choose Link another TV for a new code.", color = Color(0xFFFFC9B8), fontSize = 12.sp)
                    }
                    NavChip(if (busy) "Working…" else "Link another TV", onClick = { sync.newPairCode() }, modifier = Modifier.fillMaxWidth().focusRequester(first), enabled = !busy)
                    NavChip("Sync now", onClick = { sync.syncNow() }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    NavChip("Unlink this TV", onClick = { confirmUnlink = true }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Text("Favorites sync automatically. Weather is saved separately on each TV. Apps that are missing on a TV keep their place until installed.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
                }
                else -> {
                    Text("Start on the TV whose favorites you want to share.", color = Color.White, fontSize = 14.sp)
                    NavChip(if (busy) "Working…" else "Share this TV’s favorites", onClick = { sync.createGroup() }, modifier = Modifier.fillMaxWidth().focusRequester(first), enabled = !busy)
                    NavChip("Join an existing setup", onClick = { joining = true; sync.clearError() }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
                    Text("No account or registration. The pairing code gives the other TV access to this shared favorites list.", color = Color(0xFFA6B6D0), fontSize = 12.sp)
                }
            }
            sync.error?.let { Text(it, color = Color(0xFFFFC9B8), fontSize = 12.sp) }
            NavChip(if (joining || confirmUnlink) "Back" else "Done", onClick = ::back, modifier = Modifier.fillMaxWidth())
        }
    }
}
