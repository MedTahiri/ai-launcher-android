package io.github.medtahiri.launcher.home

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.FocusRules
import io.github.medtahiri.launcher.core.OpenGate
import io.github.medtahiri.launcher.core.categoryName
import io.github.medtahiri.launcher.core.openApp
import io.github.medtahiri.launcher.core.IconPack
import io.github.medtahiri.launcher.core.LocalNotificationDots
import io.github.medtahiri.launcher.core.NotificationDots
import io.github.medtahiri.launcher.core.LocalIconPack
import io.github.medtahiri.launcher.core.LauncherConfig
import io.github.medtahiri.launcher.core.observeApps
import io.github.medtahiri.launcher.core.writeAppListForTools
import io.github.medtahiri.launcher.home.glass.GlassHome
import io.github.medtahiri.launcher.theme.backgroundColor
import io.github.medtahiri.launcher.widgets.AppWidgets
import io.github.medtahiri.launcher.widgets.WidgetAt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import java.time.LocalDateTime
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.withContext

/** The home screen: widgets above, favorites below, and the drawer on top when it's open. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    baseConfig: LauncherConfig,
    configProblems: List<String>,
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    isDefaultLauncher: Boolean,
    homePresses: Int,
    resumes: Int,
    showHiddenApps: Boolean,
    onShowHiddenApps: () -> Unit,
    hasPhoneChanges: Boolean,
    onSaveSettings: (Map<String, JsonElement>) -> Unit,
    onResetSettings: () -> Unit,
) {
    val context = LocalContext.current
    val apps by remember { observeApps(context.applicationContext) }.collectAsState(initial = emptyList())

    // Focus rules follow the clock: checked each minute, and on return, since a sleeping phone's timers don't run.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(resumes) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 100)
        }
    }
    val focus = remember(apps, baseConfig.focus, now) {
        FocusRules.now(baseConfig.focus.rules, apps.map { it.packageName to categoryName(it.category) }, now)
    }
    val config = remember(baseConfig, focus.hidden, showHiddenApps) {
        if (showHiddenApps) baseConfig else baseConfig.copy(hiddenNow = focus.hidden)
    }
    SideEffect { OpenGate.pauseFor = { focus.pauses[it.packageName] ?: 0 } }
    val waiting by OpenGate.waiting.collectAsState()
    var customizing by remember { mutableStateOf(false) }
    val openCustomize: (() -> Unit)? = if (baseConfig.home.allowCustomize) ({ customizing = true }) else null
    val focusNote = FocusNote(
        hiddenCount = if (showHiddenApps) 0 else focus.hidden.size,
        until = focus.hiddenUntil,
        onShowAnyway = if (baseConfig.focus.allowShowAnyway) onShowHiddenApps else null,
    )

    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (debuggable) {
        LaunchedEffect(apps) {
            withContext(Dispatchers.IO) {
                writeAppListForTools(context, apps)
                AppWidgets.writeListForTools(context)
                IconPack.writeListForTools(context)
            }
        }
    }
    val background = backgroundColor(config.theme, isSystemInDarkTheme() && config.theme.mode == "auto" || config.theme.mode == "dark")

    val dots by NotificationDots.packages.collectAsState()
    CompositionLocalProvider(
        LocalIconPack provides config.theme.iconPack,
        LocalNotificationDots provides if (config.home.notificationDots) dots else emptySet(),
        LocalFocusNote provides focusNote,
        LocalOpenCustomize provides openCustomize,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .then(if (background != null) Modifier.background(background) else Modifier)
            .semantics { testTagsAsResourceId = true },
    ) {
        if (config.home.style == "glass") {
            GlassHome(config, configProblems, apps, drawerOpen, onDrawerOpenChange, homePresses, isDefaultLauncher)
        } else {
            BackHandler(enabled = drawerOpen) { onDrawerOpenChange(false) }
            BackHandler(enabled = !drawerOpen) {} // Home has nowhere to go back to

            // Hidden from accessibility while the drawer covers it, so screen readers don't read what's behind.
            Box(if (drawerOpen) Modifier.clearAndSetSemantics {} else Modifier) {
                Home(config, configProblems, apps, isDefaultLauncher, openCustomize, onOpenDrawer = { onDrawerOpenChange(true) })
            }
            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn() + slideInVertically { it / 8 },
                exit = fadeOut() + slideOutVertically { it / 8 },
            ) {
                // Over a wallpaper the drawer needs its own background to stay readable.
                AppDrawer(apps, config, background ?: MaterialTheme.colorScheme.background)
            }
        }
        waiting?.let { app ->
            FocusPause(
                app,
                seconds = focus.pauses[app.packageName] ?: 5,
                onOpen = {
                    OpenGate.waiting.value = null
                    openApp(context, app, now = true)
                },
                onCancel = { OpenGate.waiting.value = null },
            )
        }
    }
    if (customizing) {
        CustomizeScreen(
            baseConfig,
            apps,
            hasPhoneChanges,
            onSave = {
                customizing = false
                onSaveSettings(it)
            },
            onReset = {
                customizing = false
                onResetSettings()
            },
            onDismiss = { customizing = false },
        )
    }
    }
}

/** The banner offering to become the default home screen, while it isn't. */
@Composable
fun DefaultPrompt(config: LauncherConfig, isDefaultLauncher: Boolean) {
    if (isDefaultLauncher || !config.home.showDefaultPrompt) return
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            modifier = Modifier.testTag("set-default"),
            onClick = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
        ) {
            Text("Make ${config.name} your home screen")
        }
    }
}

