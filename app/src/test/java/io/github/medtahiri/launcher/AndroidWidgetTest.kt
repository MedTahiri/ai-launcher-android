package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.core.parseConfig
import io.github.medtahiri.launcher.widgets.WidgetRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidWidgetTest {
    private fun problems(options: String) =
        parseConfig("""{"home":{"widgets":[{"type":"android_widget","options":$options}]}}""", WidgetRegistry.types, WidgetRegistry::validate).problems

    @Test fun noOptionsIsFine() = assertEquals(emptyList<String>(), problems("{}"))

    @Test fun acceptsAPackageOrAWidgetClass() {
        assertEquals(emptyList<String>(), problems("""{"provider":"com.spotify.music"}"""))
        assertEquals(emptyList<String>(), problems("""{"provider":"com.android.deskclock/.DigitalAppWidgetProvider"}"""))
        assertEquals(emptyList<String>(), problems("""{"provider":"com.example/com.example.widget.Big${'$'}Provider","height":200,"width":"half"}"""))
    }

    @Test fun rejectsBadValues() {
        assertEquals(1, problems("""{"provider":"spotify"}""").size)
        assertEquals(1, problems("""{"height":20}""").size)
        assertEquals(1, problems("""{"width":"wide"}""").size)
        assertEquals(1, problems("""{"size":"big"}""").size)
    }

    @Test fun countsOnlyAndroidWidgetsAsSlots() {
        val config = parseConfig(
            """{"home":{"widgets":[{"type":"android_widget"},{"type":"clock"},{"type":"android_widget"}]}}""",
            WidgetRegistry.types,
        ).config
        assertEquals(2, WidgetRegistry.androidSlots(config.home.widgets))
    }
}
