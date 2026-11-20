package com.argentum.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

fun reportText(key: String): String {
    val spent = Repo.spent(key)
    val earned = Repo.earned(key)
    val left = Repo.remaining(key)
    val sb = StringBuilder("Argentum report · ${monthLabel(key)}\n")
    sb.append("Budget: ${money(Repo.opening(key))}\n")
    sb.append("Income added: ${money(earned)}\n")
    sb.append("Spent: ${money(spent)}\n")
    sb.append("Left: ${money(left)}\n")
    val byCat = Repo.txns.filter { !it.isIncome && Repo.periodKey(it.time) == key }
        .groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }
    if (byCat.isNotEmpty()) {
        sb.append("\nBy category\n")
        byCat.forEach { (c, v) -> sb.append("• $c: ${money(v)}\n") }
    }
    return sb.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(onBack: () -> Unit) {
    var key by remember { mutableStateOf(Repo.currentKey()) }
    val ctx = LocalContext.current
    val spent = Repo.spent(key)
    val earned = Repo.earned(key)
    val opening = Repo.opening(key)
    val left = Repo.remaining(key)
    val f = Repo.fraction(key)
    val txs = Repo.txns.filter { !it.isIncome && Repo.periodKey(it.time) == key }
    val byCat = txs.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }
        .toList().sortedByDescending { it.second }
    val days = run {
        val c = Calendar.getInstance()
        val end = minOf(System.currentTimeMillis(), Repo.periodEndMillis(key))
        maxOf(1, ((end - Repo.periodStartMillis(key)) / 86_400_000L).toInt() + 1)
    }
    val daily = txs.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.time }.get(Calendar.DAY_OF_YEAR) to
            Calendar.getInstance().apply { timeInMillis = it.time }.get(Calendar.YEAR)
    }.mapValues { e -> e.value.sumOf { it.amount } }
    val heaviest = daily.maxByOrNull { it.value }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Month report") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, reportText(key)) }
                    ctx.startActivity(Intent.createChooser(i, "Share report"))
                }) { Icon(Icons.Default.Share, "Share") }
            })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { key = Repo.prevKey(key) }) { Icon(Icons.Default.ChevronLeft, "Previous") }
                Text(monthLabel(key), Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                IconButton(onClick = { key = nextKey(key) }, enabled = key < Repo.currentKey()) { Icon(Icons.Default.ChevronRight, "Next") }
            }
            if (Repo.budgetFor(key) == null && txs.isEmpty()) {
                Text("No data for this month.", Modifier.padding(24.dp))
                return@Column
            }
            StatRow("Budget", money(opening), Gold)
            StatRow("Income added", "+" + money(earned), IncomeGreen)
            StatRow("Spent", "−" + money(spent), Brown)
            StatRow("Left", money(left), moodColor(f))
            Spacer(Modifier.height(8.dp))
            Text("Average spend per day: ${money(spent / days)}")
            heaviest?.let { h ->
                Text("Heaviest day: ${money(h.value)}")
            }
            val pct = if (opening + earned > 0) (spent / (opening + earned) * 100).toInt() else 0
            Text("You used $pct% of your money this month.", Modifier.padding(top = 4.dp))

            if (key == Repo.currentKey() && Repo.budgetFor(key) != null) {
                val st = streak()
                Text("🔥 $st day${if (st == 1) "" else "s"} within your daily allowance",
                    Modifier.padding(top = 10.dp), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(16.dp))
            Text("Spending calendar", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            SpendHeatmap(key)
            Spacer(Modifier.height(16.dp))
            Text("Where it went", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(8.dp))
            val maxV = byCat.maxOfOrNull { it.second } ?: 1.0
            byCat.forEach { (c, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c, Modifier.width(72.dp))
                    Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Color.Gray.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((v / maxV).toFloat().coerceIn(0.02f, 1f)).background(Brown))
                    }
                    Text(money(v), Modifier.padding(start = 8.dp), fontSize = 13.sp)
                }
            }
            if (txs.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Biggest spends", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                txs.sortedByDescending { it.amount }.take(3).forEach {
                    Text("• ${it.note.ifBlank { it.category }} — ${money(it.amount)}", Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, c: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 16.sp)
        Text(value, color = c, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}
