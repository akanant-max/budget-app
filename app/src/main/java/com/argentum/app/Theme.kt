package com.argentum.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val Gold = Color(0xFFD4A62A)
val Brown = Color(0xFF8B5A2B)
val IncomeGreen = Color(0xFF2E9E5B)

/** green (>80%) -> yellow -> orange -> red as the money runs out */
fun moodColor(f: Float): Color = when {
    f >= 0.8f -> Color(0xFF2EB872)
    f >= 0.5f -> lerp(Color(0xFFE6C229), Color(0xFF2EB872), (f - 0.5f) / 0.3f)
    f >= 0.25f -> lerp(Color(0xFFF08A24), Color(0xFFE6C229), (f - 0.25f) / 0.25f)
    else -> lerp(Color(0xFFD64545), Color(0xFFF08A24), f / 0.25f)
}

/** 0 = broke/dull, 1 = rich/joyful. Controls saturation of the whole theme. */
fun moodBackground(f: Float, dark: Boolean): Pair<Color, Color> {
    val grey = if (dark) Color(0xFF1A1B1F) else Color(0xFFE4E4E6)
    val greyBottom = if (dark) Color(0xFF111214) else Color(0xFFCFCFD2)
    val top = moodColor(f).let { c ->
        if (dark) lerp(Color(0xFF0E1A16), c, 0.28f) else lerp(Color(0xFFFFFFFF), c, 0.30f)
    }
    val bottom = if (dark) Color(0xFF0B0F14) else Color(0xFFFFF7E0)
    val k = f.coerceIn(0f, 1f)
    return lerp(grey, top, k) to lerp(greyBottom, bottom, k)
}

@Composable
fun ArgentumTheme(content: @Composable () -> Unit) {
    val dark = when (Repo.darkMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val scheme = if (dark) darkColorScheme(
        primary = Gold, onPrimary = Color(0xFF1A1300),
        secondary = Color(0xFFC9D1DC), surface = Color(0xFF171A21),
        background = Color(0xFF0F1420), surfaceVariant = Color(0xFF232733),
    ) else lightColorScheme(
        primary = Color(0xFF9C7410), onPrimary = Color.White,
        secondary = Color(0xFF4A5565), surface = Color(0xFFFFFFFF),
        background = Color(0xFFFAF7EF), surfaceVariant = Color(0xFFEFEADB),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun isDarkNow(): Boolean = when (Repo.darkMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}
