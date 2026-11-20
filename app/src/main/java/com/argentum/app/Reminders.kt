package com.argentum.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.util.Calendar

object Reminders {
    private const val CHANNEL = "argentum_reminder"

    private fun nextTrigger(): Long {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, Repo.reminderHour); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            set(Calendar.DAY_OF_MONTH, minOf(Repo.reminderDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        if (!c.after(now)) {
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, 1)
            c.set(Calendar.DAY_OF_MONTH, minOf(Repo.reminderDay, c.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return c.timeInMillis
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
        if (Repo.reminderOn) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(), pi)
    }

    fun notifyNow(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Monthly reminder", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Time to update your balance")
            .setContentText("Set this month's budget in Argentum.")
            .setContentIntent(open).setAutoCancel(true).build()
        nm.notify(7, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        if (Repo.reminderOn) Reminders.notifyNow(ctx)
        Reminders.schedule(ctx)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Repo.init(ctx)
        Reminders.schedule(ctx)
    }
}
