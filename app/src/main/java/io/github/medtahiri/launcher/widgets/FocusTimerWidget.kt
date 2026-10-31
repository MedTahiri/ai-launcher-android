package io.github.medtahiri.launcher.widgets

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.medtahiri.launcher.core.WidgetConfig
import kotlinx.coroutines.delay
import kotlinx.serialization.json.intOrNull
import java.util.Locale

/**
 * A countdown for focused work: press to start, press again to stop. Option: "minutes" (default 25, from 1 to 600).
 * It only shows on the home screen: it makes no sound and sends no notification when it ends.
 */
object FocusTimerWidget : LauncherWidget {
    override val type = "focus_timer"
    private const val SHOW_DONE_MS = 60 * 60_000L

    override fun validate(config: WidgetConfig): List<String> {
        val raw = config.options["minutes"] ?: return emptyList()
        return if (raw.intOrNull in 1..600) emptyList() else listOf("minutes must be a number from 1 to 600, not $raw")
    }

    @Composable
    override fun Content(config: WidgetConfig) {
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE) }
        val minutes = config.int("minutes", 25)
        // Kept in preferences: the timer goes on when the launcher closes behind another app.
        var endsAt by remember { mutableLongStateOf(prefs.getLong("ends_at", 0)) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        fun setEnd(value: Long) {
            endsAt = value
            prefs.edit().putLong("ends_at", value).apply()
        }
        LaunchedEffect(endsAt) {
            while (endsAt > System.currentTimeMillis()) {
                now = System.currentTimeMillis()
                delay(1000)
            }
            now = System.currentTimeMillis()
        }
        val left = ((endsAt - now).coerceAtLeast(0) + 999) / 1000
        val running = left > 0
        val justFinished = !running && endsAt > 0 && now - endsAt < SHOW_DONE_MS
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    running -> String.format(Locale.ROOT, "%02d:%02d", left / 60, left % 60)
                    justFinished -> "Focus done"
                    else -> "$minutes min focus"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedButton(
                modifier = Modifier.testTag("focus-timer-button"),
                onClick = { setEnd(if (running) 0 else System.currentTimeMillis() + minutes * 60_000L) },
            ) {
                Text(
                    when {
                        running -> "Stop"
                        justFinished -> "Start again"
                        else -> "Start"
                    },
                )
            }
        }
    }
}
