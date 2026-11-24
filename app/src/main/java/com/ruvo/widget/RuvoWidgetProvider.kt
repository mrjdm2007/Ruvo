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

class RuvoWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refresh(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    private fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, RuvoWidgetProvider::class.java))
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_ruvo)
            val add = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_QUICK_ADD
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val addPi = PendingIntent.getActivity(
                context, 11, add,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, addPi)
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.ruvo.UPDATE_WIDGET"
    }
}
