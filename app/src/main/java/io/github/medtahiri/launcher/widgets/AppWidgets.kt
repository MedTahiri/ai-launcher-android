package io.github.medtahiri.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import kotlin.math.abs

/**
 * Hosts Android's own home screen widgets: the ones other apps provide, like a music player or a weather app.
 *
 * Each `android_widget` in launcher.json is a slot, named by its position among them. A slot remembers the widget the
 * person put in it, in preferences, so it survives restarts. Binding a widget needs the person's consent: Android shows
 * its own "allow this launcher to create widgets" screen the first time, so the launcher needs no permission for it.
 */
object AppWidgets {
    private const val HOST_ID = 0x4C41 // any fixed number: it names this launcher's widgets to Android
    private const val PREFS = "app_widgets"
    const val REQUEST_CONFIGURE = 0x4C42

    private var host: LauncherWidgetHost? = null
    private val _changes = MutableStateFlow(0)

    /** Changes whenever a slot gets, loses or changes its widget, so the home screen reads them again. */
    val changes: StateFlow<Int> = _changes

    private var pendingConfigure: Triple<String, Int, String>? = null

    fun host(context: Context): AppWidgetHost =
        host ?: LauncherWidgetHost(context.applicationContext, HOST_ID).also { host = it }

    fun manager(context: Context): AppWidgetManager = AppWidgetManager.getInstance(context)

    /** A widget in a slot: its id with Android, and what it is. */
    data class Bound(val id: Int, val info: AppWidgetProviderInfo)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * The widget in [slot], if it still exists and still matches [spec] (the slot's `provider` option, or "" for one the
     * person picked). A slot whose provider changed in launcher.json, or whose app was uninstalled, is emptied.
     */
    fun bound(context: Context, slot: String, spec: String): Bound? {
        val saved = prefs(context).getString(slot, null) ?: return null
        val id = saved.substringBefore('|').toIntOrNull()
        val savedSpec = saved.substringAfter('|', "")
        val info = id?.let { manager(context).getAppWidgetInfo(it) }
        if (id == null || info == null || savedSpec != spec) {
            clear(context, slot, notify = false) // read while drawing: the caller already knows it's empty
            return null
        }
        return Bound(id, info)
    }

    fun save(context: Context, slot: String, id: Int, spec: String) {
        val previous = prefs(context).getString(slot, null)?.substringBefore('|')?.toIntOrNull()
        if (previous != null && previous != id) host(context).deleteAppWidgetId(previous) // a changed widget
        prefs(context).edit().putString(slot, "$id|$spec").apply()
        _changes.value++
    }

    fun clear(context: Context, slot: String, notify: Boolean = true) {
        val saved = prefs(context).getString(slot, null) ?: return
        saved.substringBefore('|').toIntOrNull()?.let { host(context).deleteAppWidgetId(it) }
        prefs(context).edit().remove(slot).apply()
        if (notify) _changes.value++
    }

    /** Frees widgets of slots that launcher.json no longer has, and any Android still holds for this launcher by mistake. */
    fun cleanUp(context: Context, slotCount: Int) {
        val prefs = prefs(context)
        prefs.all.keys.filter { (it.removePrefix("slot").toIntOrNull() ?: Int.MAX_VALUE) >= slotCount }.forEach { clear(context, it) }
        val inUse = prefs.all.values.mapNotNull { (it as? String)?.substringBefore('|')?.toIntOrNull() }.toSet()
        val host = host(context)
        host.appWidgetIds.filterNot { it in inUse }.forEach { host.deleteAppWidgetId(it) }
    }

    /** The widgets that apps on this phone offer, by app name and then widget name. */
    fun providers(context: Context): List<AppWidgetProviderInfo> {
        val pm = context.packageManager
        return manager(context).installedProviders.sortedWith(
            compareBy({ appLabel(context, it.provider.packageName).lowercase() }, { it.loadLabel(pm).lowercase() }),
        )
    }

    fun appLabel(context: Context, packageName: String): String = runCatching {
        context.packageManager.run { getApplicationLabel(getApplicationInfo(packageName, 0)).toString() }
    }.getOrDefault(packageName)

