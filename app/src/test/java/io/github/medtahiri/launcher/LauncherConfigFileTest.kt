package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.core.parseConfig
import io.github.medtahiri.launcher.widgets.WidgetRegistry
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/** Checks the repo's real launcher.json, so build.sh reports a mistake before it reaches the phone. */
class LauncherConfigFileTest {
    @Test fun launcherJsonIsValid() {
        val file = File(System.getProperty("launcher.config")!!)
        val parsed = parseConfig(file.readText(), WidgetRegistry.types, WidgetRegistry::validate)
        if (parsed.problems.isNotEmpty()) fail("launcher.json:\n" + parsed.problems.joinToString("\n") { "  - $it" })

        // What tools/verify.sh then looks for on the screen.
        val expected = parsed.config.home.widgets.map { "widget-${it.type}" } +
            parsed.config.home.favorites.map { "favorite-$it" }
        File(System.getProperty("launcher.expected")!!).apply { parentFile.mkdirs() }.writeText(expected.joinToString("") { "$it\n" })
    }
}
