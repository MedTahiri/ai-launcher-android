package io.github.medtahiri.launcher.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.openApp
import io.github.medtahiri.launcher.core.openAppInfo
import io.github.medtahiri.launcher.core.rememberAppIcon
import io.github.medtahiri.launcher.core.uninstallApp

/** An app's icon and label. Press opens it; long press offers App info and Uninstall. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(app: AppEntry, iconSize: Int, showLabel: Boolean, allowUninstall: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    val sizePx = with(LocalDensity.current) { iconSize.dp.roundToPx() }
    val icon = rememberAppIcon(app, sizePx)
    Box(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClickLabel = "Open ${app.label}",
                    onLongClick = { menuOpen = true },
                    onClick = { openApp(context, app) },
                )
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(iconSize.dp)) {
                icon?.let { Image(it, contentDescription = app.label, modifier = Modifier.size(iconSize.dp)) }
            }
            if (showLabel) {
                Text(
                    app.label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("App info") }, onClick = { menuOpen = false; openAppInfo(context, app) })
            if (allowUninstall) {
                DropdownMenuItem(text = { Text("Uninstall") }, onClick = { menuOpen = false; uninstallApp(context, app) })
            }
        }
    }
}
