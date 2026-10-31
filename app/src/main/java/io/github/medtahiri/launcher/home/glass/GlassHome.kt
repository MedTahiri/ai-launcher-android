package io.github.medtahiri.launcher.home.glass

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.LauncherConfig
import io.github.medtahiri.launcher.core.uninstallApp
import io.github.medtahiri.launcher.home.DefaultPrompt
import io.github.medtahiri.launcher.home.visibleInDrawer
import io.github.medtahiri.launcher.theme.GlassFill
import io.github.medtahiri.launcher.theme.glassSurface
import io.github.medtahiri.launcher.widgets.WidgetRegistry
import kotlinx.coroutines.launch

private val IndicatorHeight = 36.dp
private val DockHeight = 96.dp
private val DockBottomGap = 10.dp
private const val MAX_DOCK = 5

private data class MenuTarget(val app: AppEntry, val anchor: Rect)
private data class OpenGroup(val title: String, val apps: List<AppEntry>)

/**
 * The "glass" style (home.style in launcher.json): swipeable home pages over the wallpaper, a dock, a search screen, a
 * page of app groups, and icons that can be rearranged in edit mode.
 */
@Composable
fun GlassHome(
    config: LauncherConfig,
    configProblems: List<String>,
    allApps: List<AppEntry>,
    searchOpen: Boolean,
    onSearchOpenChange: (Boolean) -> Unit,
    homePresses: Int,
    isDefaultLauncher: Boolean,
) {
    val context = LocalContext.current
    val store = remember { GlassStore(context, config.home.favorites) }
    val apps = remember(allApps, config) { visibleInDrawer(allApps, config, "") }
    val byKey = remember(apps) { apps.associateBy { it.key } }
    var order by remember { mutableStateOf(store.loadOrder()) }
    var dock by remember { mutableStateOf(emptyList<String>()) }
    var wiggling by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    var group by remember { mutableStateOf<OpenGroup?>(null) }
    var goToFirstPage by remember { mutableStateOf(0) }
    val overlayOpen = menu != null || searchOpen || group != null

    LaunchedEffect(allApps) {
        if (allApps.isNotEmpty()) dock = store.loadDock(allApps.map { it.packageName }.toSet())
    }
    // The Home button: close what's open, then leave edit mode, then go to the first page.
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        when {
            menu != null || group != null -> {
                menu = null
                group = null
            }
            wiggling -> wiggling = false
            else -> goToFirstPage++
        }
    }
    BackHandler {
        menu = null
        group = null
        wiggling = false
        onSearchOpenChange(false)
    }

    val dockApps = remember(dock, apps) { dock.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } } }
    val arranged = remember(apps, order, dock) {
        val onPages = apps.filter { it.packageName !in dock }
        GlassLayout.arrange(onPages.map { it.key }, order)
    }
    val suggestions = remember(apps, dockApps) { (dockApps + apps).distinct() }
    val callbacks = IconCallbacks(
        wiggling = wiggling,
        allowUninstall = config.home.allowUninstall,
        onLongPress = { app, bounds -> menu = MenuTarget(app, bounds) },
        onRemove = { app -> uninstallApp(context, app) },
    )
    val toggleDock: (AppEntry) -> Unit = { app ->
        when {
            app.packageName in dock -> dock = dock - app.packageName
            dock.size >= MAX_DOCK -> Toast.makeText(context, "The dock is full", Toast.LENGTH_SHORT).show()
            else -> dock = dock + app.packageName
        }
        store.saveDock(dock)
    }
    val blur by animateDpAsState(if (overlayOpen) 22.dp else 0.dp, label = "blur")

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .blur(blur)
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("launcher-home"),
        ) {
            configProblems.forEach { Text("launcher.json: $it", color = Color(0xFFFF8A80), modifier = Modifier.padding(8.dp)) }
            DefaultPrompt(config, isDefaultLauncher)
            EditBar(wiggling, onDone = { wiggling = false })
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val gridHeight = maxHeight - IndicatorHeight - DockHeight - DockBottomGap
                val columns = config.home.columns
                val minCell = (config.home.iconSize + 40).dp * config.theme.textScale.coerceAtLeast(1f)
                val rows = (gridHeight / minCell).toInt().coerceAtLeast(3)
                val cellHeight = gridHeight / rows
                val widgetRows = config.home.widgets.chunked(2)
                val firstRows = (rows - widgetRows.size * 2).coerceAtLeast(0)
                val pages = remember(arranged, rows, columns, firstRows) {
                    GlassLayout.pages(arranged, firstRows * columns, rows * columns)
                }
                val pager = rememberPagerState { pages.size + 1 } // and the page of app groups
                val scope = rememberCoroutineScope()
                var dragging by remember { mutableStateOf(false) }
                LaunchedEffect(goToFirstPage) { if (goToFirstPage > 0) pager.animateScrollToPage(0) }

                Column(Modifier.fillMaxSize()) {
                    HorizontalPager(
                        pager,
                        // No pages kept ready off screen: with a keyboard or D-pad, Android focuses the first thing it
                        // finds, and the pager would scroll away from the first page to reach it.
                        beyondViewportPageCount = 0,
                        userScrollEnabled = !dragging,
                        modifier = Modifier.height(gridHeight),
                    ) { page ->
                        if (page < pages.size) {
                            val pageStart = pages.take(page).sumOf { it.size }
                            GlassPage(
                                keys = pages[page],
                                byKey = byKey,
                                config = config,
                                widgetRows = if (page == 0) widgetRows.size else 0,
                                columns = columns,
                                cellHeight = cellHeight,
                                callbacks = callbacks,
                                hasPrevious = page > 0,
                                hasNext = page < pages.lastIndex,
                                onDragChange = { dragging = it },
                                onMove = { key, slot -> order = GlassLayout.move(arranged, key, pageStart + slot) },
                                onDrop = { store.saveOrder(arranged) },
                                onMoveToPage = { key, direction ->
                                    order = GlassLayout.moveToAdjacentPage(arranged, key, pageStart, pages[page].size, direction)
                                    store.saveOrder(order)
                                    scope.launch { pager.animateScrollToPage(page + direction) }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(wiggling) {
                                        detectTapGestures(
                                            onLongPress = { if (!wiggling) wiggling = true },
                                            onTap = { if (wiggling) wiggling = false },
                                        )
                                    }
                                    .pointerInput(wiggling, config.home.swipeUpOpensDrawer) {
                                        if (wiggling || !config.home.swipeUpOpensDrawer) return@pointerInput
                                        val distance = 60.dp.toPx()
                                        var dragged = 0f
                                        detectVerticalDragGestures(
                                            onDragStart = { dragged = 0f },
                                            onVerticalDrag = { _, amount -> dragged += amount },
                                            onDragEnd = { if (kotlin.math.abs(dragged) > distance) onSearchOpenChange(true) },
                                        )
                                    },
                            )
                        } else {
                            GlassLibraryPage(apps, suggestions, callbacks, { onSearchOpenChange(true) }) { title, list ->
                                group = OpenGroup(title, list)
                            }
                        }
                    }
                    PageIndicator(pager) { onSearchOpenChange(true) }
                    Dock(dockApps, callbacks)
                }
            }
        }

        AnimatedVisibility(
            visible = group != null,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
        ) {
            group?.let { GlassGroupOverlay(it.title, it.apps, callbacks, onDismiss = { group = null }) }
        }
        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn() + slideInVertically { -it / 8 },
            exit = fadeOut() + slideOutVertically { -it / 8 },
        ) {
            GlassSearch(apps, suggestions, config, callbacks, onDismiss = { onSearchOpenChange(false) })
        }
        menu?.let { target ->
            GlassMenu(
                app = target.app,
                anchor = target.anchor,
                inDock = target.app.packageName in dock,
                allowUninstall = config.home.allowUninstall,
                onDismiss = { menu = null },
                onEditHomeScreen = { wiggling = true },
                onToggleDock = toggleDock,
            )
        }
    }
}

