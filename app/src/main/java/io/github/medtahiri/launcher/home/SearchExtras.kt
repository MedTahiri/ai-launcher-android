package io.github.medtahiri.launcher.home

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A Settings screen search can open, found by its name or by words people use for it. */
private class SettingsEntry(val label: String, val words: List<String>, val action: String)

private val SETTINGS = listOf(
    SettingsEntry("Wi-Fi", listOf("wifi", "wi-fi", "wireless", "internet"), Settings.ACTION_WIFI_SETTINGS),
    SettingsEntry("Bluetooth", listOf("bluetooth", "headphones", "pair"), Settings.ACTION_BLUETOOTH_SETTINGS),
    SettingsEntry("Network & mobile data", listOf("mobile", "data", "network", "sim", "cellular"), Settings.ACTION_WIRELESS_SETTINGS),
    SettingsEntry("Airplane mode", listOf("airplane", "aeroplane", "flight"), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
    SettingsEntry("Battery", listOf("battery", "power", "saver", "charging"), Intent.ACTION_POWER_USAGE_SUMMARY),
    SettingsEntry("Display", listOf("display", "brightness", "screen", "dark mode", "font size"), Settings.ACTION_DISPLAY_SETTINGS),
    SettingsEntry("Sound & vibration", listOf("sound", "volume", "ringtone", "vibration", "silent"), Settings.ACTION_SOUND_SETTINGS),
    SettingsEntry("Notifications", listOf("notifications", "do not disturb"), "android.settings.NOTIFICATION_SETTINGS"),
    SettingsEntry("Apps", listOf("apps", "applications", "default apps"), Settings.ACTION_APPLICATION_SETTINGS),
    SettingsEntry("Location", listOf("location", "gps"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
    SettingsEntry("Storage", listOf("storage", "space", "memory"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
    SettingsEntry("Security & screen lock", listOf("security", "lock", "password", "pin", "fingerprint", "face"), Settings.ACTION_SECURITY_SETTINGS),
    SettingsEntry("Accessibility", listOf("accessibility", "talkback", "magnification"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
    SettingsEntry("Date & time", listOf("date", "time", "time zone"), Settings.ACTION_DATE_SETTINGS),
    SettingsEntry("Languages", listOf("language", "keyboard", "input"), Settings.ACTION_LOCALE_SETTINGS),
    SettingsEntry("Home app", listOf("home app", "launcher", "default home"), Settings.ACTION_HOME_SETTINGS),
    SettingsEntry("Developer options", listOf("developer", "usb debugging", "adb"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    SettingsEntry("All settings", listOf("settings"), Settings.ACTION_SETTINGS),
)

private fun settingsFor(query: String): List<SettingsEntry> {
    val q = query.trim().lowercase()
    if (q.length < 2) return emptyList()
    return SETTINGS.filter { entry ->
        entry.label.lowercase().startsWith(q) || entry.words.any { it.startsWith(q) || (q.length >= 4 && q.startsWith(it)) }
    }.take(3)
}

/** App shortcuts, like "New message". Android only shares them with the default home screen; otherwise there are none. */
private fun loadShortcuts(context: Context): List<ShortcutInfo> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return try {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        launcherApps.profiles.flatMap { launcherApps.getShortcuts(query, it).orEmpty() }
            .filter { it.isEnabled }
            .distinctBy { it.`package` + "/" + it.id + "@" + it.userHandle.hashCode() }
    } catch (e: Exception) { // SecurityException or IllegalStateException while the phone is locked
        Log.w("SearchExtras", "Can't read shortcuts", e)
        emptyList()
    }
}

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
        if (search.shortcuts) value = withContext(Dispatchers.IO) { loadShortcuts(context) }
    }
    val text = query.trim()
    if (text.isEmpty()) return
    val answer = remember(text) { if (search.calculator) Calculator.evaluate(text) else null }
    val settings = remember(text) { if (search.settings) settingsFor(text) else emptyList() }
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
            ExtraRow("=", "$text = $it", "Tap to copy", textColor, secondaryColor, Modifier.testTag("search-answer")) {
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Answer", it))
                Toast.makeText(context, "Copied $it", Toast.LENGTH_SHORT).show()
            }
        }
        settings.forEach { entry ->
            ExtraRow("⚙", entry.label, "Settings", textColor, secondaryColor, Modifier.testTag("search-setting")) {
                open(context, Intent(entry.action))
                onDone()
            }
        }
        matchingShortcuts.forEach { shortcut ->
            ShortcutRow(shortcut, textColor, secondaryColor) {
                runCatching {
                    context.getSystemService(LauncherApps::class.java).startShortcut(shortcut, null, null)
                }.onFailure { Toast.makeText(context, "Couldn't open that", Toast.LENGTH_SHORT).show() }
                onDone()
            }
        }
        if (canSearchWeb && answer == null) {
            ExtraRow("⌕", "Search the web for “$text”", null, textColor, secondaryColor, Modifier.testTag("search-web")) {
                open(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text))
                onDone()
            }
        }
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "Not available on this phone", Toast.LENGTH_SHORT).show() }
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
            runCatching {
                val density = context.resources.displayMetrics.densityDpi
                context.getSystemService(LauncherApps::class.java).getShortcutIconDrawable(shortcut, density)
                    ?.toBitmap(96, 96)?.asImageBitmap()
            }.getOrNull()
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
