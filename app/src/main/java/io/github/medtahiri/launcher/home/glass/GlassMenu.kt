package io.github.medtahiri.launcher.home.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.openAppInfo
import io.github.medtahiri.launcher.core.uninstallApp
import io.github.medtahiri.launcher.theme.GlassDanger
import io.github.medtahiri.launcher.theme.GlassMenu
import io.github.medtahiri.launcher.theme.GlassSeparator
import kotlin.math.roundToInt

private val MenuWidth = 250.dp
private val RowHeight = 46.dp

private class MenuAction(val title: String, val color: Color, val glyph: @Composable (Color) -> Unit, val run: () -> Unit)

/** The long-press menu: the icon lifts above the dimmed home screen, and the menu springs out beside it. */
@Composable
fun GlassMenu(
    app: AppEntry,
    anchor: Rect,
    inDock: Boolean,
    allowUninstall: Boolean,
    onDismiss: () -> Unit,
    onEditHomeScreen: () -> Unit,
    onToggleDock: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 420f)) }

    val actions = buildList {
        add(MenuAction("Edit home screen", Color.White, { GridGlyph(color = it) }) { onEditHomeScreen() })
        add(MenuAction(if (inDock) "Remove from dock" else "Add to dock", Color.White, { DockGlyph(color = it) }) { onToggleDock(app) })
        add(MenuAction("App info", Color.White, { InfoGlyph(color = it) }) { openAppInfo(context, app) })
        if (allowUninstall && !app.isSystem) {
            add(MenuAction("Uninstall", GlassDanger, { MinusGlyph(color = it) }) { uninstallApp(context, app) })
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        val screenWidth = constraints.maxWidth.toFloat()
        val screenHeight = constraints.maxHeight.toFloat()
        val margin = with(density) { 16.dp.toPx() }
        val gap = with(density) { 14.dp.toPx() }
        val menuWidth = with(density) { MenuWidth.toPx() }
        val menuHeight = with(density) { RowHeight.toPx() } * actions.size
        val below = anchor.bottom + gap + menuHeight < screenHeight - margin
        val menuY = if (below) anchor.bottom + gap else anchor.top - gap - menuHeight
        val alignRight = anchor.center.x > screenWidth / 2
        val menuX = (if (alignRight) anchor.right - menuWidth else anchor.left).coerceIn(margin, screenWidth - menuWidth - margin)

        GlassIconImage(
            app,
            size = with(density) { anchor.width.toDp() },
            modifier = Modifier
                .offset { IntOffset(anchor.left.roundToInt(), anchor.top.roundToInt()) }
                .graphicsLayer {
                    val scale = 1f + 0.1f * appear.value
                    scaleX = scale
                    scaleY = scale
                },
        )

        Column(
            Modifier
                .offset { IntOffset(menuX.roundToInt(), menuY.roundToInt()) }
                .width(MenuWidth)
                .graphicsLayer {
                    scaleX = appear.value
                    scaleY = appear.value
                    alpha = appear.value.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(if (alignRight) 1f else 0f, if (below) 0f else 1f)
                }
                .clip(RoundedCornerShape(14.dp))
                .background(GlassMenu)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            actions.forEachIndexed { index, action ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .clickable {
                            onDismiss()
                            action.run()
                        }
                        .padding(horizontal = 16.dp),
                ) {
                    Text(action.title, color = action.color, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    action.glyph(action.color)
                }
                if (index < actions.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(GlassSeparator))
                }
            }
        }
    }
}