    /**
     * The widgets matching a slot's `provider` option: a package name gives all of that app's widgets, and
     * `package/class` exactly one.
     */
    fun matching(context: Context, spec: String): List<AppWidgetProviderInfo> {
        val all = manager(context).installedProviders
        if ('/' in spec) {
            val component = ComponentName.unflattenFromString(spec) ?: return emptyList()
            return all.filter { it.provider == component }
        }
        return all.filter { it.provider.packageName == spec }
    }

    /**
     * Opens the widget's own setup screen if it has one it needs, after which [onActivityResult] keeps or frees it.
     * Returns false if there's nothing to set up, so the caller saves it straight away.
     */
    fun startConfigure(activity: Activity, slot: String, id: Int, spec: String, info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        ) {
            return false
        }
        pendingConfigure = Triple(slot, id, spec)
        return try {
            host(activity).startAppWidgetConfigureActivityForResult(activity, id, 0, REQUEST_CONFIGURE, null)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("AppWidgets", "Can't open the setup of ${info.provider}", e)
            pendingConfigure = null
            false
        } catch (e: SecurityException) {
            Log.w("AppWidgets", "Can't open the setup of ${info.provider}", e)
            pendingConfigure = null
            false
        }
    }

    /** From HomeActivity: the widget's setup screen closed. Cancelled means the person doesn't want the widget. */
    fun onActivityResult(context: Context, requestCode: Int, resultCode: Int) {
        if (requestCode != REQUEST_CONFIGURE) return
        val (slot, id, spec) = pendingConfigure ?: return
        pendingConfigure = null
        if (resultCode == Activity.RESULT_OK) save(context, slot, id, spec) else host(context).deleteAppWidgetId(id)
    }

    fun startListening(context: Context) {
        runCatching { host(context).startListening() }.onFailure { Log.w("AppWidgets", "Can't start widgets", it) }
        _changes.value++ // an app may have been removed or updated while away
    }

    fun stopListening(context: Context) {
        runCatching { host(context).stopListening() }
    }

    /**
     * The size a widget is designed for, in dp. Many phones' widgets (Samsung's, for one) give a size in home screen
     * cells rather than a minimum in dp, so both are used.
     */
    fun naturalSizeDp(info: AppWidgetProviderInfo, density: Float): Pair<Int, Int> {
        val cells = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellWidth to info.targetCellHeight else 0 to 0
        val minW = (info.minWidth / density).toInt()
        val minH = (info.minHeight / density).toInt()
        val w = when { minW > 0 -> minW; cells.first > 0 -> cells.first * 80; else -> 160 }
        val h = when { minH > 0 -> minH; cells.second > 0 -> cells.second * 90; else -> 120 }
        return w to h
    }

    /** "2 × 2" for widgets sized in cells, or "250 × 110 dp". */
    fun sizeLabel(info: AppWidgetProviderInfo, density: Float): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
            return "${info.targetCellWidth} × ${info.targetCellHeight}"
        }
        val (w, h) = naturalSizeDp(info, density)
        return "$w × $h dp"
    }

    /** Debug builds only: the widgets on the phone, for tools/apps.sh --widgets. */
    fun writeListForTools(context: Context) {
        val pm = context.packageManager
        File(context.filesDir, "widgets.tsv").writeText(
            providers(context).joinToString("") {
                val size = sizeLabel(it, context.resources.displayMetrics.density)
                "${appLabel(context, it.provider.packageName)}\t${it.loadLabel(pm)}\t${it.provider.flattenToShortString()}\t$size\n"
            },
        )
    }
}

private class LauncherWidgetHost(context: Context, id: Int) : AppWidgetHost(context, id) {
    override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
        LongPressWidgetView(context)
}

/**
 * A widget's view that also reports a long press anywhere on it, even over its buttons, so the person can remove or
 * change it. Widgets handle their own touches, so this has to watch them before they do.
 */
class LongPressWidgetView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val longPress = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        cancelChildren() // so the button under the finger doesn't also act
        onLongPress?.invoke()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = event.x
                downY = event.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
            MotionEvent.ACTION_POINTER_DOWN -> removeCallbacks(longPress)
        }
        if (fired) return true
        super.dispatchTouchEvent(event)
        return true // keep getting this touch, to see whether it becomes a long press
    }

    private fun cancelChildren() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }
}
