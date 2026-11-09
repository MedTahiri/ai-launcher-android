package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.core.PhoneSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneSettingsTest {
    private fun json(text: String) = Json.parseToJsonElement(text).jsonObject
    private val empty = JsonObject(emptyMap())

    @Test fun aPhoneChangeApplies() {
        val packaged = json("""{"theme":{"accent":"#FFFFFF","mode":"dark"}}""")
        val saved = PhoneSettings.record(packaged, empty, "theme.accent", JsonPrimitive("#FF8800"))
        assertEquals(json("""{"theme":{"accent":"#FF8800","mode":"dark"}}"""), PhoneSettings.apply(packaged, saved))
    }

    @Test fun aNewerLauncherJsonValueWins() {
        val old = json("""{"theme":{"accent":"#FFFFFF"}}""")
        val saved = PhoneSettings.record(old, empty, "theme.accent", JsonPrimitive("#FF8800"))
        val rebuilt = json("""{"theme":{"accent":"#00FF00"}}""")
        assertEquals(rebuilt, PhoneSettings.apply(rebuilt, saved))
        assertEquals(empty, PhoneSettings.current(rebuilt, saved))
    }

    @Test fun otherSettingsKeepThePhonesValue() {
        val old = json("""{"theme":{"accent":"#FFFFFF"},"home":{"columns":4}}""")
        val saved = PhoneSettings.record(old, empty, "home.columns", JsonPrimitive(5))
        val rebuilt = json("""{"theme":{"accent":"#00FF00"},"home":{"columns":4}}""")
        assertEquals(json("""{"theme":{"accent":"#00FF00"},"home":{"columns":5}}"""), PhoneSettings.apply(rebuilt, saved))
    }

    @Test fun aSettingLauncherJsonDoesntHaveIsAdded() {
        val packaged = json("""{"name":"Mine"}""")
        val saved = PhoneSettings.record(packaged, empty, "home.iconSize", JsonPrimitive(72))
        assertEquals(json("""{"name":"Mine","home":{"iconSize":72}}"""), PhoneSettings.apply(packaged, saved))
    }

    @Test fun settingItBackRemovesTheChange() {
        val packaged = json("""{"home":{"columns":4}}""")
        val saved = PhoneSettings.record(packaged, empty, "home.columns", JsonPrimitive(5))
        assertEquals(empty, PhoneSettings.record(packaged, saved, "home.columns", JsonPrimitive(4)))
    }
}

class CustomizeChangesTest {
    @Test fun listsOnlyWhatChanged() {
        val before = io.github.medtahiri.launcher.core.LauncherConfig()
        val after = before.copy(
            theme = before.theme.copy(accent = "#FF8800", textScale = 1.2f),
            home = before.home.copy(favorites = listOf("com.android.chrome")),
        )
        val changes = PhoneSettings.changes(before, after)
        assertEquals(setOf("theme.accent", "theme.textScale", "home.favorites"), changes.keys)
        assertEquals("1.2", (changes["theme.textScale"] as JsonPrimitive).content)
    }
}
