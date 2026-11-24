package com.ruvo.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transactions", indices = [Index("timestamp"), Index("categoryId")])
data class RuvoTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val categoryId: Long,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "EXPENSE"
)

@Entity(tableName = "categories")
data class RuvoCategory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String = "category",
    val color: Long = 0xFF548B69,
    val isIncome: Boolean = false
)

@Dao
interface RuvoDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun transactions(): Flow<List<RuvoTransaction>>
    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    fun categories(): Flow<List<RuvoCategory>>
    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun between(from: Long, to: Long): List<RuvoTransaction>
    @Insert suspend fun insertTransaction(item: RuvoTransaction): Long
    @Update suspend fun updateTransaction(item: RuvoTransaction)
    @Delete suspend fun deleteTransaction(item: RuvoTransaction)
    @Insert suspend fun insertCategory(item: RuvoCategory): Long
    @Update suspend fun updateCategory(item: RuvoCategory)
    @Delete suspend fun deleteCategory(item: RuvoCategory)
    @Query("UPDATE transactions SET categoryId = :replacementId WHERE categoryId = :id") suspend fun moveTransactionsToCategory(id: Long, replacementId: Long)
    @Query("SELECT COUNT(*) FROM categories") suspend fun categoryCount(): Int
    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1") suspend fun category(id: Long): RuvoCategory?
}

@Database(entities = [RuvoTransaction::class, RuvoCategory::class], version = 1, exportSchema = false)
abstract class RuvoDatabase : RoomDatabase() {
    abstract fun dao(): RuvoDao
    companion object {
        @Volatile private var INSTANCE: RuvoDatabase? = null
        fun get(context: Context): RuvoDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, RuvoDatabase::class.java, "ruvo-local.db").build().also { INSTANCE = it }
        }
    }
}
