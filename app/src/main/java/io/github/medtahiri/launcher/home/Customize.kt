package io.github.medtahiri.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.LauncherConfig
import io.github.medtahiri.launcher.core.PhoneSettings
import kotlinx.serialization.json.JsonElement
import kotlin.math.roundToInt

/** Opens the Customize screen, or null when launcher.json turns it off (`home.allowCustomize`). */
val LocalOpenCustomize = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

private val BACKGROUNDS = listOf("wallpaper", "#000000", "#101418", "#1B1B2F", "#2B2118", "#FFFFFF", "#F5F0E6")
private val ACCENTS = listOf("#FFFFFF", "#A8C7FA", "#81C784", "#FFB74D", "#F48FB1", "#CE93D8", "#4DD0E1", "#FF5252")

private fun hex(value: String) = Color(0xFF000000 or value.removePrefix("#").toLong(16))

/**
 * Changing the look on the phone, without a computer. Saving keeps the changes on top of launcher.json: see
 * [PhoneSettings] for how they meet later changes to launcher.json.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CustomizeScreen(
    config: LauncherConfig,
    apps: List<AppEntry>,
    hasPhoneChanges: Boolean,
    onSave: (Map<String, JsonElement>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(config) }
    var pickingFavorites by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A dialog is its own window, so test tags need turning on again for tools to see them.
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            if (pickingFavorites) {
                FavoritesPicker(apps, draft.home.favorites, onDone = {
                    draft = draft.copy(home = draft.home.copy(favorites = it))
                    pickingFavorites = false
                })
            } else {
                CustomizeForm(draft, { draft = it }, hasPhoneChanges, { pickingFavorites = true }, onReset, onDismiss) {
                    onSave(PhoneSettings.changes(config, draft))
                }
            }
        }
    }
}

@Composable
private fun CustomizeForm(
    draft: LauncherConfig,
    onChange: (LauncherConfig) -> Unit,
    hasPhoneChanges: Boolean,
    onPickFavorites: () -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val theme = draft.theme
    val home = draft.home
    Column(Modifier.statusBarsPadding().navigationBarsPadding().testTag("customize")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text("Customize", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = onSave, modifier = Modifier.testTag("customize-save")) { Text("Save") }
        }
        HorizontalDivider()
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Changes stay on this phone. If launcher.json later changes the same setting, its new value wins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Setting("Style") {
                Choices(listOf("simple" to "Simple", "glass" to "Glass"), home.style) { onChange(draft.copy(home = home.copy(style = it))) }
            }
            Setting("Mode") {
                Choices(listOf("dark" to "Dark", "light" to "Light", "auto" to "Follow phone"), theme.mode) {
                    onChange(draft.copy(theme = theme.copy(mode = it)))
                }
            }
            Setting("Background") {
                Swatches((BACKGROUNDS + theme.background).distinct().filter { it != "auto" }, theme.background) {
                    onChange(draft.copy(theme = theme.copy(background = it)))
                }
            }
            Setting("Accent") {
                Swatches((ACCENTS + theme.accent).distinct(), theme.accent) { onChange(draft.copy(theme = theme.copy(accent = it))) }
            }
            Setting("Font") {
                Choices(listOf("default" to "Default", "sans" to "Sans", "serif" to "Serif", "mono" to "Mono"), theme.font) {
                    onChange(draft.copy(theme = theme.copy(font = it)))
                }
            }
            Setting("Text size: ${"%.1f".format(theme.textScale)}×") {
                Slider(
                    value = theme.textScale,
                    onValueChange = { onChange(draft.copy(theme = theme.copy(textScale = (it * 10).roundToInt() / 10f))) },
                    valueRange = 0.8f..2f,
                    steps = 11,
                )
            }
            Setting("Icon size: ${home.iconSize}") {
                Slider(
                    value = home.iconSize.toFloat(),
                    onValueChange = { onChange(draft.copy(home = home.copy(iconSize = (it / 4).roundToInt() * 4))) },
                    valueRange = 40f..120f,
                )
            }
            Setting("Columns") {
                Choices((3..6).map { it.toString() to it.toString() }, home.columns.toString()) {
                    onChange(draft.copy(home = home.copy(columns = it.toInt())))
                }
            }
            Toggle("App names under icons", home.showLabels) { onChange(draft.copy(home = home.copy(showLabels = it))) }
            Toggle(
                "Notification dots",
                home.notificationDots,
                note = "Shows which apps have notifications. Android will ask you to allow notification access, which also lets " +
                    "the launcher read them. It only keeps which apps have one.",
            ) { onChange(draft.copy(home = home.copy(notificationDots = it))) }
            Setting(if (home.style == "glass") "Dock apps" else "Favorite apps") {
                OutlinedButton(onClick = onPickFavorites, modifier = Modifier.testTag("customize-favorites")) {
                    Text(if (home.favorites.isEmpty()) "Choose apps" else "${home.favorites.size} chosen · Change")
                }
            }
            if (hasPhoneChanges) {
                HorizontalDivider()
                TextButton(onClick = onReset, modifier = Modifier.testTag("customize-reset")) {
                    Text("Undo all changes made on this phone", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Setting(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, note: String? = null, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Choices(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun Swatches(colors: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { value ->
            val ring = if (value == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            Box(
                Modifier
                    .size(40.dp)
                    .border(if (value == selected) 3.dp else 1.dp, ring, CircleShape)
                    .padding(5.dp)
                    .then(
                        if (value == "wallpaper") {
                            Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f), CircleShape)
                        } else {
                            Modifier.background(hex(value), CircleShape)
                        },
                    )
                    .clickable(onClickLabel = value) { onSelect(value) }
                    .semantics { contentDescription = if (value == "wallpaper") "Wallpaper" else value },
                contentAlignment = Alignment.Center,
            ) {
                if (value == "wallpaper") Text("W", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Every app with a checkbox. Apps are added in the order they're checked; chosen ones are listed first. */
@Composable
private fun FavoritesPicker(apps: List<AppEntry>, initial: List<String>, onDone: (List<String>) -> Unit) {
    var chosen by remember { mutableStateOf(initial) }
    BackHandler { onDone(chosen) }
    val sorted = remember(apps) {
        val byPackage = apps.distinctBy { it.packageName }
        byPackage.filter { it.packageName in initial }.sortedBy { initial.indexOf(it.packageName) } +
            byPackage.filter { it.packageName !in initial }
    }
    Column(Modifier.statusBarsPadding().navigationBarsPadding().testTag("favorites-picker")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Choose apps", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextButton(onClick = { onDone(chosen) }) { Text("Done") }
        }
        HorizontalDivider()
        LazyColumn {
            items(sorted, key = { it.packageName }) { app ->
                val position = chosen.indexOf(app.packageName)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { chosen = if (position >= 0) chosen - app.packageName else chosen + app.packageName }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = position >= 0, onCheckedChange = null)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, modifier = Modifier.weight(1f))
                    if (position >= 0) Text("${position + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
