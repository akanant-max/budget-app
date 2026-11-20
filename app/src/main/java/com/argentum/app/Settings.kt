package com.argentum.app

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private val CURRENCIES = listOf("₹", "$", "€", "£", "¥", "₩", "৳")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLockChanged: () -> Unit) {
    val ctx = LocalContext.current
    var recDialog by remember { mutableStateOf(false) }
    var budgetDialog by remember { mutableStateOf(false) }
    var goalDialog by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }

    fun toast(s: String) = Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()

    fun write(uri: android.net.Uri?, text: String) {
        if (uri == null) return
        runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            .onSuccess { toast("Saved") }.onFailure { toast("Could not save") }
    }

    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
        write(it, Repo.toJson().toString(2))
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) {
        write(it, Repo.csv())
    }
    val exportMd = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        write(it, reportText(Repo.currentKey()))
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            Repo.fromJson(JSONObject(text)); Repo.save(); Reminders.schedule(ctx)
        }.onSuccess { toast("Imported") }.onFailure { importError = "That file isn't an Argentum backup." }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Section("Money")
            Text("Currency", Modifier.padding(top = 4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CURRENCIES.forEach { c -> FilterChip(Repo.currency == c, { Repo.currency = c; Repo.save() }, { Text(c) }) }
            }
            Row { Spacer(Modifier.height(4.dp)) }
            RowItem("This month's budget", Repo.budgetFor(Repo.currentKey())?.let { money(it) } ?: "Not set") { budgetDialog = true }
            Stepper("Month starts on day", Repo.periodStartDay, 1..28) { Repo.periodStartDay = it; Repo.save() }
            SwitchRow("Roll leftover into next month", Repo.rollover) { Repo.rollover = it; Repo.save() }
            RowItem("Savings jar", if (Repo.goalTarget > 0) "${Repo.goalName.ifBlank { "Goal" }} · ${money(Repo.goalSaved)}/${money(Repo.goalTarget)}" else "Not set") { goalDialog = true }
            if (Repo.goalTarget > 0) {
                LinearProgressIndicator(progress = { (Repo.goalSaved / Repo.goalTarget).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = Gold)
            }

            Section("Recurring")
            Repo.recurring.forEach { r ->
                ListItem(
                    headlineContent = { Text(r.note.ifBlank { r.category }) },
                    supportingContent = { Text("Day ${r.dayOfMonth} · ${if (r.isIncome) "income" else r.category}") },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text((if (r.isIncome) "+" else "−") + money(r.amount))
                            IconButton(onClick = { Repo.removeRecurring(r.id) }) { Icon(Icons.Default.Delete, "Remove") }
                        }
                    })
            }
            TextButton(onClick = { recDialog = true }) { Text("+ Add recurring entry") }

            Section("Look & privacy")
            Text("Theme")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, l ->
                    FilterChip(Repo.darkMode == i, { Repo.darkMode = i; Repo.save() }, { Text(l) })
                }
            }
            SwitchRow("Lock app with fingerprint / PIN", Repo.lockOn) { Repo.lockOn = it; Repo.save(); onLockChanged() }

            Section("Reminder")
            SwitchRow("Monthly balance reminder", Repo.reminderOn) {
                Repo.reminderOn = it; Repo.save(); Reminders.schedule(ctx)
            }
            if (Repo.reminderOn) {
                Stepper("Day of month", Repo.reminderDay, 1..28) { Repo.reminderDay = it; Repo.save(); Reminders.schedule(ctx) }
                Stepper("Hour (24h)", Repo.reminderHour, 0..23) { Repo.reminderHour = it; Repo.save(); Reminders.schedule(ctx) }
            }

            Section("Backup")
            RowItem("Export backup (JSON)", "") { exportJson.launch("argentum-backup.json") }
            RowItem("Import backup", "") { import.launch(arrayOf("application/json", "*/*")) }
            RowItem("Export ledger (CSV)", "") { exportCsv.launch("argentum-ledger.csv") }
            RowItem("Export month report (Markdown)", "for Obsidian") { exportMd.launch("argentum-${Repo.currentKey()}.md") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (budgetDialog) BudgetDialog(Repo.currentKey()) { budgetDialog = false }
    if (recDialog) RecurringDialog { recDialog = false }
    if (goalDialog) GoalDialog { goalDialog = false }
    importError?.let {
        AlertDialog(onDismissRequest = { importError = null }, title = { Text("Import failed") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { importError = null }) { Text("OK") } })
    }
}

@Composable private fun Section(t: String) {
    Text(t, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 22.dp, bottom = 6.dp))
}

@Composable private fun RowItem(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title); Text(value, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable private fun SwitchRow(title: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f)); Switch(on, onChange)
    }
}

@Composable private fun Stepper(title: String, v: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (v > range.first) onChange(v - 1) }) { Text("−") }
            Text("$v", fontWeight = FontWeight.Bold)
            TextButton(onClick = { if (v < range.last) onChange(v + 1) }) { Text("+") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurringDialog(onDone: () -> Unit) {
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(1) }
    var income by remember { mutableStateOf(false) }
    var cat by remember { mutableStateOf("Bills") }
    AlertDialog(
        onDismissRequest = onDone, title = { Text("Recurring entry") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!income, { income = false }, { Text("Expense") })
                    FilterChip(income, { income = true }, { Text("Income") })
                }
                OutlinedTextField(amt, { amt = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Amount") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, label = { Text("Name (e.g. Netflix)") }, singleLine = true)
                Stepper("Day of month", day, 1..28) { day = it }
                if (!income) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CATEGORIES.forEach { c -> FilterChip(cat == c, { cat = c }, { Text(c) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = (amt.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                Repo.addRecurring(RecurringItem(System.nanoTime(), amt.toDouble(), income, note.trim(), if (income) "Income" else cat, day))
                onDone()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}

@Composable
private fun GoalDialog(onDone: () -> Unit) {
    var name by remember { mutableStateOf(Repo.goalName) }
    var target by remember { mutableStateOf(if (Repo.goalTarget > 0) "%.0f".format(Repo.goalTarget) else "") }
    var saved by remember { mutableStateOf(if (Repo.goalSaved > 0) "%.0f".format(Repo.goalSaved) else "") }
    AlertDialog(
        onDismissRequest = onDone, title = { Text("Savings jar") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Goal (e.g. New phone)") }, singleLine = true)
                OutlinedTextField(target, { target = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Target") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(saved, { saved = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Saved so far") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Repo.goalName = name.trim(); Repo.goalTarget = target.toDoubleOrNull() ?: 0.0
                Repo.goalSaved = saved.toDoubleOrNull() ?: 0.0; Repo.save(); onDone()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancel") } })
}
