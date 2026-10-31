package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.core.parseConfig
import io.github.medtahiri.launcher.home.matchesSearch
import io.github.medtahiri.launcher.widgets.WidgetRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherConfigTest {
    private val types = setOf("clock", "focus_timer")
    private fun problems(text: String) = parseConfig(text, types).problems

    @Test fun emptyObjectUsesDefaults() {
        val parsed = parseConfig("{}", types)
        assertEquals(emptyList<String>(), parsed.problems)
        assertEquals(4, parsed.config.home.columns)
    }

    @Test fun readsSettingsAndWidgetOptions() {
        val parsed = parseConfig(
            """{"name":"Mine","home":{"columns":3,"widgets":[{"type":"focus_timer","options":{"minutes":50}}]}}""",
            types,
        )
        assertEquals(emptyList<String>(), parsed.problems)
        assertEquals("Mine", parsed.config.name)
        assertEquals(50, parsed.config.home.widgets.single().int("minutes", 25))
    }

    @Test fun misspelledSettingIsAProblem() {
        assertEquals(1, problems("""{"home":{"colums":3}}""").size)
    }

    @Test fun brokenJsonIsAProblemNotACrash() {
        val parsed = parseConfig("{ not json", types)
        assertEquals(1, parsed.problems.size)
        assertEquals("My Launcher", parsed.config.name)
    }

    @Test fun unknownWidgetNamesTheKnownOnes() {
        val problem = problems("""{"home":{"widgets":[{"type":"weather"}]}}""").single()
        assertTrue(problem, "weather" in problem && "clock" in problem)
    }

    @Test fun invalidValuesAreProblems() {
        assertEquals(1, problems("""{"theme":{"mode":"blue"}}""").size)
        assertEquals(1, problems("""{"theme":{"background":"black"}}""").size)
        assertEquals(1, problems("""{"theme":{"accent":"#FFF"}}""").size)
        assertEquals(1, problems("""{"theme":{"font":"comic"}}""").size)
        assertEquals(1, problems("""{"theme":{"textScale":9}}""").size)
        assertEquals(1, problems("""{"home":{"columns":0}}""").size)
        assertEquals(1, problems("""{"home":{"iconSize":4}}""").size)
        assertEquals(1, problems("""{"home":{"favorites":["Chrome"]}}""").size)
        assertEquals(1, problems("""{"drawer":{"hidden":["not a package"]}}""").size)
    }

    @Test fun validBackgroundsAreAccepted() {
        listOf("#101820", "wallpaper", "auto").forEach {
            assertEquals(it, emptyList<String>(), problems("""{"theme":{"background":"$it"}}"""))
        }
    }

    @Test fun problemsFallBackToDefaults() {
        assertEquals("My Launcher", parseConfig("""{"name":"Mine","home":{"columns":0}}""", types).config.name)
    }

    @Test fun drawerLayoutIsSeparateFromHome() {
        val parsed = parseConfig("""{"home":{"columns":5,"showLabels":false}}""", types)
        assertEquals(4, parsed.config.drawer.columns)
        assertEquals(true, parsed.config.drawer.showLabels)
    }

    @Test fun realWidgetsCheckTheirOptions() {
        fun widget(json: String) = parseConfig("""{"home":{"widgets":[$json]}}""", WidgetRegistry.types, WidgetRegistry::validate).problems
        assertEquals(emptyList<String>(), widget("""{"type":"clock","options":{"size":"huge"}}"""))
        assertEquals(emptyList<String>(), widget("""{"type":"focus_timer","options":{"minutes":50}}"""))
        assertEquals(1, widget("""{"type":"clock","options":{"size":"gigantic"}}""").size)
        assertEquals(1, widget("""{"type":"focus_timer","options":{"minutes":0}}""").size)
        assertEquals(1, widget("""{"type":"focus_timer","options":{"minutes":"soon"}}""").size)
    }

    @Test fun newHomeSettingsAreChecked() {
        assertEquals(emptyList<String>(), problems("""{"home":{"spacing":12,"drawerButton":"button","swipeUpOpensDrawer":false,"allowUninstall":false},"drawer":{"only":["com.android.chrome"]}}"""))
        assertEquals(1, problems("""{"home":{"spacing":100}}""").size)
        assertEquals(1, problems("""{"home":{"drawerButton":"big"}}""").size)
        assertEquals(1, problems("""{"drawer":{"only":["Chrome"]}}""").size)
    }

    @Test fun styleIsChecked() {
        assertEquals(emptyList<String>(), problems("""{"home":{"style":"glass"}}"""))
        assertEquals(1, problems("""{"home":{"style":"fancy"}}""").size)
    }

    @Test fun searchIgnoresCaseAndAccents() {
        assertTrue(matchesSearch("Téléphone", "telephone"))
        assertTrue(matchesSearch("Paramètres", "PARAM"))
        assertTrue(matchesSearch("Chrome", " chr "))
        assertTrue(matchesSearch("Chrome", ""))
        assertEquals(false, matchesSearch("Chrome", "maps"))
    }
}
