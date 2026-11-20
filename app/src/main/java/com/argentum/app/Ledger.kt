package com.argentum.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Locale

private enum class Sort(val label: String) {
    NEWEST("Newest"), OLDEST("Oldest"), HIGH("Highest"), LOW("Lowest")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) } // 0 expenses, 1 income
    var key by remember { mutableStateOf(Repo.currentKey()) }
    var catFilter by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(Sort.NEWEST) }
    var sortMenu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    val df = remember { SimpleDateFormat("d MMM, h:mm a", Locale.getDefault()) }

    val all = Repo.txns.filter { it.isIncome == (tab == 1) && Repo.periodKey(it.time) == key }
    val list = all
        .filter { catFilter == null || it.category == catFilter }
        .filter { query.isBlank() || it.note.contains(query, true) || it.category.contains(query, true) }
        .let {
            when (sort) {
                Sort.NEWEST -> it.sortedByDescending { t -> t.time }
                Sort.OLDEST -> it.sortedBy { t -> t.time }
                Sort.HIGH -> it.sortedByDescending { t -> t.amount }
                Sort.LOW -> it.sortedBy { t -> t.amount }
            }
        }
    val shownTotal = list.sumOf { it.amount }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Ledger") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Sort") }
                    DropdownMenu(sortMenu, { sortMenu = false }) {
                        Sort.values().forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.label + if (s == sort) "  ✓" else "") },
                                onClick = { sort = s; sortMenu = false })
                        }
                    }
                }
            })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { key = Repo.prevKey(key) }) { Icon(Icons.Default.ChevronLeft, "Previous month") }
                Text(monthLabel(key), Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { key = nextKey(key) }, enabled = key < Repo.currentKey()) {
                    Icon(Icons.Default.ChevronRight, "Next month")
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0; catFilter = null }, text = { Text("Expenses") })
                Tab(tab == 1, { tab = 1; catFilter = null }, text = { Text("Income") })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(query, { query = it }, placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true,
                modifier = Modifier.fillMaxWidth())
            if (tab == 0) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(catFilter == null, { catFilter = null }, { Text("All") })
                    CATEGORIES.forEach { c -> FilterChip(catFilter == c, { catFilter = if (catFilter == c) null else c }, { Text(c) }) }
                }
            }
            Text(
                "${list.size} entries · " + (if (tab == 0) "−" else "+") + money(shownTotal),
                color = if (tab == 0) Brown else IncomeGreen, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Nothing here yet", color = Color.Gray) }
            } else LazyColumn {
                items(list, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text(t.note.ifBlank { t.category }) },
                        supportingContent = { Text("${t.category} · ${df.format(t.time)}") },
                        trailingContent = {
                            Text((if (t.isIncome) "+" else "−") + money(t.amount),
                                color = if (t.isIncome) IncomeGreen else Brown, fontWeight = FontWeight.SemiBold)
                        },
                        modifier = Modifier.clickable { editing = t }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    editing?.let { t ->
        var confirmDelete by remember(t.id) { mutableStateOf(false) }
        TxnSheet(t.isIncome, existing = t, onDismiss = { editing = null },
            onDelete = { confirmDelete = true }) { Repo.replace(it); editing = null }
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this entry?") },
            confirmButton = { TextButton(onClick = { Repo.remove(t.id); editing = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

fun monthLabel(key: String): String {
    val names = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    return names[key.substring(5, 7).toInt() - 1] + " " + key.substring(0, 4)
}

fun nextKey(key: String): String {
    var y = key.substring(0, 4).toInt()
    var m = key.substring(5, 7).toInt()
    m += 1
    if (m > 12) { m = 1; y += 1 }
    return "%04d-%02d".format(y, m)
}
