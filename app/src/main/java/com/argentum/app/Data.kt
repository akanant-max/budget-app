package com.argentum.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class Txn(
    val id: Long,
    val amount: Double,
    val isIncome: Boolean,
    val note: String,
    val category: String,
    val time: Long,
)

data class RecurringItem(
    val id: Long,
    val amount: Double,
    val isIncome: Boolean,
    val note: String,
    val category: String,
    val dayOfMonth: Int,
)

val CATEGORIES = listOf("Food", "Travel", "Books", "Bills", "Fun", "Health", "Other")

object Repo {
    private lateinit var prefs: android.content.SharedPreferences

    val txns = mutableStateListOf<Txn>()
    val recurring = mutableStateListOf<RecurringItem>()
    /** period key ("yyyy-MM") -> budget entered for that period */
    val budgets = mutableStateListOf<Pair<String, Double>>()

    var currency by mutableStateOf("₹")
    var periodStartDay by mutableStateOf(1)
    var darkMode by mutableStateOf(0) // 0 system, 1 light, 2 dark
    var rollover by mutableStateOf(false)
    var reminderOn by mutableStateOf(true)
    var reminderDay by mutableStateOf(1)
    var reminderHour by mutableStateOf(9)
    var lockOn by mutableStateOf(false)
    var goalName by mutableStateOf("")
    var goalTarget by mutableStateOf(0.0)
    var goalSaved by mutableStateOf(0.0)
    var lastRecurringKey by mutableStateOf("")

    fun init(ctx: Context) {
        if (this::prefs.isInitialized) return
        prefs = ctx.applicationContext.getSharedPreferences("argentum", Context.MODE_PRIVATE)
        load()
    }

