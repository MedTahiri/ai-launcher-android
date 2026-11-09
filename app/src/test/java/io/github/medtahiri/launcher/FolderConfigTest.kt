package io.github.medtahiri.launcher

import io.github.medtahiri.launcher.core.parseConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderConfigTest {
    private fun problems(folders: String) = parseConfig("""{"home":{"folders":$folders}}""", emptySet()).problems

    @Test fun readsFolders() {
        val parsed = parseConfig("""{"home":{"folders":[{"name":"Social","apps":["com.whatsapp","org.telegram.messenger"]}]}}""", emptySet())
        assertEquals(emptyList<String>(), parsed.problems)
        assertEquals(listOf("com.whatsapp", "org.telegram.messenger"), parsed.config.home.folders.single().apps)
    }

    @Test fun aFolderNeedsANameAndApps() {
        assertEquals(1, problems("""[{"name":" ","apps":["com.whatsapp"]}]""").size)
        assertEquals(1, problems("""[{"name":"Empty","apps":[]}]""").size)
    }

    @Test fun folderNamesAreUnique() {
        assertEquals(1, problems("""[{"name":"Work","apps":["com.slack"]},{"name":"work","apps":["com.zoom.us"]}]""").size)
    }

    @Test fun folderAppsMustBePackageNames() {
        assertEquals(1, problems("""[{"name":"Chat","apps":["WhatsApp"]}]""").size)
    }
}

class IconPackTest {
    @Test fun readsComponentsAsPacksWriteThem() {
        assertEquals("com.app/com.app.Main", io.github.medtahiri.launcher.core.IconPack.componentKey("ComponentInfo{com.app/com.app.Main}"))
        assertEquals("com.app/com.app.Main", io.github.medtahiri.launcher.core.IconPack.componentKey("ComponentInfo{com.app/.Main}"))
        assertEquals(null, io.github.medtahiri.launcher.core.IconPack.componentKey("ComponentInfo{:LAUNCHER_ACTION_APP_DRAWER}"))
    }

    @Test fun iconPackMustBeAPackageName() {
        assertEquals(1, io.github.medtahiri.launcher.core.parseConfig("""{"theme":{"iconPack":"My pack"}}""", emptySet()).problems.size)
        assertEquals(0, io.github.medtahiri.launcher.core.parseConfig("""{"theme":{"iconPack":"com.example.pack"}}""", emptySet()).problems.size)
    }
}
