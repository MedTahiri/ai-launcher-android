package io.github.medtahiri.launcher.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The launcher's settings, from launcher.json. New settings need a default, so older files keep working. */
@Serializable
data class LauncherConfig(
    val name: String = "My Launcher",
    val theme: ThemeConfig = ThemeConfig(),
    val home: HomeConfig = HomeConfig(),
    val drawer: DrawerConfig = DrawerConfig(),
)

@Serializable
data class ThemeConfig(
    /** "dark", "light" or "auto" (follows the system). */
    val mode: String = "dark",
    /** "#RRGGBB", "wallpaper" (the system wallpaper) or "auto" (from the mode). */
    val background: String = "#000000",
    /** "#RRGGBB". */
    val accent: String = "#FFFFFF",
    /** "default", "sans", "serif" or "mono". */
    val font: String = "default",
    /** Multiplies all text: 1.0 is normal. */
    val textScale: Float = 1f,
)

@Serializable
data class HomeConfig(
    /** "simple" (widgets and favorites) or "glass" (home pages, dock, search and editing, over the wallpaper). */
    val style: String = "simple",
    val widgets: List<WidgetConfig> = emptyList(),
    /** Package names, in order. Apps that aren't installed are skipped. */
    val favorites: List<String> = emptyList(),
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
)

/** A config and what's wrong with it. With problems, [config] is the defaults: the launcher still opens. */
data class ParsedConfig(val config: LauncherConfig, val problems: List<String>)

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
    val problems = validate(config, widgetTypes, validateWidget)
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
    if (!HEX_COLOR.matches(c.theme.accent)) add("theme.accent must be #RRGGBB, not \"${c.theme.accent}\"")
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
    (c.home.favorites + c.drawer.hidden + c.drawer.only).filterNot { PACKAGE_NAME.matches(it) }.forEach {
        add("\"$it\" isn't a package name (like com.android.chrome)")
    }
}
