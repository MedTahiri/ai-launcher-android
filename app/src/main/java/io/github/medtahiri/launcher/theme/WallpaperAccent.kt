package io.github.medtahiri.launcher.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import kotlin.math.pow

/** `theme.accent: "wallpaper"`: a color taken from the wallpaper, made readable on the launcher's background. */
object WallpaperAccent {
    /**
     * The accent as #RRGGBB, or null if the phone can't say. On Android 12 and newer it's the phone's own color palette,
     * which it makes from the wallpaper (some phones, Samsung's among them, don't share the wallpaper's colors directly).
     * Before that, the colors Android reports for the wallpaper, from Android 8.1.
     */
    fun resolve(context: Context, darkBackground: Boolean): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val palette = context.getColor(if (darkBackground) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
            return hex(readable(palette, darkBackground))
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val colors = runCatching {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return null
        val color = colors.secondaryColor ?: colors.primaryColor
        return hex(readable(color.toArgb(), darkBackground))
    }

    fun hex(rgb: Int) = "#%06X".format(rgb and 0xFFFFFF)

    /** Relative luminance, from 0 (black) to 1 (white). */
    fun luminance(rgb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((rgb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** Mixes [rgb] toward white on a dark background, or black on a light one, until it stands out. */
    fun readable(rgb: Int, darkBackground: Boolean): Int {
        var color = rgb and 0xFFFFFF
        repeat(10) {
            val l = luminance(color)
            if (if (darkBackground) l >= 0.35 else l <= 0.2) return color
            color = mix(color, if (darkBackground) 0xFFFFFF else 0x000000, 0.2)
        }
        return color
    }

    private fun mix(a: Int, b: Int, amount: Double): Int {
        fun channel(shift: Int) = (((a shr shift) and 0xFF) * (1 - amount) + ((b shr shift) and 0xFF) * amount).toInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
