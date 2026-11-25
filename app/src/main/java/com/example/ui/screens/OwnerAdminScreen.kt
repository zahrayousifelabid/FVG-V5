package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AppAnnouncementRecord
import com.example.data.model.PlanEntitlements
import com.example.data.model.PlatformPlanRecord
import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.export.FvgExportService
import com.example.ui.auth.AdminCredentialHelper
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.example.domain.math.BigDecimal

private data class AdminNavTab(
    val index: Int,
    val labelAr: String,
    val labelEn: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun OwnerAdminScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean = false,
    onToggleLanguage: (() -> Unit)? = null,
    onToggleTheme: (() -> Unit)? = null,
    onPreviewUserApp: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    onBack: () -> Unit = {}
) {
    val backAction = onPreviewUserApp ?: onBack
    BackHandler { backAction() }
    val s = fvgStrings(isArabic)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    if (!state.isPlatformOwner) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            FvgCard {
                Text(
                    text = if (isArabic) "هذه اللوحة (/admin) محجوزة لمسؤول المنصة المعتمد فقط." else "Restricted (/admin): Verified Platform Administrator access only.",
                    style = MaterialTheme.typography.titleMedium,
                    color = FvgNegative
                )
                FvgPrimaryButton(text = s.backHome, onClick = backAction)
            }
        }
        return
    }

    var adminSetupCompleted by remember { mutableStateOf(AdminCredentialHelper.isAdminSetupCompleted(context)) }
    if (!adminSetupCompleted) {
        OwnerOneTimeSetupGateScreen(
            isArabic = isArabic,
            onCompleted = { adminSetupCompleted = true }
        )
        return
    }

    LaunchedEffect(Unit) {
        val pendingSec = AdminCredentialHelper.consumePendingSecurityEvents(context)
        if (pendingSec.isNotEmpty()) {
            viewModel.flushPendingAdminSecurityEvents(pendingSec)
        }
    }

    var adminTab by rememberSaveable { mutableIntStateOf(0) }
    var showAlertsDialog by remember { mutableStateOf(false) }
    var userSearchQuery by remember { mutableStateOf("") }
    var orgSearchQuery by remember { mutableStateOf("") }
    var orgStatusFilter by remember { mutableStateOf("all") }
    var ticketStatusFilter by remember { mutableStateOf("all") }
    var chartStyle by rememberSaveable { mutableStateOf("bar") } // "bar", "line"
    var trafficChartStyle by rememberSaveable { mutableStateOf("donut") } // "donut", "bar", "line"

    val unreadAlertCount = remember(state.ownerAdminNotifications, state.ownerAllPaymentRequests) {
        val notifUnread = state.ownerAdminNotifications.count { !it.acknowledged }
        val existingIds = state.ownerAdminNotifications.map { it.id }.toSet()
        val pendingPayFallback = state.ownerAllPaymentRequests.count {
            it.status == "pending_verification" && "pay_${it.id}" !in existingIds
        }
        notifUnread + pendingPayFallback
    }
    val latestUnreadAlert = remember(state.ownerAdminNotifications) {
        state.ownerAdminNotifications.firstOrNull { !it.acknowledged && (it.category == "security_alert" || it.category == "new_user") }
            ?: state.ownerAdminNotifications.firstOrNull { !it.acknowledged }
    }

    val exportAdminCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csv = buildAdminAnalyticsCsv(state, isArabic)
                FvgExportService.writeTextToUri(context, uri, csv)
            }.onSuccess {
                Toast.makeText(
                    context,
                    if (isArabic) "تم تصدير تقرير الإدارة بنجاح." else "Admin analytics CSV exported.",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { e ->
                Toast.makeText(context, e.message ?: "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Full Subscription Tiers CRUD State
    val allPlans = remember(state.platformPlans) {
        if (state.platformPlans.isNotEmpty()) state.platformPlans else PlanEntitlements.defaultPlans()
    }
    val protectedPlanKeys = remember { setOf("starter", "professional", "enterprise") }
    var isCreatingCustomPlan by remember { mutableStateOf(false) }
    var selectedPlanKey by remember { mutableStateOf("professional") }
    var customPlanSlug by remember { mutableStateOf("") }
    var planToDelete by remember { mutableStateOf<String?>(null) }

    val selectedExistingPlan = remember(selectedPlanKey, allPlans, isCreatingCustomPlan) {
        if (isCreatingCustomPlan) {
            PlatformPlanRecord(
                id = "",
                key = "",
                tierLevel = 4L,
                nameEn = "",
                nameAr = "",
                priceUsd = "49.00",
                priceSdg = "110000",
                billingCycle = "30d",
                maxOrgs = 5L,
                maxUsers = 20L,
                maxProducts = 500L,
                allowAllCostingMethods = true,
                allowBomAndProduction = true,
                allowAiAssistant = true,
                allowOcr = true,
                allowPdfXlsxExport = true,
                allowAuditAndBackup = true,
                allowSectorAdvancedTools = true,
                active = true
            )
        } else {
            allPlans.firstOrNull { it.key == selectedPlanKey } ?: allPlans.first()
        }
    }

    var planNameAr by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.nameAr) }
    var planNameEn by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.nameEn) }
    var planPriceSdg by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.priceSdg) }
    var planPriceUsd by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.priceUsd) }
    var planDurationDays by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.billingCycle.removeSuffix("d").ifBlank { "30" }) }
    var planTierLevel by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.tierLevel.toString()) }
    var planMaxOrgs by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.maxOrgs.toString()) }
    var planMaxUsers by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.maxUsers.toString()) }
    var planMaxProducts by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.maxProducts.toString()) }
    var allowAllCosting by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.allowAllCostingMethods) }
    var allowBomProd by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.allowBomAndProduction) }
    var allowAi by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.allowAiAssistant) }
    var allowOcr by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.allowOcr) }
    var allowPdfXlsx by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.allowPdfXlsxExport) }
    var allowAuditBackup by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.allowAuditAndBackup) }
    var allowSectorAdvanced by remember(selectedPlanKey, isCreatingCustomPlan, selectedExistingPlan) { mutableStateOf(selectedExistingPlan.allowSectorAdvancedTools) }

    // Announcement & Email Notification publisher state
    var annTitleAr by remember { mutableStateOf("") }
    var annTitleEn by remember { mutableStateOf("") }
    var annBodyAr by remember { mutableStateOf("") }
    var annBodyEn by remember { mutableStateOf("") }
    var annTargetAudience by remember { mutableStateOf("all") }
    var annTargetEmail by remember { mutableStateOf("") }
    var sendEmailAlso by remember { mutableStateOf(true) }

    // Formula Validation Suite state
    var validationChecks by remember { mutableStateOf(FvgFinancialEngine.runFormulaValidationSuite()) }
    var sandboxMat by remember { mutableStateOf("600") }
    var sandboxLab by remember { mutableStateOf("200") }
    var sandboxFixed by remember { mutableStateOf("1000") }
    var sandboxUnits by remember { mutableStateOf("100") }
    var sandboxPrice by remember { mutableStateOf("12") }
    var sandboxTargetMargin by remember { mutableStateOf("25") }

    // Admin Security Change Password state
    var curAdminPass by remember { mutableStateOf("") }
    var newAdminPass by remember { mutableStateOf("") }
    var confirmAdminPass by remember { mutableStateOf("") }
    var adminPassStatus by remember { mutableStateOf<String?>(null) }
    var adminPassError by remember { mutableStateOf<String?>(null) }

    val adminNavTabs = remember {
        listOf(
            AdminNavTab(0, "الإحصائيات", "Analytics", Icons.Filled.BarChart, "owner_admin_tab_0"),
            AdminNavTab(1, "الدفع والعضويات", "Pay & Roles", Icons.Filled.ManageAccounts, "owner_admin_tab_1"),
            AdminNavTab(2, "الباقات", "Tiers CRUD", Icons.Filled.Sell, "owner_admin_tab_2"),
            AdminNavTab(3, "البث والدعم", "Notices", Icons.Filled.Campaign, "owner_admin_tab_3"),
            AdminNavTab(4, "المعادلات", "Formulas", Icons.Filled.Verified, "owner_admin_tab_4"),
            AdminNavTab(5, "الأمان", "Security", Icons.Filled.Lock, "owner_admin_tab_5")
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("owner_admin_screen")
    ) {
        val useRail = maxWidth >= 600.dp
        val useWideLayout = maxWidth >= 840.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(FvgInkDark, FvgDeepGreen, FvgForestGreen)
                            )
                        )
                        .statusBarsPadding()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = FvgWarmGold.copy(alpha = 0.20f),
                                    border = BorderStroke(1.dp, FvgWarmGold)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AdminPanelSettings,
                                        contentDescription = null,
                                        tint = FvgWarmGold,
                                        modifier = Modifier.padding(8.dp).size(24.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (isArabic) "مركز قيادة الإدارة التنفيذية" else "Executive Admin Command Center",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = FvgWarmIvory,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isArabic) "الحوكمة • التحليلات • صلاحيات المشتركين" else "Platform Governance • Live Analytics • Controls",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FvgWarmGold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showAlertsDialog = true },
                                    modifier = Modifier.size(48.dp).testTag("admin_alerts_bell_button")
                                ) {
                                    BadgedBox(
                                        badge = {
                                            if (unreadAlertCount > 0) {
                                                Badge(
                                                    containerColor = FvgNegative,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(if (unreadAlertCount > 99) "99+" else unreadAlertCount.toString())
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (unreadAlertCount > 0) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                            contentDescription = if (isArabic) "تنبيهات الإدارة الفورية" else "Real-time Admin Alerts",
                                            tint = FvgWarmGold
                                        )
                                    }
                                }
                                if (onToggleLanguage != null) {
                                    OutlinedButton(
                                        onClick = onToggleLanguage,
                                        border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.7f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.heightIn(min = 38.dp).testTag("admin_lang_toggle")
                                    ) {
                                        Text(
                                            text = if (isArabic) "EN" else "عربي",
                                            color = FvgWarmGold,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                if (onToggleTheme != null) {
                                    IconButton(
                                        onClick = onToggleTheme,
                                        modifier = Modifier.size(48.dp).testTag("admin_theme_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                            contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                                            tint = FvgWarmGold
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = backAction,
                                border = BorderStroke(1.dp, FvgWarmGold),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).heightIn(min = 40.dp).testTag("owner_back_button")
                            ) {
                                Icon(Icons.Filled.Visibility, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = if (isArabic) "معاينة تطبيق المستخدم" else "Preview User Workspace",
                                    color = FvgWarmIvory,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (onSignOut != null) {
                                OutlinedButton(
                                    onClick = onSignOut,
                                    border = BorderStroke(1.dp, FvgNegative.copy(alpha = 0.7f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.heightIn(min = 40.dp).testTag("admin_sign_out_button")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFF2B8B5), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = if (isArabic) "خروج" else "Sign Out",
                                        color = Color(0xFFF2B8B5),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = FvgInkDark,
                        contentColor = FvgWarmIvory
                    ) {
                        adminNavTabs.forEach { tab ->
                            val selected = adminTab == tab.index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { adminTab = tab.index },
                                icon = { Icon(tab.icon, contentDescription = if (isArabic) tab.labelAr else tab.labelEn) },
                                label = {
                                    Text(
                                        text = if (isArabic) tab.labelAr else tab.labelEn,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = FvgInkDark,
                                    selectedTextColor = FvgWarmGold,
                                    indicatorColor = FvgWarmGold,
                                    unselectedIconColor = FvgWarmIvory.copy(alpha = 0.7f),
                                    unselectedTextColor = FvgWarmIvory.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = FvgInkDark,
                        contentColor = FvgWarmIvory
                    ) {
                        adminNavTabs.forEach { tab ->
                            val selected = adminTab == tab.index
                            NavigationRailItem(
                                selected = selected,
                                onClick = { adminTab = tab.index },
                                icon = { Icon(tab.icon, contentDescription = if (isArabic) tab.labelAr else tab.labelEn) },
                                label = {
                                    Text(
                                        text = if (isArabic) tab.labelAr else tab.labelEn,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = FvgInkDark,
                                    selectedTextColor = FvgWarmGold,
                                    indicatorColor = FvgWarmGold,
                                    unselectedIconColor = FvgWarmIvory.copy(alpha = 0.7f),
                                    unselectedTextColor = FvgWarmIvory.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (latestUnreadAlert != null) {
                        item(key = "live_alert_banner") {
                            val bannerColor = when (latestUnreadAlert.severity.lowercase()) {
                                "critical" -> FvgNegative
                                "warning" -> FvgWarning
                                else -> FvgPositive
                            }
                            Surface(
                                onClick = { showAlertsDialog = true },
                                shape = RoundedCornerShape(16.dp),
                                color = bannerColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.5.dp, bannerColor),
                                modifier = Modifier.fillMaxWidth().testTag("admin_live_alert_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (latestUnreadAlert.category == "new_user") Icons.Filled.PersonAdd else Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = bannerColor
                                    )
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = if (isArabic) latestUnreadAlert.titleAr.ifBlank { latestUnreadAlert.titleEn } else latestUnreadAlert.titleEn.ifBlank { latestUnreadAlert.titleAr },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = bannerColor
                                        )
                                        Text(
                                            text = if (isArabic) latestUnreadAlert.bodyAr.ifBlank { latestUnreadAlert.bodyEn } else latestUnreadAlert.bodyEn.ifBlank { latestUnreadAlert.bodyAr },
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    TextButton(
                                        onClick = { viewModel.markAdminNotificationRead(latestUnreadAlert.id, true) },
                                        modifier = Modifier.heightIn(min = 40.dp)
                                    ) {
                                        Text(if (isArabic) "إخفاء" else "Read", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }

                    when (adminTab) {
                        0 -> {
                            val activeOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "active" }
                            val trialOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "trial" }
                            val expiredOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "expired" }
                            val suspendedOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "cancelled" }
                            val pendingPays = state.ownerAllPaymentRequests.count { it.status == "pending_verification" }
                            val approvedPays = state.ownerAllPaymentRequests.count { it.status == "approved" }
                            val rejectedPays = state.ownerAllPaymentRequests.count { it.status == "rejected" }
                            val androidVisits = state.ownerPlatformVisits.count { it.platform == "android" && it.screen != "android_install" }
                            val webVisits = state.ownerPlatformVisits.count { it.platform == "web_pwa" && it.screen != "pwa_install" }
                            val androidInstalls = state.ownerPlatformVisits.count { it.screen == "android_install" }
                            val pwaInstalls = state.ownerPlatformVisits.count { it.screen == "pwa_install" }
                            val totalDownloads = androidInstalls + pwaInstalls

                            item(key = "admin_export_csv_hero_banner") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = FvgDeepGreen,
                                    border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth().testTag("admin_export_csv_hero_card")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = FvgWarmGold.copy(alpha = 0.22f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Download,
                                                    contentDescription = null,
                                                    tint = FvgWarmGold,
                                                    modifier = Modifier.padding(8.dp).size(24.dp)
                                                )
                                            }
                                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = if (isArabic) "تصدير تقرير المستخدمين وحركة المرور (CSV)" else "Download User Metrics & Site Traffic CSV",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = FvgWarmIvory
                                                )
                                                Text(
                                                    text = if (isArabic)
                                                        "ملف CSV للتقارير المالية دون اتصال يشمل إحصائيات حركة المرور، الزوار، التثبيتات، والمستخدمين"
                                                    else
                                                        "Offline financial CSV report including site traffic, app installs, user metrics, and subscriptions",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = FvgWarmGold
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        FvgGoldButton(
                                            text = if (isArabic) "تحميل CSV" else "Download CSV",
                                            icon = Icons.Filled.Download,
                                            onClick = { exportAdminCsvLauncher.launch("fvg_admin_analytics_${System.currentTimeMillis()}.csv") },
                                            testTag = "admin_hero_download_csv_button"
                                        )
                                    }
                                }
                            }

                            item {
                                if (useWideLayout) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            AdminMetricTile(
                                                title = if (isArabic) "المستخدمون المسجلون" else "Registered Users",
                                                value = state.ownerAllUsers.size.toString(),
                                                subtitle = if (isArabic) "حساب نشط" else "Verified profiles",
                                                accent = FvgForestGreen,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AdminMetricTile(
                                                title = if (isArabic) "المنظمات" else "Organizations",
                                                value = state.ownerAllOrgs.size.toString(),
                                                subtitle = if (isArabic) "نشط $activeOrgs • تجربة $trialOrgs" else "Active $activeOrgs • Trial $trialOrgs",
                                                accent = FvgSoftTeal,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AdminMetricTile(
                                                title = if (isArabic) "زيارات المنصة" else "Site & App Visits",
                                                value = (androidVisits + webVisits).toString(),
                                                subtitle = if (isArabic) "أندرويد $androidVisits • ويب $webVisits" else "Android $androidVisits • Web $webVisits",
                                                accent = FvgWarmGold,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            AdminMetricTile(
                                                title = if (isArabic) "تثبيتات التطبيق" else "App Downloads",
                                                value = totalDownloads.toString(),
                                                subtitle = if (isArabic) "أندرويد $androidInstalls • ويب $pwaInstalls" else "APK $androidInstalls • PWA $pwaInstalls",
                                                accent = FvgPositive,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AdminMetricTile(
                                                title = if (isArabic) "مدفوعات قيد المراجعة" else "Pending Payments",
                                                value = pendingPays.toString(),
                                                subtitle = if (isArabic) "معتمد: $approvedPays" else "Approved: $approvedPays",
                                                accent = if (pendingPays > 0) FvgWarning else FvgPositive,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AdminMetricTile(
                                                title = if (isArabic) "تنبيهات الأمان والدعم" else "Alerts & Tickets",
                                                value = "$unreadAlertCount / ${state.ownerSupportTickets.size}",
                                                subtitle = if (isArabic) "تنبيه غير مقروء / تذاكر" else "Unread alerts / Tickets",
                                                accent = if (unreadAlertCount > 0) FvgNegative else FvgAntiqueGold,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AdminMetricTile(
                                                title = if (isArabic) "المستخدمون المسجلون" else "Registered Users",
                                                value = state.ownerAllUsers.size.toString(),
                                                subtitle = if (isArabic) "حساب نشط" else "Verified profiles",
                                                accent = FvgForestGreen,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AdminMetricTile(
                                                title = if (isArabic) "المنظمات" else "Organizations",
                                                value = state.ownerAllOrgs.size.toString(),
                                                subtitle = if (isArabic) "نشط $activeOrgs • تجربة $trialOrgs" else "Active $activeOrgs • Trial $trialOrgs",
                                                accent = FvgSoftTeal,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AdminMetricTile(
                                                title = if (isArabic) "زيارات المنصة" else "Site & App Visits",
                                                value = (androidVisits + webVisits).toString(),
                                                subtitle = if (isArabic) "أندرويد $androidVisits • ويب $webVisits" else "Android $androidVisits • Web $webVisits",
                                                accent = FvgWarmGold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AdminMetricTile(
                                                title = if (isArabic) "تثبيتات التطبيق" else "App Downloads",
                                                value = totalDownloads.toString(),
                                                subtitle = if (isArabic) "أندرويد $androidInstalls • ويب $pwaInstalls" else "APK $androidInstalls • PWA $pwaInstalls",
                                                accent = FvgPositive,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            AdminMetricTile(
                                                title = if (isArabic) "مدفوعات قيد المراجعة" else "Pending Payments",
                                                value = pendingPays.toString(),
                                                subtitle = if (isArabic) "معتمد: $approvedPays" else "Approved: $approvedPays",
                                                accent = if (pendingPays > 0) FvgWarning else FvgPositive,
                                                modifier = Modifier.weight(1f)
                                            )
                                            AdminMetricTile(
                                                title = if (isArabic) "تنبيهات الأمان والدعم" else "Alerts & Tickets",
                                                value = "$unreadAlertCount / ${state.ownerSupportTickets.size}",
                                                subtitle = if (isArabic) "تنبيه غير مقروء / تذاكر" else "Unread alerts / Tickets",
                                                accent = if (unreadAlertCount > 0) FvgNegative else FvgAntiqueGold,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }

                            // Charts 1 & 2: Side-by-side on tablet/desktop, stacked on phone
                            item {
                                val regBars = remember(state.ownerAllUsers, state.ownerAllOrgs, state.ownerPlatformVisits, isArabic) {
                                    buildDailySeries(
                                        userDates = state.ownerAllUsers.mapNotNull { it.createdAt?.toDate() ?: it.trialStartedAt?.toDate() },
                                        visitDates = state.ownerPlatformVisits.mapNotNull { it.createdAt?.toDate() },
                                        isArabic = isArabic
                                    )
                                }
                                val trafficSlices = listOf(
                                    Triple(if (isArabic) "زوار أندرويد" else "Android Visits", androidVisits.toFloat(), FvgDeepGreen),
                                    Triple(if (isArabic) "زوار الويب PWA" else "Web PWA Visits", webVisits.toFloat(), FvgSoftTeal),
                                    Triple(if (isArabic) "تثبيت أندرويد" else "Android Installs", androidInstalls.toFloat(), FvgWarmGold),
                                    Triple(if (isArabic) "تثبيت الويب PWA" else "PWA Installs", pwaInstalls.toFloat(), FvgAntiqueGold)
                                )

                                @Composable
                                fun RegChartCard(mod: Modifier = Modifier) {
                                    FvgCard(modifier = mod) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isArabic) "نمو التسجيلات والزيارات (آخر 6 أيام)" else "Registrations & Visits (Last 6 Days)",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            FvgChipSelector(
                                                options = listOf("bar", "line"),
                                                selected = chartStyle,
                                                onSelect = { chartStyle = it },
                                                labelResolver = {
                                                    if (it == "bar") (if (isArabic) "أعمدة" else "Bar")
                                                    else (if (isArabic) "خطي" else "Line")
                                                }
                                            )
                                        }
                                        if (chartStyle == "bar") {
                                            FvgBarComparisonCanvas(
                                                bars = regBars.map { it.first to it.second.toFloat() },
                                                barColorPrimary = FvgDeepGreen,
                                                barColorSecondary = FvgWarmGold
                                            )
                                        } else {
                                            AdminLabeledLineChart(
                                                points = regBars.map { it.first to (it.second + it.third).toFloat() },
                                                lineColor = FvgForestGreen,
                                                secondaryPoints = regBars.map { it.first to it.second.toFloat() },
                                                secondaryColor = FvgWarmGold,
                                                legendPrimary = if (isArabic) "الزيارات" else "Visits",
                                                legendSecondary = if (isArabic) "التسجيلات" else "Signups"
                                            )
                                        }
                                        Text(
                                            text = if (isArabic)
                                                "إجمالي المسجلين: ${state.ownerAllUsers.size} | منظمات: ${state.ownerAllOrgs.size}"
                                            else
                                                "Total Registered Users: ${state.ownerAllUsers.size} | Orgs: ${state.ownerAllOrgs.size}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                @Composable
                                fun TrafficChartCard(mod: Modifier = Modifier) {
                                    FvgCard(modifier = mod) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isArabic) "توزيع الزوار وتنزيلات المنصة" else "Traffic Channels & App Installs",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            FvgChipSelector(
                                                options = listOf("donut", "bar"),
                                                selected = trafficChartStyle,
                                                onSelect = { trafficChartStyle = it },
                                                labelResolver = {
                                                    if (it == "donut") (if (isArabic) "دائري" else "Donut")
                                                    else (if (isArabic) "أعمدة" else "Bar")
                                                }
                                            )
                                        }
                                        if (trafficChartStyle == "donut") {
                                            FvgDonutBreakdownCanvas(
                                                slices = trafficSlices,
                                                centerTitle = if (isArabic) "الإجمالي" else "Total",
                                                centerValue = (androidVisits + webVisits + totalDownloads).toString()
                                            )
                                        } else {
                                            FvgBarComparisonCanvas(
                                                bars = trafficSlices.map { it.first to it.second },
                                                barColorPrimary = FvgSoftTeal,
                                                barColorSecondary = FvgWarmGold
                                            )
                                        }
                                    }
                                }

                                if (useWideLayout) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RegChartCard(Modifier.weight(1f))
                                        TrafficChartCard(Modifier.weight(1f))
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        RegChartCard(Modifier.fillMaxWidth())
                                        TrafficChartCard(Modifier.fillMaxWidth())
                                    }
                                }
                            }

                            // Chart 3: Subscription Statuses & Payments Breakdown
                            item {
                                FvgCard {
                                    Text(
                                        text = if (isArabic) "حالة المنظمات والمدفوعات" else "Subscription & Payment Breakdown",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    FvgDonutBreakdownCanvas(
                                        slices = listOf(
                                            Triple(if (isArabic) "اشتراك نشط" else "Active Paid", activeOrgs.toFloat(), FvgPositive),
                                            Triple(if (isArabic) "فترة تجربة" else "Trial", trialOrgs.toFloat(), FvgWarmGold),
                                            Triple(if (isArabic) "منتهي" else "Expired", expiredOrgs.toFloat(), FvgWarning),
                                            Triple(if (isArabic) "موقوف" else "Suspended", suspendedOrgs.toFloat(), FvgNegative)
                                        ),
                                        centerTitle = if (isArabic) "المنظمات" else "Orgs",
                                        centerValue = state.ownerAllOrgs.size.toString()
                                    )
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                        Text(if (isArabic) "دفع معتمد: $approvedPays" else "Approved: $approvedPays", style = MaterialTheme.typography.labelMedium, color = FvgPositive, fontWeight = FontWeight.Bold)
                                        Text(if (isArabic) "معلق: $pendingPays" else "Pending: $pendingPays", style = MaterialTheme.typography.labelMedium, color = FvgWarning, fontWeight = FontWeight.Bold)
                                        Text(if (isArabic) "مرفوض: $rejectedPays" else "Rejected: $rejectedPays", style = MaterialTheme.typography.labelMedium, color = FvgNegative, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            item {
                                FvgCard {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isArabic) "سجل المستخدمين المسجلين (${state.ownerAllUsers.size})" else "Registered Users Directory (${state.ownerAllUsers.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = { exportAdminCsvLauncher.launch("fvg_admin_analytics_${System.currentTimeMillis()}.csv") },
                                            modifier = Modifier.heightIn(min = 42.dp).testTag("owner_export_csv_button")
                                        ) {
                                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isArabic) "تصدير CSV" else "CSV", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    FvgField(userSearchQuery, { userSearchQuery = it }, if (isArabic) "بحث بالاسم أو البريد..." else "Search by name or email...")
                                }
                            }
                            val filteredUsers = state.ownerAllUsers.filter { u ->
                                val q = userSearchQuery.trim().lowercase()
                                q.isEmpty() || u.email.lowercase().contains(q) || u.displayName.lowercase().contains(q)
                            }
                            items(filteredUsers, key = { it.userId }) { u ->
                                val userOrgs = state.ownerAllOrgs.filter { it.ownerId == u.userId || u.userId in it.memberUids }
                                val welcomeMail = state.ownerMailOutbox.firstOrNull { m ->
                                    m.mailType == "welcome" && m.to.any { it.equals(u.email, ignoreCase = true) }
                                }
                                val welcomeState = welcomeMail?.delivery?.get("state")?.toString()
                                    ?: if (welcomeMail != null) "QUEUED" else "NOT_QUEUED"
                                val welcomeColor = when (welcomeState) {
                                    "SUCCESS" -> FvgPositive
                                    "PENDING_SMTP_CONFIG", "QUEUED" -> FvgWarning
                                    "ERROR" -> FvgNegative
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                FvgCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(u.displayName.ifBlank { "FVG User" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                            Text(u.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            if (userOrgs.isNotEmpty()) {
                                                Text(
                                                    text = (if (isArabic) "المنظمة: " else "Org: ") + userOrgs.joinToString(", ") { "${it.name} (${it.subscriptionStatus})" },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = welcomeColor.copy(alpha = 0.14f)
                                            ) {
                                                Text(
                                                    text = (if (isArabic) "حالة الترحيب: " else "Welcome Mail: ") + welcomeState,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = welcomeColor,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FvgRoleBadge("${u.preferredLanguage.uppercase()} • ${u.themeMode}")
                                            OutlinedButton(
                                                onClick = { viewModel.resendWelcomeEmailAsOwner(u) },
                                                enabled = !state.isBusy && u.email.contains("@"),
                                                modifier = Modifier.heightIn(min = 40.dp).testTag("owner_resend_welcome_${u.userId}")
                                            ) {
                                                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text(if (isArabic) "إرسال ترحيب" else "Resend Welcome", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        1 -> {
                            item {
                                FvgCard {
                                    Text(if (isArabic) "طابور مراجعة المدفوعات (${state.ownerAllPaymentRequests.size})" else "Payment Verification Queue (${state.ownerAllPaymentRequests.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (state.ownerAllPaymentRequests.isEmpty()) {
                                item { FvgEmptyStateCard(if (isArabic) "لا توجد طلبات دفع مرسلة بعد." else "No payment verification requests yet.") }
                            } else {
                                items(state.ownerAllPaymentRequests, key = { "${it.orgId}_${it.id}" }) { req ->
                                    var decisionNotes by remember(req.id) { mutableStateOf(req.reviewNotes) }
                                    val orgNameLabel = state.ownerAllOrgs.firstOrNull { it.id == req.orgId }?.name ?: req.orgId
                                    val submitterEmail = state.ownerAllUsers.firstOrNull { it.userId == req.createdBy }?.email ?: req.createdBy
                                    FvgCard {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("$orgNameLabel • ${req.planTier}", style = MaterialTheme.typography.titleMedium)
                                            FvgRoleBadge(req.status)
                                        }
                                        Text(
                                            text = if (isArabic)
                                                "المبلغ: ${req.amount} ${req.currency} | الوسيلة: ${req.paymentMethod}\nالمرجع: ${req.referenceNo} | آخر 4 أرقام: ${req.last4Digits.ifBlank { "غير محدد" }}\nالمرسل: ${req.senderName} ($submitterEmail) | التاريخ: ${req.transferDate}"
                                            else
                                                "Amount: ${req.amount} ${req.currency} | Method: ${req.paymentMethod}\nRef: ${req.referenceNo} | Last 4 Digits: ${req.last4Digits.ifBlank { "N/A" }}\nSender: ${req.senderName} ($submitterEmail) | Date: ${req.transferDate}",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        if (req.receiptMatchStatus.isNotBlank() && req.receiptMatchStatus != "unverified") {
                                            val matchBg = when (req.receiptMatchStatus) {
                                                "exact_match" -> FvgPositive.copy(alpha = 0.14f)
                                                "close_match" -> FvgAntiqueGold.copy(alpha = 0.18f)
                                                else -> MaterialTheme.colorScheme.errorContainer
                                            }
                                            val matchFg = when (req.receiptMatchStatus) {
                                                "exact_match" -> FvgPositive
                                                "close_match" -> FvgAntiqueGold
                                                else -> MaterialTheme.colorScheme.error
                                            }
                                            val matchTitle = when (req.receiptMatchStatus) {
                                                "exact_match" -> if (isArabic) "مطابقة آلية دقيقة ✓" else "Auto-Match: Exact ✓"
                                                "close_match" -> if (isArabic) "مطابقة قريبة (فرق رسوم/قروش)" else "Auto-Match: Close Match"
                                                "duplicate_ref" -> if (isArabic) "تحذير: رقم مرجع مكرر!" else "Alert: Duplicate Reference!"
                                                else -> if (isArabic) "عدم تطابق المبلغ مع الباقة" else "Amount Mismatch vs Plan"
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = matchBg,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("owner_receipt_match_badge_${req.id}")
                                            ) {
                                                Column(modifier = Modifier.padding(8.dp)) {
                                                    Text(
                                                        text = matchTitle + if (req.expectedPlanAmount.isNotBlank()) " (${if (isArabic) "المتوقع:" else "Expected:"} ${req.expectedPlanAmount})" else "",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = matchFg
                                                    )
                                                    if (req.receiptMatchNote.isNotBlank()) {
                                                        Text(
                                                            text = req.receiptMatchNote,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (req.status == "pending_verification") {
                                            FvgField(decisionNotes, { decisionNotes = it }, if (isArabic) "ملاحظة المراجعة / سبب الرفض" else "Verification / Rejection Note")
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FvgPrimaryButton(
                                                    text = if (isArabic) "موافقة وتفعيل" else "Approve & Activate",
                                                    icon = Icons.Filled.CheckCircle,
                                                    enabled = !state.isBusy,
                                                    onClick = {
                                                        viewModel.reviewPaymentRequestAsOwner(
                                                            req,
                                                            "approved",
                                                            decisionNotes.ifBlank { "Verified by platform owner (${req.referenceNo})" }
                                                        )
                                                    },
                                                    modifier = Modifier.weight(1.2f),
                                                    testTag = "owner_approve_payment_button"
                                                )
                                                OutlinedButton(
                                                    enabled = !state.isBusy,
                                                    onClick = {
                                                        viewModel.reviewPaymentRequestAsOwner(
                                                            req,
                                                            "rejected",
                                                            decisionNotes.ifBlank { "Rejected: transaction could not be verified" }
                                                        )
                                                    },
                                                    modifier = Modifier.weight(0.8f).heightIn(min = 48.dp)
                                                ) {
                                                    Text(if (isArabic) "رفض" else "Reject", color = FvgNegative)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                FvgCard {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(if (isArabic) "إدارة الاشتراكات والتعليق وصلاحيات الأعضاء (${state.ownerAllOrgs.size})" else "Org Subscriptions, Suspend & RBAC (${state.ownerAllOrgs.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    FvgField(orgSearchQuery, { orgSearchQuery = it }, if (isArabic) "بحث باسم المنظمة أو ID..." else "Search org by name or ID...")
                                    FvgChipSelector(
                                        options = listOf("all", "trial", "active", "expired", "cancelled"),
                                        selected = orgStatusFilter,
                                        onSelect = { orgStatusFilter = it },
                                        labelResolver = { st ->
                                            when (st) {
                                                "all" -> if (isArabic) "الكل" else "All"
                                                "trial" -> if (isArabic) "تجربة" else "Trial"
                                                "active" -> if (isArabic) "نشط" else "Active"
                                                "expired" -> if (isArabic) "منتهي" else "Expired"
                                                else -> if (isArabic) "موقوف" else "Suspended"
                                            }
                                        }
                                    )
                                }
                            }

                            val planOptions = (listOf("trial") + allPlans.map { it.key.ifBlank { it.id } }).distinct()
                            val filteredOrgs = state.ownerAllOrgs.filter { org ->
                                val q = orgSearchQuery.trim().lowercase()
                                val matchesText = q.isEmpty() || org.name.lowercase().contains(q) || org.id.lowercase().contains(q)
                                val matchesStatus = orgStatusFilter == "all" || org.subscriptionStatus == orgStatusFilter
                                matchesText && matchesStatus
                            }
                            items(filteredOrgs, key = { it.id }) { org ->
                                var overridePlan by remember(org.id, org.planTier) { mutableStateOf(org.planTier) }
                                var overrideStatus by remember(org.id, org.subscriptionStatus) { mutableStateOf(org.subscriptionStatus) }
                                var overrideReason by remember(org.id) { mutableStateOf("") }
                                var assignEmailOrUid by remember(org.id) { mutableStateOf("") }
                                var assignRole by remember(org.id) { mutableStateOf("accountant") }
                                val isSuspended = org.subscriptionStatus == "cancelled" || org.subscriptionStatus == "expired"

                                FvgCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(org.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        FvgRoleBadge("${org.planTier} • ${org.subscriptionStatus}")
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("ID: ${org.id} | Sector: ${org.sectorType} | ${org.baseCurrency}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                        OutlinedButton(
                                            onClick = { viewModel.loadOwnerOrgAuditLogs(org) },
                                            modifier = Modifier.heightIn(min = 38.dp).testTag("owner_org_audit_${org.id}")
                                        ) {
                                            Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isArabic) "سجل التدقيق" else "Audit Logs", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    FvgChipSelector(planOptions, overridePlan, { overridePlan = it })
                                    FvgChipSelector(listOf("trial", "active", "expired", "cancelled"), overrideStatus, { overrideStatus = it })
                                    FvgField(overrideReason, { overrideReason = it }, if (isArabic) "سبب التعديل الإداري" else "Admin Override Reason")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.ownerUpdateOrgSubscription(
                                                    org = org,
                                                    planTier = overridePlan,
                                                    subscriptionStatus = if (isSuspended) "active" else "cancelled",
                                                    newTrialEndsAtMillis = null,
                                                    reason = overrideReason.ifBlank { if (isSuspended) "Owner reactivated workspace" else "Owner suspended workspace" }
                                                )
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) {
                                            Text(
                                                text = if (isSuspended) (if (isArabic) "تفعيل" else "Activate") else (if (isArabic) "إيقاف مؤقت" else "Suspend"),
                                                color = if (isSuspended) FvgPositive else FvgNegative
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.ownerUpdateOrgSubscription(
                                                    org = org,
                                                    planTier = overridePlan,
                                                    subscriptionStatus = "trial",
                                                    newTrialEndsAtMillis = System.currentTimeMillis() + 7L * 86400_000L,
                                                    reason = overrideReason.ifBlank { "Owner trial extension (+7 days)" }
                                                )
                                            },
                                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text(if (isArabic) "+7 أيام" else "+7d Trial") }
                                        FvgPrimaryButton(
                                            text = if (isArabic) "حفظ" else "Apply",
                                            enabled = !state.isBusy,
                                            onClick = {
                                                viewModel.ownerUpdateOrgSubscription(
                                                    org = org,
                                                    planTier = overridePlan,
                                                    subscriptionStatus = overrideStatus,
                                                    newTrialEndsAtMillis = null,
                                                    reason = overrideReason.ifBlank { "Owner status adjustment" }
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                    Text(if (isArabic) "صلاحيات الأعضاء (${org.memberUids.size}):" else "Organization Member Roles (${org.memberUids.size}):", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                    org.memberUids.forEach { mUid ->
                                        val mRole = org.roles[mUid] ?: "viewer"
                                        val mUser = state.ownerAllUsers.firstOrNull { it.userId == mUid }
                                        val mLabel = mUser?.let { "${it.displayName.ifBlank { it.email }} (${it.email})" } ?: mUid
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(mLabel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            if (mUid == org.ownerId) {
                                                FvgRoleBadge("owner")
                                            } else {
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    listOf("admin", "accountant", "viewer").forEach { r ->
                                                        FilterChip(
                                                            selected = mRole == r,
                                                            onClick = { viewModel.ownerUpdateOrgMemberRole(org, mUid, r) },
                                                            label = { Text(r, style = MaterialTheme.typography.labelSmall) }
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.ownerRemoveOrgMember(org, mUid) },
                                                        modifier = Modifier.size(34.dp)
                                                    ) {
                                                        Icon(
                                                            Icons.Filled.Close,
                                                            contentDescription = if (isArabic) "إزالة عضو" else "Remove member",
                                                            tint = FvgNegative,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FvgField(
                                            value = assignEmailOrUid,
                                            onValueChange = { assignEmailOrUid = it },
                                            label = if (isArabic) "بريد أو UID عضو لإضافته/تعديله" else "User Email or UID to assign",
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(
                                            enabled = !state.isBusy && assignEmailOrUid.isNotBlank(),
                                            onClick = {
                                                val input = assignEmailOrUid.trim()
                                                val matchedUid = state.ownerAllUsers.firstOrNull {
                                                    it.email.equals(input, ignoreCase = true) || it.userId == input
                                                }?.userId ?: input
                                                viewModel.ownerUpdateOrgMemberRole(org, matchedUid, assignRole)
                                                assignEmailOrUid = ""
                                            },
                                            modifier = Modifier.heightIn(min = 48.dp)
                                        ) {
                                            Text(if (isArabic) "منح ($assignRole)" else "Assign")
                                        }
                                    }
                                    FvgChipSelector(listOf("admin", "accountant", "viewer"), assignRole, { assignRole = it })
                                }
                            }
                        }

                        2 -> {
                            item {
                                FvgCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isArabic) "إدارة الباقات الكاملة (إنشاء • تعديل • حذف)" else "Subscription Tiers Full CRUD", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                        OutlinedButton(
                                            onClick = { isCreatingCustomPlan = !isCreatingCustomPlan },
                                            modifier = Modifier.heightIn(min = 44.dp).testTag("owner_toggle_new_plan_button")
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isCreatingCustomPlan) (if (isArabic) "تعديل باقة حالية" else "Edit Existing") else (if (isArabic) "باقة جديدة" else "New Tier"))
                                        }
                                    }
                                    if (isCreatingCustomPlan) {
                                        FvgField(
                                            value = customPlanSlug,
                                            onValueChange = { customPlanSlug = it.lowercase().replace(Regex("[^a-z0-9_\\-]"), "") },
                                            label = if (isArabic) "معرّف الباقة (حروف إنجليزية مثل vip_gold)" else "Tier Key/Slug (e.g. vip_gold)",
                                            testTag = "owner_custom_plan_slug_input"
                                        )
                                    } else {
                                        val existingKeys = allPlans.map { it.key.ifBlank { it.id } }
                                        FvgChipSelector(
                                            options = existingKeys,
                                            selected = selectedPlanKey,
                                            onSelect = { selectedPlanKey = it },
                                            labelResolver = { k ->
                                                val found = allPlans.firstOrNull { (it.key.ifBlank { it.id }) == k }
                                                if (isArabic) (found?.nameAr?.ifBlank { k } ?: k) else (found?.nameEn?.ifBlank { k } ?: k)
                                            }
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgField(planNameAr, { planNameAr = it }, if (isArabic) "الاسم بالعربية" else "Arabic Name", modifier = Modifier.weight(1f), testTag = "owner_plan_name_ar_input")
                                        FvgField(planNameEn, { planNameEn = it }, if (isArabic) "الاسم بالإنجليزية" else "English Name", modifier = Modifier.weight(1f), testTag = "owner_plan_name_en_input")
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgField(planPriceSdg, { planPriceSdg = it }, if (isArabic) "ج.س" else "SDG", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(planPriceUsd, { planPriceUsd = it }, if (isArabic) "دولار" else "USD", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(planDurationDays, { planDurationDays = it }, if (isArabic) "الأيام" else "Days", numeric = true, modifier = Modifier.weight(0.7f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgField(planMaxOrgs, { planMaxOrgs = it }, if (isArabic) "أقصى منظمات" else "Max Orgs", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(planMaxUsers, { planMaxUsers = it }, if (isArabic) "أقصى أعضاء" else "Max Users", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(planMaxProducts, { planMaxProducts = it }, if (isArabic) "أقصى منتجات" else "Max SKUs", numeric = true, modifier = Modifier.weight(1f))
                                    }
                                    FvgField(planTierLevel, { planTierLevel = it }, if (isArabic) "ترتيب المستوى (0 إلى 10)" else "Tier Level (0..10)", numeric = true)
                                    Text(if (isArabic) "المميزات المفعّلة لهذه الباقة (7 صلاحيات):" else "Feature Entitlements (7 Toggles):", style = MaterialTheme.typography.labelLarge)
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allowAllCosting, onCheckedChange = { allowAllCosting = it })
                                            Text(if (isArabic) "جميع طرق التكاليف الـ 8 (وإلا 2 فقط)" else "All 8 Costing Methods (vs 2 basic)", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allowBomProd, onCheckedChange = { allowBomProd = it })
                                            Text(if (isArabic) "تركيبات BOM وأوامر الإنتاج" else "BOM Recipes & Production Orders", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allowSectorAdvanced, onCheckedChange = { allowSectorAdvanced = it })
                                            Text(if (isArabic) "أدوات القطاعات المتقدمة (حاسبات التصنيع والأغذية)" else "Advanced Sector Tools (Mfg & Food Calculators)", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allowAi, onCheckedChange = { allowAi = it })
                                            Text(if (isArabic) "المساعد المالي الذكي (التحليل المالي للمنشأة)" else "Workspace AI Financial Analysis", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allowOcr, onCheckedChange = { allowOcr = it })
                                            Text(if (isArabic) "قراءة الفواتير بالكاميرا (OCR)" else "Camera Invoice OCR", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allowPdfXlsx, onCheckedChange = { allowPdfXlsx = it })
                                            Text(if (isArabic) "تصدير PDF و XLSX" else "PDF & XLSX Export", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(checked = allowAuditBackup, onCheckedChange = { allowAuditBackup = it })
                                            Text(if (isArabic) "سجل التدقيق والنسخ الاحتياطي الشامل" else "Audit Trail & Full Workspace Backup", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                    val targetKey = if (isCreatingCustomPlan) customPlanSlug.trim() else selectedPlanKey
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgGoldButton(
                                            text = if (isCreatingCustomPlan) (if (isArabic) "إنشاء الباقة الجديدة" else "Create Custom Tier") else (if (isArabic) "حفظ تعديلات الباقة" else "Save Plan Changes"),
                                            enabled = !state.isBusy && targetKey.isNotBlank() && planNameAr.isNotBlank() && planNameEn.isNotBlank(),
                                            onClick = {
                                                viewModel.savePlatformPlanAsOwner(
                                                    PlatformPlanRecord(
                                                        id = targetKey,
                                                        key = targetKey,
                                                        tierLevel = (planTierLevel.toLongOrNull() ?: 2L).coerceIn(0L, 10L),
                                                        nameAr = planNameAr,
                                                        nameEn = planNameEn,
                                                        priceSdg = planPriceSdg.ifBlank { "0" },
                                                        priceUsd = planPriceUsd.ifBlank { "0.00" },
                                                        billingCycle = "${planDurationDays.ifBlank { "30" }}d",
                                                        maxOrgs = (planMaxOrgs.toLongOrNull() ?: 1L).coerceAtLeast(1L),
                                                        maxUsers = (planMaxUsers.toLongOrNull() ?: 5L).coerceAtLeast(1L),
                                                        maxProducts = (planMaxProducts.toLongOrNull() ?: 25L).coerceAtLeast(1L),
                                                        allowAllCostingMethods = allowAllCosting,
                                                        allowBomAndProduction = allowBomProd,
                                                        allowAiAssistant = allowAi,
                                                        allowOcr = allowOcr,
                                                        allowPdfXlsxExport = allowPdfXlsx,
                                                        allowAuditAndBackup = allowAuditBackup,
                                                        allowSectorAdvancedTools = allowSectorAdvanced,
                                                        active = true
                                                    )
                                                )
                                                if (isCreatingCustomPlan) {
                                                    selectedPlanKey = targetKey
                                                    isCreatingCustomPlan = false
                                                    customPlanSlug = ""
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            testTag = "owner_save_plan_button"
                                        )
                                        if (!isCreatingCustomPlan && targetKey !in protectedPlanKeys) {
                                            OutlinedButton(
                                                onClick = { planToDelete = targetKey },
                                                enabled = !state.isBusy,
                                                modifier = Modifier.heightIn(min = 48.dp).testTag("owner_delete_plan_button")
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = if (isArabic) "حذف" else "Delete", tint = FvgNegative)
                                            }
                                        }
                                    }
                                }
                            }
                            items(allPlans, key = { it.id.ifBlank { it.key } }) { p ->
                                val pk = p.key.ifBlank { p.id }
                                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("${if (isArabic) p.nameAr else p.nameEn} ($pk • L${p.tierLevel})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        FvgRoleBadge("$${p.priceUsd} | ${p.priceSdg} SDG")
                                    }
                                    Text(
                                        text = "MaxOrgs=${p.maxOrgs} • MaxUsers=${p.maxUsers} • MaxSKUs=${p.maxProducts} | 8Costing=${p.allowAllCostingMethods} • BOM=${p.allowBomAndProduction} • Sectors=${p.allowSectorAdvancedTools} • AI=${p.allowAiAssistant} • OCR=${p.allowOcr} • Export=${p.allowPdfXlsxExport} • Audit=${p.allowAuditAndBackup}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }

                        3 -> {
                            item {
                                FvgCard {
                                    Text(if (isArabic) "نشر إشعار داخل التطبيق + قائمة /mail البريدية" else "Publish Targeted Notice & Queue /mail Email", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Text(if (isArabic) "الفئة المستهدفة:" else "Target Audience:", style = MaterialTheme.typography.labelLarge)
                                    FvgChipSelector(
                                        options = listOf("all", "trial", "paid", "specific_email"),
                                        selected = annTargetAudience,
                                        onSelect = { annTargetAudience = it },
                                        labelResolver = { aud ->
                                            when (aud) {
                                                "all" -> if (isArabic) "الجميع" else "All Users"
                                                "trial" -> if (isArabic) "فترة التجربة" else "Trial Users"
                                                "paid" -> if (isArabic) "المشتركون" else "Paid Users"
                                                else -> if (isArabic) "بريد محدد" else "Specific Email"
                                            }
                                        }
                                    )
                                    if (annTargetAudience == "specific_email") {
                                        FvgField(annTargetEmail, { annTargetEmail = it }, if (isArabic) "بريد المستلم المحدد" else "Recipient Email")
                                    }
                                    FvgField(annTitleAr, { annTitleAr = it }, if (isArabic) "عنوان الإشعار (عربي)" else "Arabic Title")
                                    FvgField(annTitleEn, { annTitleEn = it }, if (isArabic) "عنوان الإشعار (إنجليزي)" else "English Title")
                                    FvgField(annBodyAr, { annBodyAr = it }, if (isArabic) "النص (عربي)" else "Arabic Body", singleLine = false)
                                    FvgField(annBodyEn, { annBodyEn = it }, if (isArabic) "النص (إنجليزي)" else "English Body", singleLine = false)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = sendEmailAlso, onCheckedChange = { sendEmailAlso = it })
                                        Text(
                                            text = if (isArabic) "إرسال نسخة بريدية في طابور /mail للمشتركين"
                                            else "Also queue /mail broadcast email",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    FvgGoldButton(
                                        text = if (isArabic) "نشر الإشعار الآن" else "Publish Notice & Queue Email",
                                        icon = Icons.Filled.Campaign,
                                        enabled = !state.isBusy && (annTitleAr.isNotBlank() || annTitleEn.isNotBlank()),
                                        onClick = {
                                            viewModel.saveAnnouncementAsOwner(
                                                AppAnnouncementRecord(
                                                    titleAr = annTitleAr,
                                                    titleEn = annTitleEn,
                                                    bodyAr = annBodyAr,
                                                    bodyEn = annBodyEn,
                                                    targetAudience = annTargetAudience,
                                                    targetEmail = annTargetEmail.trim(),
                                                    active = true
                                                )
                                            )
                                            if (sendEmailAlso) {
                                                val recipientEmails = when (annTargetAudience) {
                                                    "specific_email" -> listOfNotNull(annTargetEmail.trim().takeIf { it.contains("@") })
                                                    "trial" -> {
                                                        val trialOrgOwners = state.ownerAllOrgs.filter { it.subscriptionStatus == "trial" }.map { it.ownerId }.toSet()
                                                        state.ownerAllUsers.filter { it.userId in trialOrgOwners && it.email.contains("@") }.map { it.email }
                                                    }
                                                    "paid" -> {
                                                        val paidOrgOwners = state.ownerAllOrgs.filter { it.subscriptionStatus == "active" }.map { it.ownerId }.toSet()
                                                        state.ownerAllUsers.filter { it.userId in paidOrgOwners && it.email.contains("@") }.map { it.email }
                                                    }
                                                    else -> state.ownerAllUsers.map { it.email }.filter { it.contains("@") }
                                                }.distinct()
                                                if (recipientEmails.isNotEmpty()) {
                                                    val subj = annTitleAr.ifBlank { annTitleEn }
                                                    val body = annBodyAr.ifBlank { annBodyEn } + "\n\nFVG Platform Administration"
                                                    viewModel.sendOwnerNotificationEmail(recipientEmails, subj, body)
                                                }
                                            }
                                            annTitleAr = ""; annTitleEn = ""; annBodyAr = ""; annBodyEn = ""; annTargetEmail = ""
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (state.ownerMailOutbox.isNotEmpty()) {
                                val smtpIssues = state.ownerMailOutbox.count {
                                    val st = it.delivery["state"]?.toString().orEmpty()
                                    st == "PENDING_SMTP_CONFIG" || st == "ERROR"
                                }
                                val hasSuccess = state.ownerMailOutbox.any { it.delivery["state"]?.toString() == "SUCCESS" }
                                item {
                                    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text(if (isArabic) "سجل /mail الصادر (${state.ownerMailOutbox.size})" else "Recent /mail Outbox Queue (${state.ownerMailOutbox.size})", style = MaterialTheme.typography.titleMedium)
                                        }
                                        if (smtpIssues > 0 || !hasSuccess) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = FvgWarning.copy(alpha = 0.14f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = if (isArabic)
                                                        "تشخيص SMTP: يوجد رسائل بانتظار الإرسال الفعلي أو بدون تأكيد SUCCESS. للإرسال التلقائي إلى بريد المستخدمين تأكدي من ضبط FVG_SMTP_APP_PASSWORD ونشر Cloud Functions."
                                                    else
                                                        "SMTP Notice: Some emails are waiting for live SMTP dispatch ($smtpIssues issue(s)). Configure FVG_SMTP_APP_PASSWORD secret & deploy Cloud Functions for live Gmail delivery.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = FvgWarning,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                        state.ownerMailOutbox.take(10).forEach { m ->
                                            val subj = m.message["subject"]?.toString() ?: m.mailType
                                            val deliveryState = m.delivery["state"]?.toString() ?: "QUEUED"
                                            val deliveryErr = m.delivery["error"]?.toString().orEmpty()
                                            Text("• ${m.to.joinToString(", ")} — $subj (${m.mailType} • $deliveryState)", style = MaterialTheme.typography.bodySmall)
                                            if (deliveryErr.isNotBlank()) {
                                                Text("   ↳ $deliveryErr", style = MaterialTheme.typography.labelSmall, color = FvgNegative)
                                            }
                                        }
                                    }
                                }
                            }
                            item {
                                FvgCard {
                                    Text(if (isArabic) "تذاكر الدعم (${state.ownerSupportTickets.size})" else "Support Tickets (${state.ownerSupportTickets.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    FvgChipSelector(
                                        options = listOf("all", "open", "resolved"),
                                        selected = ticketStatusFilter,
                                        onSelect = { ticketStatusFilter = it },
                                        labelResolver = { st ->
                                            when (st) {
                                                "all" -> if (isArabic) "الكل" else "All"
                                                "open" -> if (isArabic) "مفتوحة" else "Open"
                                                else -> if (isArabic) "تم الحل" else "Resolved"
                                            }
                                        }
                                    )
                                }
                            }
                            val filteredTickets = state.ownerSupportTickets.filter { tk ->
                                ticketStatusFilter == "all" || tk.status == ticketStatusFilter
                            }
                            items(filteredTickets, key = { it.id }) { tk ->
                                var replyText by remember(tk.id) { mutableStateOf(tk.ownerReply) }
                                FvgCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${tk.senderName} (${tk.senderEmail})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                        FvgRoleBadge(tk.status)
                                    }
                                    Text(tk.message, style = MaterialTheme.typography.bodyMedium)
                                    FvgField(replyText, { replyText = it }, if (isArabic) "رد الإدارة" else "Admin Reply", singleLine = false)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgPrimaryButton(
                                            text = if (isArabic) "إرسال الرد وإغلاق" else "Reply & Resolve",
                                            enabled = replyText.isNotBlank() && !state.isBusy,
                                            onClick = { viewModel.replySupportTicketAsOwner(tk.id, "resolved", replyText) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (tk.status == "resolved") {
                                            OutlinedButton(
                                                onClick = { viewModel.replySupportTicketAsOwner(tk.id, "open", replyText) },
                                                enabled = !state.isBusy,
                                                modifier = Modifier.heightIn(min = 48.dp)
                                            ) {
                                                Text(if (isArabic) "إعادة فتح" else "Reopen")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        4 -> {
                            item {
                                val passedCount = validationChecks.count { it.passed }
                                FvgCard {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Filled.Verified, contentDescription = null, tint = if (passedCount == validationChecks.size) FvgPositive else FvgNegative)
                                            Column {
                                                Text(
                                                    text = if (isArabic) "مختبر التحقق المحاسبي واعتماد المعادلات" else "Accounting Formula Validation Dashboard",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = if (isArabic) "اجتازت $passedCount من ${validationChecks.size} معادلة معتمدة وفق IFRS/IMA"
                                                    else "$passedCount of ${validationChecks.size} verified formulas passing IFRS/IMA assertions",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = if (passedCount == validationChecks.size) FvgPositive else FvgNegative
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { validationChecks = FvgFinancialEngine.runFormulaValidationSuite() },
                                            modifier = Modifier.heightIn(min = 44.dp).testTag("run_formula_suite_button")
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Text(if (isArabic) "إعادة الفحص" else "Re-Run")
                                        }
                                    }
                                }
                            }
                            items(validationChecks, key = { it.id }) { chk ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, if (chk.passed) FvgPositive.copy(alpha = 0.5f) else FvgNegative),
                                    modifier = Modifier.fillMaxWidth().testTag("formula_check_${chk.id}")
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(if (isArabic) chk.nameAr else chk.nameEn, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Surface(shape = RoundedCornerShape(8.dp), color = (if (chk.passed) FvgPositive else FvgNegative).copy(alpha = 0.14f)) {
                                                Text(
                                                    text = if (chk.passed) (if (isArabic) "معتمد ✓" else "PASS ✓") else (if (isArabic) "فشل ✗" else "FAIL ✗"),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (chk.passed) FvgPositive else FvgNegative,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Text("${chk.formulaText} [${chk.standardReference}]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Text(if (isArabic) "المتوقع: ${chk.expectedOutput}" else "Expected: ${chk.expectedOutput}", style = MaterialTheme.typography.bodySmall)
                                        Text(if (isArabic) "النتيجة الفعلية: ${chk.actualOutput}" else "Actual Engine: ${chk.actualOutput}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            item {
                                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(if (isArabic) "مختبر اختبار يدوي للمعادلات (Live Sandbox)" else "Live Accounting Formula Sandbox", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgField(sandboxMat, { sandboxMat = it }, if (isArabic) "المواد" else "Materials", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(sandboxLab, { sandboxLab = it }, if (isArabic) "العمالة" else "Labor", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(sandboxFixed, { sandboxFixed = it }, if (isArabic) "الثابتة" else "Fixed", numeric = true, modifier = Modifier.weight(1f))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgField(sandboxUnits, { sandboxUnits = it }, if (isArabic) "الوحدات" else "Units", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(sandboxPrice, { sandboxPrice = it }, if (isArabic) "سعر البيع" else "Selling Price", numeric = true, modifier = Modifier.weight(1f))
                                        FvgField(sandboxTargetMargin, { sandboxTargetMargin = it }, if (isArabic) "هامش %" else "Target %", numeric = true, modifier = Modifier.weight(1f))
                                    }
                                    val liveOutcome = remember(sandboxMat, sandboxLab, sandboxFixed, sandboxUnits, sandboxPrice, sandboxTargetMargin) {
                                        runCatching {
                                            val input = FvgFinancialEngine.CostingInput(
                                                directMaterial = FvgFinancialEngine.d(sandboxMat),
                                                directLabor = FvgFinancialEngine.d(sandboxLab),
                                                overhead = BigDecimal.ZERO,
                                                fixedCosts = FvgFinancialEngine.d(sandboxFixed),
                                                goodUnits = FvgFinancialEngine.d(sandboxUnits),
                                                sellingPrice = FvgFinancialEngine.d(sandboxPrice)
                                            )
                                            FvgFinancialEngine.calculateCostingByMethod("target", input, FvgFinancialEngine.d(sandboxTargetMargin))
                                        }.getOrNull()
                                    }
                                    if (liveOutcome != null) {
                                        val r = liveOutcome.coreResult
                                        Text(
                                            text = if (isArabic)
                                                "تكلفة الوحدة: ${r.unitCost} | هامش الربح: ${r.profitMarginPct}% | نسبة الإضافة: ${r.markupPct}%\nنقطة التعادل: ${r.breakEvenUnits ?: "غير قابل"} وحدة | المسموح للهامش المستهدف: ${liveOutcome.targetAllowableUnitCost}"
                                            else
                                                "Unit Cost: ${r.unitCost} | Margin: ${r.profitMarginPct}% | Markup: ${r.markupPct}%\nBreak-Even: ${r.breakEvenUnits ?: "N/A"} units | Allowable Cost: ${liveOutcome.targetAllowableUnitCost}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(if (isArabic) "أدخل أرقاماً صالحة أكبر من صفر للوحدات." else "Enter valid positive numbers.", color = FvgNegative, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        else -> {
                            item {
                                FvgCard {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = FvgWarmGold)
                                            Column {
                                                Text(
                                                    text = if (isArabic) "مراقبة الأمان والإشعارات الفورية" else "Real-Time Security & Alert Monitor",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = if (isArabic) "$unreadAlertCount تنبيه غير مقروء من المشغلات السحابية" else "$unreadAlertCount unread alert(s) from Firestore triggers",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.runAdminSecurityScan(isArabic) },
                                            enabled = !state.isBusy,
                                            modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("admin_security_scan_tab_btn")
                                        ) {
                                            Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isArabic) "فحص أمني فوري" else "Run Security Scan", style = MaterialTheme.typography.labelSmall)
                                        }
                                        OutlinedButton(
                                            onClick = { showAlertsDialog = true },
                                            modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("admin_open_alerts_tab_btn")
                                        ) {
                                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isArabic) "فتح مركز التنبيهات" else "Open Alert Center", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            item {
                                FvgCard {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = if (isArabic) "أمان بوابة الإدارة وتغيير كلمة المرور" else "Admin Portal Security & Password",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = FvgPositive.copy(alpha = 0.14f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (isArabic)
                                                "تم تفعيل كلمة المرور الإدارية الخاصة المشفرة (SHA-256) وإتمام الإعداد الأولي لمرة واحدة بنجاح ✓"
                                            else
                                                "Custom administrator password (salted SHA-256) is active; one-time setup completed ✓",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = FvgPositive,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                    var showCurSecPass by remember { mutableStateOf(false) }
                                    var showNewSecPass by remember { mutableStateOf(false) }
                                    var showConfirmSecPass by remember { mutableStateOf(false) }
                                    OutlinedTextField(
                                        value = curAdminPass,
                                        onValueChange = { curAdminPass = it },
                                        label = { Text(if (isArabic) "كلمة المرور الحالية" else "Current Admin Password") },
                                        visualTransformation = if (showCurSecPass) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(onClick = { showCurSecPass = !showCurSecPass }) {
                                                Icon(if (showCurSecPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_cur_pass_input")
                                    )
                                    OutlinedTextField(
                                        value = newAdminPass,
                                        onValueChange = { newAdminPass = it },
                                        label = { Text(if (isArabic) "كلمة المرور الجديدة (8 أحرف + رقم/رمز)" else "New Password (8+ chars, letter + number/symbol)") },
                                        visualTransformation = if (showNewSecPass) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(onClick = { showNewSecPass = !showNewSecPass }) {
                                                Icon(if (showNewSecPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_new_pass_input")
                                    )
                                    OutlinedTextField(
                                        value = confirmAdminPass,
                                        onValueChange = { confirmAdminPass = it },
                                        label = { Text(if (isArabic) "تأكيد كلمة المرور الجديدة" else "Confirm New Password") },
                                        visualTransformation = if (showConfirmSecPass) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        singleLine = true,
                                        trailingIcon = {
                                            IconButton(onClick = { showConfirmSecPass = !showConfirmSecPass }) {
                                                Icon(if (showConfirmSecPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_confirm_pass_input")
                                    )
                                    adminPassError?.let { err ->
                                        Text(err, color = FvgNegative, style = MaterialTheme.typography.bodySmall)
                                    }
                                    adminPassStatus?.let { st ->
                                        Text(st, color = FvgPositive, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    FvgGoldButton(
                                        text = if (isArabic) "تحديث كلمة مرور الإدارة" else "Update Admin Password",
                                        icon = Icons.Filled.Security,
                                        enabled = curAdminPass.isNotBlank() && newAdminPass.length >= 8 && newAdminPass == confirmAdminPass,
                                        onClick = {
                                            adminPassError = null
                                            adminPassStatus = null
                                            AdminCredentialHelper.changeAdminPassword(
                                                context = context,
                                                currentPassword = curAdminPass,
                                                newPassword = newAdminPass,
                                                confirmPassword = confirmAdminPass,
                                                isArabic = isArabic
                                            )
                                                .onSuccess {
                                                    scope.launch {
                                                        runCatching { FirebaseAuth.getInstance().currentUser?.updatePassword(newAdminPass)?.await() }
                                                    }
                                                    curAdminPass = ""
                                                    newAdminPass = ""
                                                    confirmAdminPass = ""
                                                    adminPassStatus = if (isArabic) "تم تغيير كلمة مرور الإدارة بنجاح." else "Admin password updated successfully."
                                                }
                                                .onFailure { e ->
                                                    adminPassError = e.message ?: if (isArabic) "تعذّر تحديث كلمة المرور" else "Failed to update password"
                                                }
                                        },
                                        modifier = Modifier.fillMaxWidth().testTag("admin_change_pass_button")
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAlertsDialog) {
        OwnerNotificationsCenterDialog(
            notifications = state.ownerAdminNotifications,
            ownerAllUsers = state.ownerAllUsers,
            ownerAllPaymentRequests = state.ownerAllPaymentRequests,
            isBusy = state.isBusy,
            isArabic = isArabic,
            onMarkRead = { notifId, ack -> viewModel.markAdminNotificationRead(notifId, ack) },
            onMarkAllRead = { viewModel.markAllAdminNotificationsRead() },
            onDeleteNotification = { notifId -> viewModel.deleteAdminNotificationAsOwner(notifId) },
            onRunSecurityScan = { viewModel.runAdminSecurityScan(isArabic) },
            onJumpToTab = { tabIdx -> adminTab = tabIdx },
            onInspectOrgAuditLogs = { orgId ->
                val targetOrg = state.ownerAllOrgs.firstOrNull { it.id == orgId }
                if (targetOrg != null) {
                    showAlertsDialog = false
                    viewModel.loadOwnerOrgAuditLogs(targetOrg)
                }
            },
            onDismiss = { showAlertsDialog = false }
        )
    }

    if (state.ownerAuditLogsOrgName.isNotBlank()) {
        OwnerAuditLogsDialog(
            orgName = state.ownerAuditLogsOrgName,
            logs = state.ownerAuditLogs,
            isLoading = state.isLoadingOwnerAuditLogs,
            isArabic = isArabic,
            onDismiss = { viewModel.clearOwnerOrgAuditLogs() }
        )
    }

    planToDelete?.let { delId ->
        AlertDialog(
            onDismissRequest = { planToDelete = null },
            title = { Text(if (isArabic) "تأكيد حذف الباقة" else "Confirm Tier Deletion") },
            text = {
                Text(
                    if (isArabic) "هل أنت متأكدة من حذف الباقة المخصصة ($delId)؟ لن تتأثر المنظمات القائمة ما لم يتم تغيير اشتراكها."
                    else "Delete custom subscription tier ($delId)?"
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = FvgNegative),
                    onClick = {
                        viewModel.deletePlatformPlanAsOwner(delId)
                        planToDelete = null
                        selectedPlanKey = "professional"
                    }
                ) {
                    Text(if (isArabic) "حذف نهائي" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { planToDelete = null }) {
                    Text(if (isArabic) "إلغاء" else "Cancel")
                }
            }
        )
    }
}
