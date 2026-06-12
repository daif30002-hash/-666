package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Barn::class,
        DailyRecord::class,
        StockItem::class,
        StockTransaction::class,
        WeeklyWeight::class,
        Alert::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun barnDao(): BarnDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun stockDao(): StockDao
    abstract fun stockTransactionDao(): StockTransactionDao
    abstract fun weeklyWeightDao(): WeeklyWeightDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poultry_smart_farm_db"
                )
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDb(database)
                }
            }
        }

        suspend fun populateDb(db: AppDatabase) {
            // 1. Populate default 4 barns as requested
            val barnDao = db.barnDao()
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L
            
            // Starter cycles at different starting dates
            barnDao.insertBarn(Barn(code = "عنبر أ (A1)", capacity = 5000, initialChicks = 4800, startDate = now - 15 * oneDayMs, breed = "كوب 500 (Cobb)"))
            barnDao.insertBarn(Barn(code = "عنبر ب (B2)", capacity = 6000, initialChicks = 5800, startDate = now - 8 * oneDayMs, breed = "روص 308 (Ross)"))
            barnDao.insertBarn(Barn(code = "عنبر ج (C3)", capacity = 5000, initialChicks = 5000, startDate = now - 32 * oneDayMs, breed = "إنديان ريفر (Indian River)"))
            barnDao.insertBarn(Barn(code = "عنبر د (D4)", capacity = 5500, initialChicks = 5300, startDate = now - 1 * oneDayMs, breed = "كوب 500 (Cobb)"))

            // 2. Populate stock defaults
            val stockDao = db.stockDao()
            stockDao.insertStockItem(StockItem("STARTER_FEED", "علف بادئ (Starter 21-23%)", "STARTER_FEED", 4200.0, "كجم", 500.0))
            stockDao.insertStockItem(StockItem("GROWER_FEED", "علف نامي (Grower 20%)", "GROWER_FEED", 7500.0, "كجم", 500.0))
            stockDao.insertStockItem(StockItem("FINISHER_FEED", "علف ناهي (Finisher 18%)", "FINISHER_FEED", 6000.0, "كجم", 500.0))
            stockDao.insertStockItem(StockItem("SHAVINGS", "نشارة الخشب الطبيعية", "SHAVINGS", 350.0, "كيس", 50.0))
            stockDao.insertStockItem(StockItem("MEDICINE", "علاجات ومضادات حيوية معتمدة", "MEDICINE", 85.0, "كجم", 10.0))
            stockDao.insertStockItem(StockItem("VITAMINS", "فيتامينات ومكملات مائية", "VITAMINS", 45.0, "لتر", 5.0))

            // 3. Populate default stock transactions (initial stock)
            val txDao = db.stockTransactionDao()
            txDao.insertTransaction(StockTransaction(itemId = "STARTER_FEED", transactionType = "IN", quantity = 4200.0, date = now, notes = "رصيد افتتاحي"))
            txDao.insertTransaction(StockTransaction(itemId = "GROWER_FEED", transactionType = "IN", quantity = 7500.0, date = now, notes = "رصيد افتتاحي"))
            txDao.insertTransaction(StockTransaction(itemId = "FINISHER_FEED", transactionType = "IN", quantity = 6000.0, date = now, notes = "رصيد افتتاحي"))
            txDao.insertTransaction(StockTransaction(itemId = "SHAVINGS", transactionType = "IN", quantity = 350.0, date = now, notes = "رصيد افتتاحي"))
            txDao.insertTransaction(StockTransaction(itemId = "MEDICINE", transactionType = "IN", quantity = 85.0, date = now, notes = "رصيد افتتاحي"))
            txDao.insertTransaction(StockTransaction(itemId = "VITAMINS", transactionType = "IN", quantity = 45.0, date = now, notes = "رصيد افتتاحي"))

            // 4. Populating some historical weight entries for analytics demo
            val weightDao = db.weeklyWeightDao()
            // Barn 3 (عنبر ج) - Age 32 days (Week 1, Week 2, Week 3, Week 4)
            weightDao.insertWeight(WeeklyWeight(barnId = 3, weekNumber = 1, sampleSize = 50, averageWeight = 195.0, uniformity = 88.0, standardDeviation = 12.0, rawWeightsCsv = "190,200,195,185,205", date = now - 25 * oneDayMs))
            weightDao.insertWeight(WeeklyWeight(barnId = 3, weekNumber = 2, sampleSize = 50, averageWeight = 485.0, uniformity = 86.0, standardDeviation = 28.0, rawWeightsCsv = "470,490,480,500,485", date = now - 18 * oneDayMs))
            weightDao.insertWeight(WeeklyWeight(barnId = 3, weekNumber = 3, sampleSize = 50, averageWeight = 960.0, uniformity = 84.0, standardDeviation = 55.0, rawWeightsCsv = "940,970,950,980,960", date = now - 11 * oneDayMs))
            weightDao.insertWeight(WeeklyWeight(barnId = 3, weekNumber = 4, sampleSize = 50, averageWeight = 1580.0, uniformity = 82.0, standardDeviation = 90.0, rawWeightsCsv = "1540,1610,1570,1600,1580", date = now - 4 * oneDayMs))

            // Populating some historical daily records for Barn 3 (عنبر ج) to showcase charts/FCR
            val recordDao = db.dailyRecordDao()
            // 7 days of daily records for Barn 3
            for (day in 25..31) {
                recordDao.insertRecord(DailyRecord(
                    barnId = 3,
                    date = now - (32 - day) * oneDayMs,
                    ageInDays = day,
                    feedMorningKg = 310.0,
                    feedEveningKg = 315.0,
                    waterLiters = 1250.0,
                    deadMorning = 1,
                    deadEvening = 2,
                    soldQty = 0,
                    internalConsumption = 0,
                    culledQty = 0
                ))
            }
        }
    }
}
