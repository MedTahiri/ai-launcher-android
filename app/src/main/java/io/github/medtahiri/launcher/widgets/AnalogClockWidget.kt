package io.github.medtahiri.launcher.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import io.github.medtahiri.launcher.core.WidgetConfig
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A clock with hands on a glass card. */
object AnalogClockWidget : LauncherWidget {
    override val type = "analog_clock"

    @Composable
    override fun Content(config: WidgetConfig) {
        val now = produceState(LocalTime.now()) {
            while (true) {
                value = LocalTime.now()
                delay(1000 - System.currentTimeMillis() % 1000)
            }
        }.value
        val accent = MaterialTheme.colorScheme.primary
        WidgetCard {
            Canvas(Modifier.fillMaxSize().padding(14.dp)) {
                val r = size.minDimension / 2
                for (h in 0 until 12) {
                    val a = h * PI / 6
                    val outer = point(a, r)
                    val inner = point(a, if (h % 3 == 0) r * 0.84f else r * 0.91f)
                    drawLine(Color.White.copy(alpha = 0.75f), inner, outer, (if (h % 3 == 0) 2.5.dp else 1.2.dp).toPx(), StrokeCap.Round)
                }
                val minutes = now.minute + now.second / 60f
                val hours = now.hour % 12 + minutes / 60f
                hand(hours / 12f, r * 0.5f, 4.dp.toPx(), Color.White)
                hand(minutes / 60f, r * 0.74f, 2.8.dp.toPx(), Color.White)
                hand(now.second / 60f, r * 0.8f, 1.2.dp.toPx(), accent)
                drawCircle(accent, radius = 3.dp.toPx(), center = center)
            }
        }
    }

    private fun DrawScope.point(angle: Double, length: Float) =
        Offset(center.x + length * sin(angle).toFloat(), center.y - length * cos(angle).toFloat())

    private fun DrawScope.hand(turn: Float, length: Float, width: Float, color: Color) {
        drawLine(color, center, point(turn * 2 * PI, length), width, StrokeCap.Round)
    }
}
