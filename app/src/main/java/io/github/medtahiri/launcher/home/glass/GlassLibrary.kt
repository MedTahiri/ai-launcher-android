package io.github.medtahiri.launcher.home.glass

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.theme.GlassSecondaryText
import io.github.medtahiri.launcher.theme.glassSurface

private val categories = listOf(
    "Social" to setOf(ApplicationInfo.CATEGORY_SOCIAL),
    "Entertainment" to setOf(ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_AUDIO),
    "Games" to setOf(ApplicationInfo.CATEGORY_GAME),
    "Photo & video" to setOf(ApplicationInfo.CATEGORY_IMAGE),
    "Productivity" to setOf(ApplicationInfo.CATEGORY_PRODUCTIVITY),
    "News & maps" to setOf(ApplicationInfo.CATEGORY_NEWS, ApplicationInfo.CATEGORY_MAPS),
)

/** The last page: every app, sorted into groups by the category it reports. */
@Composable
fun GlassLibraryPage(
    apps: List<AppEntry>,
    suggestions: List<AppEntry>,
    callbacks: IconCallbacks,
    onOpenSearch: () -> Unit,
    onOpenGroup: (String, List<AppEntry>) -> Unit,
) {
    val groups = remember(apps, suggestions) {
        val grouped = categories.map { (title, values) -> title to apps.filter { it.category in values } }
        val placed = grouped.flatMap { it.second }.toSet()
        (listOf("Suggestions" to (suggestions + apps).distinct().take(4)) + grouped + ("Other" to apps.filter { it !in placed }))
            .filter { it.second.isNotEmpty() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().testTag("glass-library"),
    ) {
        item(span = { GridItemSpan(2) }) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .glassSurface(RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenSearch)
                    .padding(horizontal = 12.dp),
            ) {
                SearchGlyph(size = 15.dp, color = GlassSecondaryText)
                Spacer(Modifier.width(8.dp))
                Text("All apps", color = GlassSecondaryText, fontSize = 16.sp)
            }
        }
        items(groups, key = { it.first }) { (title, groupApps) ->
            GroupCard(title, groupApps, callbacks, onOpenGroup)
        }
    }
}

@Composable
private fun GroupCard(
    title: String,
    apps: List<AppEntry>,
    callbacks: IconCallbacks,
    onOpenGroup: (String, List<AppEntry>) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .glassSurface(RoundedCornerShape(26.dp))
                .padding(12.dp),
        ) {
            val gap = 10.dp
            val cell = (maxWidth - gap) / 2
            // Three icons, then a fourth, or a small grid that opens the whole group.
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                listOf(0, 1, 2, 3).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        pair.forEach { i ->
                            Box(Modifier.width(cell).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                when {
                                    i == 3 && apps.size > 4 -> MiniGrid(apps.drop(3).take(4), cell) { onOpenGroup(title, apps) }
                                    i < apps.size -> GlassIcon(apps[i], callbacks.copyForLibrary(), iconSize = cell, showLabel = false)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = LocalContentColor.current, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** The library's icons don't wiggle: only the home pages can be rearranged. */
private fun IconCallbacks.copyForLibrary() = IconCallbacks(false, allowUninstall, onLongPress, onRemove)

@Composable
private fun MiniGrid(apps: List<AppEntry>, size: Dp, onClick: () -> Unit) {
    val gap = 4.dp
    val mini = (size - gap) / 2
    Column(verticalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.clickable(onClick = onClick)) {
        apps.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                pair.forEach { GlassIconImage(it, mini) }
            }
        }
    }
}

/** A whole group, opened from the library. */
@Composable
fun GlassGroupOverlay(title: String, apps: List<AppEntry>, callbacks: IconCallbacks, onDismiss: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
            Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .glassSurface(RoundedCornerShape(38.dp))
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                items(apps, key = { it.key }) { app ->
                    GlassIcon(app, callbacks.copyForLibrary(), labelColor = Color.White, onOpened = onDismiss)
                }
            }
        }
    }
}
