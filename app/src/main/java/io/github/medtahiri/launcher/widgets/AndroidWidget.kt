package io.github.medtahiri.launcher.widgets

import androidx.compose.ui.res.stringResource
import io.github.medtahiri.launcher.R
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.requiredSize
import android.widget.RemoteViews
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.util.Log
import android.os.Build
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import io.github.medtahiri.launcher.core.WidgetConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A home screen widget from another app, like a music player or weather. The person chooses it on the phone, or the
 * `provider` option names the app (or the exact widget) so a tap adds that one. Long press to change or remove it.
 */
object AndroidWidget : LauncherWidget {
    override val type = "android_widget"

    private val OPTIONS = setOf("provider", "height", "width")
    private val PACKAGE = "[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+"
    private val PROVIDER = Regex("$PACKAGE(/\\.?[a-zA-Z_][a-zA-Z0-9_\$]*(\\.[a-zA-Z_][a-zA-Z0-9_\$]*)*)?")

    override fun validate(config: WidgetConfig): List<String> = buildList {
        (config.options.keys - OPTIONS).forEach { add("unknown option \"$it\". Options: ${OPTIONS.sorted().joinToString()}") }
        config.options["provider"]?.let {
            if (!it.isString || !PROVIDER.matches(it.content)) {
                add("provider must be a package name or package/class (tools/apps.sh --widgets lists them), not ${it}")
            }
        }
        config.options["height"]?.let { if (config.int("height", -1) !in 48..480) add("height must be from 48 to 480 (dp), not ${it.content}") }
        config.options["width"]?.let { if (it.content !in setOf("full", "half")) add("width must be full or half, not \"${it.content}\"") }
    }

    /** In the glass style, it takes a whole row unless `width` is half. */
    override fun isWide(config: WidgetConfig): Boolean = config.string("width", "full") == "full"

    @Composable
    override fun Content(config: WidgetConfig) {
        val slot = "slot${LocalWidgetSlot.current.androidIndex}"
        AppWidgetSlot(config, slot, LocalWidgetSlot.current.fillsArea)
    }
}

@Composable
private fun AppWidgetSlot(config: WidgetConfig, slot: String, fillsArea: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val spec = config.string("provider", "")
    val version by AppWidgets.changes.collectAsState()
    val bound = remember(version, slot, spec) { AppWidgets.bound(context, slot, spec) }
    var picking by remember { mutableStateOf<List<AppWidgetProviderInfo>?>(null) }
    var pendingBind by remember { mutableStateOf<Pair<Int, AppWidgetProviderInfo>?>(null) }

    fun finish(id: Int, info: AppWidgetProviderInfo) {
        if (activity == null || !AppWidgets.startConfigure(activity, slot, id, spec, info)) AppWidgets.save(context, slot, id, spec)
    }

    // Android's "allow this launcher to create widgets" screen, shown when the launcher hasn't been allowed yet.
    val askToBind = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val (id, info) = pendingBind ?: return@rememberLauncherForActivityResult
        pendingBind = null
        if (result.resultCode == Activity.RESULT_OK) finish(id, info) else AppWidgets.host(context).deleteAppWidgetId(id)
    }

    fun bind(info: AppWidgetProviderInfo) {
        val id = AppWidgets.host(context).allocateAppWidgetId()
        if (AppWidgets.manager(context).bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            finish(id, info)
        } else {
            pendingBind = id to info
            askToBind.launch(
                android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile),
            )
        }
    }

    fun choose() {
        val matching = if (spec.isEmpty()) emptyList() else AppWidgets.matching(context, spec)
        if (matching.size == 1) bind(matching[0]) else picking = matching.ifEmpty { AppWidgets.providers(context) }
    }

    val density = LocalDensity.current
    val height = config.int("height", -1).takeIf { it > 0 }
        ?: bound?.let { AppWidgets.naturalSizeDp(it.info, density.density).second.coerceIn(64, 400) }
        ?: 96
    val area = if (fillsArea) Modifier.fillMaxSize() else Modifier.fillMaxWidth().height(height.dp)

    if (bound == null) {
        Placeholder(spec, area, onClick = ::choose)
    } else {
        key(bound.id) {
            var menuOpen by remember { mutableStateOf(false) }
            Box(area) {
                AndroidView(
                    factory = { ctx -> AppWidgets.host(ctx).createView(ctx, bound.id, bound.info) },
                    update = { view -> (view as? LongPressWidgetView)?.onLongPress = { menuOpen = true } },
                    modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                        // The view isn't reachable from here, so ask Android directly with the same sizes.
                        val options = android.os.Bundle()
                        AppWidgets.manager(context).getAppWidgetOptions(bound.id)?.let { options.putAll(it) }
                        val w = (size.width / density.density).toInt()
                        val h = (size.height / density.density).toInt()
                        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
                        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
                        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            options.putParcelableArrayList(
                                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                                arrayListOf(android.util.SizeF(w.toFloat(), h.toFloat())),
                            )
                        }
                        AppWidgets.manager(context).updateAppWidgetOptions(bound.id, options)
                    },
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.change_widget)) }, onClick = { menuOpen = false; choose() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.remove_widget)) }, onClick = { menuOpen = false; AppWidgets.clear(context, slot) })
                }
            }
        }
    }

    picking?.let { options ->
        WidgetPicker(options, onDismiss = { picking = null }, onPick = { picking = null; bind(it) })
    }
}

