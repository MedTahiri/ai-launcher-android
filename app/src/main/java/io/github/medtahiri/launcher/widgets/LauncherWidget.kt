package io.github.medtahiri.launcher.widgets

import androidx.compose.runtime.Composable
import io.github.medtahiri.launcher.core.WidgetConfig

/**
 * A piece of the home screen. To add one: create a file in this folder with an object implementing this, and add it
 * to [WidgetRegistry]. launcher.json can then use its type, with any options it reads from [WidgetConfig].
 */
interface LauncherWidget {
    val type: String

    /** What's wrong with this widget's options in launcher.json, so build.sh can report it. */
    fun validate(config: WidgetConfig): List<String> = emptyList()

    @Composable
    fun Content(config: WidgetConfig)
}

object WidgetRegistry {
    private val all: List<LauncherWidget> = listOf(ClockWidget, AnalogClockWidget, CalendarWidget, BatteryWidget, FocusTimerWidget)

    val types: Set<String> = all.map { it.type }.toSet()

    fun validate(config: WidgetConfig): List<String> = find(config.type)?.validate(config).orEmpty()

    fun find(type: String): LauncherWidget? = all.firstOrNull { it.type == type }
}
