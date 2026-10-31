package io.github.medtahiri.launcher.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.medtahiri.launcher.theme.glassSurface

/** A square glass card that fits the space it's given, up to 170dp. */
@Composable
fun WidgetCard(content: @Composable () -> Unit) {
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val height = if (constraints.maxHeight == Constraints.Infinity) maxWidth else maxHeight
        val side = min(min(maxWidth, height), 170.dp)
        Box(Modifier.size(side).glassSurface(RoundedCornerShape(26.dp))) { content() }
    }
}
