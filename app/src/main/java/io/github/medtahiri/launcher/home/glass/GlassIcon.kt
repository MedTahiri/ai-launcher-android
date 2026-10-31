package io.github.medtahiri.launcher.home.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.openApp
import io.github.medtahiri.launcher.core.rememberAppIcon
import io.github.medtahiri.launcher.theme.SoftSquareShape

/** What every icon needs to know and report. */
class IconCallbacks(
    val wiggling: Boolean,
    val allowUninstall: Boolean,
    val onLongPress: (AppEntry, Rect) -> Unit,
    val onRemove: (AppEntry) -> Unit,
)

/** Just the icon image. It loads when first shown, so a phone with many apps isn't slowed down. */
@Composable
fun GlassIconImage(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon = rememberAppIcon(app, sizePx, fullBleed = true)
    Box(modifier.size(size).clip(SoftSquareShape)) {
        icon?.let { Image(it, contentDescription = app.label, modifier = Modifier.size(size)) }
    }
}

@Composable
private fun wiggleAngle(app: AppEntry): Float {
    // A slightly different speed for each icon, so that the screen doesn't move in lockstep.
    val period = 230 + app.packageName.hashCode().mod(5) * 20
    val angle by rememberInfiniteTransition(label = "wiggle").animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Reverse),
        label = "angle",
    )
    return angle
}

/**
 * A home screen icon. Press opens the app and dims the icon; long press reports where the icon is, so that the menu can
 * be drawn over it. In edit mode it wiggles and has a remove badge.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassIcon(
    app: AppEntry,
    callbacks: IconCallbacks,
    modifier: Modifier = Modifier,
    iconSize: Dp = 56.dp,
    showLabel: Boolean = true,
    labelColor: Color = LocalContentColor.current,
    onOpened: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val angle = if (callbacks.wiggling) wiggleAngle(app) else 0f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .graphicsLayer { rotationZ = angle }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = "Open ${app.label}",
                onClick = {
                    if (!callbacks.wiggling) {
                        openApp(context, app)
                        onOpened()
                    }
                },
                onLongClick = {
                    if (!callbacks.wiggling) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        callbacks.onLongPress(app, bounds)
                    }
                },
            ),
    ) {
        Box {
            GlassIconImage(app, iconSize, Modifier.onGloballyPositioned { bounds = it.boundsInRoot() })
            if (pressed && !callbacks.wiggling) {
                Box(Modifier.size(iconSize).clip(SoftSquareShape).background(Color.Black.copy(alpha = 0.3f)))
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = callbacks.wiggling && callbacks.allowUninstall && !app.isSystem,
                enter = scaleIn(),
                exit = scaleOut(),
                modifier = Modifier.offset((-6).dp, (-6).dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xE6545458))
                        .clickable(onClickLabel = "Uninstall ${app.label}") { callbacks.onRemove(app) },
                ) {
                    Box(Modifier.size(width = 10.dp, height = 2.dp).background(Color.White))
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                app.label,
                modifier = Modifier.width(iconSize + 16.dp),
                style = TextStyle(
                    color = labelColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black.copy(alpha = 0.45f), blurRadius = 4f),
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
