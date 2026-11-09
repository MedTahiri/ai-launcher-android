package io.github.medtahiri.launcher.home

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.medtahiri.launcher.core.GesturesConfig
import kotlinx.serialization.json.JsonElement

/** What the phone side of the launcher can do for the home screen: Customize, profiles and backups. */
data class PhoneControls(
    val hasPhoneChanges: Boolean,
    val onSaveSettings: (Map<String, JsonElement>, String) -> Unit,
    val onResetSettings: () -> Unit,
    /** The profile in use, or null. */
    val activeProfile: String?,
    /** The profile picked on the phone, or "" for automatic. */
    val pickedProfile: String,
    /** Another profile's hours have started: start again with it. */
    val onProfileDue: () -> Unit,
    /** theme.accent as set, before "wallpaper" becomes a color. */
    val accentSetting: String,
    val onRestored: () -> Unit,
)

/** The home screen's gestures from launcher.json, and what runs them. */
class HomeGestures(private val config: GesturesConfig, private val run: (String) -> Unit) {
    private fun action(gesture: String) = config.all[gesture] ?: "none"

    fun has(gesture: String) = action(gesture) != "none"

    /** Runs the gesture's action, if it has one. */
    fun perform(gesture: String): Boolean {
        val action = action(gesture)
        if (action == "none") return false
        run(action)
        return true
    }
}

val LocalHomeGestures = staticCompositionLocalOf { HomeGestures(GesturesConfig()) {} }
