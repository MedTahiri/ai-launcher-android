package io.github.medtahiri.launcher.home

import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import io.github.medtahiri.launcher.R
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.medtahiri.launcher.core.LauncherConfig
import io.github.medtahiri.launcher.core.Shortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A Settings screen search can open, found by its name or by words people use for it: the English ones here, and
 * [localWords], a comma-separated string resource, in the launcher's language.
 */
private class SettingsEntry(@StringRes val label: Int, @StringRes val localWords: Int, val words: List<String>, val action: String)

private val SETTINGS = listOf(
    SettingsEntry(R.string.setting_wifi, R.string.words_wifi, listOf("wifi", "wi-fi", "wireless", "internet"), Settings.ACTION_WIFI_SETTINGS),
    SettingsEntry(R.string.setting_bluetooth, R.string.words_bluetooth, listOf("bluetooth", "headphones", "pair"), Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingsEntry(R.string.setting_network, R.string.words_network, listOf("mobile", "data", "network", "sim", "cellular"), Settings.ACTION_WIRELESS_SETTINGS),
    SettingsEntry(R.string.setting_airplane, R.string.words_airplane, listOf("airplane", "aeroplane", "flight"), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingsEntry(R.string.setting_battery, R.string.words_battery, listOf("battery", "power", "saver", "charging"), Intent.ACTION_POWER_USAGE_SUMMARY),
    SettingsEntry(R.string.setting_display, R.string.words_display, listOf("display", "brightness", "screen", "dark mode", "font size"), Settings.ACTION_DISPLAY_SETTINGS),
    SettingsEntry(R.string.setting_sound, R.string.words_sound, listOf("sound", "volume", "ringtone", "vibration", "silent"), Settings.ACTION_SOUND_SETTINGS),
    SettingsEntry(R.string.setting_notifications, R.string.words_notifications, listOf("notifications", "do not disturb"), "android.settings.NOTIFICATION_SETTINGS"),
    SettingsEntry(R.string.setting_apps, R.string.words_apps, listOf("apps", "applications", "default apps"), Settings.ACTION_APPLICATION_SETTINGS),
    SettingsEntry(R.string.setting_location, R.string.words_location, listOf("location", "gps"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingsEntry(R.string.setting_storage, R.string.words_storage, listOf("storage", "space", "memory"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingsEntry(R.string.setting_security, R.string.words_security, listOf("security", "lock", "password", "pin", "fingerprint", "face"), Settings.ACTION_SECURITY_SETTINGS),
    SettingsEntry(R.string.setting_accessibility, R.string.words_accessibility, listOf("accessibility", "talkback", "magnification"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingsEntry(R.string.setting_date, R.string.words_date, listOf("date", "time", "time zone"), Settings.ACTION_DATE_SETTINGS),
    SettingsEntry(R.string.setting_languages, R.string.words_languages, listOf("language", "keyboard", "input"), Settings.ACTION_LOCALE_SETTINGS),
    SettingsEntry(R.string.setting_home, R.string.words_home, listOf("home app", "launcher", "default home"), Settings.ACTION_HOME_SETTINGS),
    SettingsEntry(R.string.setting_developer, R.string.words_developer, listOf("developer", "usb debugging", "adb"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    SettingsEntry(R.string.setting_all, R.string.words_all, listOf("settings"), Settings.ACTION_SETTINGS),
)

private fun settingsFor(context: Context, query: String): List<SettingsEntry> {
    val q = plain(query.trim())
    if (q.length < 2) return emptyList()
    return SETTINGS.filter { entry ->
        val words = entry.words + context.getString(entry.localWords).split(',').map { plain(it.trim()) }.filter { it.isNotEmpty() }
        plain(context.getString(entry.label)).startsWith(q) || words.any { it.startsWith(q) || (q.length >= 4 && q.startsWith(it)) }
    }.take(3)
}

/** Lowercase, without accents: "luminosite" finds "luminosité". */
private fun plain(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/**
 * What search finds besides apps: the answer to a sum, Settings screens, app shortcuts, and searching the web. Shown
 * above the apps, while something is typed.
 */
@Composable
fun SearchExtras(
    query: String,
    config: LauncherConfig,
    visiblePackages: Set<String>,
    textColor: Color,
    secondaryColor: Color,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val search = config.search
    // Loaded once each time search opens: shortcuts change as people use their apps.
    val shortcuts by produceState(emptyList<ShortcutInfo>(), search.shortcuts) {
        if (search.shortcuts) value = withContext(Dispatchers.IO) { Shortcuts.all(context) }
    }
    val text = query.trim()
    if (text.isEmpty()) return
    val answer = remember(text) { if (search.calculator) Calculator.evaluate(text) else null }
    val settings = remember(text) { if (search.settings) settingsFor(context, text) else emptyList() }
    val matchingShortcuts = remember(text, shortcuts, visiblePackages) {
        if (text.length < 2) emptyList() else shortcuts.filter {
            it.`package` in visiblePackages && (matchesSearch(it.shortLabel?.toString().orEmpty(), text) ||
                matchesSearch(it.longLabel?.toString().orEmpty(), text))
        }.take(4)
    }
    val canSearchWeb = remember {
        search.web && Intent(Intent.ACTION_WEB_SEARCH).resolveActivity(context.packageManager) != null
    }

    Column(modifier.testTag("search-extras")) {
        answer?.let {
            ExtraRow("=", "$text = $it", stringResource(R.string.tap_to_copy), textColor, secondaryColor, Modifier.testTag("search-answer")) {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Answer", it))
                Toast.makeText(context, context.getString(R.string.copied, it), Toast.LENGTH_SHORT).show()
            }
        }
        settings.forEach { entry ->
            ExtraRow("⚙", stringResource(entry.label), stringResource(R.string.settings), textColor, secondaryColor, Modifier.testTag("search-setting")) {
                open(context, Intent(entry.action))
                onDone()
            }
        }
        matchingShortcuts.forEach { shortcut ->
            ShortcutRow(shortcut, textColor, secondaryColor) {
                Shortcuts.start(context, shortcut)
                onDone()
            }
        }
        if (canSearchWeb && answer == null) {
            ExtraRow("⌕", stringResource(R.string.search_web, text), null, textColor, secondaryColor, Modifier.testTag("search-web")) {
                open(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text))
                onDone()
            }
        }
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, R.string.not_available, Toast.LENGTH_SHORT).show() }
}

@Composable
private fun ExtraRow(
    glyph: String,
    title: String,
    subtitle: String?,
    textColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageBitmap? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
            if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(32.dp)) else Text(glyph, color = textColor, fontSize = 20.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, color = secondaryColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: ShortcutInfo, textColor: Color, secondaryColor: Color, onClick: () -> Unit) {
    val context = LocalContext.current
    var icon by remember(shortcut.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(shortcut.id) {
        icon = withContext(Dispatchers.IO) {
            Shortcuts.icon(context, shortcut, 96)
        }
    }
    val app = remember(shortcut.`package`) {
        runCatching {
            context.packageManager.run { getApplicationLabel(getApplicationInfo(shortcut.`package`, 0)) }
        }.getOrNull()?.toString() ?: shortcut.`package`
    }
    ExtraRow(
        "↗",
        shortcut.shortLabel?.toString() ?: shortcut.id,
        app,
        textColor,
        secondaryColor,
        Modifier.testTag("search-shortcut"),
        icon,
        onClick,
    )
}
