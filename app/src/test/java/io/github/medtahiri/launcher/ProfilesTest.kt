package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.core.Profiles
import io.github.medtahiri.launcher.core.parseConfig
import io.github.medtahiri.launcher.theme.WallpaperAccent
import io.github.medtahiri.launcher.widgets.WidgetRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ProfilesTest {
    private fun parse(text: String) = parseConfig(text, WidgetRegistry.types, WidgetRegistry::validate)
    private val work = """{"name":"Work","from":"09:00","to":"17:00","days":["weekdays"],"set":{"theme.accent":"#FF8800","home.columns":5}}"""
    private val evening = """{"name":"Evening","set":{"theme.mode":"light"}}"""

    @Test fun activeFollowsHoursUnlessOneIsPicked() {
        val profiles = parse("""{"profiles":[$work,$evening]}""").config.profiles
        val mondayNoon = LocalDateTime.parse("2026-11-09T12:00")
        assertEquals("Work", Profiles.active(profiles, "", mondayNoon)?.name)
        assertNull(Profiles.active(profiles, "", LocalDateTime.parse("2026-11-09T18:00")))
        assertEquals("Evening", Profiles.active(profiles, "Evening", mondayNoon)?.name) // picked, though it has no hours
        assertEquals("Work", Profiles.active(profiles, "Gone", mondayNoon)?.name) // a picked profile that was removed
    }

    @Test fun aProfileThatBreaksTheConfigIsReported() {
        val problems = parse("""{"profiles":[{"name":"Bad","set":{"theme.accent":"orange","home.colums":3}}]}""").problems
        assertEquals(1, problems.size) // the misspelled setting stops it first
        assertTrue(problems.single().startsWith("profiles (Bad)"))
        assertEquals(1, parse("""{"profiles":[{"name":"Bad","set":{"theme.accent":"orange"}}]}""").problems.size)
    }

    @Test fun profileRulesAreChecked() {
        val problems = parse(
            """{"profiles":[{"name":"","set":{}},{"name":"A","from":"09:00","set":{"name":"x"}},{"name":"a","days":["mon"],"set":{"home.columns":3}}]}""",
        ).problems
        // no name, sets nothing, from without to, can't change name, days without hours, two called "a"
        assertEquals(6, problems.size)
    }

    @Test fun gesturesAcceptKnownActionsAndApps() {
        assertEquals(0, parse("""{"gestures":{"doubleTap":"lock","swipeDown":"notifications","swipeRight":"app:com.android.chrome"}}""").problems.size)
        assertEquals(2, parse("""{"gestures":{"doubleTap":"sleep","swipeLeft":"app:chrome"}}""").problems.size)
    }

    @Test fun contactAndHelpWidgetsNeedANumber() {
        assertEquals(0, parse("""{"home":{"widgets":[{"type":"contact","options":{"name":"Mom","phone":"+212 600 000000","action":"whatsapp"}}]}}""").problems.size)
        assertEquals(3, parse("""{"home":{"widgets":[{"type":"contact","options":{"phone":"call me","action":"video"}}]}}""").problems.size)
        assertEquals(1, parse("""{"home":{"widgets":[{"type":"help_button"}]}}""").problems.size)
    }

    @Test fun accentCanComeFromTheWallpaperAndPrivateAppsArePackages() {
        assertEquals(0, parse("""{"theme":{"accent":"wallpaper"},"drawer":{"private":["com.bank.app"]}}""").problems.size)
        assertEquals(1, parse("""{"drawer":{"private":["My bank"]}}""").problems.size)
    }

    @Test fun wallpaperAccentIsMadeReadable() {
        val darkBlue = 0x0A1A3A
        assertTrue(WallpaperAccent.luminance(WallpaperAccent.readable(darkBlue, darkBackground = true)) >= 0.35)
        val paleYellow = 0xFFF4C0
        assertTrue(WallpaperAccent.luminance(WallpaperAccent.readable(paleYellow, darkBackground = false)) <= 0.2)
        assertEquals(0xA8C7FA, WallpaperAccent.readable(0xA8C7FA, darkBackground = true)) // already fine: unchanged
        assertEquals("#0A1A3A", WallpaperAccent.hex(darkBlue))
    }
}
