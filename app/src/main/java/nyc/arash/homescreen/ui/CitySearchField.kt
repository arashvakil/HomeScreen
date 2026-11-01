package nyc.arash.homescreen.ui

import android.content.Context
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView

/** Fire TV's IME needs native editor actions and pre-IME Back handling. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CitySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onKeyboardDismiss: () -> Unit,
    onFocused: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val inputMode = LocalInputModeManager.current
    AndroidView(
        modifier = modifier.semantics { contentDescription = "City or postal code" },
        factory = { context ->
            CitySearchEditText(context).apply {
                setText(query)
                setSelection(text.length)
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onQueryChange(s?.toString().orEmpty()) }
                    override fun afterTextChanged(s: Editable?) = Unit
                })
                post { beginEditing() }
            }
        },
        onRelease = { it.finishEditing() },
        update = { editor ->
            editor.onDismissKeyboard = { inputMode.requestInputMode(InputMode.Keyboard); onKeyboardDismiss() }
            editor.onSubmit = { inputMode.requestInputMode(InputMode.Keyboard); onSearch() }
            editor.onFocusChangeListener = android.view.View.OnFocusChangeListener { _, focused ->
                onFocused(focused)
                if (!focused) editor.hideKeyboard()
            }
            if (editor.text.toString() != query) {
                editor.setText(query)
                editor.setSelection(editor.text.length)
            }
        },
    )
}

private class CitySearchEditText(context: Context) : EditText(context) {
    var onDismissKeyboard: () -> Unit = {}
    var onSubmit: () -> Unit = {}
    private val keyboard get() = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    init {
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        // Focus restoration must never reopen a keyboard the user just dismissed.
        showSoftInputOnFocus = false
        filters = arrayOf(InputFilter.LengthFilter(100))
        hint = "City or postal code"
        textSize = 16f
        setTextColor(android.graphics.Color.WHITE)
        setHintTextColor(android.graphics.Color.rgb(137, 155, 185))
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val padding = (14 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        setOnClickListener { beginEditing() }
        setOnEditorActionListener { _, action, event ->
            if (action == EditorInfo.IME_ACTION_SEARCH || action == EditorInfo.IME_ACTION_DONE || action == EditorInfo.IME_ACTION_NEXT ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0)) {
                finishEditing()
                onSubmit()
                true
            } else false
        }
    }

    fun beginEditing() {
        requestFocus()
        keyboard.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hideKeyboard() { keyboard.hideSoftInputFromWindow(windowToken, 0) }

    fun finishEditing() {
        clearFocus()
        hideKeyboard()
    }

    override fun onKeyPreIme(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                finishEditing()
                onDismissKeyboard()
            }
            return true
        }
        return super.onKeyPreIme(keyCode, event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            finishEditing()
            onDismissKeyboard()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && event.repeatCount == 0) {
            beginEditing()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
