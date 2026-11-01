package nyc.arash.homescreen.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import nyc.arash.homescreen.R

private val LocalNavigationSound = staticCompositionLocalOf<NavigationSound?> { null }

@Composable
internal fun NavigationSoundProvider(content: @Composable () -> Unit) {
    val context = LocalContext.current.applicationContext
    val sound = remember(context) { NavigationSound(context) }
    DisposableEffect(sound) { onDispose { sound.release() } }
    CompositionLocalProvider(LocalNavigationSound provides sound, content = content)
}

/** Arm on a directional key, but play only if it actually transfers focus. */
internal fun Modifier.navigationSound(): Modifier = composed {
    val sound = LocalNavigationSound.current
    this.onPreviewKeyEvent { event ->
        when (event.nativeKeyEvent.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_DPAD_RIGHT,
            AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_DPAD_DOWN ->
                sound?.directionKey(event.type == KeyEventType.KeyDown)
        }
        false
    }.onFocusChanged { if (it.isFocused) sound?.focusMoved() }
}

private class NavigationSound(context: Context) {
    private val feedback = NavigationFeedback()
    private var ready = false
    private val pool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    ).build()
    private val sample: Int

    init {
        pool.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        sample = pool.load(context, R.raw.navigation_beep, 1)
    }

    fun directionKey(down: Boolean) = feedback.directionKey(down, SystemClock.uptimeMillis())

    fun focusMoved() {
        if (feedback.focusMoved(SystemClock.uptimeMillis()) && ready) {
            // The sample itself is quiet; system sound volume/mute remains authoritative.
            pool.play(sample, 0.35f, 0.35f, 0, 0, 1f)
        }
    }

    fun release() {
        ready = false
        pool.release()
    }
}

/** Ignore initial/restored focus, blocked moves, stale key presses and rapid repeats. */
internal class NavigationFeedback {
    private var pressedAt: Long? = null
    private var lastPlayedAt: Long? = null

    fun directionKey(down: Boolean, now: Long) {
        pressedAt = if (down) now else null
    }

    fun focusMoved(now: Long): Boolean {
        val pressed = pressedAt ?: return false
        pressedAt = null
        if (now - pressed !in 0..150) return false
        if (lastPlayedAt?.let { now - it < 70 } == true) return false
        lastPlayedAt = now
        return true
    }
}
