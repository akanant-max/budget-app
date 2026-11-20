package com.argentum.app

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated "how rich you are" scene, drawn live (no GIF files needed).
 * f in 0..1: stacked coins + sparkles when rich, a lone coin and a drifting
 * cloud when broke.
 */
@Composable
fun WealthScene(f: Float, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "scene")
    val phase by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "phase"
    )
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val w = size.width
        val h = size.height
        val ground = h * 0.88f
        drawRect(Color.Black.copy(alpha = 0.10f), Offset(0f, ground), androidx.compose.ui.geometry.Size(w, h - ground))

        val stacks = when {
            f > 0.8f -> 5
            f > 0.55f -> 4
            f > 0.3f -> 3
            f > 0.1f -> 2
            else -> 1
        }
        val coinsPer = (2 + f * 8).toInt().coerceAtLeast(1)
        val cw = w * 0.11f
        val ch = cw * 0.28f
        val startX = w / 2 - (stacks - 1) * cw * 0.65f
        for (s in 0 until stacks) {
            val cx = startX + s * cw * 1.3f
            val n = if (s == stacks / 2) coinsPer + 2 else (coinsPer - s % 3).coerceAtLeast(1)
            for (i in 0 until n) coin(cx, ground - ch * (i + 1) * 0.85f, cw, ch, f)
        }

        if (f > 0.5f) {
            // sparkles twinkle
            for (k in 0 until 7) {
                val p = (phase + k / 7f) % 1f
                val a = sin(p * PI).toFloat()
                val x = w * (0.12f + 0.76f * ((k * 37) % 100) / 100f)
                val y = h * (0.1f + 0.5f * ((k * 53) % 100) / 100f)
                sparkle(x, y, 6f + 8f * a, Gold.copy(alpha = a))
            }
        } else {
            // slow grey cloud for the lean times
            val cx = w * (0.2f + 0.6f * phase)
            val cy = h * 0.22f
            val c = Color.Gray.copy(alpha = 0.45f)
            drawCircle(c, 22f, Offset(cx, cy))
            drawCircle(c, 30f, Offset(cx + 26f, cy - 8f))
            drawCircle(c, 22f, Offset(cx + 56f, cy))
        }
    }
}

private fun DrawScope.coin(cx: Float, cy: Float, w: Float, h: Float, f: Float) {
    val body = if (f > 0.3f) Gold else Color(0xFF9A9A9A)
    val rim = if (f > 0.3f) Color(0xFFF3D26B) else Color(0xFFBDBDBD)
    drawOval(body.copy(alpha = 1f), Offset(cx - w / 2, cy), androidx.compose.ui.geometry.Size(w, h))
    drawOval(rim, Offset(cx - w / 2, cy - h * 0.25f), androidx.compose.ui.geometry.Size(w, h))
}

private fun DrawScope.sparkle(x: Float, y: Float, r: Float, c: Color) {
    drawLine(c, Offset(x - r, y), Offset(x + r, y), strokeWidth = 3f)
    drawLine(c, Offset(x, y - r), Offset(x, y + r), strokeWidth = 3f)
}
