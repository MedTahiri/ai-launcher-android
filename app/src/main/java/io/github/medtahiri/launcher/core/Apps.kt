package io.github.medtahiri.launcher.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** One icon in the launcher. A package can have several, and a work profile its own copy. */
data class AppEntry(val label: String, val packageName: String, val key: String, val info: LauncherActivityInfo) {
    val user: UserHandle get() = info.user
    val isSystem: Boolean get() = info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
    /** One of ApplicationInfo.CATEGORY_*, as the app reports it. */
    val category: Int get() = info.applicationInfo.category
}

private fun loadApps(context: Context): List<AppEntry> {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return launcherApps.profiles
        .flatMap { user -> launcherApps.getActivityList(null, user) }
        .filter { it.applicationInfo.packageName != context.packageName }
        .map { info ->
            AppEntry(info.label.toString(), info.applicationInfo.packageName, "${info.componentName.flattenToString()}#${info.user.hashCode()}", info)
        }
        .sortedBy { it.label.lowercase() }
}

/** The apps now, and again whenever one is installed, removed or changed. */
fun observeApps(context: Context): Flow<List<AppEntry>> = callbackFlow {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    fun reload() {
        IconCache.clear()
        launch { send(loadApps(context)) }
    }
    val callback = object : LauncherApps.Callback() {
        override fun onPackageAdded(packageName: String, user: UserHandle) = reload()
        override fun onPackageRemoved(packageName: String, user: UserHandle) = reload()
        override fun onPackageChanged(packageName: String, user: UserHandle) = reload()
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }
    send(loadApps(context))
    launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    awaitClose { launcherApps.unregisterCallback(callback) }
}.flowOn(Dispatchers.Default)

private object IconCache {
    private val icons = LruCache<String, ImageBitmap>(300)
    operator fun get(key: String): ImageBitmap? = icons.get(key)
    operator fun set(key: String, icon: ImageBitmap) { icons.put(key, icon) }
    fun clear() = icons.evictAll()
}

/**
 * The app's icon at this size, loaded off the main thread: null until it's ready. With [fullBleed], it fills a square,
 * ready for the caller to clip to a shape of its own, instead of having the phone's icon shape.
 */
@Composable
fun rememberAppIcon(app: AppEntry, sizePx: Int, fullBleed: Boolean = false): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = "${app.key}@$sizePx/$fullBleed"
    // Keyed, so that a list position that now holds another app can never keep showing the old app's icon.
    var icon by remember(cacheKey) { mutableStateOf(IconCache[cacheKey]) }
    LaunchedEffect(cacheKey) {
        if (icon == null) {
            icon = withContext(Dispatchers.Default) {
                val bitmap = if (fullBleed) fullBleedIcon(context, app, sizePx) else app.info.getBadgedIcon(0).toBitmap(sizePx, sizePx)
                bitmap.asImageBitmap().also { IconCache[cacheKey] = it }
            }
        }
    }
    return icon
}

private fun fullBleedIcon(context: Context, app: AppEntry, sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val drawable = app.info.getIcon(0)
    if (drawable is AdaptiveIconDrawable) {
        // Adaptive layers are 108dp, with the visible 72dp in the middle: overdraw by a quarter on each side.
        val inset = sizePx / 4
        listOfNotNull(drawable.background, drawable.foreground).forEach {
            it.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
            it.draw(canvas)
        }
    } else {
        canvas.drawColor(android.graphics.Color.WHITE)
        val pad = sizePx / 8
        drawable.setBounds(pad, pad, sizePx - pad, sizePx - pad)
        drawable.draw(canvas)
    }
    // Keeps the work badge on work profile apps.
    return context.packageManager
        .getUserBadgedIcon(BitmapDrawable(context.resources, bitmap), app.user)
        .toBitmap(sizePx, sizePx)
}

/** Debug builds only: writes the apps to a private file for tools/apps.sh, which agents use to find package names. */
fun writeAppListForTools(context: Context, apps: List<AppEntry>) {
    File(context.filesDir, "apps.tsv").writeText(
        apps.joinToString("") { "${it.label}\t${it.packageName}\t${categoryName(it.info.applicationInfo.category)}\n" },
    )
}

private fun categoryName(category: Int) = when (category) {
    ApplicationInfo.CATEGORY_GAME -> "game"
    ApplicationInfo.CATEGORY_AUDIO -> "audio"
    ApplicationInfo.CATEGORY_VIDEO -> "video"
    ApplicationInfo.CATEGORY_IMAGE -> "image"
    ApplicationInfo.CATEGORY_SOCIAL -> "social"
    ApplicationInfo.CATEGORY_NEWS -> "news"
    ApplicationInfo.CATEGORY_MAPS -> "maps"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
    else -> "other"
}

fun openApp(context: Context, app: AppEntry) {
    context.getSystemService(LauncherApps::class.java).startMainActivity(app.info.componentName, app.user, null, null)
}

fun openAppInfo(context: Context, app: AppEntry) {
    context.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.info.componentName, app.user, null, null)
}

/** Android's own uninstall screen, which asks first. */
fun uninstallApp(context: Context, app: AppEntry) {
    context.startActivity(
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", app.packageName, null))
            .putExtra(Intent.EXTRA_USER, app.user),
    )
}
