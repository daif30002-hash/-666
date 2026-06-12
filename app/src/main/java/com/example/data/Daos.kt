package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BarnDao {
    @Query("SELECT * FROM barns ORDER BY id ASC")
    fun getAllBarns(): Flow<List<Barn>>

    @Query("SELECT * FROM barns WHERE id = :id")
    suspend fun getBarnById(id: Int): Barn?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBarn(barn: Barn): Long

    @Update
    suspend fun updateBarn(barn: Barn)

    @Delete
    suspend fun deleteBarn(barn: Barn)
}

@Dao
interface DailyRecordDao {
    @Query("SELECT * FROM daily_records WHERE barnId = :barnId ORDER BY date DESC")
    fun getRecordsForBarn(barnId: Int): Flow<List<DailyRecord>>

    @Query("SELECT * FROM daily_records ORDER BY date DESC")
    fun getAllRecords(): Flow<List<DailyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: DailyRecord): Long

    @Query("DELETE FROM daily_records WHERE id = :id")
    suspend fun deleteRecordById(id: Int)
}

@Dao
interface StockDao {
    @Query("SELECT * FROM stock")
    fun getAllStockItems(): Flow<List<StockItem>>

    @Query("SELECT * FROM stock WHERE id = :id")
    suspend fun getStockItemById(id: String): StockItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStockItem(item: StockItem)

    @Query("UPDATE stock SET currentQuantity = :newQty WHERE id = :id")
    suspend fun updateStockQuantity(id: String, newQty: Double)
}

@Dao
interface StockTransactionDao {
    @Query("SELECT * FROM stock_transactions ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<StockTransaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: StockTransaction): Long
}

@Dao
interface WeeklyWeightDao {
    @Query("SELECT * FROM weights WHERE barnId = :barnId ORDER BY weekNumber ASC")
    fun getWeightsForBarn(barnId: Int): Flow<List<WeeklyWeight>>

    @Query("SELECT * FROM weights ORDER BY date DESC")
    fun getAllWeights(): Flow<List<WeeklyWeight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeight(weight: WeeklyWeight): Long
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts WHERE isResolved = 0 ORDER BY date DESC")
    fun getUnresolvedAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts ORDER BY date DESC")
    fun getAllAlerts(): Flow<List<Alert>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert): Long

    @Query("UPDATE alerts SET isResolved = 1 WHERE id = :id")
    suspend fun resolveAlert(id: Int)
}
