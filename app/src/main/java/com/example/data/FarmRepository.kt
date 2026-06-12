package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.sqrt

class FarmRepository(private val db: AppDatabase) {

    private val barnDao = db.barnDao()
    private val dailyRecordDao = db.dailyRecordDao()
    private val stockDao = db.stockDao()
    private val stockTransactionDao = db.stockTransactionDao()
    private val weeklyWeightDao = db.weeklyWeightDao()
    private val alertDao = db.alertDao()

    // 1) Barns
    val allBarns: Flow<List<Barn>> = barnDao.getAllBarns()
    suspend fun getBarnById(id: Int): Barn? = barnDao.getBarnById(id)
    suspend fun insertBarn(barn: Barn): Long = barnDao.insertBarn(barn)
    suspend fun updateBarn(barn: Barn) = barnDao.updateBarn(barn)
    suspend fun deleteBarn(barn: Barn) = barnDao.deleteBarn(barn)

    // 2) Daily Records
    val allRecords: Flow<List<DailyRecord>> = dailyRecordDao.getAllRecords()
    fun getRecordsForBarn(barnId: Int): Flow<List<DailyRecord>> = dailyRecordDao.getRecordsForBarn(barnId)

    // Smart transaction for inserting record and automatically:
    // - Deducting feed quantity from inventory
    // - Registering stock transactions
    // - Checking and issuing alerts (high death rates, low feed consumption, water-to-feed ratio abnormalities)
    suspend fun insertDailyRecord(record: DailyRecord, barnCode: String, initialChicksCount: Int) {
        // Insert Daily Record
        dailyRecordDao.insertRecord(record)
        val now = System.currentTimeMillis()

        // 1. Calculate Feed Deductions
        val totalFeedConsumed = record.feedMorningKg + record.feedEveningKg
        if (totalFeedConsumed > 0) {
            val feedType = when {
                record.ageInDays <= 12 -> "STARTER_FEED"
                record.ageInDays in 13..25 -> "GROWER_FEED"
                else -> "FINISHER_FEED"
            }

            val currentStock = stockDao.getStockItemById(feedType)
            if (currentStock != null) {
                val newQty = (currentStock.currentQuantity - totalFeedConsumed).coerceAtLeast(0.0)
                stockDao.updateStockQuantity(feedType, newQty)

                // Log automatic stock transaction
                stockTransactionDao.insertTransaction(
                    StockTransaction(
                        itemId = feedType,
                        transactionType = "OUT_AUTO",
                        quantity = totalFeedConsumed,
                        date = record.date,
                        notes = "صرف تلقائي لعنبر ($barnCode) - يوم ${record.ageInDays}"
                    )
                )

                // Inventory Alert: Stock Low
                if (newQty <= currentStock.minThreshold) {
                    alertDao.insertAlert(
                        Alert(
                            barnId = record.barnId,
                            type = "STOCK_LOW",
                            section = "المخزن",
                            message = "مخزون ${currentStock.name} منخفض جداً! المتبقي: $newQty ${currentStock.unit}.",
                            date = now
                        )
                    )
                }
            }
        }

        // 2. Intelligence & Alerts (Drawn from scientific standards under Dr. Dheifallah's supervision)
        val dailyMortalityCount = record.deadMorning + record.deadEvening + record.culledQty
        val dailyMortalityPct = if (initialChicksCount > 0) {
            (dailyMortalityCount.toDouble() / initialChicksCount) * 100
        } else 0.0

        // Warning: if single-day mortality exceeds 0.25%, warn user immediately!
        if (dailyMortalityPct > 0.25) {
            alertDao.insertAlert(
                Alert(
                    barnId = record.barnId,
                    type = "MORTALITY",
                    section = barnCode,
                    message = "ارتفاع معدل النفوق في يوم واحد إلى ${String.format("%.2f", dailyMortalityPct)}% (${dailyMortalityCount} طائر)! يرجى المتابعة البيطرية العاجلة.",
                    date = now
                )
            )
        }

        // Anomaly Detection: water-feed ratio check
        // Standard water intake is usually between 1.6 and 2.3 times feed weight intake
        if (totalFeedConsumed > 0 && record.waterLiters > 0) {
            val waterFeedRatio = record.waterLiters / totalFeedConsumed
            if (waterFeedRatio < 1.4) {
                alertDao.insertAlert(
                    Alert(
                        barnId = record.barnId,
                        type = "ANOMALY",
                        section = barnCode,
                        message = "خلل ذكاء: استهلاك مائي منخفض جداً مقارنة بالعلف (${String.format("%.2f", waterFeedRatio)} لتر/كجم). قد يشير لارتفاع درجة الحرارة المرضي أو فشل خطوط المياه.",
                        date = now
                    )
                )
            } else if (waterFeedRatio > 2.5) {
                alertDao.insertAlert(
                    Alert(
                        barnId = record.barnId,
                        type = "ANOMALY",
                        section = barnCode,
                        message = "خلل ذكاء: استهلاك مائي مفرط (${String.format("%.2f", waterFeedRatio)} لتر/كجم). قد يكون دليلاً على وجود إسهالات معوية حادة أو ارتفاع مفرط بالحرارة.",
                        date = now
                    )
                )
            }
        }
    }

    suspend fun deleteDailyRecord(recordId: Int) {
        dailyRecordDao.deleteRecordById(recordId)
    }

