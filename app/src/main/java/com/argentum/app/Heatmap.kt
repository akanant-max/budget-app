package com.argentum.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private const val DAY = 86_400_000L

fun periodLength(key: String): Int =
    Math.round((Repo.periodEndMillis(key) - Repo.periodStartMillis(key)) / DAY.toDouble()).toInt().coerceAtLeast(1)

/** spend per day index (0 = first day of the period) */
fun dailySpend(key: String): DoubleArray {
    val start = Repo.periodStartMillis(key)
    val out = DoubleArray(periodLength(key))
    Repo.txns.filter { !it.isIncome && Repo.periodKey(it.time) == key }.forEach {
        val i = ((it.time - start) / DAY).toInt()
        if (i in out.indices) out[i] += it.amount
    }
    return out
}

fun dailyAllowance(key: String): Double = Repo.total(key) / periodLength(key)

/** consecutive days (counting back from today) spent within the daily allowance */
fun streak(): Int {
    val key = Repo.currentKey()
    if (Repo.budgetFor(key) == null) return 0
    val spend = dailySpend(key)
    val allow = dailyAllowance(key)
    val today = ((System.currentTimeMillis() - Repo.periodStartMillis(key)) / DAY).toInt()
        .coerceIn(0, spend.lastIndex)
    var n = 0
    for (i in today downTo 0) {
        if (spend[i] <= allow) n++ else break
    }
    return n
}

@Composable
fun SpendHeatmap(key: String) {
    val spend = dailySpend(key)
    val allow = dailyAllowance(key).coerceAtLeast(1.0)
    val start = Repo.periodStartMillis(key)
    val now = System.currentTimeMillis()
    val offset = Calendar.getInstance().apply { timeInMillis = start }.get(Calendar.DAY_OF_WEEK) - 1
    val cells = offset + spend.size
    val rows = (cells + 6) / 7
    val labels = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, Modifier.weight(1f), fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - offset
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(
                        when {
                            idx !in spend.indices -> Color.Transparent
                            start + idx * DAY > now -> Color.Gray.copy(alpha = 0.08f)
                            spend[idx] <= 0.0 -> Color.Gray.copy(alpha = 0.2f)
                            spend[idx] > allow * 1.5 -> Color(0xFFB3261E)
                            else -> lerp(Color(0xFFE8C99B), Brown, (spend[idx] / (allow * 1.5)).toFloat().coerceIn(0f, 1f))
                        }
                    ), contentAlignment = Alignment.Center) {
                        if (idx in spend.indices) Text("${idx + 1}", fontSize = 10.sp, color = Color.Black.copy(alpha = 0.55f))
                    }
                }
            }
        }
        Text("Darker = more spent · red = well over your daily allowance",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
    }
}
