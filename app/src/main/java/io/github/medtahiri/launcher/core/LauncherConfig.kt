package io.github.medtahiri.launcher.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.intOrNull

/** The launcher's settings, from launcher.json. New settings need a default, so older files keep working. */
@Serializable
data class LauncherConfig(
    val name: String = "My Launcher",
    val theme: ThemeConfig = ThemeConfig(),
    val home: HomeConfig = HomeConfig(),
    val drawer: DrawerConfig = DrawerConfig(),
    val focus: FocusConfig = FocusConfig(),
    val search: SearchConfig = SearchConfig(),
    val gestures: GesturesConfig = GesturesConfig(),
    val profiles: List<ProfileConfig> = emptyList(),
    /** Apps the focus rules hide right now. Worked out on the phone, never read from launcher.json. */
    @Transient val hiddenNow: Set<String> = emptySet(),
)

/**
 * What gestures on empty home screen space do: none, drawer, search, customize, notifications, quick_settings, lock or
 * app:PACKAGE. Notifications, quick settings and lock need Accessibility access, which the launcher asks for.
 */
@Serializable
data class GesturesConfig(
    val doubleTap: String = "none",
    val swipeDown: String = "none",
    /** Simple style only: in glass, sideways swipes turn pages. */
    val swipeLeft: String = "none",
    val swipeRight: String = "none",
) {
    val all: Map<String, String> get() = mapOf("doubleTap" to doubleTap, "swipeDown" to swipeDown, "swipeLeft" to swipeLeft, "swipeRight" to swipeRight)
}

/** What search finds besides apps. */
@Serializable
data class SearchConfig(
    /** Answers sums like 12*7. */
    val calculator: Boolean = true,
    /** Settings screens, like Wi-Fi or Battery. */
    val settings: Boolean = true,
    /** App shortcuts, like "New message". Android only shares them with the default home screen. */
    val shortcuts: Boolean = true,
    /** "Search the web", which hands the words to the phone's browser or search app. */
    val web: Boolean = true,
)

@Serializable
data class ThemeConfig(
    /** "dark", "light" or "auto" (follows the system). */
    val mode: String = "dark",
    /** "#RRGGBB", "wallpaper" (the system wallpaper) or "auto" (from the mode). */
    val background: String = "#000000",
    /** "#RRGGBB", or "wallpaper" for a color from the wallpaper. */
    val accent: String = "#FFFFFF",
    /** "default", "sans", "serif" or "mono". */
    val font: String = "default",
    /** Multiplies all text: 1.0 is normal. */
    val textScale: Float = 1f,
    /** The package name of an installed icon pack, or "" for each app's own icon. */
    val iconPack: String = "",
)

@Serializable
data class HomeConfig(
    /** "simple" (widgets and favorites) or "glass" (home pages, dock, search and editing, over the wallpaper). */
    val style: String = "simple",
    val widgets: List<WidgetConfig> = emptyList(),
    /** Package names, in order. Apps that aren't installed are skipped. */
    val favorites: List<String> = emptyList(),
    /** Named groups of apps: after the favorites in the simple style, and first on the groups page in the glass style. */
    val folders: List<FolderConfig> = emptyList(),
    val columns: Int = 4,
    /** In dp. */
    val iconSize: Int = 56,
    val showLabels: Boolean = true,
    /** The banner offering to become the default home screen, while it isn't. */
    val showDefaultPrompt: Boolean = true,
    /** Extra space between favorites, in dp. */
    val spacing: Int = 0,
    /** How "All apps" looks: "text" or "button" (large and filled). */
    val drawerButton: String = "text",
    val swipeUpOpensDrawer: Boolean = true,
    /** Whether long-pressing an app offers Uninstall. */
    val allowUninstall: Boolean = true,
    /** A dot on apps with notifications. Needs the person to allow notification access, which the launcher asks for. */
    val notificationDots: Boolean = false,
    /** Whether long press on the home screen offers Customize, for changing the look on the phone. */
    val allowCustomize: Boolean = true,
    /** Says the app's name aloud as it opens ("Opening WhatsApp"), for people who find icons hard to read. */
    val speakAppNames: Boolean = false,
)

@Serializable
data class FolderConfig(
    val name: String,
    /** Package names, in order. Apps that aren't installed are skipped, and a folder with none isn't shown. */
    val apps: List<String>,
)

@Serializable
data class WidgetConfig(
    val type: String,
    val options: Map<String, JsonPrimitive> = emptyMap(),
) {
    fun int(key: String, default: Int): Int = options[key]?.intOrNull ?: default
    fun string(key: String, default: String): String = options[key]?.content ?: default
}

@Serializable
data class DrawerConfig(
    /** Package names to leave out of the drawer. */
    val hidden: List<String> = emptyList(),
    /** When not empty, the drawer shows only these package names. */
    val only: List<String> = emptyList(),
    val search: Boolean = true,
    val columns: Int = 4,
    /** In dp. */
    val iconSize: Int = 56,
    val showLabels: Boolean = true,
    /** Private apps: left out everywhere, and shown only after unlocking with the phone's fingerprint, face or PIN. */
    @SerialName("private") val privateApps: List<String> = emptyList(),
)

/** A config and what's wrong with it. With problems, [config] is the defaults: the launcher still opens. */
data class ParsedConfig(val config: LauncherConfig, val problems: List<String>)

val GESTURE_ACTIONS = listOf("none", "drawer", "search", "customize", "notifications", "quick_settings", "lock")