    // ---------- periods ----------
    fun periodKey(time: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = time }
        var y = c.get(Calendar.YEAR)
        var m = c.get(Calendar.MONTH)
        if (c.get(Calendar.DAY_OF_MONTH) < periodStartDay) {
            m -= 1
            if (m < 0) { m = 11; y -= 1 }
        }
        return "%04d-%02d".format(y, m + 1)
    }

    fun currentKey() = periodKey(System.currentTimeMillis())

    fun prevKey(key: String): String {
        var y = key.substring(0, 4).toInt()
        var m = key.substring(5, 7).toInt() - 1
        m -= 1
        if (m < 0) { m = 11; y -= 1 }
        return "%04d-%02d".format(y, m + 1)
    }

    fun periodStartMillis(key: String): Long {
        val y = key.substring(0, 4).toInt()
        val m = key.substring(5, 7).toInt() - 1
        return Calendar.getInstance().apply {
            clear(); set(y, m, minOf(periodStartDay, getActualMaximum(Calendar.DAY_OF_MONTH)), 0, 0, 0)
        }.timeInMillis
    }

    fun periodEndMillis(key: String): Long {
        val y = key.substring(0, 4).toInt()
        val m = key.substring(5, 7).toInt() - 1
        val c = Calendar.getInstance().apply { clear(); set(y, m, 1, 0, 0, 0); add(Calendar.MONTH, 1) }
        c.set(Calendar.DAY_OF_MONTH, minOf(periodStartDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        return c.timeInMillis
    }

    fun daysLeft(key: String = currentKey()): Int {
        val ms = periodEndMillis(key) - System.currentTimeMillis()
        return maxOf(1, Math.ceil(ms / 86_400_000.0).toInt())
    }

    // ---------- money maths ----------
    fun budgetFor(key: String): Double? = budgets.firstOrNull { it.first == key }?.second

    fun spent(key: String) = txns.filter { !it.isIncome && periodKey(it.time) == key }.sumOf { it.amount }
    fun earned(key: String) = txns.filter { it.isIncome && periodKey(it.time) == key }.sumOf { it.amount }

    /** money available at the start of a period (entered budget + optional rollover) */
    fun opening(key: String, depth: Int = 0): Double {
        val b = budgetFor(key) ?: 0.0
        if (!rollover || depth > 24) return b
        val pk = prevKey(key)
        if (budgetFor(pk) == null) return b
        return b + remaining(pk, depth + 1).coerceAtLeast(0.0)
    }

    fun total(key: String) = opening(key) + earned(key)
    fun remaining(key: String, depth: Int = 0) = opening(key, depth) + earned(key) - spent(key)
    fun fraction(key: String): Float {
        val t = total(key)
        if (t <= 0) return 0f
        return (remaining(key) / t).toFloat().coerceIn(0f, 1f)
    }
    fun safeToSpend(key: String = currentKey()) = remaining(key) / daysLeft(key)

    // ---------- mutations ----------
    fun setBudget(key: String, amount: Double) {
        budgets.removeAll { it.first == key }
        budgets.add(key to amount)
        save()
    }

    fun add(t: Txn) { txns.add(0, t); txns.sortByDescending { it.time }; save() }
    fun remove(id: Long) { txns.removeAll { it.id == id }; save() }
    fun replace(t: Txn) {
        val i = txns.indexOfFirst { it.id == t.id }
        if (i >= 0) { txns[i] = t; txns.sortByDescending { it.time }; save() }
    }

    fun addRecurring(r: RecurringItem) { recurring.add(r); save() }
    fun removeRecurring(id: Long) { recurring.removeAll { it.id == id }; save() }

    /** post this period's recurring entries once */
    fun applyRecurring() {
        val key = currentKey()
        if (lastRecurringKey == key) return
        if (budgetFor(key) != null) {
            val start = periodStartMillis(key)
            recurring.forEach { r ->
                val c = Calendar.getInstance().apply { timeInMillis = start }
                c.set(Calendar.DAY_OF_MONTH, minOf(r.dayOfMonth, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
                var when_ = c.timeInMillis
                if (when_ < start) when_ = start
                if (when_ > System.currentTimeMillis()) when_ = System.currentTimeMillis()
                txns.add(Txn(System.nanoTime(), r.amount, r.isIncome, r.note, r.category, when_))
            }
            txns.sortByDescending { it.time }
            lastRecurringKey = key
            save()
        }
    }

    // ---------- persistence ----------
    private fun txnJson(t: Txn) = JSONObject()
        .put("id", t.id).put("a", t.amount).put("in", t.isIncome)
        .put("n", t.note).put("c", t.category).put("t", t.time)

    private fun txnFrom(o: JSONObject) = Txn(
        o.getLong("id"), o.getDouble("a"), o.getBoolean("in"),
        o.optString("n"), o.optString("c", "Other"), o.getLong("t")
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("v", 1)
        put("txns", JSONArray().also { a -> txns.forEach { a.put(txnJson(it)) } })
        put("recurring", JSONArray().also { a ->
            recurring.forEach {
                a.put(JSONObject().put("id", it.id).put("a", it.amount).put("in", it.isIncome)
                    .put("n", it.note).put("c", it.category).put("d", it.dayOfMonth))
            }
        })
        put("budgets", JSONArray().also { a ->
            budgets.forEach { a.put(JSONObject().put("k", it.first).put("b", it.second)) }
        })
        put("currency", currency); put("startDay", periodStartDay); put("dark", darkMode)
        put("rollover", rollover); put("remOn", reminderOn); put("remDay", reminderDay)
        put("remHour", reminderHour); put("lock", lockOn)
        put("goalName", goalName); put("goalTarget", goalTarget); put("goalSaved", goalSaved)
        put("lastRec", lastRecurringKey)
    }

    fun fromJson(o: JSONObject) {
        txns.clear(); recurring.clear(); budgets.clear()
        o.optJSONArray("txns")?.let { a -> for (i in 0 until a.length()) txns.add(txnFrom(a.getJSONObject(i))) }
        o.optJSONArray("recurring")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let {
                recurring.add(RecurringItem(it.getLong("id"), it.getDouble("a"), it.getBoolean("in"),
                    it.optString("n"), it.optString("c", "Other"), it.getInt("d")))
            }
        }
        o.optJSONArray("budgets")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { budgets.add(it.getString("k") to it.getDouble("b")) }
        }
        currency = o.optString("currency", "₹"); periodStartDay = o.optInt("startDay", 1)
        darkMode = o.optInt("dark", 0); rollover = o.optBoolean("rollover", false)
        reminderOn = o.optBoolean("remOn", true); reminderDay = o.optInt("remDay", 1)
        reminderHour = o.optInt("remHour", 9); lockOn = o.optBoolean("lock", false)
        goalName = o.optString("goalName"); goalTarget = o.optDouble("goalTarget", 0.0)
        goalSaved = o.optDouble("goalSaved", 0.0); lastRecurringKey = o.optString("lastRec")
        txns.sortByDescending { it.time }
    }

    fun save() { prefs.edit().putString("data", toJson().toString()).apply() }

    private fun load() {
        prefs.getString("data", null)?.let { runCatching { fromJson(JSONObject(it)) } }
    }

    fun csv(): String {
        val sb = StringBuilder("date,type,amount,category,note\n")
        val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        txns.sortedBy { it.time }.forEach {
            sb.append(f.format(it.time)).append(',').append(if (it.isIncome) "income" else "expense").append(',')
                .append(it.amount).append(',').append(it.category).append(',')
                .append('"').append(it.note.replace("\"", "\"\"")).append("\"\n")
        }
        return sb.toString()
    }
}

fun money(v: Double): String {
    val neg = v < 0
    val s = "%,.2f".format(Math.abs(v)).removeSuffix(".00")
    return (if (neg) "-" else "") + Repo.currency + s
}
