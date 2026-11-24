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
import java.util.Locale

class RuvoWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "com.ruvo.UPDATE_WIDGET") updateAll(context)
    }
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateAll(context)

    private fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RuvoWidgetProvider::class.java))
        if (ids.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            val today = LocalDate.now()
            val rows = RuvoDatabase.get(context).dao().between(today.startMillis(), today.endMillis())
            val total = rows.filter { it.type == "EXPENSE" }.sumOf { it.amountPaise }
            val amount = String.format(Locale("en", "IN"), "₹%,.0f", total / 100.0)
            ids.forEach { id ->
                val views = RemoteViews(context.packageName, R.layout.widget_ruvo)
                views.setTextViewText(R.id.widget_amount, amount)
                val open = Intent(context, MainActivity::class.java)
                val openPi = PendingIntent.getActivity(context, 10, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val add = Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_QUICK_ADD)
                val addPi = PendingIntent.getActivity(context, 11, add, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                views.setOnClickPendingIntent(R.id.widget_root, openPi)
                views.setOnClickPendingIntent(R.id.widget_brand, openPi)
                views.setOnClickPendingIntent(R.id.widget_amount, openPi)
                views.setOnClickPendingIntent(R.id.widget_today, openPi)
                views.setOnClickPendingIntent(R.id.widget_add, addPi)
                manager.updateAppWidget(id, views)
            }
        }
    }
}