/**
 * One home page. All its icons are children of one Box, placed by offset, so that an icon keeps its finger while the
 * others move around it (in rows, it would be rebuilt whenever it changed row, which would end the drag).
 */
@Composable
private fun GlassPage(
    keys: List<String>,
    byKey: Map<String, AppEntry>,
    config: LauncherConfig,
    widgetRows: Int,
    columns: Int,
    cellHeight: Dp,
    callbacks: IconCallbacks,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onDragChange: (Boolean) -> Unit,
    onMove: (String, Int) -> Unit,
    onDrop: () -> Unit,
    onMoveToPage: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(horizontal = 12.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val cellWidth = maxWidth / columns
            val cellWidthPx = with(density) { cellWidth.toPx() }
            val cellHeightPx = with(density) { cellHeight.toPx() }
            val top = cellHeight * 2 * widgetRows
            val topPx = with(density) { top.toPx() }
            val widthPx = constraints.maxWidth.toFloat()
            // A drag outlives many recompositions, so it must always call the latest of these.
            val currentKeys by rememberUpdatedState(keys)
            val move by rememberUpdatedState(onMove)
            val drop by rememberUpdatedState(onDrop)
            val moveToPage by rememberUpdatedState(onMoveToPage)
            val previousExists by rememberUpdatedState(hasPrevious)
            val nextExists by rememberUpdatedState(hasNext)
            var draggedKey by remember { mutableStateOf<String?>(null) }
            var draggedAt by remember { mutableStateOf(Offset.Zero) }

            fun slotPosition(index: Int) = Offset(
                (index % columns) * cellWidthPx,
                topPx + (index / columns) * cellHeightPx,
            )

            Column(Modifier.fillMaxWidth()) {
                config.home.widgets.chunked(2).take(widgetRows).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(cellHeight * 2)) {
                        row.forEach { widget ->
                            Box(
                                Modifier.weight(1f).fillMaxHeight().padding(10.dp).testTag("widget-${widget.type}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                WidgetRegistry.find(widget.type)?.Content(widget)
                            }
                        }
                    }
                }
            }

            keys.forEachIndexed { index, itemKey ->
                val app = byKey[itemKey] ?: return@forEachIndexed
                key(itemKey) {
                    val target = slotPosition(index).round()
                    val placed by animateIntOffsetAsState(target, label = "slot")
                    val isDragged = draggedKey == itemKey
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset { if (isDragged) draggedAt.round() else placed }
                            .size(cellWidth, cellHeight)
                            .zIndex(if (isDragged) 1f else 0f)
                            .testTag("glass-app-${app.packageName}")
                            .pointerInput(callbacks.wiggling) {
                                if (!callbacks.wiggling) return@pointerInput
                                detectDragGestures(
                                    onDragStart = {
                                        draggedAt = slotPosition(currentKeys.indexOf(itemKey).coerceAtLeast(0))
                                        draggedKey = itemKey
                                        onDragChange(true)
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        draggedAt += amount
                                        val slot = GlassLayout.slotAt(
                                            draggedAt.x + cellWidthPx / 2,
                                            draggedAt.y + cellHeightPx / 2 - topPx,
                                            cellWidthPx,
                                            cellHeightPx,
                                            columns,
                                            currentKeys.size,
                                        )
                                        if (currentKeys.getOrNull(slot) != itemKey) move(itemKey, slot)
                                    },
                                    onDragEnd = {
                                        val centerX = draggedAt.x + cellWidthPx / 2
                                        val edge = cellWidthPx * 0.35f
                                        when {
                                            centerX < edge && previousExists -> moveToPage(itemKey, -1)
                                            centerX > widthPx - edge && nextExists -> moveToPage(itemKey, 1)
                                            else -> drop()
                                        }
                                        draggedKey = null
                                        onDragChange(false)
                                    },
                                    onDragCancel = {
                                        drop()
                                        draggedKey = null
                                        onDragChange(false)
                                    },
                                )
                            },
                    ) {
                        GlassIcon(app, callbacks, iconSize = config.home.iconSize.dp, showLabel = config.home.showLabels)
                    }
                }
            }
        }
    }
}

