package io.github.medtahiri.launcher.home.glass

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

/** Saves what people arrange on the phone: the order of the icons and the apps in the dock. */
class GlassStore(context: Context, private val configFavorites: List<String>) {
    private val prefs = context.getSharedPreferences("glass_layout", Context.MODE_PRIVATE)
    private val packageManager = context.packageManager

    fun loadOrder(): List<String> = list(KEY_ORDER)

    fun saveOrder(keys: List<String>) = save(KEY_ORDER, keys)

    /**
     * launcher.json's favorites, until the person changes the dock on the phone. Changing them in launcher.json wins
     * again, so that a new build always shows what was asked for.
     */
    fun loadDock(installed: Set<String>): List<String> {
        val stamp = configFavorites.joinToString(SEPARATOR)
        if (prefs.getString(KEY_STAMP, null) == stamp && prefs.contains(KEY_DOCK)) return list(KEY_DOCK)
        val dock = configFavorites.ifEmpty { defaultDock(installed) }
        prefs.edit().putString(KEY_STAMP, stamp).apply()
        saveDock(dock)
        return dock
    }

    fun saveDock(packages: List<String>) = save(KEY_DOCK, packages)

    private fun defaultDock(installed: Set<String>): List<String> = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING),
        Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER),
        Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
    ).mapNotNull { packageManager.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        .filter { it in installed }
        .distinct()

    private fun list(key: String) = prefs.getString(key, "").orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }

    private fun save(key: String, items: List<String>) {
        prefs.edit().putString(key, items.joinToString(SEPARATOR)).apply()
    }

    private companion object {
        const val KEY_ORDER = "order"
        const val KEY_DOCK = "dock"
        const val KEY_STAMP = "favorites_stamp"
        const val SEPARATOR = "|"
    }
}
