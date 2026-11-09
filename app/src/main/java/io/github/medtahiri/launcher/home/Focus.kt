package io.github.medtahiri.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.rememberAppIcon
import kotlinx.coroutines.delay

/** What the drawer says about apps the focus rules hide now, and how to show them anyway (null if it can't). */
data class FocusNote(val hiddenCount: Int, val until: String?, val onShowAnyway: (() -> Unit)?)

val LocalFocusNote = staticCompositionLocalOf<FocusNote?> { null }

/** "3 apps hidden until 18:00 · Show": hiding apps silently would make the phone seem broken. */
@Composable
fun HiddenAppsNote(color: Color, modifier: Modifier = Modifier) {
    val note = LocalFocusNote.current ?: return
    if (note.hiddenCount == 0) return
    val apps = if (note.hiddenCount == 1) "1 app" else "${note.hiddenCount} apps"
    Row(modifier.fillMaxWidth().testTag("focus-hidden-note"), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$apps hidden for focus" + (note.until?.let { " until $it" } ?: ""),
            color = color,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        note.onShowAnyway?.let { show ->
            TextButton(onClick = show, modifier = Modifier.testTag("focus-show-anyway")) { Text("Show", color = color) }
        }
    }
}

/**
 * A pause before a distracting app opens: the person waits a few seconds, then chooses. "Not now" is the easy answer.
 */
@Composable
fun FocusPause(app: AppEntry, seconds: Int, onOpen: () -> Unit, onCancel: () -> Unit) {
    var left by remember(app) { mutableIntStateOf(seconds) }
    LaunchedEffect(app) {
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    BackHandler(onBack = onCancel)
    val iconPx = with(LocalDensity.current) { 72.dp.roundToPx() }
    val icon = rememberAppIcon(app, iconPx)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .pointerInput(Unit) { detectTapGestures { } } // taps stop here, not on the home screen below
            .padding(32.dp)
            .testTag("focus-pause"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp)) { icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(72.dp)) } }
            Text("Take a breath.", color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(
                if (left > 0) "Do you really want to open ${app.label}? You can in $left…" else "Still want to open ${app.label}?",
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().testTag("focus-not-now")) { Text("Not now") }
            OutlinedButton(
                onClick = onOpen,
                enabled = left == 0,
                modifier = Modifier.fillMaxWidth().testTag("focus-open-anyway"),
            ) {
                Text("Open ${app.label}", color = if (left == 0) Color.White else Color.White.copy(alpha = 0.4f))
            }
        }
    }
}
