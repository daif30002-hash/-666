package com.example.data

import androidx.room.*

@Entity(tableName = "barns")
data class Barn(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val capacity: Int,
    val initialChicks: Int,
    val startDate: Long, // Epoch millis
    val breed: String // Cobb, Ross, Indian River
)

@Entity(
    tableName = "daily_records",
    foreignKeys = [
        ForeignKey(
            entity = Barn::class,
            parentColumns = ["id"],
            childColumns = ["barnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("barnId")]
)
data class DailyRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val barnId: Int,
    val date: Long, // Epoch millis
    val ageInDays: Int,
    val feedMorningKg: Double,
    val feedEveningKg: Double,
    val waterLiters: Double,
    val deadMorning: Int,
    val deadEvening: Int,
    val soldQty: Int,
    val internalConsumption: Int,
    val culledQty: Int // الإعدام
)

@Entity(tableName = "stock")
data class StockItem(
    @PrimaryKey val id: String, // STARTER_FEED, GROWER_FEED, FINISHER_FEED, SHAVINGS, MEDICINE, VITAMINS
    val name: String,
    val type: String,
    val currentQuantity: Double,
    val unit: String,
    val minThreshold: Double
)

@Entity(tableName = "stock_transactions")
data class StockTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemId: String, // Reference to StockItem.id
    val transactionType: String, // IN (شراء/توريد), OUT_AUTO (صرف تلفائي), OUT_MANUAL (صرف يدوي)
    val quantity: Double,
    val date: Long,
    val notes: String
)

@Entity(
    tableName = "weights",
    foreignKeys = [
        ForeignKey(
            entity = Barn::class,
            parentColumns = ["id"],
            childColumns = ["barnId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("barnId")]
)
data class WeeklyWeight(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val barnId: Int,
    val weekNumber: Int,
    val sampleSize: Int,
    val averageWeight: Double, // in grams
    val uniformity: Double, // % percentage within 10% of mean
    val standardDeviation: Double, // Standard deviation
    val rawWeightsCsv: String, // Comma separated list of weights (e.g. "120,130,125...")
    val date: Long
)

@Entity(tableName = "alerts")
data class Alert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val barnId: Int?, // Optional link to Barn
    val type: String, // MORTALITY, FEED_DROP, WEIGHT_LOW, STOCK_LOW, ANOMALY
    val section: String, // Barn code or Inventory, etc.
    val message: String,
    val date: Long,
    val isResolved: Boolean = false
)
