package nyc.arash.homescreen.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/** TV-native focus targets; no system IME or focus restoration can trap city entry. */
@Composable
internal fun CityKeyboard(query: String, firstKey: FocusRequester, onChange: (String) -> Unit, onSearch: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ".chunked(10).forEachIndexed { row, keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                keys.forEachIndexed { column, character ->
                    NavChip(character.toString(), onClick = { onChange(CityQuery.append(query, character.toString())) },
                        modifier = Modifier.weight(1f).then(if (row == 0 && column == 0) Modifier.focusRequester(firstKey) else Modifier), compact = true)
                }
                repeat(10 - keys.length) { Spacer(Modifier.weight(1f)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            NavChip("Space", onClick = { onChange(CityQuery.append(query, " ")) }, modifier = Modifier.weight(1f), compact = true)
            NavChip("Delete", onClick = { onChange(CityQuery.delete(query)) }, modifier = Modifier.weight(1f), compact = true)
            NavChip("Clear", onClick = { onChange("") }, modifier = Modifier.weight(1f), compact = true)
            NavChip("Search", onClick = onSearch, modifier = Modifier.weight(1f), compact = true)
        }
    }
}

internal object CityQuery {
    fun append(query: String, text: String): String = (query + text).take(100)
    fun delete(query: String): String = if (query.isEmpty()) query else query.dropLast(Character.charCount(query.codePointBefore(query.length)))
}
