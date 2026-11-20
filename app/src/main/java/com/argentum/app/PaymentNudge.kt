package com.argentum.app

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** GPay (India), PhonePe, Paytm, BHIM */
val PAY_APPS = setOf(
    "com.google.android.apps.nbu.paisa.user",
    "com.phonepe.app",
    "net.one97.paytm",
    "in.org.npci.upiapp",
)

const val EXTRA_SPEND = "argentum_spend"
const val EXTRA_AMOUNT = "argentum_amount"
const val EXTRA_NOTE = "argentum_note"

object PayNotify {
    private const val CHANNEL = "argentum_nudge"
    private const val ID = 8
    private var lastNudge = 0L

    private fun post(ctx: Context, title: String, text: String, amount: Double?, note: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Payment nudges", NotificationManager.IMPORTANCE_HIGH))
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_SPEND, true)
            if (amount != null) putExtra(EXTRA_AMOUNT, amount)
            putExtra(EXTRA_NOTE, note)
        }
        val pi = PendingIntent.getActivity(ctx, 2, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(title).setContentText(text)
            .setContentIntent(pi).setAutoCancel(true)
            .setTimeoutAfter(30 * 60_000L)
            .addAction(0, "Log it", pi).build()
        nm.notify(ID, n)
    }

    fun nudge(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now - lastNudge < 30_000) return
        lastNudge = now
        post(ctx, "Did you log it?", "Tap to record this payment in Argentum.", null, "")
    }

    /** same notification id as the nudge, so this replaces it instead of doubling up */
    fun autofill(ctx: Context, amount: Double, note: String) {
        lastNudge = System.currentTimeMillis()
        post(ctx, "Spent ${money(amount)}?", if (note.isBlank()) "Tap to log it." else "To $note · tap to log it.", amount, note)
    }
}

/** Notices when you leave (or open) a payment app. */
class NudgeService : AccessibilityService() {
    private var current: String? = null

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg == current) return
        if (pkg == "com.android.systemui" || pkg == "android" ||
            pkg.contains("inputmethod") || pkg.contains("keyboard")) return
        val prev = current
        current = pkg
        if (pkg == packageName) return
        Repo.init(this)
        if (!Repo.nudgeOn) return
        val fire = if (Repo.nudgeMode == 1) pkg in PAY_APPS else (prev in PAY_APPS && pkg !in PAY_APPS)
        if (fire) PayNotify.nudge(this)
    }

    override fun onInterrupt() {}
}

/** Reads payment-success notifications from the payment apps to pre-fill the amount. */
class PayListener : NotificationListenerService() {
    private var lastKey = ""
    private var lastAt = 0L

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in PAY_APPS) return
        Repo.init(this)
        if (!Repo.autofillOn) return
        val ex = sbn.notification.extras
        val text = listOfNotNull(
            ex.getCharSequence("android.title"), ex.getCharSequence("android.text"),
            ex.getCharSequence("android.bigText")
        ).joinToString(" ")
        val (amount, note) = parsePayment(text) ?: return
        val k = "$amount|$note"
        val now = System.currentTimeMillis()
        if (k == lastKey && now - lastAt < 20_000) return
        lastKey = k; lastAt = now
        PayNotify.autofill(this, amount, note)
    }
}

fun parsePayment(text: String): Pair<Double, String>? {
    val lower = text.lowercase()
    if (listOf("received", "credited", "request", "refund", "cashback", "fail").any { lower.contains(it) }) return null
    if (listOf("paid", "sent", "debited", "successful", "payment of").none { lower.contains(it) }) return null
    val m = Regex("""(?:₹|rs\.?|inr)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE).find(text) ?: return null
    val amount = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
    if (amount <= 0) return null
    val payee = Regex("""\bto\s+([A-Za-z0-9 .&'_-]{2,40})""", RegexOption.IGNORE_CASE)
        .find(text)?.groupValues?.get(1)?.trim().orEmpty()
    return amount to payee
}

fun isNudgeServiceEnabled(ctx: Context): Boolean {
    val s = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
    return s.contains("${ctx.packageName}/${NudgeService::class.java.name}")
}

fun isListenerEnabled(ctx: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
