package com.argentum.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onLedger: () -> Unit, onReport: () -> Unit, onSettings: () -> Unit,
               prefill: Prefill? = null, onPrefillUsed: () -> Unit = {}) {
    val key = Repo.currentKey()
    val hasBudget = Repo.budgetFor(key) != null
    val f = if (hasBudget) Repo.fraction(key) else 0.6f
    val dark = isDarkNow()
    val (top, bottom) = moodBackground(f, dark)
    var sheet by remember { mutableStateOf<Boolean?>(null) } // false = spend, true = add
    var budgetDialog by remember { mutableStateOf(!hasBudget) }
    var lastUndo by remember { mutableStateOf<Txn?>(null) }
    val snack = remember { SnackbarHostState() }

    LaunchedEffect(prefill, hasBudget) {
        if (prefill != null && hasBudget) sheet = false
    }

    LaunchedEffect(lastUndo) {
        val u = lastUndo ?: return@LaunchedEffect
        val r = snack.showSnackbar("Logged ${money(u.amount)}", "Undo", duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) Repo.remove(u.id)
        lastUndo = null
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Argentum", fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onLedger) { Icon(Icons.Default.Receipt, "Ledger") }
                IconButton(onClick = onReport) { Icon(Icons.Default.BarChart, "Report") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            }

            Spacer(Modifier.weight(0.6f))
            WealthScene(f)
            Spacer(Modifier.height(12.dp))

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (hasBudget) money(Repo.remaining(key)) else "—",
                    fontSize = 52.sp, fontWeight = FontWeight.Bold, color = moodColor(f)
                )
                if (hasBudget) {
                    Text("of ${money(Repo.total(key))} · ${Repo.daysLeft(key)} days left",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Spacer(Modifier.height(6.dp))
                    Text("Safe to spend today: ${money(Repo.safeToSpend(key).coerceAtLeast(0.0))}",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth().padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BigRoundButton(Icons.Default.Remove, Brown, "Spend", enabled = hasBudget) { sheet = false }
                BigRoundButton(Icons.Default.Add, IncomeGreen, "Add money", enabled = hasBudget) { sheet = true }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp))
    }

    sheet?.let { isIncome ->
        TxnSheet(
            isIncome,
            initialAmount = if (!isIncome) prefill?.amount else null,
            initialNote = if (!isIncome) prefill?.note.orEmpty() else "",
            onDismiss = { sheet = null; onPrefillUsed() }
        ) { t -> Repo.add(t); lastUndo = t; sheet = null; onPrefillUsed() }
    }
    if (budgetDialog) BudgetDialog(key, onDone = { budgetDialog = false })
}

@Composable
private fun BigRoundButton(icon: androidx.compose.ui.graphics.vector.ImageVector, c: Color, label: String,
                           enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick, enabled = enabled, modifier = Modifier.size(72.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = c, contentColor = Color.White)
    ) { Icon(icon, label, Modifier.size(36.dp)) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnSheet(isIncome: Boolean, existing: Txn? = null, onDismiss: () -> Unit,
             onDelete: (() -> Unit)? = null, initialAmount: Double? = null, initialNote: String = "",
             onSave: (Txn) -> Unit) {
    var amountText by remember {
        mutableStateOf((existing?.amount ?: initialAmount)?.let { "%.2f".format(it).removeSuffix(".00") } ?: "")
    }
    var note by remember { mutableStateOf(existing?.note ?: initialNote) }
    var cat by remember { mutableStateOf(existing?.category ?: if (isIncome) "Income" else "Food") }
    val amt = amountText.toDoubleOrNull() ?: 0.0
    val key = Repo.currentKey()
    val bal = Repo.remaining(key) + (existing?.let { if (it.isIncome) -it.amount else it.amount } ?: 0.0)
    val after = if (isIncome) bal + amt else bal - amt
    val total = Repo.total(key) + (if (isIncome) amt else 0.0)
    val afterFrac = if (total > 0) (after / total).toFloat().coerceIn(0f, 1f) else 0f

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(if (isIncome) "Add money" else "Spend", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Amount") }, prefix = { Text(Repo.currency) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text(money(bal), color = Gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (isIncome) "+" else "−", fontSize = 18.sp)
                Text(money(amt), color = if (isIncome) IncomeGreen else Brown, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("=", fontSize = 18.sp)
                Text(money(after), color = moodColor(afterFrac), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(note, { note = it }, label = { Text("What was it for? (optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            if (!isIncome) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CATEGORIES.forEach { c ->
                        FilterChip(cat == c, { cat = c }, { Text(c) })
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Button(
                onClick = {
                    onSave(Txn(existing?.id ?: System.nanoTime(), amt, isIncome, note.trim(), cat,
                        existing?.time ?: System.currentTimeMillis()))
                },
                enabled = amt > 0, modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (existing != null) "Save changes" else if (isIncome) "Add" else "Log spend") }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete entry", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun BudgetDialog(key: String, onDone: () -> Unit) {
    var text by remember { mutableStateOf(Repo.budgetFor(key)?.let { "%.2f".format(it).removeSuffix(".00") } ?: "") }
    AlertDialog(
        onDismissRequest = { if (Repo.budgetFor(key) != null) onDone() },
        title = { Text("This month's budget") },
        text = {
            OutlinedTextField(
                text, { text = it.filter { c -> c.isDigit() || c == '.' } },
                prefix = { Text(Repo.currency) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
        },
        confirmButton = {
            TextButton(enabled = (text.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                Repo.setBudget(key, text.toDouble()); Repo.applyRecurring(); onDone()
            }) { Text("Set") }
        },
        dismissButton = { if (Repo.budgetFor(key) != null) TextButton(onClick = onDone) { Text("Cancel") } }
    )
}

data class Prefill(val amount: Double?, val note: String)
