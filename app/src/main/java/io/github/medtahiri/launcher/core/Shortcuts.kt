package io.github.medtahiri.launcher.core

import io.github.medtahiri.launcher.R
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * App shortcuts, like "New message" or "New tab". Android only shares them with the default home screen; otherwise
 * these return nothing. Call them off the main thread.
 */
object Shortcuts {
    private const val FLAGS = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST

    private fun query(context: Context, build: (LauncherApps.ShortcutQuery) -> Unit, user: android.os.UserHandle?): List<ShortcutInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        return try {
            if (!launcherApps.hasShortcutHostPermission()) return emptyList()
            val users = user?.let { listOf(it) } ?: launcherApps.profiles
            users.flatMap { u -> launcherApps.getShortcuts(LauncherApps.ShortcutQuery().also(build), u).orEmpty() }.filter { it.isEnabled }
        } catch (e: Exception) { // SecurityException, or IllegalStateException while the phone is locked
            Log.w("Shortcuts", "Can't read shortcuts", e)
            emptyList()
        }
    }

    /** One app's shortcuts, in its own order, at most [max]. */
    fun forApp(context: Context, app: AppEntry, max: Int = 4): List<ShortcutInfo> =
        query(context, { it.setQueryFlags(FLAGS).setPackage(app.packageName).setActivity(app.info.componentName) }, app.user)
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(max)

    /** Every app's shortcuts, for search. */
    fun all(context: Context): List<ShortcutInfo> =
        query(context, { it.setQueryFlags(FLAGS or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED) }, null)
            .distinctBy { it.`package` + "/" + it.id + "@" + it.userHandle.hashCode() }

    fun start(context: Context, shortcut: ShortcutInfo) {
        runCatching { context.getSystemService(LauncherApps::class.java).startShortcut(shortcut, null, null) }
            .onFailure { Toast.makeText(context, R.string.could_not_open, Toast.LENGTH_SHORT).show() }
    }

    fun icon(context: Context, shortcut: ShortcutInfo, sizePx: Int): ImageBitmap? = runCatching {
        context.getSystemService(LauncherApps::class.java)
            .getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
            ?.toBitmap(sizePx, sizePx)?.asImageBitmap()
    }.getOrNull()

    fun label(shortcut: ShortcutInfo): String = (shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id).toString()
}
