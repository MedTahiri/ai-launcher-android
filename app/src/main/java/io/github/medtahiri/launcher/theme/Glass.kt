package io.github.medtahiri.launcher.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Dark glass: dark enough that its white text stays readable on any wallpaper. */
val GlassFill = Color.Black.copy(alpha = 0.32f)
val GlassStroke = Color.White.copy(alpha = 0.18f)
val GlassMenu = Color(0xF0222226)
val GlassSeparator = Color.White.copy(alpha = 0.12f)
val GlassDanger = Color(0xFFFF6B5E)
val GlassSecondaryText = Color(0xB3F2F2F7)

/** A rounded square with continuous corners (a superellipse). */
val SoftSquareShape: Shape = GenericShape { size, _ ->
    val a = size.width / 2
    val b = size.height / 2
    val exponent = 2.0 / 4.5
    val steps = 96
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = a + a * (sign(c) * abs(c).pow(exponent)).toFloat()
        val y = b + b * (sign(s) * abs(s).pow(exponent)).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

fun Modifier.glassSurface(shape: Shape): Modifier =
    clip(shape).background(GlassFill).border(0.5.dp, GlassStroke, shape)
