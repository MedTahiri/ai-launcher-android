package io.github.medtahiri.launcher.home.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.medtahiri.launcher.core.AppEntry
import io.github.medtahiri.launcher.core.LauncherConfig
import io.github.medtahiri.launcher.core.openApp
import io.github.medtahiri.launcher.home.visibleInDrawer
import io.github.medtahiri.launcher.theme.GlassMenu
import io.github.medtahiri.launcher.theme.GlassSecondaryText
import io.github.medtahiri.launcher.theme.GlassSeparator
import io.github.medtahiri.launcher.theme.glassSurface

private val CardShape = RoundedCornerShape(16.dp)

/** The search screen: suggestions at first, then results while typing. In this style, it's also the way to all apps. */
@Composable
fun GlassSearch(
    apps: List<AppEntry>,
    suggestions: List<AppEntry>,
    config: LauncherConfig,
    callbacks: IconCallbacks,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    // With nothing typed, it lists every app: this screen is how this style reaches them all.
    val results = remember(apps, config, query) {
        visibleInDrawer(apps, config, query).sortedByDescending { it.label.startsWith(query.trim(), ignoreCase = true) }
    }
    val close = {
        keyboard?.hide()
        onDismiss()
    }
    val accent = MaterialTheme.colorScheme.primary
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { close() } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp)
            .testTag("app-drawer"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    results.firstOrNull()?.let {
                        openApp(context, it)
                        close()
                    }
                }),
                modifier = Modifier.weight(1f).focusRequester(focus).testTag("drawer-search"),
                decorationBox = { inner ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(42.dp)
                            .glassSurface(RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                    ) {
                        SearchGlyph(size = 16.dp, color = GlassSecondaryText)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text("Search apps", color = GlassSecondaryText, fontSize = 17.sp)
                            inner()
                        }
                        if (query.isNotEmpty()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GlassSecondaryText)
                                    .clickable(onClickLabel = "Clear") { query = "" },
                            ) { CloseGlyph(size = 8.dp, color = Color.Black) }
                        }
                    }
                },
            )
            Text(
                "Cancel",
                color = Color.White,
                fontSize = 17.sp,
                modifier = Modifier.padding(start = 12.dp).clickable(onClick = close),
            )
        }

        if (query.isBlank()) {
            SectionTitle("Suggestions")
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(GlassMenu)
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(vertical = 14.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    repeat(4) { i ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            suggestions.getOrNull(i)?.let { GlassIcon(it, callbacks, labelColor = Color.White, onOpened = close) }
                        }
                    }
                }
            }
        }
        SectionTitle(if (query.isBlank()) "All apps" else "Apps")
        if (results.isEmpty()) {
            Text("No results", color = GlassSecondaryText, fontSize = 17.sp, modifier = Modifier.padding(top = 24.dp).align(Alignment.CenterHorizontally))
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 12.dp).clip(CardShape).background(GlassMenu)) {
                itemsIndexed(results, key = { _, app -> app.key }) { index, app ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    openApp(context, app)
                                    close()
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            GlassIconImage(app, 40.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(app.label, color = Color.White, fontSize = 17.sp)
                        }
                        if (index < results.lastIndex) {
                            Box(Modifier.padding(start = 68.dp).fillMaxWidth().height(0.5.dp).background(GlassSeparator))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
    )
}
