package io.github.medtahiri.launcher.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.medtahiri.launcher.core.WidgetConfig

/**
 * A piece of the home screen. To add one: create a file in this folder with an object implementing this, and add it
 * to [WidgetRegistry]. launcher.json can then use its type, with any options it reads from [WidgetConfig].
 */
interface LauncherWidget {
    val type: String

    /** What's wrong with this widget's options in launcher.json, so build.sh can report it. */
    fun validate(config: WidgetConfig): List<String> = emptyList()

    /** In the glass style, whether it takes a whole row instead of half of one. */
    fun isWide(config: WidgetConfig): Boolean = false

    @Composable
    fun Content(config: WidgetConfig)
}

object WidgetRegistry {
    private val all: List<LauncherWidget> =
        listOf(
            ClockWidget, AnalogClockWidget, CalendarWidget, BatteryWidget, FocusTimerWidget, AndroidWidget, ContactWidget, HelpButtonWidget,
        )

    val types: Set<String> = all.map { it.type }.toSet()

    fun validate(config: WidgetConfig): List<String> = find(config.type)?.validate(config).orEmpty()

    fun find(type: String): LauncherWidget? = all.firstOrNull { it.type == type }

    fun isWide(config: WidgetConfig): Boolean = find(config.type)?.isWide(config) ?: false

    /** How many of the widgets are Android widgets, each of which keeps what the person put in it. */
    fun androidSlots(widgets: List<WidgetConfig>): Int = widgets.count { it.type == AndroidWidget.type }
}

/** Where a widget is: [androidIndex] counts only Android widgets, and [fillsArea] is true when its size is given to it. */
data class WidgetSlot(val androidIndex: Int, val fillsArea: Boolean)

val LocalWidgetSlot = staticCompositionLocalOf { WidgetSlot(0, false) }

/** Shows the widget at [index] in `home.widgets`. Both home styles use this, so every widget knows its place. */
@Composable
fun WidgetAt(widgets: List<WidgetConfig>, index: Int, fillsArea: Boolean) {
    val widget = widgets[index]
    val androidIndex = widgets.take(index).count { it.type == AndroidWidget.type }
    CompositionLocalProvider(LocalWidgetSlot provides WidgetSlot(androidIndex, fillsArea)) {
        WidgetRegistry.find(widget.type)?.Content(widget)
    }
}
