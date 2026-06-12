package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.BarnAnalysis
import com.example.viewmodel.FarmViewModel
import com.example.viewmodel.GlobalDashboard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// BILINGUAL TRANSLATION SYSTEM
// ──────────────────────────────────────────────────────────
class TranslationHelper(val isAr: Boolean) {
    fun t(ar: String, en: String): String = if (isAr) ar else en
}

@Composable
fun MainAppScreen() {
    val viewModel: FarmViewModel = viewModel()
    val context = LocalContext.current

    // Observe streams
    val barns by viewModel.barns.collectAsState()
    val stockItems by viewModel.stockItems.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val activeAlerts by viewModel.activeAlerts.collectAsState()
    val dailyRecords by viewModel.dailyRecords.collectAsState()
    val weeklyWeights by viewModel.weeklyWeights.collectAsState()
    
    val userRole by viewModel.userRole.collectAsState()
    val selectedBarnId by viewModel.selectedBarnId.collectAsState()

    // UI state
    var currentTab by remember { mutableStateOf("dashboard") }
    var isArabic by remember { mutableStateOf(true) }
    val tr = remember(isArabic) { TranslationHelper(isArabic) }

    // Dialog state
    var showAddBarnDialog by remember { mutableStateOf(false) }
    var showAddWeightDialog by remember { mutableStateOf(false) }
    var showAddStockDialog by remember { mutableStateOf(false) }
    var activeBarnForWeights by remember { mutableStateOf<Barn?>(null) }

    val dbData = viewModel.getGlobalDashboardData()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                // Top Custom Durable App Bar - Clean Minimalism Style
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    Column {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1.5f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = tr.t("لمار للخدمات البيطرية 🐔", "Lamar Veterinary Services 🐔"),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = tr.t("بواب الدواجن الذكي • د.ضيف الله الحسني", "Smart Poultry Portal • Dr. Dheifallah"),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = tr.t("تواصل: 773826501 | 713223940", "Contact: 773826501 | 713223940"),
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Normal
                                )
                            }

                            // Right Controls (Language & Role Switcher)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                var roleExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Surface(
                                        onClick = { roleExpanded = true },
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Text(
                                                text = when (userRole) {
                                                    "ADMIN" -> tr.t("مدير", "Manager")
                                                    "SUPERVISOR" -> tr.t("مشرف", "Supervisor")
                                                    else -> tr.t("عامل", "Worker")
                                                },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = roleExpanded,
                                        onDismissRequest = { roleExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(tr.t("مدير (كامل الصلاحية)", "Manager (Full Admin)")) },
                                            onClick = {
                                                viewModel.updateUserRole("ADMIN")
                                                roleExpanded = false
                                                Toast.makeText(context, tr.t("أنت الآن بصفة: مدير", "Role swapped: Manager"), Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr.t("مشرف (إدخال وتعديل)", "Supervisor (Edit)")) },
                                            onClick = {
                                                viewModel.updateUserRole("SUPERVISOR")
                                                roleExpanded = false
                                                Toast.makeText(context, tr.t("أنت الآن بصفة: مشرف ومسجل بيانات", "Role swapped: Supervisor"), Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(tr.t("عامل (عرض فقط)", "Worker (View)")) },
                                            onClick = {
                                                viewModel.updateUserRole("WORKER")
                                                roleExpanded = false
                                                Toast.makeText(context, tr.t("أنت الآن بصفة: عامل مراقبة", "Role swapped: Worker"), Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }

                                // Language Switcher
                                IconButton(
                                    onClick = { isArabic = !isArabic },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Translate,
                                        contentDescription = "Language",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        // Bottom border divider
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp
                        )
                    }
                }

                // Quick global diagnostics banner
                if (activeAlerts.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(vertical = 4.dp, horizontal = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = tr.t(
                                    "تنبيه نشط! لديك عدد (${activeAlerts.size}) خلل أو مؤشرات نفوق مرتفعة.",
                                    "Active Warning! You have (${activeAlerts.size}) health anomalies."
                                ),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { currentTab = "dashboard" },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(24.dp)
                            ) {
                                Text(tr.t("عرض", "Show"), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    thickness = 1.dp
                )
                NavigationBar(
                    windowInsets = WindowInsets.navigationBars,
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    NavigationBarItem(
                        selected = currentTab == "dashboard",
                        onClick = { currentTab = "dashboard" },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text(tr.t("الرئيسية", "Main"), fontSize = 10.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == "barns",
                        onClick = { currentTab = "barns" },
                        icon = { Icon(Icons.Default.HomeWork, contentDescription = null) },
                        label = { Text(tr.t("العنابر", "Barns"), fontSize = 10.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == "records",
                        onClick = { currentTab = "records" },
                        icon = { Icon(Icons.Default.EditCalendar, contentDescription = null) },
                        label = { Text(tr.t("اليومية", "Daily"), fontSize = 10.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == "inventory",
                        onClick = { currentTab = "inventory" },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                        label = { Text(tr.t("المخزن", "Stock"), fontSize = 10.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == "veterinary",
                        onClick = { currentTab = "veterinary" },
                        icon = { Icon(Icons.Default.MedicalServices, contentDescription = null) },
                        label = { Text(tr.t("الوقائي", "Veterinary"), fontSize = 10.sp) }
                    )
                    NavigationBarItem(
                        selected = currentTab == "reports",
                        onClick = { currentTab = "reports" },
                        icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                        label = { Text(tr.t("التقرير", "Reports"), fontSize = 10.sp) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main content based on Tab
            AnimatedContent(
                targetState = currentTab,
                transitionSpec = {
                    slideInHorizontally { width -> if (isArabic) -width else width } + fadeIn() togetherWith
                            slideOutHorizontally { width -> if (isArabic) width else -width } + fadeOut()
                },
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    "dashboard" -> DashboardTab(
                        viewModel = viewModel,
                        dbData = dbData,
                        activeAlerts = activeAlerts,
                        tr = tr
                    )
                    "barns" -> BarnsTab(
                        viewModel = viewModel,
                        barns = barns,
                        tr = tr,
                        onAddBarnClick = { showAddBarnDialog = true },
                        onWeighClick = { barn ->
                            activeBarnForWeights = barn
                            showAddWeightDialog = true
                        }
                    )
                    "records" -> RecordsTab(
                        viewModel = viewModel,
                        barns = barns,
                        dailyRecords = dailyRecords,
                        tr = tr
                    )
                    "inventory" -> InventoryTab(
                        viewModel = viewModel,
                        stockItems = stockItems,
                        transactions = transactions,
                        tr = tr,
                        onAdjustClick = { showAddStockDialog = true }
                    )
                    "veterinary" -> VeterinaryTab(
                        tr = tr
                    )
                    "reports" -> ReportsTab(
                        viewModel = viewModel,
                        barns = barns,
                        tr = tr
                    )
                }
            }
        }
    }

    // Modal dialogue: Add Barn
    if (showAddBarnDialog) {
        AddBarnDialog(
            tr = tr,
            onDismiss = { showAddBarnDialog = false },
            onConfirm = { code, cap, chicks, breed ->
                viewModel.addBarn(code, cap, chicks, breed)
                showAddBarnDialog = false
            }
        )
    }

    // Modal dialogue: Add Weekly Weights
    if (showAddWeightDialog && activeBarnForWeights != null) {
        AddWeightDialog(
            barn = activeBarnForWeights!!,
            tr = tr,
            onDismiss = { showAddWeightDialog = false },
            onConfirm = { week, csv ->
                viewModel.saveSampleWeights(activeBarnForWeights!!.id, week, csv)
                showAddWeightDialog = false
                activeBarnForWeights = null
            }
        )
    }

    // Modal dialogue: Add Stock
    if (showAddStockDialog) {
        AdjustStockDialog(
            stockItems = stockItems,
            tr = tr,
            onDismiss = { showAddStockDialog = false },
            onConfirm = { itemId, qty, type, notes ->
                viewModel.purchaseOrTransferStock(itemId, qty, type, notes)
                showAddStockDialog = false
                Toast.makeText(context, tr.t("تم حفظ معاملة المخزون بنجاح", "Stock transaction saved successfully"), Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ──────────────────────────────────────────────────────────
// SCREEN 1: DASHBOARD TAB
// ──────────────────────────────────────────────────────────
@Composable
fun DashboardTab(
    viewModel: FarmViewModel,
    dbData: GlobalDashboard,
    activeAlerts: List<Alert>,
    tr: TranslationHelper
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome and vet authority banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = tr.t("لوحة الإدارة والمؤشرات الحيوية 📊", "Management Portal & Vital Indicators 📊"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tr.t(
                            "هذا النظام مغلق وحديث يحسب FCR التراكمي ونسبة النفوق، بإشراف الدليل البيطري للمار للخدمات البيطرية والمتابعة العلمية للدكتور ضيف الله الحسني.",
                            "Closed system manager dynamically calculating cumulative FCR and mortality, under direct advisory of Lamar Veterinary Services and Dr. Dheifallah Al-Hasani."
                        ),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Global Grid Indicators
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardIndicatorCard(
                    title = tr.t("العدد الكلي للطيور", "Total Flock Birds"),
                    value = "${dbData.totalBirds}",
                    extra = tr.t("طائر حي", "live birds"),
                    icon = Icons.Default.Pets,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                DashboardIndicatorCard(
                    title = tr.t("إجمالي النافق والهلاك", "Total Dead / Culled"),
                    value = "${dbData.totalDead}",
                    extra = tr.t("حالة وفاة", "mortalities"),
                    icon = Icons.Default.DisabledByDefault,
                    color = Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardIndicatorCard(
                    title = tr.t("معامل التحويل (FCR)", "Flock Avg FCR"),
                    value = if (dbData.averageFCR > 0) String.format("%.3f", dbData.averageFCR) else "0.000",
                    extra = tr.t("كيلو علف/كجم لحم", "kg feed/kg bird"),
                    icon = Icons.Default.TrendingUp,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
                DashboardIndicatorCard(
                    title = tr.t("الوزن المتوقع للتسويق", "Flock Expected Weight"),
                    value = if (dbData.expectedWeightGrams > 0) "${dbData.expectedWeightGrams.toInt()}g" else "42g",
                    extra = tr.t("نهاية الأسبوع 5", "by week 5 end"),
                    icon = Icons.Default.Scale,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Active Diagnostics & Alerts Section (Realtime offline anomaly detection!)
        item {
            Text(
                text = tr.t("تنبيهات عاجلة وكاشف القيم الشاذه (AI/Diagnostics) 🚨", "Urgent Diagnostics & Anomaly Alerts 🚨"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (activeAlerts.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Safe",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = tr.t("الحيوانات في وضع مستقر", "Herd Status Green"),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = tr.t("لا توجد خلل مائي أو وفيات غير طبيعية مسجلة حالياً.", "No abnormal feed drops, water ratios or mortality spikes detected."),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            items(activeAlerts) { alert ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (alert.type) {
                            "MORTALITY" -> Color(0xFFFFEBEE)
                            "ANOMALY" -> Color(0xFFFFF3E0)
                            else -> Color(0xFFE0F7FA)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (alert.type) {
                                    "MORTALITY" -> Icons.Default.Dangerous
                                    "ANOMALY" -> Icons.Default.ReportProblem
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = when (alert.type) {
                                    "MORTALITY" -> Color(0xFFC62828)
                                    "ANOMALY" -> Color(0xFFE65100)
                                    else -> Color(0xFF006064)
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "[ ${alert.section} ] - ${alert.type}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = { viewModel.resolveAlert(alert.id) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = when (alert.type) {
                                        "MORTALITY" -> Color(0xFFC62828)
                                        "ANOMALY" -> Color(0xFFE65100)
                                        else -> Color(0xFF006064)
                                    }
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(tr.t("حل المشكلة", "Resolve"), fontSize = 10.sp, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = alert.message,
                            fontSize = 11.sp,
                            color = Color.Black.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardIndicatorCard(
    title: String,
    value: String,
    extra: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.height(115.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = extra,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ──────────────────────────────────────────────────────────
// SCREEN 2: BARNS MANAGER TAB
// ──────────────────────────────────────────────────────────
@Composable
fun BarnsTab(
    viewModel: FarmViewModel,
    barns: List<Barn>,
    tr: TranslationHelper,
    onAddBarnClick: () -> Unit,
    onWeighClick: (Barn) -> Unit
) {
    Scaffold(
        floatingActionButton = {
            if (viewModel.canModify()) {
                FloatingActionButton(
                    onClick = onAddBarnClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Barn")
                }
            }
        },
        bottomBar = {} // Required to dodge overlap
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = tr.t("حالة وبيانات العنابر الحالية 🏛️", "Barns Management & Capacity 🏛️"),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = tr.t("اضغط على أي عنبر لإضافة وزن عينة وتحليل التناسق", "Tap any barn to enter weekly sample weights"),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (barns.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(tr.t("لا توجد عنابر مضافة حالياً. أضف عنبر جديد من زر +", "No active barns. Create one using the + button."))
                    }
                }
            } else {
                items(barns) { barn ->
                    // Calculate live analytics for this specific barn
                    val analysis = viewModel.getBarnAnalytics(barn.id)
                    BarnCardItem(
                        barn = barn,
                        analysis = analysis,
                        tr = tr,
                        onWeighClick = { onWeighClick(barn) },
                        onDeleteClick = {
                            viewModel.deleteBarn(barn)
                        },
                        canDelete = viewModel.canDelete()
                    )
                }
            }
        }
    }
}

@Composable
fun BarnCardItem(
    barn: Barn,
    analysis: BarnAnalysis,
    tr: TranslationHelper,
    onWeighClick: () -> Unit,
    onDeleteClick: () -> Unit,
    canDelete: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = barn.code,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = tr.t("السلالة: ${barn.breed}", "Breed: ${barn.breed}"),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Text(
                            text = tr.t("عمر: ${analysis.age} يوم", "${analysis.age} Days Old"),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (canDelete) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDeleteClick, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp
            )

            // Quantities
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr.t("العدد عند البداية", "Initial Chicks"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${barn.initialChicks} ${tr.t("صوص", "chicks")}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr.t("العدد الحالي", "Current Stock"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${analysis.currentCount} ${tr.t("طائر", "birds")}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr.t("نسبة النفوق", "Mortality Rate"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${String.format("%.2f", analysis.mortalityPct)}%",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (analysis.mortalityPct > 5.0) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Feed & Weight
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr.t("معامل الكفاءة (FCR)", "Cumulative FCR"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (analysis.fcr > 0) String.format("%.3f", analysis.fcr) else "N/A",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr.t("الوزن (فعلي / معياري)", "Weight (Actual/Target)"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${analysis.avgWeightGrams.toInt()}g / ${analysis.standardWeight.toInt()}g",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (analysis.avgWeightGrams < analysis.standardWeight * 0.9) Color(0xFFE65100) else MaterialTheme.colorScheme.onSurface
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(tr.t("تناسق الوزن (Uniformity)", "Uniformity index"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (analysis.uniformity > 0) "${String.format("%.1f", analysis.uniformity)}%" else "N/A",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (analysis.uniformity >= 85.0) Color(0xFF2E7D32) else if (analysis.uniformity < 75.0 && analysis.uniformity > 0.0) Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action: Weigh weekly samples
            Button(
                onClick = onWeighClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Icon(Icons.Default.Scale, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(tr.t("إدخل وحساب عينات الأوزان الأسبوعية ⚖️", "Analyze Sample Weights ⚖️"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// SCREEN 3: RECORDS LOG / ENTRY TAB
// ──────────────────────────────────────────────────────────
@Composable
fun RecordsTab(
    viewModel: FarmViewModel,
    barns: List<Barn>,
    dailyRecords: List<DailyRecord>,
    tr: TranslationHelper
) {
    var selectedBarnForRecord by remember { mutableStateOf<Barn?>(null) }
    
    // Form Inputs
    var feedMorning by remember { mutableStateOf("") }
    var feedEvening by remember { mutableStateOf("") }
    var waterLiters by remember { mutableStateOf("") }
    var deadMorning by remember { mutableStateOf("") }
    var deadEvening by remember { mutableStateOf("") }
    var culledQty by remember { mutableStateOf("") }
    var soldQty by remember { mutableStateOf("") }
    var internalQty by remember { mutableStateOf("") }

    // Set default barn
    LaunchedEffect(barns) {
        if (selectedBarnForRecord == null && barns.isNotEmpty()) {
            selectedBarnForRecord = barns.first()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = tr.t("السجل اليومي والتحصين الذكي 📅", "Smart Daily Records & Immunization 📅"),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = tr.t(
                    "أدخل الكميات المنصرفة للقطيع يومياً وسيقوم النظام فوراً بحساب FCR، معدل النفوق، وصرف العلف والماء تلقائياً من المخازن.",
                    "Log dynamic feed, water and flock states. System auto-deducts stocks and evaluates health ratios."
                ),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (barns.isEmpty()) {
            item {
                Text(
                    text = tr.t("عليك إضافة عنبر أولاً لتتمكن من إدخال التقارير اليومية.", "Add at least one barn to enable daily record writing."),
                    color = Color.Red,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        } else {
            // Screen Row Selector
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(tr.t("اختر العنبر المراد الكتابة له:", "Select target Barn:"), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            barns.forEach { barn ->
                                FilterChip(
                                    selected = selectedBarnForRecord?.id == barn.id,
                                    onClick = { selectedBarnForRecord = barn },
                                    label = { Text(barn.code) }
                                )
                            }
                        }
                    }
                }
            }

            // Entry Form Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            tr.t(
                                "نموذج الإدخال الذكي لليوم (العمر المتوقع: ${selectedBarnForRecord?.let { (System.currentTimeMillis() - it.startDate) / (24 * 60 * 60 * 1000L) } ?: 0} يوم)",
                                "Smart Entry Form (Computed age: ${selectedBarnForRecord?.let { (System.currentTimeMillis() - it.startDate) / (24 * 60 * 60 * 1000L) } ?: 0} days)"
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        // Feed Section
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = feedMorning,
                                onValueChange = { feedMorning = it },
                                label = { Text(tr.t("علف صباحي (كجم)", "AM Feed (kg)")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("feed_am_input")
                            )
                            OutlinedTextField(
                                value = feedEvening,
                                onValueChange = { feedEvening = it },
                                label = { Text(tr.t("علف مسائي (كجم)", "PM Feed (kg)")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("feed_pm_input")
                            )
                        }

                        // Water Section
                        OutlinedTextField(
                            value = waterLiters,
                            onValueChange = { waterLiters = it },
                            label = { Text(tr.t("الماء المستهلك اليوم (لتر)", "Water Intake (Liters)")) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("water_input")
                        )

                        // Mortality Sections
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = deadMorning,
                                onValueChange = { deadMorning = it },
                                label = { Text(tr.t("نافق صباحي", "AM Deaths")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = deadEvening,
                                onValueChange = { deadEvening = it },
                                label = { Text(tr.t("نافق مسائي", "PM Deaths")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = culledQty,
                                onValueChange = { culledQty = it },
                                label = { Text(tr.t("الاستبعاد/إعدام", "Culled qty")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Selling & Consumption Sections
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = soldQty,
                                onValueChange = { soldQty = it },
                                label = { Text(tr.t("بيع من العنبر", "Sold qty")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = internalQty,
                                onValueChange = { internalQty = it },
                                label = { Text(tr.t("استهلاك ذاتي", "Internal use")) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        val context = LocalContext.current
                        Button(
                            onClick = {
                                if (selectedBarnForRecord == null) return@Button

                                // Verify role permissions
                                if (!viewModel.canModify()) {
                                    Toast.makeText(context, tr.t("ليس لديك صلاحية لإدخال بيانات يومية. عادل صلاحيتك من ترويسة الصفحة أولاً.", "Unauthorized. Swap user privileges at the top header."), Toast.LENGTH_LONG).show()
                                    return@Button
                                }

                                val morningFeedVal = feedMorning.toDoubleOrNull() ?: 0.0
                                val eveningFeedVal = feedEvening.toDoubleOrNull() ?: 0.0
                                val waterVal = waterLiters.toDoubleOrNull() ?: 0.0
                                val deadAmVal = deadMorning.toIntOrNull() ?: 0
                                val deadPmVal = deadEvening.toIntOrNull() ?: 0
                                val culledVal = culledQty.toIntOrNull() ?: 0
                                val soldVal = soldQty.toIntOrNull() ?: 0
                                val internalVal = internalQty.toIntOrNull() ?: 0

                                viewModel.addDailyRecord(
                                    barnId = selectedBarnForRecord!!.id,
                                    feedMorningKg = morningFeedVal,
                                    feedEveningKg = eveningFeedVal,
                                    waterLiters = waterVal,
                                    deadMorning = deadAmVal,
                                    deadEvening = deadPmVal,
                                    soldQty = soldVal,
                                    internalConsumption = internalVal,
                                    culledQty = culledVal
                                )

                                Toast.makeText(context, tr.t("تم حفظ النشرة ودراسة الاستهلاك ومخصومات المستودع تلقائياً!", "Entry saved & automatic inventory deductions processed!"), Toast.LENGTH_SHORT).show()

                                // Clear forms
                                feedMorning = ""
                                feedEvening = ""
                                waterLiters = ""
                                deadMorning = ""
                                deadEvening = ""
                                culledQty = ""
                                soldQty = ""
                                internalQty = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(tr.t("حفظ وإشراك التحليل الحركي 🚜", "Save & Apply Feed Calculations 🚜"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Selected Barn History
        item {
            Text(
                text = tr.t("سجل القيود المأخوذة مؤخرا 🧬", "Recent Logs History 🧬"),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        val filteredRecords = dailyRecords.filter { selectedBarnForRecord == null || it.barnId == selectedBarnForRecord!!.id }
        if (filteredRecords.isEmpty()) {
            item {
                Text(
                    text = tr.t("لم يتم تسجيل قيود لهذا العنبر في الدورة الحالية.", "No recent log history found for this barn."),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(filteredRecords) { record ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr.t("يوم عمر: ${record.ageInDays}", "Age Day: ${record.ageInDays}"),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            val formattedDate = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(record.date))
                            Text(text = formattedDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(tr.t("العلائف: ${record.feedMorningKg + record.feedEveningKg}كجم", "Feed: ${record.feedMorningKg + record.feedEveningKg}kg"), fontSize = 11.sp)
                            Text(tr.t("المياه: ${record.waterLiters} لتر", "Water: ${record.waterLiters}L"), fontSize = 11.sp)
                            Text(tr.t("وفيات: ${record.deadMorning + record.deadEvening} نافق", "Deaths: ${record.deadMorning + record.deadEvening}"), fontSize = 11.sp, color = Color.Red.copy(alpha = 0.8f))
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// SCREEN 4: INVENTORY SMART TAB
// ──────────────────────────────────────────────────────────
@Composable
fun InventoryTab(
    viewModel: FarmViewModel,
    stockItems: List<StockItem>,
    transactions: List<StockTransaction>,
    tr: TranslationHelper,
    onAdjustClick: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            if (viewModel.canModify()) {
                FloatingActionButton(
                    onClick = onAdjustClick,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.AddBusiness, contentDescription = "Add Inventory")
                }
            }
        },
        bottomBar = {}
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column {
                    Text(
                        text = tr.t("إدارة المخزون والعلائف والأدوية 📦", "Stock Tracker & feed bins 📦"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = tr.t(
                            "مستودع علائف (Starter/Grower/Finisher) والنشارة والأدوية. يصرف فوراً من التقرير اليومي تلقائياً.",
                            "Meters current feed and veterinary medications. Automatically subtracted in real-time."
                        ),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Global stock inventory cards
            items(stockItems) { item ->
                // Estimate remaining days
                // Let's assume daily consumption averages. (Normally ~0.15 kg per bird)
                val totalBirds = viewModel.getGlobalDashboardData().totalBirds
                val dayConsumption = if (totalBirds > 0) totalBirds * 0.15 else 100.0 // fallback
                val remainingDaysForecast = if (item.id.contains("FEED")) {
                    item.currentQuantity / dayConsumption
                } else null

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${String.format("%.1f", item.currentQuantity)} ${item.unit}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (item.currentQuantity <= item.minThreshold) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Progress Level bar
                        val fillRatio = (item.currentQuantity / 12000.0).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fillRatio },
                            color = if (item.currentQuantity <= item.minThreshold) Color.Red else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = tr.t("الحد الأدنى الآمن: ${item.minThreshold.toInt()}", "Min Safe Limit: ${item.minThreshold.toInt()}"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (remainingDaysForecast != null) {
                                Text(
                                    text = if (remainingDaysForecast < 3) {
                                        tr.t("⚠️ ينفد خلال ${remainingDaysForecast.toInt()} أيام!", "⚠️ Empty in ${remainingDaysForecast.toInt()} days!")
                                    } else {
                                        tr.t("يكفي لـ ${remainingDaysForecast.toInt()} يوم عمل", "Sufficient for ${remainingDaysForecast.toInt()} days")
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (remainingDaysForecast < 3) Color.Red else Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                }
            }

            // Transaction log header
            item {
                Text(
                    text = tr.t("سجل التوريدات والصرف الأخير للمخازن 📜", "Recent Stock Ledgers 📜"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Text(
                        tr.t("لا توجد تحركات مخزنية مدونة حالياً.", "No inventory operations saved yet."),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(transactions.take(15)) { tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                val itemTypeStr = when(tx.itemId) {
                                    "STARTER_FEED" -> tr.t("علف بادئ", "Starter Feed")
                                    "GROWER_FEED" -> tr.t("علف نامي", "Grower Feed")
                                    "FINISHER_FEED" -> tr.t("علف ناهي", "Finisher Feed")
                                    "SHAVINGS" -> tr.t("نشارة", "Shavings")
                                    "MEDICINE" -> tr.t("أدوية", "Medication")
                                    "VITAMINS" -> tr.t("فيتامينات", "Vitamins")
                                    else -> tx.itemId
                                }
                                Text(
                                    text = "$itemTypeStr - " + if (tx.transactionType == "IN") tr.t("توريد/شراء", "Purchase/Refill") else tr.t("صرف آلي", "Auto Consumption"),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(tx.notes, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                text = (if (tx.transactionType == "IN") "+" else "-") + "${tx.quantity}",
                                fontWeight = FontWeight.Bold,
                                color = if (tx.transactionType == "IN") Color(0xFF2E7D32) else Color.Red,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// SCREEN 5: VETERINARY ENCYCLOPEDIA TAB
// ──────────────────────────────────────────────────────────
@Composable
fun VeterinaryTab(
    tr: TranslationHelper
) {
    val diseases = remember {
        listOf(
            Disease(
                nameAr = "أمفاليتس / التهاب السرة (Omphalitis)",
                ageAr = "عمر 1 - 7 أيام (فترة الحضانة)",
                nameEn = "Omphalitis (Yolk sac infection)",
                ageEn = "Days 1 to 7 (Brooding phase)",
                causeAr = "تلوث جرثومي (E. coli, Pseudomonas) في المفقس قبل تفريخ البيض.",
                causeEn = "Bacterial contamination (E. coli, Pseudomonas) at hatchery or brooding tray.",
                symptomsAr = "كتاكيت خاملة ومتجمعة، جدار بطن غير ملتئم، انتفاخ في منطقة السرة، بطن طري ورطوبة مفرطة بمؤشر نفوق أسبوعي متنامي.",
                symptomsEn = "Listless chicks clustering near heaters, unhealed naval scars, soft bloated mushy abdomens with rising first-week mortality.",
                treatmentAr = "استخدام مضاد حيوي واسع النطاق وقائي مثل كولستين (Colistin) 100,000 UI/كجم أو جنتامايسين في مياه الشرب.",
                treatmentEn = "Broad spectrum supportive water antibiotics: Colistin 100,000 UI per kg bodyweight or Gentamicin for 3-5 days.",
                preventionAr = "تعقيم مكائن المفقس بالكامل بالتبخير والتحكم بحرارة رطوبة العنبر في أول يوم لتثبيتها عند 33-34 مئوية.",
                preventionEn = "Fully disinfect hatchery equipment. Keep house temperature stabilized at 33-34°C during first 48 hours."
            ),
            Disease(
                nameAr = "الكوكسيديا (Coccidiosis)",
                ageAr = "عمر 14 - 28 يوماً (الفترة الانتقالية للنمو)",
                nameEn = "Coccidiosis",
                ageEn = "Days 14 to 28 (Mid growth period)",
                causeAr = "طفيليات الأيميريا النشطة (Eimeria protozoa) المضاعفة داخل الأمعاء.",
                causeEn = "Eimeria protozoa multiplying rapidly inside moist warm litter.",
                symptomsAr = "إسهال بني مائي أو مدمم، خمول كامل، ريش منتفخ، فقدان ملحوظ للشهية وتراجع حاد في النمو والوزن الفعلي.",
                symptomsEn = "Bloody, dark diarrhea, listlessness, ruffled wet feathers, severe appetite drop with rapid weight loss.",
                treatmentAr = "أمبوليوم (Amprolium) بجرعة 1.25 جرام لكل لتر أو دواء تولترازوريل (Toltrazuril 2.5%) بجرعة 1 مل لكل لتر مياه لمدة يومين متتاليين.",
                treatmentEn = "Amprolium 20% dosage: 1.25g per liter. Alternatively, Toltrazuril (Baycox) 2.5% at 1ml/liter water for 48 hours.",
                preventionAr = "الحفاظ على جفاف النشارة وتجديد الرواسب بشكل دوري. تجنب ارتفاع نسبة رطوبة التغطية الأرضية عن 25%.",
                preventionEn = "Keep shavings completely dry. Repair leaky nipples immediately to prevent litter humidity from exceeding 25%."
            ),
            Disease(
                nameAr = "مرض الجمبورو (Gumboro / IBD)",
                ageAr = "عمر 12 - 24 يوماً (المرض المدمر للمناعة)",
                nameEn = "Infectious Bursal Disease (IBD)",
                ageEn = "Days 12 to 24 (Immunosuppressive spike)",
                causeAr = "فيروس الجمبورو (Birnavirus) الذي يدمر حويصلة فابريشيا المسؤولة عن بناء أجسام المناعة.",
                causeEn = "Infectious Birnavirus attacking the Bursa of Fabricius organ.",
                symptomsAr = "خمول جماعي، ارتعاش، إسهال طباشيري أبيض، نقر في فتحة المجمع، قفز مفاجئ في الوفيات اليومية لأكثر من 1-2% يومياً.",
                symptomsEn = "Sudden onset of severe depression, ruffled feathers, white watery chalky diarrhea, high mortality spike.",
                treatmentAr = "لا يوجد علاج مباشر كونه مرض فيروسي. يتم إعطاء مغسلات الكلى ومضاد الإجهاد وفيتامين C مع روافع المناعة (فيتامين E-Sel).",
                treatmentEn = "No direct antibiotic cure. Provide supportive renal tonics, high-dose Vitamin C + E-Selenium to survive virus phase.",
                preventionAr = "الالتزام الصارم بجدول اللقاحات الوقائي في عمري 12 و 18 يوماً عن طريق ماء الشرب البارد بعد تعطيش القطيع ساعتين.",
                preventionEn = "Strictly implement Live vaccine schedule (IBD Intermediate Plus) around days 12 and 18 via clean, cool drinking water."
            ),
            Disease(
                nameAr = "التهاب الجهاز التنفسي المزمن (CRD Mycoplasma)",
                ageAr = "عمر 21 - 45 يوماً (نهاية الدورة)",
                nameEn = "Chronic Respiratory Disease (CRD)",
                ageEn = "Days 21 to 45 (Late cycle respiratory block)",
                causeAr = "بكتريا المايكوبلازما (Mycoplasma gallisepticum) المضاعفة بفعل غاز الأمونيا وسوء التهوية.",
                causeEn = "Mycoplasma bacteria triggered by high ammonia concentration (>20 ppm) and weak closed fan automation.",
                symptomsAr = "خرخرة تنفسية (صوت كحة)، تورم في الجيوب الأنفية، إفرازات رغوية في العين، وانخفاض تدريجي مستمر في أكل الأعلاف اليومية.",
                symptomsEn = "Respiratory gasping, mild coughing (wet rales), bubbles in eyes, swollen facial sinuses with falling feed intakes.",
                treatmentAr = "مضاد تايلوزين تارترات (Tylosin) 0.5 جرام لكل لتر أو دواء تيلميكوزين (Tilmicosin) 0.3 مل لكل لتر لمدة 3-5 أيام.",
                treatmentEn = "Tylosin Tartrate dosing: 0.5g per liter. Or Tilmicosin 0.3ml/liter for 3-5 days in drinking water.",
                preventionAr = "ضبط سرعة الشفاطات بشكل آلي لضمان تجديد الهواء باستمرار ومنع تراكم غاز الأمونيا الخانق.",
                preventionEn = "Maintain high closed-house air exchange cubic volumes. Keep under-house ammonia emissions totally minimal."
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = tr.t("دليل أمراض ومضادات الدواجن والجرعات 🩺", "Broiler Veterinary Disease & Dose Manual 🩺"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = tr.t(
                        "الدليل العلمي المعتمد للوقاية، التشخيص، تفشي الأوبئة، وتحديد نسب الجرعات الدوائية بإشراف د.ضيف الله الحسني.",
                        "Direct expert veterinary parameters for prevention, dosing, and infection vectors under Dr. Dheifallah's scientific authority."
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(diseases) { disease ->
            var expanded by remember { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr.t(disease.nameAr, disease.nameEn),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = tr.t(disease.ageAr, disease.ageEn),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }

                    if (expanded) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(text = tr.t("🔬 المسبب الأساسي:", "🔬 Cause Agent:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = tr.t(disease.causeAr, disease.causeEn), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))

                        Text(text = tr.t("🤒 الأعراض الكلينيكية والظاهرية:", "🤒 Clinical Symptoms:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = tr.t(disease.symptomsAr, disease.symptomsEn), fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))

                        Text(text = tr.t("💊 خطة العلاج والمضادات الحيوية والجرعة:", "💊 Pharmaceutical Treatment & Dose:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            text = tr.t(disease.treatmentAr, disease.treatmentEn),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .padding(6.dp)
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        )

                        Text(text = tr.t("🛡️ سبل الوقاية والحد من انتشار العدوى البيطرية:", "🛡️ Biosecurity & Infection Prevention:"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(text = tr.t(disease.preventionAr, disease.preventionEn), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// SCREEN 6: PROFESSIONAL PRINT / REPORTS TAB
// ──────────────────────────────────────────────────────────
@Composable
fun ReportsTab(
    viewModel: FarmViewModel,
    barns: List<Barn>,
    tr: TranslationHelper
) {
    var selectedBarnIdFilter by remember { mutableStateOf<Int?>(null) }
    var selectedReportType by remember { mutableStateOf("DAILY") } // DAILY, CUMULATIVE, CYCLE, PERFORMANCE, MORTALITY
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = tr.t("إصدار التقارير ومؤشرات الأداء الموثقة 📄", "Export Documented PDF/Excel Reports 📄"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = tr.t(
                        "أنشئ وثائق رسمية برأسية لمار للخدمات البيطرية وتذييل للمهندس المطور ضيف الله الحسني. للتصدير كـ PDF طباعة أو Excel.",
                        "Generate formal vet-certified reports with precise Lamar headers and footer signatures. Fits clean A4 scales."
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // report type card selector
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr.t("1. اختر نوع التقرير المطلوب استخراجه المعين:", "1. Select Report Type:"), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    // Report Type Options
                    val types = listOf(
                        "DAILY" to tr.t("التقرير اليومي لجميع العنابر", "Daily All Barns"),
                        "CUMULATIVE" to tr.t("التقرير التراكمي للقطيع", "Flock Cumulative"),
                        "CYCLE" to tr.t("تقرير نهاية الدورة الكاملة", "End of Cycle Record"),
                        "PERFORMANCE" to tr.t("تقرير كفاءة FCR وبيانات النمو", "FCR & Performance"),
                        "MORTALITY" to tr.t("تقرير وفيات ونفوق العنابر", "Mortality Analysis")
                    )

                    types.forEach { (typeKey, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReportType = typeKey }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedReportType == typeKey,
                                onClick = { selectedReportType = typeKey }
                            )
                            Text(text = label, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp
                    )

                    Text(tr.t("2. تصفية حسب عنبر محدد (اختياري):", "2. Filter by Barn (Optional):"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedBarnIdFilter == null,
                            onClick = { selectedBarnIdFilter = null },
                            label = { Text(tr.t("الكل", "All Barns")) }
                        )
                        barns.forEach { barn ->
                            FilterChip(
                                selected = selectedBarnIdFilter == barn.id,
                                onClick = { selectedBarnIdFilter = barn.id },
                                label = { Text(barn.code) }
                            )
                        }
                    }
                }
            }
        }

        // Document Action Buttons (PDF & Excel)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.printReportPdf(context, selectedReportType, selectedBarnIdFilter)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr.t("تصدير PDF 📄", "Export to PDF 📄"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        viewModel.exportReportExcelCSV(context, selectedReportType, selectedBarnIdFilter)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    Icon(Icons.Default.BorderOuter, contentDescription = "Excel")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tr.t("تصدير Excel 📊", "Export to Excel 📊"), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        // Visual preview box of report header design in M3
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        tr.t("التصميم البصري للتقرير الموثق (A4):", "Visual Layout of Generated Documents (A4):"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Header Simulation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, Color.Gray.copy(alpha = 0.3f))
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("لمار للخدمات البيطرية", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("هاتف: +9677132233940", fontSize = 8.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Lamar Vet Services", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Text("Tel: +9677132233940", fontSize = 8.sp)
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                            Text(
                                "نسخة ورقية معتمدة ومحسوبة بالخوارزميات للأداء ومتابعة الأوبئة",
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp, 10.dp)
                                    .align(Alignment.CenterHorizontally)
                                    .background(Color.Gray.copy(alpha = 0.2f))
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 1.dp
                            )
                            Text(
                                "تنفيذ وتطوير ضيف الله الحسني 773826501",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}


// ──────────────────────────────────────────────────────────
// MODALS & DIALOGS IMPLEMENTATION
// ──────────────────────────────────────────────────────────

@Composable
fun AddBarnDialog(
    tr: TranslationHelper,
    onDismiss: () -> Unit,
    onConfirm: (code: String, capacity: Int, chicks: Int, breed: String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var chicks by remember { mutableStateOf("") }
    var breed by remember { mutableStateOf("كوب 500 (Cobb)") }
    var breedExpanded by remember { mutableStateOf(false) }

    val breeds = listOf("كوب 500 (Cobb)", "روص 308 (Ross)", "إنديان ريفر (Indian River)")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tr.t("إضافة عنبر تسمين جديد 🏛️", "Add New Broiler Barn 🏛️"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text(tr.t("كود أو اسم العنبر (مثال ع1)", "Barn Name/Code (e.g., A1)")) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = capacity,
                    onValueChange = { capacity = it },
                    label = { Text(tr.t("السعة الكلية للمبنى (طائر)", "Total Barn Capacity (birds)")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = chicks,
                    onValueChange = { chicks = it },
                    label = { Text(tr.t("عدد كتاكيت البداية المدخلة", "Initial Chicks Count")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                // Breed Selector drop down
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { breedExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = tr.t("سلالة العنبر: $breed", "Breed Selected: $breed"))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = breedExpanded,
                        onDismissRequest = { breedExpanded = false }
                    ) {
                        breeds.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    breed = item
                                    breedExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(tr.t("تراجع", "Cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val capInt = capacity.toIntOrNull() ?: 0
                            val chicksInt = chicks.toIntOrNull() ?: 0
                            if (code.isNotEmpty() && capInt > 0 && chicksInt > 0) {
                                onConfirm(code, capInt, chicksInt, breed)
                            }
                        }
                    ) {
                        Text(tr.t("حفظ", "Save"))
                    }
                }
            }
        }
    }
}

@Composable
fun AddWeightDialog(
    barn: Barn,
    tr: TranslationHelper,
    onDismiss: () -> Unit,
    onConfirm: (week: Int, rawCsv: String) -> Unit
) {
    var weekNumber by remember { mutableStateOf("1") }
    var rawWeightsCsv by remember { mutableStateOf("") }

    var computedMean by remember { mutableStateOf(0.0) }
    var computedUniformity by remember { mutableStateOf(0.0) }
    var computedSD by remember { mutableStateOf(0.0) }

    // Realtime uniformity calculating preview
    LaunchedEffect(rawWeightsCsv) {
        val list = rawWeightsCsv.split(",")
            .mapNotNull { it.trim().toDoubleOrNull() }
        if (list.isNotEmpty()) {
            val mean = list.average()
            computedMean = mean
            
            val variance = list.map { (it - mean) * (it - mean) }.sum() / list.size
            computedSD = sqrt(variance)

            val tenPct = mean * 0.10
            val count = list.count { it in (mean - tenPct)..(mean + tenPct) }
            computedUniformity = (count.toDouble() / list.size) * 100.0
        } else {
            computedMean = 0.0
            computedUniformity = 0.0
            computedSD = 0.0
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = tr.t("حساب أوزان العينة للتناسق والإنحراف ⚖️", "Weight Uniformity & SD Calculator ⚖️"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = tr.t("عنبر: ${barn.code} (السلالة: ${barn.breed})", "Barn: ${barn.code} (${barn.breed})"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = weekNumber,
                    onValueChange = { weekNumber = it },
                    label = { Text(tr.t("رقم الأسبوع الحالي للدورة", "Current cycle week number")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = rawWeightsCsv,
                    onValueChange = { rawWeightsCsv = it },
                    label = { Text(tr.t("إدخال الأوزان منفصلة بفاصلة (جرام) (مثال: 180,195,210,190...)", "Enter weights in grams separated by commas (e.g. 180,195,210,190)")) },
                    placeholder = { Text("180,195,210,190") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth()
                )

                // Live dynamic math output card
                if (computedMean > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(tr.t("النتائج الحسابية اللحظية للعينة:", "Realtime Sample Calculations:"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(tr.t("متوسط وزن العينة: ${String.format("%.1f", computedMean)} جرام", "Mean Average Weight: ${String.format("%.1f", computedMean)} g"), fontSize = 11.sp)
                            Text(tr.t("الانحراف المعياري (SD): ${String.format("%.1f", computedSD)}", "Standard Deviation (SD): ${String.format("%.1f", computedSD)}"), fontSize = 11.sp)
                            
                            val statusStr = if (computedUniformity >= 85.0) tr.t("ممتاز ومثالي (القطيع ينمو معاً)", "Excellent & Uniform herd")
                                            else if (computedUniformity >= 75.0) tr.t("مقبول ومتوسط", "Average Uniformity")
                                            else tr.t("ضعيف جداً! (يوجد تفاوت أحجام - يطلب فرز طعام العنابر لدعم الأقزام)", "Poor Uniformity! (Requires food lines sorting)")
                            Text(
                                text = tr.t("معامل التناسق (Uniformity): ${String.format("%.1f", computedUniformity)}% - ($statusStr)", "Uniformity Index: ${String.format("%.1f", computedUniformity)}% - ($statusStr)"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (computedUniformity >= 85.0) Color(0xFF2E7D32) else if (computedUniformity < 75.0) Color.Red else Color.Black
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(tr.t("تراجع", "Cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val wk = weekNumber.toIntOrNull() ?: 1
                            if (rawWeightsCsv.isNotEmpty() && computedMean > 0) {
                                onConfirm(wk, rawWeightsCsv)
                            }
                        }
                    ) {
                        Text(tr.t("تخزين العينات ودراستها", "Analyze & Store"))
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustStockDialog(
    stockItems: List<StockItem>,
    tr: TranslationHelper,
    onDismiss: () -> Unit,
    onConfirm: (itemId: String, quantity: Double, type: String, notes: String) -> Unit
) {
    var selectedItemId by remember { mutableStateOf(stockItems.firstOrNull()?.id ?: "") }
    var quantity by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("IN") } // IN (وارد), OUT_MANUAL (صرف يدوي)
    var notes by remember { mutableStateOf("") }
    var itemExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Text(
                    text = tr.t("معاملة مستودعية ومشتريات 📦", "Warehouse Stock & Purchase Refill 📦"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                // Item Selector dropdown
                Box(modifier = Modifier.fillMaxWidth()) {
                    val currentName = stockItems.find { it.id == selectedItemId }?.name ?: selectedItemId
                    OutlinedButton(
                        onClick = { itemExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = tr.t("العنصر المحدد: $currentName", "Item: $currentName"))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = itemExpanded,
                        onDismissRequest = { itemExpanded = false }
                    ) {
                        stockItems.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name) },
                                onClick = {
                                    selectedItemId = item.id
                                    itemExpanded = false
                                }
                            )
                        }
                    }
                }

                // IN / OUT Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { type = "IN" }
                    ) {
                        RadioButton(selected = type == "IN", onClick = { type = "IN" })
                        Text(tr.t("وارد (شراء/توريد)", "Incoming Refill / Purchase"), fontSize = 11.sp)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { type = "OUT_MANUAL" }
                    ) {
                        RadioButton(selected = type == "OUT_MANUAL", onClick = { type = "OUT_MANUAL" })
                        Text(tr.t("صرف مالي يدوي", "Manual Adjustment Out"), fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(tr.t("الكمية الفعلية بالوحدة المعينة", "Quantity")) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(tr.t("ملاحظات وسند التوريد (رقم الفاتورة أو المشتري الحركي)", "Supply Invoice details / notes")) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(tr.t("تراجع", "Cancel"))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val qtyDouble = quantity.toDoubleOrNull() ?: 0.0
                            if (selectedItemId.isNotEmpty() && qtyDouble > 0) {
                                onConfirm(selectedItemId, qtyDouble, type, notes)
                            }
                        }
                    ) {
                        Text(tr.t("حفظ المعاملة", "Refill Stock"))
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────
// DISEASE DATA MODEL definition
// ──────────────────────────────────────────────────────────
data class Disease(
    val nameAr: String,
    val nameEn: String,
    val ageAr: String,
    val ageEn: String,
    val causeAr: String,
    val causeEn: String,
    val symptomsAr: String,
    val symptomsEn: String,
    val treatmentAr: String,
    val treatmentEn: String,
    val preventionAr: String,
    val preventionEn: String
)
