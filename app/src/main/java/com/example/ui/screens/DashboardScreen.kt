package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.engine.FvgFinancialEngine
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.domain.math.BigDecimal

@Composable
fun DashboardScreen(
    state: FvgUiState,
    userDisplayName: String,
    isArabic: Boolean,
    isDark: Boolean,
    unreadNotificationsCount: Int = 0,
    onNotificationsClick: () -> Unit = {},
    onOpenCalculator: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenOwnerAdmin: () -> Unit = {},
    onReplayTour: () -> Unit = {},
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onCreateOrganization: (String, String, String) -> Unit,
    onApplySectorPreset: (String) -> Unit = {},
    onFlushOfflineQueue: () -> Unit = {},
    onResolveOfflineConflict: (String, Boolean) -> Unit = { _, _ -> },
    onNavigateTab: (Int) -> Unit
) {
    val s = fvgStrings(isArabic)
    val context = LocalContext.current
    val org = state.activeOrg
    val stmt = state.statementsReport
    val currency = org?.baseCurrency ?: "SDG"
    var chartWindow by remember { mutableStateOf("last6") }
    var inspectedSectorKey by remember { mutableStateOf<String?>(null) }

    val uidKey = state.userProfile?.userId ?: "anon"
    val welcomePrefs = remember(context) { context.getSharedPreferences("fvg_welcome_prefs", Context.MODE_PRIVATE) }
    var welcomeDismissed by remember(uidKey) {
        mutableStateOf(welcomePrefs.getBoolean("welcome_dismissed_$uidKey", false))
    }
    val visibleAnnouncements = remember(state.announcements, state.activeOrg, state.userProfile) {
        state.visibleAnnouncements()
    }

    val totalCash = stmt?.cashAndBank ?: BigDecimal.ZERO
    val invBook = state.totalInventoryBookValue
    val invRepl = state.totalReplacementCost
    val combinedBookPortfolio = FvgFinancialEngine.money(totalCash.add(invBook))

    val revenue = stmt?.revenue ?: BigDecimal.ZERO
    val expenses = FvgFinancialEngine.money((stmt?.cogs ?: BigDecimal.ZERO).add(stmt?.operatingExpenses ?: BigDecimal.ZERO))
    val netProfit = stmt?.netProfit ?: BigDecimal.ZERO

    val latestInflation = state.inflationRecords.firstOrNull()
    val latestInfPct = latestInflation?.monthlyRatePct?.let { runCatching { FvgFinancialEngine.d(it) }.getOrNull() }
    val latestSnap = state.costSnapshots.lastOrNull()
    val riskAssessment = remember(latestSnap, latestInfPct, stmt) {
        latestSnap?.let { snap ->
            FvgFinancialEngine.evaluateFinancialRisk(
                profitMarginPct = runCatching { FvgFinancialEngine.d(snap.profitMarginPct) }.getOrNull(),
                inflationRatePct = latestInfPct,
                totalDebt = stmt?.totalLiabilities ?: BigDecimal.ZERO,
                totalEquity = stmt?.totalEquityWithNetIncome ?: BigDecimal.ZERO
            )
        }
    }

    val priceTrendPoints = remember(state.priceObservations, chartWindow) {
        val list = if (chartWindow == "last6") state.priceObservations.takeLast(6) else state.priceObservations
        list.mapNotNull { obs ->
            obs.price.toFloatOrNull()?.let { obs.observationDate.takeLast(5) to it }
        }
    }
    val rawName = state.userProfile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName
    val resolvedName = remember(rawName) {
        val clean = if (rawName.contains("@")) {
            rawName.substringBefore("@").replace(".", " ").replace("_", " ").trim()
        } else {
            rawName.trim()
        }
        clean.ifBlank { if (isArabic) "المستخدم المميّز" else "Valued User" }
    }
    val userInitials = remember(resolvedName) {
        resolvedName.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .take(2)
            .joinToString("") { it.first().toString() }
            .ifBlank { "FV" }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgTopHeaderBanner(
                title = if (isArabic) "مرحباً، $resolvedName" else "Welcome, $resolvedName",
                subtitle = if (isArabic) "رؤيتك المالية، دليلنا" else "Your Financial Vision, Our Guide",
                icon = Icons.Filled.AccountBalanceWallet,
                isArabic = isArabic,
                isDark = isDark,
                onToggleLanguage = onToggleLanguage,
                onToggleTheme = onToggleTheme,
                orgName = org?.name,
                roleBadge = org?.let { state.currentUserRole },
                userInitials = userInitials,
                unreadNotificationsCount = unreadNotificationsCount,
                onNotificationsClick = onNotificationsClick,
                onCalculatorClick = onOpenCalculator,
                onHelpClick = onOpenHelp,
                onProfileClick = onOpenProfile
            )
        }

        // Hybrid Offline-First Sync & Conflict Resolution Banner
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                val hasPending = state.offlineQueue.isNotEmpty()
                val hasConflicts = state.offlineConflicts.isNotEmpty()
                val statusColor = when {
                    hasConflicts -> MaterialTheme.colorScheme.errorContainer
                    !state.isOnline || hasPending -> FvgAntiqueGold.copy(alpha = 0.16f)
                    else -> FvgPositive.copy(alpha = 0.10f)
                }
                FvgCard(
                    containerColor = statusColor,
                    modifier = Modifier.testTag("hybrid_sync_status_card")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (state.isOnline) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = if (hasConflicts) MaterialTheme.colorScheme.error else if (state.isOnline) FvgPositive else FvgAntiqueGold
                            )
                            Column {
                                Text(
                                    text = when {
                                        hasConflicts -> if (isArabic) "تعارض مزامنة (${state.offlineConflicts.size}) — يتطلب قرارك" else "Sync Conflict (${state.offlineConflicts.size}) — Action Needed"
                                        !state.isOnline -> if (isArabic) "وضع عدم الاتصال نشط • طابور الانتظار: ${state.offlineQueue.size}" else "Offline Mode • Queued Offline: ${state.offlineQueue.size}"
                                        hasPending -> if (isArabic) "متصل بالشبكة • قيد الانتظار للمزامنة: ${state.offlineQueue.size}" else "Online • Pending Sync: ${state.offlineQueue.size}"
                                        else -> if (isArabic) "متصل بالشبكة • المزامنة الهجينة محدثة ✓" else "Online • Hybrid Cloud Sync Ready ✓"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (hasPending) {
                                    Text(
                                        text = state.offlineQueue.take(3).joinToString(" • ") { it.title },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (hasPending) {
                            OutlinedButton(
                                onClick = onFlushOfflineQueue,
                                enabled = state.isOnline && !state.isBusy,
                                modifier = Modifier
                                    .heightIn(min = 42.dp)
                                    .testTag("offline_sync_now_button")
                            ) {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isArabic) "مزامنة الآن" else "Sync Now", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }

                    if (hasConflicts) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        state.offlineConflicts.forEach { conflict ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${conflict.mutation.operationType}: ${conflict.localSummary}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = if (isArabic)
                                            "نسخة السحابة: ${conflict.serverSummary}"
                                        else
                                            "Cloud Version: ${conflict.serverSummary}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FvgPrimaryButton(
                                            text = if (isArabic) "اعتماد المحلي" else "Keep Local",
                                            enabled = !state.isBusy,
                                            onClick = { onResolveOfflineConflict(conflict.id, true) },
                                            modifier = Modifier.weight(1f),
                                            testTag = "conflict_keep_local_${conflict.id}"
                                        )
                                        OutlinedButton(
                                            enabled = !state.isBusy,
                                            onClick = { onResolveOfflineConflict(conflict.id, false) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("conflict_keep_server_${conflict.id}")
                                        ) {
                                            Text(if (isArabic) "اعتماد السحابة" else "Keep Server")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dismissible Owner Welcome Letter Card
        if (!welcomeDismissed && state.userProfile != null) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgCard(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.testTag("welcome_letter_card")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (isArabic) "رسالة ترحيب من المالكة زهراء يوسف" else "Welcome Message from Owner Zahra Yousif",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            TextButton(
                                onClick = {
                                    welcomeDismissed = true
                                    welcomePrefs.edit().putBoolean("welcome_dismissed_$uidKey", true).apply()
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(if (isArabic) "إخفاء" else "Dismiss")
                            }
                        }
                        Text(
                            text = if (isArabic)
                                "مرحباً بك يا $resolvedName في دليل رؤيتك المالي (FVG)! تم تجهيز مساحة عملك وتفعيل تجربتك المجانية الكاملة لمدة 7 أيام.\n— زهراء يوسف"
                            else
                                "Welcome $resolvedName to Financial Vision Guide (FVG)! Your workspace is initialized with a full 7-day free trial.\n— Zahra Yousif",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        // Targeted Announcements from Owner Console
        if (visibleAnnouncements.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    visibleAnnouncements.take(3).forEach { ann ->
                        FvgCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Text(
                                    text = if (isArabic) ann.titleAr.ifBlank { ann.titleEn } else ann.titleEn.ifBlank { ann.titleAr },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = if (isArabic) ann.bodyAr.ifBlank { ann.bodyEn } else ann.bodyEn.ifBlank { ann.bodyAr },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }

        // Platform Owner Console Exclusive Entry
        if (state.isPlatformOwner) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgGoldButton(
                        text = if (isArabic) "فتح لوحة الإدارة الحصرية (/admin) — المالكة زهراء" else "Open Restricted /admin Owner Console",
                        icon = Icons.Filled.AdminPanelSettings,
                        onClick = onOpenOwnerAdmin,
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "open_owner_admin_button"
                    )
                }
            }
        }

        if (org != null && state.isTrialExpiredAndLocked()) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgTrialExpiredGateCard(
                        isArabic = isArabic,
                        orgName = org.name,
                        durationHours = state.effectiveTrialDurationHours(),
                        onUpgradeClick = { onNavigateTab(5) }
                    )
                }
            }
        } else if (org != null && (org.subscriptionStatus == "trial" || org.trialEndsAt != null)) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgTrialCountdownBanner(
                        trialEndsAtMillis = state.effectiveTrialEndsMillis(),
                        isArabic = isArabic,
                        isPreviewTrial = false,
                        durationHours = state.effectiveTrialDurationHours(),
                        serverAnchorMillis = state.serverAnchorMillis,
                        elapsedBaselineMillis = state.elapsedBaselineRealtimeMillis,
                        onActionClick = { onNavigateTab(5) }
                    )
                }
            }
        }

        if (org == null && !state.isLoading) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OrganizationSetupCard(
                        isArabic = isArabic,
                        isBusy = state.isBusy,
                        onCreate = onCreateOrganization
                    )
                }
            }
        }

        // Hero Card: Total Cash & Inventory Book Value + Sparkline
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(FvgDeepGreen, FvgForestGreen)))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = s.totalPortfolioValue,
                                style = MaterialTheme.typography.labelLarge,
                                color = FvgWarmGold,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = FvgWarmGold.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = currency,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = FvgWarmGold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Text(
                            text = "${combinedBookPortfolio.toPlainString()} $currency",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = FvgWarmIvory
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isArabic)
                                        "النقدية: $totalCash  |  المخزون الدفتري: $invBook"
                                    else
                                        "Cash: $totalCash  •  Inventory Book: $invBook",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = FvgWarmIvory.copy(alpha = 0.85f)
                                )
                                Text(
                                    text = if (isArabic)
                                        "تكلفة الاستبدال الحالية (تقدير): ${FvgFinancialEngine.money(invRepl)} $currency"
                                    else
                                        "Current Replacement Estimate: ${FvgFinancialEngine.money(invRepl)} $currency",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FvgWarmGold
                                )
                            }
                            FvgSparklineCanvas(
                                values = if (priceTrendPoints.size >= 2) priceTrendPoints.map { it.second } else listOf(10f, 12f, 11.5f, 14f),
                                modifier = Modifier
                                    .width(92.dp)
                                    .height(42.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3 KPI Summary Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiMiniCard(
                    title = s.revenueLabel,
                    value = "$revenue",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = s.expensesLabel,
                    value = "$expenses",
                    accentColor = FvgAntiqueGold,
                    modifier = Modifier.weight(1f)
                )
                KpiMiniCard(
                    title = s.netProfitLabel,
                    value = "$netProfit",
                    accentColor = if (netProfit < BigDecimal.ZERO) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Full-Width Rectangular Traffic-Light Business Health Signal (Green / Yellow / Red)
        item {
            val overallSignalLevel = when {
                stmt != null && !stmt.isAccountingEquationBalanced -> "red"
                riskAssessment?.level == "high" || netProfit < BigDecimal.ZERO -> "red"
                riskAssessment?.level == "medium" || (latestInfPct != null && latestInfPct >= BigDecimal("10")) -> "yellow"
                invRepl > invBook.multiply(BigDecimal("1.15")) && invBook > BigDecimal.ZERO -> "yellow"
                else -> "green"
            }
            val signalTitle = when (overallSignalLevel) {
                "red" -> if (isArabic) "تنبيه مالي حرج — راجع التوازن أو المخاطر" else "Critical Financial Alert — Review Risk or Balance"
                "yellow" -> if (isArabic) "انتباه مالي — ضغط تضخم أو فجوة استبدال" else "Caution — Moderate Risk, Inflation or Replacement Gap"
                else -> if (isArabic) "الوضع المالي مستقر وآمن" else "Financial Position Healthy & Balanced"
            }
            val signalSubtitle = when (overallSignalLevel) {
                "red" -> if (stmt != null && !stmt.isAccountingEquationBalanced) {
                    if (isArabic) "توجد مراجعة مطلوبة في توازن الميزانية العمومية." else "Balance Sheet equation requires attention."
                } else {
                    riskAssessment?.explanation ?: (if (isArabic) "صافي الربح أو الهامش في المنطقة الحرجة؛ راجع التسعير والتكاليف." else "Margin or net profit in critical zone; review pricing & costs.")
                }
                "yellow" -> riskAssessment?.explanation ?: if (isArabic) {
                    "تكلفة استبدال المخزون ($invRepl) تفوق الدفتري ($invBook) أو هناك ضغط تضخم."
                } else {
                    "Inventory replacement ($invRepl) exceeds book value ($invBook) or inflation elevated."
                }
                else -> if (isArabic) {
                    "القيود متوازنة ومؤشرات الهامش والتكلفة ضمن النطاق الآمن."
                } else {
                    "Accounting equation balanced and margins within safe operating bounds."
                }
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgStatusSignalBanner(
                    level = overallSignalLevel,
                    title = signalTitle,
                    subtitle = signalSubtitle,
                    isArabic = isArabic
                )
            }
        }

        // Executive Financial Health Score Card (0-100)
        item {
            val health = remember(stmt, state.costSnapshots, latestInfPct) {
                FvgFinancialEngine.computeFinancialHealthScore(stmt, state.costSnapshots, latestInfPct)
            }
            val scoreColor = when {
                health.score >= 85 -> FvgPositive
                health.score >= 65 -> FvgAntiqueGold
                else -> FvgNegative
            }
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("executive_health_score_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isArabic) "مؤشر الصحة المالية التنفيذي (FVG Health Score)" else "Executive Financial Health Score",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isArabic) health.summaryAr else health.summaryEn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = scoreColor.copy(alpha = 0.14f),
                            border = BorderStroke(1.5.dp, scoreColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${health.score}/100",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scoreColor
                                )
                                Text(
                                    text = if (isArabic) "التقييم ${health.grade}" else "Grade ${health.grade}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = scoreColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Financial Risk Summary Card
        if (riskAssessment != null) {
            item {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isArabic) "تقييم المخاطر المالية (${latestSnap?.productName})" else "Financial Risk Engine (${latestSnap?.productName})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                            FvgRiskBadge(level = riskAssessment.level, isArabic = isArabic)
                        }
                        Text(
                            text = if (isArabic) "${riskAssessment.ruleName}: ${riskAssessment.actualValue} (الحد ${riskAssessment.thresholdValue})"
                            else "${riskAssessment.ruleName}: ${riskAssessment.actualValue} (Threshold ${riskAssessment.thresholdValue})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(riskAssessment.explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // Financial Overview Chart
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "نظرة عامة على الأداء المالي والأسعار" else "Financial Overview & Price Trajectory",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        FvgChipSelector(
                            options = listOf("last6", "all"),
                            selected = chartWindow,
                            onSelect = { chartWindow = it },
                            labelResolver = { if (it == "last6") (if (isArabic) "آخر 6" else "Last 6") else (if (isArabic) "الكل" else "All") }
                        )
                    }
                    if (priceTrendPoints.size >= 2) {
                        FvgSplineLineChartCanvas(points = priceTrendPoints)
                    }
                    if (stmt == null && state.journalEntries.isEmpty()) {
                        Text(
                            text = if (isArabic)
                                "لم يتم تسجيل قيود محاسبية بعد. ابدأ بتسجيل قيد أو فاتورة في تبويب المحاسبة."
                            else
                                "No posted accounting entries yet. Post balanced entries in Ledger to populate financial overview bars.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        FvgBarComparisonCanvas(
                            bars = listOf(
                                (if (isArabic) "إيرادات" else "Revenue") to revenue.toFloat(),
                                (if (isArabic) "تكلفة" else "COGS/Exp") to expenses.toFloat(),
                                (if (isArabic) "ربح" else "Net Profit") to netProfit.toFloat(),
                                (if (isArabic) "مخزون" else "Inv.Book") to invBook.toFloat()
                            )
                        )
                    }
                }
            }
        }

        // Inflation & Price Watch Summary
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard {
                    Text(
                        text = if (isArabic) "مراقبة التضخم وتغير الأسعار" else "Inflation & Price Movement Monitor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(s.inflationIndexLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = latestInflation?.let { "${it.indexValue} (${it.monthlyRatePct}% / ${it.sourceType})" }
                                    ?: if (isArabic) "غير مسجل بعد" else "Not recorded yet",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isArabic) "المنتجات المراقبة" else "Tracked Products",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isArabic) "${state.products.size} (${state.priceObservations.size} سجل)" else "${state.products.size} (${state.priceObservations.size} obs)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (state.priceObservations.isNotEmpty()) {
                        val latestObs = state.priceObservations.last()
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${latestObs.productName}: ${latestObs.previousPrice} -> ${latestObs.price} ${latestObs.currency} (${latestObs.pctChange}% ${latestObs.direction})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick Actions Grid (6 operational modules + Calculator + Profile + Help/Tour)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard {
                    Text(
                        text = if (isArabic) "وصول سريع للوحدات والأدوات" else "Quick Access to Modules & Tools",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionTile(s.navPrices, Icons.AutoMirrored.Filled.TrendingUp, { onNavigateTab(1) }, Modifier.weight(1f), "quick_prices")
                        QuickActionTile(s.navCosting, Icons.Filled.Calculate, { onNavigateTab(2) }, Modifier.weight(1f), "quick_costing")
                        QuickActionTile(s.navAccounting, Icons.Filled.AccountBalance, { onNavigateTab(3) }, Modifier.weight(1f), "quick_accounting")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionTile(s.navInventory, Icons.Filled.Inventory2, { onNavigateTab(4) }, Modifier.weight(1f), "quick_inventory")
                        QuickActionTile(s.navAssistantReports, Icons.Filled.AutoAwesome, { onNavigateTab(5) }, Modifier.weight(1f), "quick_assistant")
                        QuickActionTile(if (isArabic) "الآلة الحاسبة" else "Calculator", Icons.Filled.Dialpad, onOpenCalculator, Modifier.weight(1f), "quick_calculator")
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickActionTile(if (isArabic) "ملفي" else "Profile", Icons.Filled.Person, onOpenProfile, Modifier.weight(1f), "quick_profile")
                        QuickActionTile(if (isArabic) "المساعدة" else "Help", Icons.Filled.HelpOutline, onOpenHelp, Modifier.weight(1f), "quick_help")
                        QuickActionTile(if (isArabic) "الجولة" else "Tour", Icons.Filled.Explore, onReplayTour, Modifier.weight(1f), "quick_tour")
                    }
                }
            }
        }

        // Active Sectors (2 Core Operational Sectors with Dedicated Calculators)
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "القطاعات المفعّلة والحاسبات المخصصة" else "Active Sectors & Dedicated Calculators",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        FvgFieldHelpIcon(
                            title = if (isArabic) "القطاعات المفعّلة" else "Active Sectors",
                            explanation = if (isArabic)
                                "تم تفعيل قطاعي التصنيع والأغذية بحاسبات ونماذج متخصصة. اضغط على أي قطاع مفعّل لفتح حاسبة تكاليف الدفعة والوصفات ودليل الحسابات المخصص له."
                            else
                                "Manufacturing and Food & Beverage are actively provisioned with specialized batch and recipe calculators and industry-tailored charts of accounts.",
                            isArabic = isArabic
                        )
                    }

                    val activeSectors = FvgFinancialEngine.FVG_SECTORS.filter { it.key == "manufacturing" || it.key == "food_beverage" }
                    val upcomingSectors = FvgFinancialEngine.FVG_SECTORS.filter { it.key != "manufacturing" && it.key != "food_beverage" }

                    activeSectors.forEach { sec ->
                        val isCurrent = org?.sectorType == sec.key
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, if (isCurrent) FvgWarmGold else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { inspectedSectorKey = sec.key }
                                .testTag("dashboard_sector_card_${sec.key}")
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = if (sec.key == "manufacturing") Icons.Filled.PrecisionManufacturing else Icons.Filled.Restaurant,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column {
                                            Text(
                                                text = if (isArabic) sec.nameAr else sec.nameEn,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (isCurrent) {
                                                Text(
                                                    text = if (isArabic) "القطاع النشط الحالي لمؤسستك" else "Current Active Organization Sector",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = FvgAntiqueGold,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = FvgPositive.copy(alpha = 0.16f)
                                    ) {
                                        Text(
                                            text = if (isArabic) "مفعّل ✓" else "Active ✓",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = FvgPositive,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (sec.key == "manufacturing") {
                                        if (isArabic) "حاسبة تكاليف خطوط الإنتاج، المواد المباشرة، الأجور الصناعية، تكاليف المصنع الإضافية (FOH)، وحساب سعر البيع بهامش 25%."
                                        else "Production run calculator, direct materials, shop floor labor, FOH absorption, and margin-based pricing."
                                    } else {
                                        if (isArabic) "حاسبة تكلفة الوصفات والمكونات الطازجة، التغليف، امتصاص الهدر والتلف، وسياسة الصلاحية (FEFO)."
                                        else "Recipe costing, fresh ingredient yield, packaging, spoilage absorption, and FEFO expiry risk."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { inspectedSectorKey = sec.key },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isArabic) "فتح حاسبة ${if (sec.key == "manufacturing") "التصنيع" else "الأغذية"} المخصصة" else "Open Sector Calculator",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    // Upcoming Sectors grouped cleanly
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = if (isArabic) "خارطة القطاعات القادمة قريباً (في مرحلة التطوير):" else "Upcoming Sectors (In Development Pipeline):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    upcomingSectors.forEach { sec ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isArabic) sec.nameAr else sec.nameEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = FvgWarmGold.copy(alpha = 0.16f)
                                ) {
                                    Text(
                                        text = if (isArabic) "قريباً (خطوة قادمة)" else "Coming Soon",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Compact Founder & Support Card
        item {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                FvgFounderCard(isArabic = isArabic, compact = true)
            }
        }
    }

    inspectedSectorKey?.let { key ->
        FvgSectorCalculatorSheet(
            sectorKey = key,
            isArabic = isArabic,
            defaultCurrency = currency,
            onApplyPreset = {
                onApplySectorPreset(key)
            },
            onDismiss = {
                inspectedSectorKey = null
            },
            onGoToCosting = {
                onNavigateTab(2)
            }
        )
    }
}

@Composable
private fun OrganizationSetupCard(
    isArabic: Boolean,
    isBusy: Boolean,
    onCreate: (String, String, String) -> Unit
) {
    val s = fvgStrings(isArabic)
    var orgName by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("SDG") }
    var sectorType by remember { mutableStateOf("manufacturing") }

    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(s.createOrgPromptTitle, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(s.createOrgPromptBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        FvgField(
            value = orgName,
            onValueChange = { orgName = it },
            label = if (isArabic) "اسم المنظمة / الشركة" else "Organization Name",
            testTag = "setup_org_name_input"
        )
        Text(if (isArabic) "القطاع:" else "Business Sector:", style = MaterialTheme.typography.labelLarge)
        FvgChipSelector(
            options = FvgFinancialEngine.FVG_SECTORS.map { it.key },
            selected = sectorType,
            onSelect = { sectorType = it },
            labelResolver = { key ->
                val sec = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == key }
                if (sec == null) key else if (isArabic) sec.nameAr else sec.nameEn
            }
        )
        Text(if (isArabic) "العملة الأساسية:" else "Base Currency:", style = MaterialTheme.typography.labelLarge)
        FvgChipSelector(
            options = listOf("SDG", "USD", "SAR", "AED", "EUR", "EGP"),
            selected = currency,
            onSelect = { currency = it }
        )
        FvgPrimaryButton(
            text = if (isBusy) (if (isArabic) "جاري التهيئة..." else "Initializing...")
            else if (isArabic) "إنشاء المنظمة وتفعيل الدليل المحاسبي (تجربة 7 أيام)" else "Initialize Organization (7-Day Trial)",
            enabled = !isBusy && orgName.isNotBlank(),
            icon = Icons.Filled.Business,
            onClick = { onCreate(orgName, currency, sectorType) },
            testTag = "create_org_button"
        )
    }
}

@Composable
private fun KpiMiniCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    Surface(
        modifier = modifier
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
