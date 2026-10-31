package io.github.medtahiri.launcher.widgets

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.medtahiri.launcher.core.WidgetConfig
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The time and date. Option "size": "medium", "large" (default) or "huge", which fills the screen's width without
 * ever overflowing. A 12-hour phone also gets AM/PM, in small type.
 */
object ClockWidget : LauncherWidget {
    override val type = "clock"
    private val SIZES = setOf("medium", "large", "huge")

    override fun validate(config: WidgetConfig): List<String> {
        val size = config.string("size", "large")
        return if (size in SIZES) emptyList() else listOf("size must be ${SIZES.joinToString()}, not \"$size\"")
    }

    @Composable
    override fun Content(config: WidgetConfig) {
        val context = LocalContext.current
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000) // wakes as the minute changes
            }
        }
        val date = Date(now)
        val locale = Locale.getDefault()
        val is24Hour = DateFormat.is24HourFormat(context)
        val time = SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm", locale).format(date)
        val day = SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale).format(date)
        val size = config.string("size", "large")
        val typography = MaterialTheme.typography
        val color = LocalContentColor.current
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = if (size == "huge") Modifier.fillMaxWidth() else Modifier) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                if (size == "huge") {
                    BasicText(
                        time,
                        modifier = Modifier.weight(1f, fill = false),
                        style = typography.displayLarge.copy(fontWeight = FontWeight.Light, textAlign = TextAlign.Center),
                        color = ColorProducer { color },
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 48.sp, maxFontSize = 160.sp),
                    )
                } else {
                    Text(time, style = if (size == "medium") typography.displayMedium else typography.displayLarge)
                }
                if (!is24Hour) {
                    Text(SimpleDateFormat("a", locale).format(date), style = typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(day, style = typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
