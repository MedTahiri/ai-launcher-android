package io.github.medtahiri.launcher.core

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File

/**
 * Debug builds only: a launcher.json pushed by tools/push-config.sh, used instead of the packaged one so that a settings
 * change shows in a second, with no build. Installing a new build makes it stale, and it's dropped: the build's own
 * launcher.json is newer.
 */
object LiveConfig {
    private const val FILE = "launcher-live.json"
    private const val STATUS = "config-status.txt"

    private fun debuggable(context: Context) = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** The pushed launcher.json, if there's one newer than this install. */
    fun read(context: Context): String? {
        if (!debuggable(context)) return null
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (file.lastModified() < installed) {
            file.delete()
            return null
        }
        return runCatching { file.readText() }.getOrNull()
    }

    /** For tools/push-config.sh: "ok", or what's wrong with the config, one problem a line. */
    fun reportStatus(context: Context, problems: List<String>) {
        if (!debuggable(context)) return
        File(context.filesDir, STATUS).writeText(if (problems.isEmpty()) "ok\n" else problems.joinToString("") { "$it\n" })
    }
}
