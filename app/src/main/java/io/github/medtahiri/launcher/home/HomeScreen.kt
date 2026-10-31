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
import io.github.medtahiri.launcher.core.LauncherConfig
import io.github.medtahiri.launcher.core.observeApps
import io.github.medtahiri.launcher.core.writeAppListForTools
import io.github.medtahiri.launcher.home.glass.GlassHome
import io.github.medtahiri.launcher.theme.backgroundColor
import io.github.medtahiri.launcher.widgets.WidgetRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The home screen: widgets above, favorites below, and the drawer on top when it's open. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HomeScreen(
    config: LauncherConfig,
    configProblems: List<String>,
    drawerOpen: Boolean,
    onDrawerOpenChange: (Boolean) -> Unit,
    isDefaultLauncher: Boolean,
    homePresses: Int,
) {
    val context = LocalContext.current
    val apps by remember { observeApps(context.applicationContext) }.collectAsState(initial = emptyList())
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    if (debuggable) {
        LaunchedEffect(apps) { withContext(Dispatchers.IO) { writeAppListForTools(context, apps) } }
    }
    val background = backgroundColor(config.theme, isSystemInDarkTheme() && config.theme.mode == "auto" || config.theme.mode == "dark")

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
                Home(config, configProblems, apps, isDefaultLauncher, onOpenDrawer = { onDrawerOpenChange(true) })
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

@Composable
private fun Home(
    config: LauncherConfig,
    configProblems: List<String>,
    apps: List<AppEntry>,
    isDefaultLauncher: Boolean,
    onOpenDrawer: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .testTag("launcher-home")
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
                    config.home.widgets.forEach { widget ->
                        Box(Modifier.testTag("widget-${widget.type}")) { WidgetRegistry.find(widget.type)?.Content(widget) }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(config.home.spacing.dp)) {
                    favorites(apps, config).chunked(config.home.columns).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(config.home.spacing.dp)) {
                            row.forEach {
                                AppIcon(
                                    it,
                                    config.home.iconSize,
                                    config.home.showLabels,
                                    config.home.allowUninstall,
                                    Modifier.weight(1f).testTag("favorite-${it.packageName}"),
                                )
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