    // 3) Stock
    val allStockItems: Flow<List<StockItem>> = stockDao.getAllStockItems()
    suspend fun getStockItem(id: String): StockItem? = stockDao.getStockItemById(id)
    suspend fun insertStockItem(item: StockItem) = stockDao.insertStockItem(item)
    suspend fun updateStockItemQty(id: String, newQty: Double) = stockDao.updateStockQuantity(id, newQty)

    // Manual stock adjustments (purchase/transfer)
    suspend fun adjustStockManual(itemId: String, quantity: Double, type: String, notes: String) {
        val current = stockDao.getStockItemById(itemId) ?: return
        val newQty = if (type == "IN") {
            current.currentQuantity + quantity
        } else {
            (current.currentQuantity - quantity).coerceAtLeast(0.0)
        }
        stockDao.updateStockQuantity(itemId, newQty)
        stockTransactionDao.insertTransaction(
            StockTransaction(
                itemId = itemId,
                transactionType = type,
                quantity = quantity,
                date = System.currentTimeMillis(),
                notes = notes
            )
        )
    }

    // 4) Stock Transactions
    val allTransactions: Flow<List<StockTransaction>> = stockTransactionDao.getAllTransactions()

    // 5) Weights Analysis
    val allWeights: Flow<List<WeeklyWeight>> = weeklyWeightDao.getAllWeights()
    fun getWeightsForBarn(barnId: Int): Flow<List<WeeklyWeight>> = weeklyWeightDao.getWeightsForBarn(barnId)

    suspend fun insertWeeklyWeight(
        barnId: Int,
        weekNumber: Int,
        rawWeightsCsv: String,
        barnCode: String,
        breed: String
    ): Long {
        // Parse CSV weights and calculate mean, SD, uniformity
        val weights = rawWeightsCsv.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
        
        if (weights.isEmpty()) return -1L

        val avgWeight = weights.average()
        
        // SD
        val variance = weights.map { (it - avgWeight) * (it - avgWeight) }.sum() / weights.size
        val sd = sqrt(variance)

        // Uniformity: % of birds within +/- 10% of standard/mean weight
        val baseTenPct = avgWeight * 0.10
        val lowBound = avgWeight - baseTenPct
        val highBound = avgWeight + baseTenPct
        val inRange = weights.count { it in lowBound..highBound }
        val uniformityVal = (inRange.toDouble() / weights.size) * 100.0

        val rowId = weeklyWeightDao.insertWeight(
            WeeklyWeight(
                barnId = barnId,
                weekNumber = weekNumber,
                sampleSize = weights.size,
                averageWeight = avgWeight,
                uniformity = uniformityVal,
                standardDeviation = sd,
                rawWeightsCsv = rawWeightsCsv,
                date = System.currentTimeMillis()
            )
        )

        // Alert if average weight is significantly lower than breed standard
        val standardWeightGrams = getBreedWeightStandard(breed, weekNumber)
        if (avgWeight < standardWeightGrams * 0.90) { // More than 10% below standard
            alertDao.insertAlert(
                Alert(
                    barnId = barnId,
                    type = "WEIGHT_LOW",
                    section = barnCode,
                    message = "الوزن الأسبوعي (${String.format("%.1fg", avgWeight)}) متخلف بوضوح عن معيار سلالة $breed للأسبوع $weekNumber المتوقع فيه: (${standardWeightGrams}g). يرجى تحسين جودة التهوية ومراجعة جودة العلف.",
                    date = System.currentTimeMillis()
                )
            )
        }

        return rowId
    }

    // 6) Alerts
    val unresolvedAlerts: Flow<List<Alert>> = alertDao.getUnresolvedAlerts()
    val allAlerts: Flow<List<Alert>> = alertDao.getAllAlerts()
    suspend fun resolveAlert(alertId: Int) = alertDao.resolveAlert(alertId)
    suspend fun insertManualAlert(alert: Alert) = alertDao.insertAlert(alert)

    // Breed Standards Helper (Cobb, Ross, Indian River)
    // Dynamic values representing target weight (in grams) for weeks 1 to 6
    fun getBreedWeightStandard(breed: String, weekNumber: Int): Double {
        return when (breed.lowercase()) {
            "روص 308 (ross)", "ross" -> when (weekNumber) {
                1 -> 190.0
                2 -> 490.0
                3 -> 970.0
                4 -> 1580.0
                5 -> 2250.0
                6 -> 2950.0
                else -> 190.0 + (weekNumber - 1) * 500.0
            }
            "إنديان ريفر (indian river)", "indian river" -> when (weekNumber) {
                1 -> 180.0
                2 -> 465.0
                3 -> 920.0
                4 -> 1515.0
                5 -> 2170.0
                6 -> 2840.0
                else -> 180.0 + (weekNumber - 1) * 480.0
            }
            else -> { // Cobb 500 default
                when (weekNumber) {
                    1 -> 195.0
                    2 -> 485.0
                    3 -> 965.0
                    4 -> 1590.0
                    5 -> 2290.0
                    6 -> 3000.0
                    else -> 195.0 + (weekNumber - 1) * 510.0
                }
            }
        }
    }

    fun getBreedFCRStandard(breed: String, weekNumber: Int): Double {
        // Target cumulative FCR per week
        return when (weekNumber) {
            1 -> 0.85
            2 -> 1.05
            3 -> 1.25
            4 -> 1.45
            5 -> 1.62
            6 -> 1.78
            else -> 1.45 + (weekNumber * 0.05)
        }
    }
}
