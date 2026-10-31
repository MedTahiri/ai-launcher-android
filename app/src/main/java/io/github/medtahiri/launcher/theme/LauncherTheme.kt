package io.github.medtahiri.launcher.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import io.github.medtahiri.launcher.core.ThemeConfig

private fun hex(value: String) = Color(0xFF000000 or value.removePrefix("#").toLong(16))

/** The screen's background, or null to let the system wallpaper show. */
fun backgroundColor(theme: ThemeConfig, dark: Boolean): Color? = when (theme.background) {
    "wallpaper" -> null
    "auto" -> if (dark) Color.Black else Color.White
    else -> hex(theme.background)
}

/** Whether text and system bar icons must be dark: they follow the real background, whatever the mode says. */
fun usesDarkText(theme: ThemeConfig, dark: Boolean): Boolean =
    backgroundColor(theme, dark)?.let { it.luminance() > 0.5f } ?: !dark

@Composable
fun LauncherTheme(theme: ThemeConfig, content: @Composable () -> Unit) {
    val dark = when (theme.mode) {
        "light" -> false
        "auto" -> isSystemInDarkTheme()
        else -> true
    }
    val darkText = usesDarkText(theme, dark)
    val text = if (darkText) Color(0xFF111111) else Color(0xFFF5F5F5)
    val muted = text.copy(alpha = 0.72f)
    val surface = if (darkText) Color(0xFFF0F0F0) else Color(0xFF1C1C1C)
    val accent = hex(theme.accent)
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val colors = (if (darkText) lightColorScheme() else darkColorScheme()).copy(
        primary = accent, onPrimary = onAccent,
        background = surface, onBackground = text,
        surface = surface, onSurface = text,
        surfaceVariant = surface, onSurfaceVariant = muted,
        outline = muted,
    )
    val family = when (theme.font) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> FontFamily.Default
    }
    val base = Typography()
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        headlineMedium = base.headlineMedium.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        bodyLarge = base.bodyLarge.copy(fontFamily = family),
        bodyMedium = base.bodyMedium.copy(fontFamily = family),
        bodySmall = base.bodySmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
    )
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * theme.textScale)) {
        MaterialTheme(colorScheme = colors, typography = typography) {
            // Without this, text with no color of its own is black, whatever the background.
            CompositionLocalProvider(LocalContentColor provides text, content = content)
        }
    }
}