@Composable
private fun Placeholder(spec: String, modifier: Modifier, onClick: () -> Unit) {
    val context = LocalContext.current
    val label = remember(spec) {
        val matching = if (spec.isEmpty()) emptyList() else AppWidgets.matching(context, spec)
        when {
            spec.isEmpty() -> context.getString(R.string.add_a_widget)
            matching.isEmpty() -> context.getString(R.string.widget_none_here, AppWidgets.appLabel(context, spec.substringBefore('/')))
            matching.size == 1 -> context.getString(R.string.add_named, matching[0].loadLabel(context.packageManager))
            else -> context.getString(R.string.add_app_widget, AppWidgets.appLabel(context, spec))
        }
    }
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f), shape)
            .clickable(onClickLabel = label, onClick = onClick)
            .testTag("add-widget")
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("+  $label", color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
    }
}

/** The widgets to choose from, grouped by app, each with its preview. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WidgetPicker(options: List<AppWidgetProviderInfo>, onDismiss: () -> Unit, onPick: (AppWidgetProviderInfo) -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().testTag("widget-picker")) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.choose_widget), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
                if (options.isEmpty()) {
                    Text(stringResource(R.string.no_widgets), modifier = Modifier.padding(16.dp))
                }
                val grouped = remember(options) { options.groupBy { it.provider.packageName }.toList() }
                LazyColumn(Modifier.fillMaxWidth()) {
                    grouped.forEach { (packageName, widgets) ->
                        item(key = packageName) {
                            Text(
                                AppWidgets.appLabel(context, packageName),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                            )
                        }
                        items(widgets, key = { it.provider.flattenToString() }) { info ->
                            WidgetChoice(info) { onPick(info) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetChoice(info: AppWidgetProviderInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    // Newer widgets often have a preview layout instead of an image: the widget itself, drawn with sample content.
    val layoutPreview = remember(info.provider) { inflatePreview(context, info) }
    var preview by remember(info.provider) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(info.provider) {
        if (layoutPreview != null) return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            runCatching {
                val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0)
                val maxPx = (160 * density).toInt()
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: maxPx
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: maxPx
                val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
                drawable.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
            }.getOrNull()
        }
    }
    val label = remember(info.provider) { info.loadLabel(context.packageManager) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = stringResource(R.string.add_named, label), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(120.dp).clipToBounds(), contentAlignment = Alignment.Center) {
            if (layoutPreview != null) {
                LayoutPreview(info, layoutPreview, 120.dp)
                Box(Modifier.matchParentSize().clickable(onClickLabel = stringResource(R.string.add_named, label), onClick = onClick)) // its own buttons stay inert
            } else {
                preview?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            }
        }
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                AppWidgets.sizeLabel(info, density),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A widget's preview layout, if it has one and it can be drawn here. It's drawn with the phone's default theme, which
 * the layouts of some phones' own widgets depend on; one that still fails falls back to an image.
 */
private fun inflatePreview(context: android.content.Context, info: AppWidgetProviderInfo): android.view.View? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || info.previewLayout == 0 || info.previewImage != 0) return null
    val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
    return runCatching { RemoteViews(info.provider.packageName, info.previewLayout).apply(themed, FrameLayout(themed)) }
        .onFailure { Log.w("AndroidWidget", "No preview layout for ${info.provider}: ${it.message}") }
        .getOrNull()
}

/** A preview layout, drawn at the size the widget is designed for and scaled down to fit [box]. */
@Composable
private fun LayoutPreview(info: AppWidgetProviderInfo, view: android.view.View, box: Dp) {
    val density = LocalDensity.current.density
    val (w, h) = AppWidgets.naturalSizeDp(info, density)
    val scale = minOf(1f, box.value / w, box.value / h)
    AndroidView(
        factory = { ctx -> FrameLayout(ctx).apply { (view.parent as? android.view.ViewGroup)?.removeView(view); addView(view) } },
        modifier = Modifier.requiredSize(w.dp, h.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    )
}