private val json = Json { ignoreUnknownKeys = false } // a misspelled setting is a mistake, not something to skip
private val HEX_COLOR = Regex("#[0-9a-fA-F]{6}")
private val PACKAGE_NAME = Regex("[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+")

fun parseConfig(
    text: String,
    widgetTypes: Set<String>,
    validateWidget: (WidgetConfig) -> List<String> = { emptyList() },
): ParsedConfig {
    val config = try {
        json.decodeFromString<LauncherConfig>(text)
    } catch (e: IllegalArgumentException) { // includes SerializationException
        return ParsedConfig(LauncherConfig(), listOf(e.message?.lineSequence()?.take(3)?.joinToString(" ") ?: "invalid JSON"))
    }
    val problems = validate(config, widgetTypes, validateWidget).toMutableList()
    // Each profile must make a valid config too: checked now, so a profile can't break the launcher at 18:00.
    if (problems.isEmpty() && config.profiles.isNotEmpty()) {
        val raw = json.parseToJsonElement(text).jsonObject
        config.profiles.forEach { profile ->
            val withProfile = Profiles.apply(JsonObject(raw - "profiles"), profile)
            val result = runCatching { json.decodeFromJsonElement(LauncherConfig.serializer(), withProfile) }
            result.exceptionOrNull()?.let {
                problems += "profiles (${profile.name}): ${it.message?.lineSequence()?.first() ?: "invalid setting"}"
            }
            result.getOrNull()?.let { problems += validate(it, widgetTypes, validateWidget).map { p -> "profiles (${profile.name}): $p" } }
        }
    }
    return if (problems.isEmpty()) ParsedConfig(config, problems) else ParsedConfig(LauncherConfig(), problems)
}

private fun validate(
    c: LauncherConfig,
    widgetTypes: Set<String>,
    validateWidget: (WidgetConfig) -> List<String>,
): List<String> = buildList {
    if (c.name.isBlank()) add("name can't be empty")
    if (c.theme.mode !in setOf("dark", "light", "auto")) add("theme.mode must be dark, light or auto, not \"${c.theme.mode}\"")
    if (c.theme.background !in setOf("wallpaper", "auto") && !HEX_COLOR.matches(c.theme.background)) {
        add("theme.background must be #RRGGBB, wallpaper or auto, not \"${c.theme.background}\"")
    }
    if (c.theme.accent != "wallpaper" && !HEX_COLOR.matches(c.theme.accent)) {
        add("theme.accent must be #RRGGBB or wallpaper, not \"${c.theme.accent}\"")
    }
    if (c.theme.font !in setOf("default", "sans", "serif", "mono")) add("theme.font must be default, sans, serif or mono, not \"${c.theme.font}\"")
    if (c.theme.textScale !in 0.7f..2.5f) add("theme.textScale must be from 0.7 to 2.5, not ${c.theme.textScale}")
    if (c.home.columns !in 1..8) add("home.columns must be from 1 to 8, not ${c.home.columns}")
    if (c.home.iconSize !in 24..160) add("home.iconSize must be from 24 to 160, not ${c.home.iconSize}")
    if (c.home.style !in setOf("simple", "glass")) add("home.style must be simple or glass, not \"${c.home.style}\"")
    if (c.home.spacing !in 0..64) add("home.spacing must be from 0 to 64, not ${c.home.spacing}")
    if (c.home.drawerButton !in setOf("text", "button")) add("home.drawerButton must be text or button, not \"${c.home.drawerButton}\"")
    if (c.drawer.columns !in 1..8) add("drawer.columns must be from 1 to 8, not ${c.drawer.columns}")
    if (c.drawer.iconSize !in 24..160) add("drawer.iconSize must be from 24 to 160, not ${c.drawer.iconSize}")
    c.home.widgets.forEach {
        if (it.type !in widgetTypes) {
            add("home.widgets: unknown type \"${it.type}\". Known types: ${widgetTypes.sorted().joinToString()}")
        } else {
            addAll(validateWidget(it).map { problem -> "home.widgets: ${it.type}: $problem" })
        }
    }
    c.home.folders.forEach { folder ->
        if (folder.name.isBlank()) add("home.folders: a folder needs a name")
        if (folder.apps.isEmpty()) add("home.folders: \"${folder.name}\" has no apps")
    }
    c.home.folders.groupBy { it.name.trim().lowercase() }.filter { it.value.size > 1 }.forEach {
        add("home.folders: two folders are called \"${it.value.first().name}\"")
    }
    c.gestures.all.forEach { (gesture, action) ->
        val app = action.removePrefix("app:")
        if (action !in GESTURE_ACTIONS && !(action.startsWith("app:") && PACKAGE_NAME.matches(app))) {
            add("gestures.$gesture must be ${GESTURE_ACTIONS.joinToString()} or app:PACKAGE, not \"$action\"")
        }
    }
    addAll(Profiles.validate(c.profiles))
    addAll(FocusRules.validate(c.focus) { PACKAGE_NAME.matches(it) })
    if (c.theme.iconPack.isNotEmpty() && !PACKAGE_NAME.matches(c.theme.iconPack)) {
        add("theme.iconPack must be a package name (tools/apps.sh --icon-packs lists them), not \"${c.theme.iconPack}\"")
    }
    (c.home.favorites + c.home.folders.flatMap { it.apps } + c.drawer.hidden + c.drawer.only + c.drawer.privateApps).filterNot { PACKAGE_NAME.matches(it) }.forEach {
        add("\"$it\" isn't a package name (like com.android.chrome)")
    }
}
