package io.github.medtahiri.launcher.home

import androidx.compose.ui.res.stringResource
import io.github.medtahiri.launcher.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.LauncherConfig
import io.github.medtahiri.launcher.core.LocalNotificationDots
import io.github.medtahiri.launcher.core.NotificationDot
import io.github.medtahiri.launcher.core.rememberAppIcon

/** A folder on the home screen: its first four icons in a square, and its name. Press opens it. */
@Composable
fun FolderIcon(folder: Folder, config: LauncherConfig, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val iconSize = config.home.iconSize
    Column(
        modifier
            .clickable(onClickLabel = stringResource(R.string.open_folder, folder.name)) { open = true }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val gap = (iconSize / 14).coerceAtLeast(2)
        val mini = (iconSize - gap * 3) / 2
        Box(Modifier.size(iconSize.dp)) {
            Box(
                Modifier
                    .size(iconSize.dp)
                    .clip(RoundedCornerShape((iconSize / 4).dp))
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.14f))
                    .padding(gap.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
                    folder.apps.take(4).chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                            pair.forEach { MiniIcon(it, mini) }
                        }
                    }
                }
            }
            // One dot for the folder, if any app in it has notifications.
            val dots = LocalNotificationDots.current
            if (folder.apps.any { it.packageName in dots }) NotificationDot(Modifier.align(Alignment.TopEnd), (iconSize / 4.5f).dp)
        }
        if (config.home.showLabels) {
            Text(
                folder.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
    if (open) FolderSheet(folder, config, onDismiss = { open = false })
}

@Composable
private fun MiniIcon(app: AppEntry, sizeDp: Int) {
    val px = with(LocalDensity.current) { sizeDp.dp.roundToPx() }
    Box(Modifier.size(sizeDp.dp)) {
        rememberAppIcon(app, px)?.let { Image(it, contentDescription = null, modifier = Modifier.size(sizeDp.dp)) }
    }
}

/** An open folder: its apps in a grid, over the home screen. Back or a tap outside closes it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FolderSheet(folder: Folder, config: LauncherConfig, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().semantics { testTagsAsResourceId = true }.testTag("open-folder"),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(folder.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                val columns = config.home.columns.coerceIn(3, 4)
                folder.apps.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach {
                            // Closes as it opens the app, so the folder isn't still open on return.
                            AppIcon(it, config.home.iconSize.coerceAtMost(64), true, config.home.allowUninstall, Modifier.weight(1f), onOpened = onDismiss)
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
