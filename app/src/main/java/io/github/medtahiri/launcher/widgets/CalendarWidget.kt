package io.github.medtahiri.launcher.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.medtahiri.launcher.core.WidgetConfig
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Today's date on a glass card. It shows no events, since that would need the calendar permission. */
object CalendarWidget : LauncherWidget {
    override val type = "calendar"

    @Composable
    override fun Content(config: WidgetConfig) {
        val today = produceState(LocalDate.now()) {
            while (true) {
                value = LocalDate.now()
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }.value
        val locale = Locale.getDefault()
        WidgetCard {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    today.dayOfWeek.getDisplayName(TextStyle.FULL, locale).uppercase(locale),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(today.dayOfMonth.toString(), color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.weight(1f))
                Text(
                    today.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
