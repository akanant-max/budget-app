package com.argentum.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb

class ArgentumWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        Repo.init(ctx)
        ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val key = Repo.currentKey()
            val has = Repo.budgetFor(key) != null
            val v = RemoteViews(ctx.packageName, R.layout.widget)
            if (has) {
                v.setTextViewText(R.id.w_amount, money(Repo.remaining(key)))
                v.setTextColor(R.id.w_amount, moodColor(Repo.fraction(key)).toArgb())
                v.setTextViewText(R.id.w_sub, "Safe today ${money(Repo.safeToSpend(key).coerceAtLeast(0.0))}")
            } else {
                v.setTextViewText(R.id.w_amount, "Argentum")
                v.setTextViewText(R.id.w_sub, "Tap to set your budget")
            }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.root, open)
            return v
        }

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ArgentumWidget::class.java))
            if (ids.isNotEmpty()) ids.forEach { mgr.updateAppWidget(it, build(ctx)) }
        }
    }
}