/** A search button at rest, and page dots while swiping. */
@Composable
private fun PageIndicator(pager: PagerState, onSearch: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(IndicatorHeight), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = pager.isScrollInProgress,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "indicator",
        ) { scrolling ->
            if (scrolling) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(GlassFill).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    repeat(pager.pageCount) { i ->
                        val size by animateDpAsState(if (i == pager.currentPage) 8.dp else 7.dp, label = "dot")
                        Box(
                            Modifier.size(size).clip(CircleShape)
                                .background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.4f)),
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .glassSurface(RoundedCornerShape(50))
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("open-drawer"),
                ) {
                    SearchGlyph(size = 12.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Search", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Dock(apps: List<AppEntry>, callbacks: IconCallbacks) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .padding(bottom = DockBottomGap)
            .fillMaxWidth()
            .height(DockHeight)
            .glassSurface(RoundedCornerShape(34.dp)),
    ) {
        if (apps.isEmpty()) {
            Text("Long-press an app to add it here", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        apps.forEach { app ->
            GlassIcon(app, callbacks, showLabel = false, modifier = Modifier.testTag("favorite-${app.packageName}"))
        }
    }
}

/** While editing: launcher options on the left, Done on the right. */
@Composable
private fun EditBar(visible: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    // Its space is always kept, so that the icons don't jump when editing starts.
    Box(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 16.dp)) {
        if (!visible) return@Box
        Box(Modifier.align(Alignment.CenterStart)) {
            GlassPill("Edit") { menuOpen = true }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Wallpaper") },
                    onClick = {
                        menuOpen = false
                        openSystemScreen(context, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Change home app") },
                    onClick = {
                        menuOpen = false
                        openSystemScreen(context, Intent(Settings.ACTION_HOME_SETTINGS))
                    },
                )
                DropdownMenuItem(
                    text = { Text("Android settings") },
                    onClick = {
                        menuOpen = false
                        openSystemScreen(context, Intent(Settings.ACTION_SETTINGS))
                    },
                )
            }
        }
        Box(Modifier.align(Alignment.CenterEnd)) { GlassPill("Done", onDone) }
    }
}

private fun openSystemScreen(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Not available on this phone", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun GlassPill(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .glassSurface(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
