package io.github.medtahiri.launcher.home.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Small hand-drawn icons, so the app doesn't need the material-icons dependency.

@Composable
fun SearchGlyph(size: Dp = 20.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.11f
        val r = this.size.minDimension * 0.32f
        val c = Offset(r + stroke, r + stroke)
        val d = r * 0.72f
        drawCircle(color, radius = r, center = c, style = Stroke(stroke))
        drawLine(color, Offset(c.x + d, c.y + d), Offset(this.size.width - stroke, this.size.height - stroke), stroke, StrokeCap.Round)
    }
}

@Composable
fun CloseGlyph(size: Dp = 16.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.12f
        drawLine(color, Offset(stroke, stroke), Offset(s - stroke, s - stroke), stroke, StrokeCap.Round)
        drawLine(color, Offset(s - stroke, stroke), Offset(stroke, s - stroke), stroke, StrokeCap.Round)
    }
}

/** Four squares: "Edit home screen". */
@Composable
fun GridGlyph(size: Dp = 18.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val cell = s * 0.42f
        listOf(0f to 0f, s - cell to 0f, 0f to s - cell, s - cell to s - cell).forEach { (x, y) ->
            drawRoundRect(color, Offset(x, y), Size(cell, cell), CornerRadius(s * 0.1f), style = Stroke(s * 0.08f))
        }
    }
}

/** A phone with a bar at the bottom: "Dock". */
@Composable
fun DockGlyph(size: Dp = 18.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.08f
        drawRoundRect(color, Offset(s * 0.18f, stroke), Size(s * 0.64f, s - stroke * 2), CornerRadius(s * 0.12f), style = Stroke(stroke))
        drawRoundRect(color, Offset(s * 0.3f, s * 0.72f), Size(s * 0.4f, s * 0.1f), CornerRadius(s * 0.05f))
    }
}

/** A circle with an "i": "App info". */
@Composable
fun InfoGlyph(size: Dp = 18.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.08f
        drawCircle(color, radius = s / 2 - stroke, style = Stroke(stroke))
        drawCircle(color, radius = stroke * 0.8f, center = Offset(s / 2, s * 0.3f))
        drawLine(color, Offset(s / 2, s * 0.45f), Offset(s / 2, s * 0.72f), stroke * 1.2f, StrokeCap.Round)
    }
}

/** A circle with a minus: "Uninstall". */
@Composable
fun MinusGlyph(size: Dp = 18.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.08f
        drawCircle(color, radius = s / 2 - stroke, style = Stroke(stroke))
        drawLine(color, Offset(s * 0.3f, s / 2), Offset(s * 0.7f, s / 2), stroke * 1.2f, StrokeCap.Round)
    }
}
