package com.ruvo.data

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ruvo.widget.RuvoWidgetProvider
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch\nimport kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class RuvoViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = RuvoDatabase.get(app).dao()
    val transactions = dao.transactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = dao.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            if (dao.categoryCount() == 0) {
                listOf(
                    RuvoCategory(name="Food", icon="restaurant", color=0xFFE3A35C),
                    RuvoCategory(name="Travel", icon="directions_bus", color=0xFF6F9DB8),
                    RuvoCategory(name="Shopping", icon="shopping_bag", color=0xFFB18AB8),
                    RuvoCategory(name="Study", icon="school", color=0xFF7F9D78),
                    RuvoCategory(name="Snacks", icon="coffee", color=0xFFC78C69),
                    RuvoCategory(name="Bills", icon="receipt", color=0xFF8A91A8),
                    RuvoCategory(name="Other", icon="category", color=0xFF87958B),
                    RuvoCategory(name="Income", icon="payments", color=0xFF548B69, isIncome=true)
                ).forEach { dao.insertCategory(it) }
            }
        }
    }

    fun save(item: RuvoTransaction) = viewModelScope.launch {
        if (item.id == 0L) dao.insertTransaction(item) else dao.updateTransaction(item)
        refreshWidget()
    }
    fun delete(item: RuvoTransaction) = viewModelScope.launch { dao.deleteTransaction(item); refreshWidget() }
    fun addCategory(name: String, icon: String, color: Long, income: Boolean = false) = viewModelScope.launch {
        if (name.isNotBlank()) dao.insertCategory(RuvoCategory(name=name.trim(), icon=icon, color=color, isIncome=income))
    }
    fun updateCategory(item: RuvoCategory) = viewModelScope.launch {\n        if (item.name.isNotBlank()) dao.updateCategory(item.copy(name = item.name.trim()))\n    }\n    fun deleteCategory(item: RuvoCategory) = viewModelScope.launch {\n        val fallback = dao.categories().first().firstOrNull { it.id != item.id && it.isIncome == item.isIncome }\n        if (fallback != null) {\n            dao.moveTransactionsToCategory(item.id, fallback.id)\n            dao.deleteCategory(item)\n        }\n    }\n    fun category(id: Long, callback: (RuvoCategory?) -> Unit) = viewModelScope.launch { callback(dao.category(id)) }

    private fun refreshWidget() {
        getApplication<Application>().sendBroadcast(Intent(getApplication(), RuvoWidgetProvider::class.java).setAction("com.ruvo.UPDATE_WIDGET"))
    }
}

fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun LocalDate.endMillis(): Long = plusDays(1).startMillis()
