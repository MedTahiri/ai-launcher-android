package io.github.medtahiri.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.LauncherConfig

/** All apps, with search. */
@Composable
fun AppDrawer(apps: List<AppEntry>, config: LauncherConfig, background: Color) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, config, query) { visibleInDrawer(apps, config, query) }
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .pointerInput(Unit) { detectTapGestures {} } // taps on empty space stop here, not on the home screen below
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .testTag("app-drawer"),
    ) {
        if (config.drawer.search) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("drawer-search"),
            )
        }
        if (shown.isEmpty()) {
            Text("No apps found", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        LazyVerticalGrid(columns = GridCells.Fixed(config.drawer.columns), modifier = Modifier.fillMaxSize()) {
            items(shown, key = { it.key }) { app ->
                AppIcon(app, config.drawer.iconSize, config.drawer.showLabels, config.home.allowUninstall)
            }
        }
    }
}
