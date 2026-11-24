package com.ruvo.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ruvo.MainActivity
import com.ruvo.R
import com.ruvo.data.RuvoDatabase
import com.ruvo.data.startMillis
import com.ruvo.data.endMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class RuvoWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            val pending = goAsync()
            refresh(context, pending)
        } else {
            super.onReceive(context, intent)
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        refresh(context, pending)
    }

    private fun refresh(context: Context, pending: PendingResult) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val manager = AppWidgetManager.getInstance(context)
                val ids = manager.getAppWidgetIds(ComponentName(context, RuvoWidgetProvider::class.java))
                if (ids.isEmpty()) return@launch
                val today = LocalDate.now()
                val rows = RuvoDatabase.get(context).dao().between(today.startMillis(), today.endMillis())
                val total = rows.filter { it.type == "EXPENSE" }.sumOf { it.amountPaise }
                val amount = NumberFormatCompat.format(total)
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_ruvo)
                    val add = Intent(context, MainActivity::class.java).apply {
                        action = MainActivity.ACTION_QUICK_ADD
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    val addPi = PendingIntent.getActivity(context, 11, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    views.setOnClickPendingIntent(R.id.widget_root, addPi)
                    manager.updateAppWidget(id, views)
                }
            } catch (_: Exception) {
                // Keep the last rendered widget content if the local database is temporarily unavailable.
            } finally {
                pending.finish()
            }
        }
    }

    private object NumberFormatCompat {
        fun format(paise: Long): String {
            val formatter = java.text.NumberFormat.getCurrencyInstance(Locale("en", "IN"))
            formatter.maximumFractionDigits = 2
            formatter.minimumFractionDigits = if (paise % 100L == 0L) 0 else 2
            return formatter.format(java.math.BigDecimal.valueOf(paise, 2))
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.ruvo.UPDATE_WIDGET"
    }
}
