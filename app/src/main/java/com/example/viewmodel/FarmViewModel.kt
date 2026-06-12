package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class FarmViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = FarmRepository(db)

    // Current logged-in user role
    // Roles: "ADMIN" (مدير), "SUPERVISOR" (مشرف), "WORKER" (عامل)
    private val _userRole = MutableStateFlow("ADMIN")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    // 1) Barns State
    val barns: StateFlow<List<Barn>> = repository.allBarns.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 2) Stock state
    val stockItems: StateFlow<List<StockItem>> = repository.allStockItems.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 3) Stock Transactions
    val transactions: StateFlow<List<StockTransaction>> = repository.allTransactions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 4) Alerts
    val activeAlerts: StateFlow<List<Alert>> = repository.unresolvedAlerts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allAlerts: StateFlow<List<Alert>> = repository.allAlerts.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 5) All Daily Records
    val dailyRecords: StateFlow<List<DailyRecord>> = repository.allRecords.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // 6) Weights
    val weeklyWeights: StateFlow<List<WeeklyWeight>> = repository.allWeights.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Selected barn filter
    private val _selectedBarnId = MutableStateFlow<Int?>(null)
    val selectedBarnId: StateFlow<Int?> = _selectedBarnId.asStateFlow()

    fun selectBarn(barnId: Int?) {
        _selectedBarnId.value = barnId
    }

    fun updateUserRole(role: String) {
        _userRole.value = role
    }

    // Checking permissions helper
    fun canModify(): Boolean {
        val role = _userRole.value
        return role == "ADMIN" || role == "SUPERVISOR"
    }

    fun canDelete(): Boolean {
        return _userRole.value == "ADMIN"
    }

    // Barn Actions
    fun addBarn(code: String, capacity: Int, initialChicks: Int, breed: String, startDate: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            repository.insertBarn(
                Barn(
                    code = code,
                    capacity = capacity,
                    initialChicks = initialChicks,
                    startDate = startDate,
                    breed = breed
                )
            )
        }
    }

    fun deleteBarn(barn: Barn) {
        viewModelScope.launch {
            if (canDelete()) {
                repository.deleteBarn(barn)
            }
        }
    }

    // Daily Records Actions
    fun addDailyRecord(
        barnId: Int,
        feedMorningKg: Double,
        feedEveningKg: Double,
        waterLiters: Double,
        deadMorning: Int,
        deadEvening: Int,
        soldQty: Int,
        internalConsumption: Int,
        culledQty: Int,
        recordDate: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            val barnList = barns.value
            val barn = barnList.find { it.id == barnId } ?: return@launch
            
            // Calculate Age in days
            val ageDays = ((recordDate - barn.startDate) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)

            val record = DailyRecord(
                barnId = barnId,
                date = recordDate,
                ageInDays = ageDays,
                feedMorningKg = feedMorningKg,
                feedEveningKg = feedEveningKg,
                waterLiters = waterLiters,
                deadMorning = deadMorning,
                deadEvening = deadEvening,
                soldQty = soldQty,
                internalConsumption = internalConsumption,
                culledQty = culledQty
            )

            repository.insertDailyRecord(record, barn.code, barn.initialChicks)
        }
    }

    fun deleteDailyRecord(id: Int) {
        viewModelScope.launch {
            if (canDelete()) {
                repository.deleteDailyRecord(id)
            }
        }
    }

    // Stock Actions
    fun purchaseOrTransferStock(itemId: String, quantity: Double, type: String, notes: String) {
        viewModelScope.launch {
            repository.adjustStockManual(itemId, quantity, type, notes)
        }
    }

    // Weight Actions
    fun saveSampleWeights(barnId: Int, week: Int, rawCsv: String) {
        viewModelScope.launch {
            val barn = barns.value.find { it.id == barnId } ?: return@launch
            repository.insertWeeklyWeight(
                barnId = barnId,
                weekNumber = week,
                rawWeightsCsv = rawCsv,
                barnCode = barn.code,
                breed = barn.breed
            )
        }
    }

    // Resolve alert
    fun resolveAlert(id: Int) {
        viewModelScope.launch {
            repository.resolveAlert(id)
        }
    }

    // ──────────────────────────────────────────────────────────
    // Advanced CALCULATIONS & ANALYSIS (FCR, ADG, Production Index PI)
    // ──────────────────────────────────────────────────────────

    // Helpers to compute calculations for a specific barn
    fun getBarnAnalytics(barnId: Int): BarnAnalysis {
        val barnList = barns.value
        val barn = barnList.find { it.id == barnId } ?: return BarnAnalysis()

        val records = dailyRecords.value.filter { it.barnId == barnId }
        val weights = weeklyWeights.value.filter { it.barnId == barnId }

        val totalFeedConsumed = records.sumOf { it.feedMorningKg + it.feedEveningKg }
        val totalDead = records.sumOf { it.deadMorning + it.deadEvening + it.culledQty }
        val totalSold = records.sumOf { it.soldQty }
        val totalInternal = records.sumOf { it.internalConsumption }
        
        val currentCount = (barn.initialChicks - totalDead - totalSold - totalInternal).coerceAtLeast(0)
        val mortalityPct = if (barn.initialChicks > 0) (totalDead.toDouble() / barn.initialChicks) * 100 else 0.0
        val occupancyPct = if (barn.capacity > 0) (currentCount.toDouble() / barn.capacity) * 100 else 0.0

        // Avg weight (latest recorded weight)
        val latestWeightObj = weights.maxByOrNull { it.weekNumber }
        val avgWeightGrams = latestWeightObj?.averageWeight ?: 42.0 // Day 1 chick is about 42g
        val uniformity = latestWeightObj?.uniformity ?: 0.0

        // Total weight produced (g) = (currentCount * avgWeight) + (totalSold * soldAvgWeight)
        // For simplicity: Weight produced = currentCount * (avgWeightGrams / 1000) + totalSold * 1.8 (assuming marketed weight or average)
        val totalWeightProducedKg = (currentCount * (avgWeightGrams / 1000.0)) + (totalSold * 1.8)

        // FCR = Total Feed Eaten (kg) / Total Weight Produced (kg)
        val fcr = if (totalWeightProducedKg > 0) totalFeedConsumed / totalWeightProducedKg else 0.0

        // ADG (Average Daily Gain in grams)
        // ADG = (Current Weight - Day 1 weight) / currentAge
        val currentAgeDays = if (records.isNotEmpty()) records.maxOf { it.ageInDays } else {
            ((System.currentTimeMillis() - barn.startDate) / (24 * 60 * 60 * 1000L)).toInt().coerceAtLeast(1)
        }
        val adg = (avgWeightGrams - 42.0) / currentAgeDays

        // Production Index (PI) = (Livability % * Average Weight kg) / (Age in Days * FCR) * 100
        val livabilityPct = 100.0 - mortalityPct
        val pi = if (currentAgeDays > 0 && fcr > 0) {
            (livabilityPct * (avgWeightGrams / 1000.0)) / (currentAgeDays * fcr) * 100.0
        } else 0.0

        // Breed comparison predictions
        val targetWeight = repository.getBreedWeightStandard(barn.breed, (currentAgeDays / 7).coerceAtLeast(1))
        val targetFcr = repository.getBreedFCRStandard(barn.breed, (currentAgeDays / 7).coerceAtLeast(1))

        // Predictions
        // Final Weight Prediction at day 38 (grams)
        val expectedFinalWeight = avgWeightGrams + (38 - currentAgeDays).coerceAtLeast(0) * adg.coerceIn(40.0..70.0)
        // Feed forecast to finish cycle (Day 38)
        // Broiler eats ~4.8kg average. Remaining feed per bird = (4.8 - (totalFeedConsumed / barn.initialChicks)).coerceAtLeast(0.0)
        val feedEatenPerBird = if (barn.initialChicks > 0) totalFeedConsumed / barn.initialChicks else 0.0
        val remainingFeedPerBird = (4.75 - feedEatenPerBird).coerceAtLeast(0.0)
        val expectedRequiredFeedKg = remainingFeedPerBird * currentCount

        return BarnAnalysis(
            barnId = barnId,
            code = barn.code,
            breed = barn.breed,
            age = currentAgeDays,
            currentCount = currentCount,
            occupancyPct = occupancyPct,
            mortalityPct = mortalityPct,
            totalDead = totalDead,
            totalFeedKg = totalFeedConsumed,
            avgWeightGrams = avgWeightGrams,
            uniformity = uniformity,
            fcr = fcr,
            adg = adg,
            productionIndex = pi,
            standardWeight = targetWeight,
            standardFcr = targetFcr,
            expectedFinalWeightGrams = expectedFinalWeight,
            expectedRequiredFeedKg = expectedRequiredFeedKg
        )
    }

    // Aggregate values for home dashboard
    fun getGlobalDashboardData(): GlobalDashboard {
        val barnList = barns.value
        val listAnalysis = barnList.map { getBarnAnalytics(it.id) }

        val totalBirds = listAnalysis.sumOf { it.currentCount }
        val totalDead = listAnalysis.sumOf { it.totalDead }
        val overallFcr = if (listAnalysis.isNotEmpty()) listAnalysis.map { it.fcr }.filter { it > 0 }.average() else 0.0
        val expectedWeight = if (listAnalysis.isNotEmpty()) listAnalysis.map { it.expectedFinalWeightGrams }.average() else 0.0

        return GlobalDashboard(
            totalBirds = totalBirds,
            totalDead = totalDead,
            averageFCR = if (overallFcr.isNaN()) 0.0 else overallFcr,
            expectedWeightGrams = if (expectedWeight.isNaN()) 0.0 else expectedWeight
        )
    }

    // ──────────────────────────────────────────────────────────
    // EXPORT & REPORTING SYSTEM
    // ──────────────────────────────────────────────────────────

    // Generate HTML for Reports
    fun generateReportHtml(reportType: String, barnIdFilter: Int?): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val currentDateStr = dateFormat.format(Date())

        val headingTitle = when (reportType) {
            "DAILY" -> "التقرير اليومي الموحد للعنابر"
            "CUMULATIVE" -> "التقرير التحليلي التراكمي"
            "CYCLE" -> "تقرير نهاية الدورة الإنتاجية"
            "MORTALITY" -> "تقرير النفوق وحالة القطيع"
            "PERFORMANCE" -> "تقرير كفاءة وأداء العنابر FCR"
            else -> "تقرير عنبر مخصص"
        }

        // Gather statistics
        val activeBarns = barns.value.filter { barnIdFilter == null || it.id == barnIdFilter }
        val analyzedData = activeBarns.map { getBarnAnalytics(it.id) }

        var tablesHtml = ""
        analyzedData.forEach { analysis ->
            val statusColor = if (analysis.mortalityPct > 5.0) "#ef5350" else "#2e7d32"
            val fcrStatus = if (analysis.fcr in 1.4..1.6) "ممتاز" else if (analysis.fcr < 1.4 && analysis.fcr > 0) "مثالي" else "مرتفع (بحاجة للتحسين)"
            
            tablesHtml += """
            <div class="barn-card">
                <h3>عنبر: ${analysis.code} (${analysis.breed})</h3>
                <table class="report-table">
                    <tr>
                        <th>المؤشر الفني</th>
                        <th>القيمة الفعلية</th>
                        <th>المعيار العالمي</th>
                    </tr>
                    <tr>
                        <td>عمر الطيور (يوم)</td>
                        <td>${analysis.age} يوم</td>
                        <td>-</td>
                    </tr>
                    <tr>
                        <td>العدد الحالي / البداية</td>
                        <td>${analysis.currentCount} / ${activeBarns.find { it.id == analysis.barnId }?.initialChicks} طائر</td>
                        <td>-</td>
                    </tr>
                    <tr>
                        <td>نسبة النفوق الإجمالية</td>
                        <td style="color: $statusColor; font-weight: bold;">${String.format("%.2f", analysis.mortalityPct)}%</td>
                        <td>&lt; 5.0%</td>
                    </tr>
                    <tr>
                        <td>متوسط الوزن الحالي</td>
                        <td>${String.format("%.1f", analysis.avgWeightGrams)} جرام</td>
                        <td>${String.format("%.1f", analysis.standardWeight)} جرام</td>
                    </tr>
                    <tr>
                        <td>معامل التناسق (Uniformity)</td>
                        <td>${String.format("%.1f", analysis.uniformity)}%</td>
                        <td>&gt; 85%</td>
                    </tr>
                    <tr>
                        <td>معامل التحويل الغذائي (FCR)</td>
                        <td style="font-weight: bold;">${String.format("%.3f", analysis.fcr)} ($fcrStatus)</td>
                        <td>${String.format("%.2f", analysis.standardFcr)}</td>
                    </tr>
                    <tr>
                        <td>معدل النمو اليومي (ADG)</td>
                        <td>${String.format("%.1f", analysis.adg)} جرام/يوم</td>
                        <td>&gt; 60 جرام/يوم</td>
                    </tr>
                    <tr>
                        <td>دليل الكفاءة الإنتاجية (PI)</td>
                        <td>${String.format("%.1f", analysis.productionIndex)}</td>
                        <td>&gt; 350</td>
                    </tr>
                    <tr style="background-color: #f1f8e9;">
                        <td>الوزن المتوقع نهاية الدورة (38 يوم)</td>
                        <td><strong>${String.format("%.1f", analysis.expectedFinalWeightGrams)} جرام</strong></td>
                        <td>-</td>
                    </tr>
                    <tr style="background-color: #f1f8e9;">
                        <td>كمية العلف المتبقية المطلوبة للدورة</td>
                        <td><strong>${String.format("%.1f", analysis.expectedRequiredFeedKg)} كجم</strong></td>
                        <td>-</td>
                    </tr>
                </table>
            </div>
            """.trimIndent()
        }

        // Add standard diseases advices by Dr. Dheifallah Al-Hasani
        val vetInsightsHtml = """
        <div class="vet-notice">
            <h4>💡 توصيات بيطرية وفنية هامة — بإشراف د.ضيف الله الحسني:</h4>
            <ul>
                <li><strong>التحكم بالتهوية والأمونيا:</strong> إن ارتفاع غاز الأمونيا في العنابر المغلقة عن 15 ppm يؤدي لصدمات للجهاز التنفسي ويحفز عدوى الـ CRD (الميكوبلازما). حافظ على سرعات مراوح متوازنة.</li>
                <li><strong>علاج السالمونيلا والتهاب السرة:</strong> في الأسبوع الأول، يُنصح باستخدام الكولستين مع جنتامايسين أو دواء وقائي مناسب عند وجود وفيات حضانة مرتفعة.</li>
                <li><strong>تفادي الكوكسيديا:</strong> حافظ على رطوبة النشارة أقل من 25%. في حال الإصابة (إسهال مدمم بعمر 14-25 يوم) استخدم الأمبوليوم 1.25 جم/لتر مع فيتامين K لمدة 3-5 أيام.</li>
                <li><strong>الجمبورو (IBD):</strong> تحصين القطيع في المياه بعمر 12-14 يوماً وتجنب الإجهاد مع إعطاء روافع المناعة (فيتامين E + سلينيوم).</li>
            </ul>
        </div>
        """.trimIndent()

        return """
        <!DOCTYPE html>
        <html dir="rtl" lang="ar">
        <head>
            <meta charset="UTF-8">
            <style>
                body {
                    font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                    color: #333;
                    margin: 20px;
                    background-color: #fafafa;
                }
                .header-container {
                    display: flex;
                    justify-content: space-between;
                    border-bottom: 3px double #2e7d32;
                    padding-bottom: 10px;
                    margin-bottom: 20px;
                }
                .header-right {
                    text-align: right;
                }
                .header-left {
                    text-align: left;
                    font-family: 'Helvetica Neue', Arial, sans-serif;
                }
                .logo-text-ar {
                    font-size: 20px;
                    font-weight: bold;
                    color: #2e7d32;
                }
                .logo-text-en {
                    font-size: 19px;
                    font-weight: bold;
                    color: #2e7d32;
                }
                .sub-text {
                    font-size: 13px;
                    color: #555;
                }
                .report-title-box {
                    text-align: center;
                    margin: 25px 0;
                }
                .report-title {
                    font-size: 22px;
                    font-weight: bold;
                    color: #1b5e20;
                    background-color: #e8f5e9;
                    display: inline-block;
                    padding: 8px 30px;
                    border-radius: 20px;
                    border: 1px solid #a5d6a7;
                }
                .date-stamp {
                    font-size: 12px;
                    color: #777;
                    text-align: center;
                    margin-bottom: 20px;
                }
                .barn-card {
                    background-color: #fff;
                    border: 1px solid #e0e0e0;
                    border-radius: 8px;
                    padding: 15px;
                    margin-bottom: 20px;
                    box-shadow: 0 2px 4px rgba(0,0,0,0.05);
                }
                .barn-card h3 {
                    margin-top: 0;
                    color: #2e7d32;
                    border-bottom: 1px solid #eee;
                    padding-bottom: 5px;
                }
                .report-table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-top: 10px;
                    font-size: 14px;
                }
                .report-table th, .report-table td {
                    border: 1px solid #e0e0e0;
                    padding: 8px 12px;
                    text-align: right;
                }
                .report-table th {
                    background-color: #f5f5f5;
                    font-weight: bold;
                    color: #424242;
                }
                .vet-notice {
                    background-color: #ffe0b2;
                    border-right: 5px solid #f57c00;
                    padding: 15px;
                    margin-top: 30px;
                    border-radius: 4px;
                }
                .vet-notice h4 {
                    margin: 0 0 10px 0;
                    color: #e65100;
                }
                .vet-notice ul {
                    margin: 0;
                    padding-right: 20px;
                    font-size: 13px;
                    line-height: 1.6;
                }
                .footer {
                    margin-top: 50px;
                    border-top: 1px solid #ccc;
                    padding-top: 10px;
                    text-align: center;
                    font-size: 12px;
                    color: #666;
                }
            </style>
        </head>
        <body>
            <div class="header-container">
                <div class="header-right">
                    <div class="logo-text-ar">لمار للخدمات البيطرية</div>
                    <div class="sub-text">استشارات - إشراف - مكملات وأدوية دواجن</div>
                    <div class="sub-text">هاتف: +9677132233940</div>
                </div>
                <div class="header-left">
                    <div class="logo-text-en">Lamar Veterinary Services</div>
                    <div class="sub-text">Consulting - Supervision & Poultry Meds</div>
                    <div class="sub-text">Tel: +9677132233940</div>
                </div>
            </div>

            <div class="report-title-box">
                <div class="report-title">$headingTitle</div>
            </div>
            <div class="date-stamp">تاريخ اصدار التقرير: $currentDateStr</div>

            $tablesHtml

            $vetInsightsHtml

            <div class="footer">
                تنفيذ وتطوير ضيف الله الحسني 773826501 &copy; 2026 - جميع الحقوق محفوظة
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    // PDF Printer flow
    fun printReportPdf(context: Context, reportType: String, barnIdFilter: Int?) {
        val htmlContent = generateReportHtml(reportType, barnIdFilter)
        
        viewModelScope.launch {
            // Must run on main thread for WebView
            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = webView.createPrintDocumentAdapter("تقرير_مزرعة_لمار")
                    val jobName = "Lamar Poultry Report - " + System.currentTimeMillis()
                    
                    printManager.print(
                        jobName,
                        printAdapter,
                        PrintAttributes.Builder().build()
                    )
                }
            }
            webView.loadDataWithBaseURL(null, htmlContent, "text/html", "utf-8", null)
        }
    }

    // Excel CSV export flow
    fun exportReportExcelCSV(context: Context, reportType: String, barnIdFilter: Int?) {
        val activeBarns = barns.value.filter { barnIdFilter == null || it.id == barnIdFilter }
        val analyzedData = activeBarns.map { getBarnAnalytics(it.id) }

        val csvBuilder = StringBuilder()
        csvBuilder.append("الترويسة, لمار للخدمات البيطرية - هاتف +9677132233940\n")
        csvBuilder.append("تقرير الدواجن, $reportType\n")
        csvBuilder.append("التاريخ, ${SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())}\n\n")
        csvBuilder.append("العنبر,السلالة,العمر,العدد الحالي,نسبة النفوق %,الوزن الفعلي (جرام),معامل التناسق %,FCR (التحويل الغذائي),ADG (النمو اليومي),FCR المعياري\n")

        analyzedData.forEach { analysis ->
            csvBuilder.append("${analysis.code},")
            csvBuilder.append("${analysis.breed},")
            csvBuilder.append("${analysis.age},")
            csvBuilder.append("${analysis.currentCount},")
            csvBuilder.append("${String.format("%.2f", analysis.mortalityPct)}%,")
            csvBuilder.append("${String.format("%.1f", analysis.avgWeightGrams)},")
            csvBuilder.append("${String.format("%.1f", analysis.uniformity)}%,")
            csvBuilder.append("${String.format("%.3f", analysis.fcr)},")
            csvBuilder.append("${String.format("%.1f", analysis.adg)},")
            csvBuilder.append("${String.format("%.2f", analysis.standardFcr)}\n")
        }

        csvBuilder.append("\nتذييل الصفحة, تنفيذ وتطوير ضيف الله الحسني 773826501\n")

        try {
            val fileName = "Lamar_Poultry_Report_${System.currentTimeMillis()}.csv"
            val file = File(context.getExternalFilesDir(null), fileName)
            FileOutputStream(file).use { out ->
                // Add UTF-8 BOM for Microsoft Excel to open Arabic text correctly!
                out.write(0xEF)
                out.write(0xBB)
                out.write(0xBF)
                out.write(csvBuilder.toString().toByteArray())
            }
            Toast.makeText(context, "تم تصدير ملف Excel بنجاح: ${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "خطأ في تصدير التقرير: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// Data holder classes for analytics UI
data class BarnAnalysis(
    val barnId: Int = 0,
    val code: String = "",
    val breed: String = "",
    val age: Int = 0,
    val currentCount: Int = 0,
    val occupancyPct: Double = 0.0,
    val mortalityPct: Double = 0.0,
    val totalDead: Int = 0,
    val totalFeedKg: Double = 0.0,
    val avgWeightGrams: Double = 0.0,
    val uniformity: Double = 0.0,
    val fcr: Double = 0.0,
    val adg: Double = 0.0,
    val productionIndex: Double = 0.0,
    val standardWeight: Double = 0.0,
    val standardFcr: Double = 0.0,
    val expectedFinalWeightGrams: Double = 0.0,
    val expectedRequiredFeedKg: Double = 0.0
)

data class GlobalDashboard(
    val totalBirds: Int = 0,
    val totalDead: Int = 0,
    val averageFCR: Double = 0.0,
    val expectedWeightGrams: Double = 0.0
)