/** While notification dots are on but not allowed yet: a banner that opens the Settings screen to allow them. */
@Composable
fun NotificationDotsPrompt(config: LauncherConfig) {
    if (!config.home.notificationDots) return
    val context = LocalContext.current
    val connected by NotificationDots.connected.collectAsState()
    // Checked again when Android connects or disconnects, so it doesn't flash while connecting at startup.
    val allowed = remember(connected) { connected || NotificationDots.allowed(context) }
    if (allowed) return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(
            modifier = Modifier.testTag("allow-notification-dots"),
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    .onFailure { context.startActivity(Intent(Settings.ACTION_SETTINGS)) } // a phone without that screen
            },
        ) {
            Text("Allow notification dots: find ${config.name} and turn it on")
        }
    }
}

@Composable
private fun Home(
    config: LauncherConfig,
    configProblems: List<String>,
    apps: List<AppEntry>,
    isDefaultLauncher: Boolean,
    openCustomize: (() -> Unit)?,
    onOpenDrawer: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .testTag("launcher-home")
            // Long press on empty space: change the look on the phone.
            .then(if (openCustomize != null) Modifier.pointerInput(openCustomize) { detectTapGestures(onLongPress = { openCustomize() }) } else Modifier)
            .then(
                if (config.home.swipeUpOpensDrawer) {
                    Modifier.pointerInput(Unit) {
                        val distance = 48.dp.toPx()
                        var dragged = 0f
                        detectVerticalDragGestures(onDragStart = { dragged = 0f }) { _, amount ->
                            dragged += amount
                            if (dragged < -distance) { // far enough up, however slowly
                                dragged = 0f
                                onOpenDrawer()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        configProblems.forEach { Text("launcher.json: $it", color = Color(0xFFFF8A80), modifier = Modifier.padding(top = 8.dp)) }
        DefaultPrompt(config, isDefaultLauncher)
        NotificationDotsPrompt(config)
        // Widgets centered above the favorites; it all scrolls if it doesn't fit (large text, or a small screen).
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = rememberScrollState()
            Column(
                Modifier
                    .fillMaxWidth()
                    // Only scrolls when it has to: otherwise it would take the swipe up that opens the drawer.
                    .verticalScroll(scroll, enabled = scroll.maxValue > 0)
                    .heightIn(min = maxHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    config.home.widgets.forEachIndexed { index, widget ->
                        Box(Modifier.testTag("widget-${widget.type}")) { WidgetAt(config.home.widgets, index, fillsArea = false) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(config.home.spacing.dp)) {
                    // Favorites, then folders, in one grid.
                    val items: List<Any> = favorites(apps, config) + folders(apps, config)
                    items.chunked(config.home.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(config.home.spacing.dp)) {
                            row.forEach {
                                when (it) {
                                    is AppEntry -> AppIcon(
                                        it,
                                        config.home.iconSize,
                                        config.home.showLabels,
                                        config.home.allowUninstall,
                                        Modifier.weight(1f).testTag("favorite-${it.packageName}"),
                                    )
                                    is Folder -> FolderIcon(it, config, Modifier.weight(1f).testTag("folder-${it.name}"))
                                }
                            }
                            repeat(config.home.columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
        if (config.home.drawerButton == "button") {
            Button(onClick = onOpenDrawer, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(vertical = 4.dp).testTag("open-drawer")) {
                Text("All apps")
            }
        } else {
            TextButton(onClick = onOpenDrawer, modifier = Modifier.testTag("open-drawer")) {
                Text("All apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
