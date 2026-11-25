package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.CostSnapshot
import com.example.data.model.ProductionOrderRecord
import com.example.data.model.StandardCostSheet
import com.example.domain.engine.FvgFinancialEngine
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import com.example.domain.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CostingScenariosScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenCalculator: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val s = fvgStrings(isArabic)
    var subTab by remember { mutableIntStateOf(0) }
    val defaultCurrency = state.activeOrg?.baseCurrency ?: "SDG"
    val currentPeriod = remember { SimpleDateFormat("yyyy-MM", Locale.US).format(Date()) }

    Column(modifier = Modifier.fillMaxSize().testTag("costing_scenarios_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "التكاليف والتركيبات والمخاطر" else "Costing, BOM, Risk & Scenarios",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.Calculate,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            orgName = state.activeOrg?.name,
            roleBadge = state.currentUserRole,
            onCalculatorClick = onOpenCalculator,
            onHelpClick = onOpenHelp,
            onProfileClick = onOpenProfile
        )
        FvgSubTabRow(
            tabs = listOf(s.tabProductCosting, s.tabCostingCvp, s.tabStandardsProd, s.tabWhatIfScenarios),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "costing"
        )

        when (subTab) {
            0 -> ProductCostingTab(state, viewModel, isArabic, currentPeriod, defaultCurrency)
            1 -> CvpAndRiskTab(state, isArabic, defaultCurrency)
            2 -> StandardsAndVarianceTab(state, viewModel, isArabic, currentPeriod, defaultCurrency)
            3 -> WhatIfScenariosTab(state, viewModel, isArabic)
        }
    }
}

@Composable
private fun ProductCostingTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    defaultPeriod: String,
    defaultCurrency: String
) {
    var selectedProductId by remember { mutableStateOf("") }
    var productName by remember { mutableStateOf("") }
    var period by remember { mutableStateOf(defaultPeriod) }
    var method by remember { mutableStateOf("actual") }
    var directMat by remember { mutableStateOf("0") }
    var indirectMat by remember { mutableStateOf("0") }
    var directLab by remember { mutableStateOf("0") }
    var indirectLab by remember { mutableStateOf("0") }
    var overhead by remember { mutableStateOf("0") }
    var energyFuel by remember { mutableStateOf("0") }
    var packaging by remember { mutableStateOf("0") }
    var transport by remember { mutableStateOf("0") }
    var wasteScrap by remember { mutableStateOf("0") }
    var fixedCosts by remember { mutableStateOf("0") }
    var goodUnits by remember { mutableStateOf("100") }
    var sellingPrice by remember { mutableStateOf("0") }
    var completedEquivUnitsStr by remember { mutableStateOf("100") }

    var targetPct by remember { mutableStateOf("20") }
    var abcPool by remember { mutableStateOf("1000") }
    var abcTotalDriver by remember { mutableStateOf("100") }
    var abcSelectedDriver by remember { mutableStateOf("25") }

    // BOM / Recipe Formula Builder State
    var bomName by remember { mutableStateOf("") }
    var bomUnit by remember(isArabic) { mutableStateOf(if (isArabic) "قطعة" else "pcs") }
    var bomBatchSize by remember { mutableStateOf("100") }
    var bomScrapPct by remember { mutableStateOf("3") }
    var ing1Name by remember(isArabic) { mutableStateOf(if (isArabic) "مادة أساسية" else "Primary Material") }
    var ing1Qty by remember { mutableStateOf("50") }
    var ing1UnitCost by remember { mutableStateOf("120") }
    var ing2Name by remember(isArabic) { mutableStateOf(if (isArabic) "تغليف" else "Packaging") }
    var ing2Qty by remember { mutableStateOf("100") }
    var ing2UnitCost by remember { mutableStateOf("15") }
    var bomNotes by remember { mutableStateOf("") }

    val bomPreview = remember(bomBatchSize, bomScrapPct, ing1Name, ing1Qty, ing1UnitCost, ing2Name, ing2Qty, ing2UnitCost) {
        runCatching {
            val batchSizeVal = FvgFinancialEngine.d(bomBatchSize).also { require(it > BigDecimal.ZERO) }
            val scrapPctVal = FvgFinancialEngine.dOrZero(bomScrapPct)
            val batchBreakdown = FvgFinancialEngine.calculateBomUnitCost(
                lines = listOf(
                    FvgFinancialEngine.BomLineInput(ing1Name, FvgFinancialEngine.dOrZero(ing1Qty), FvgFinancialEngine.dOrZero(ing1UnitCost), scrapPctVal),
                    FvgFinancialEngine.BomLineInput(ing2Name, FvgFinancialEngine.dOrZero(ing2Qty), FvgFinancialEngine.dOrZero(ing2UnitCost), scrapPctVal)
                )
            )
            val batchCost = batchBreakdown.effectiveMaterialCostPerUnit
            val unitCost = FvgFinancialEngine.money(batchCost.divide(batchSizeVal, FvgFinancialEngine.MC))
            batchCost to unitCost
        }.getOrNull()
    }

    val liveInput = remember(
        directMat, indirectMat, directLab, indirectLab, overhead, energyFuel,
        packaging, transport, wasteScrap, fixedCosts, goodUnits, sellingPrice
    ) {
        runCatching {
            FvgFinancialEngine.CostingInput(
                directMaterial = FvgFinancialEngine.dOrZero(directMat),
                indirectMaterial = FvgFinancialEngine.dOrZero(indirectMat),
                directLabor = FvgFinancialEngine.dOrZero(directLab),
                indirectLabor = FvgFinancialEngine.dOrZero(indirectLab),
                overhead = FvgFinancialEngine.dOrZero(overhead),
                energyFuel = FvgFinancialEngine.dOrZero(energyFuel),
                packaging = FvgFinancialEngine.dOrZero(packaging),
                transport = FvgFinancialEngine.dOrZero(transport),
                wasteScrap = FvgFinancialEngine.dOrZero(wasteScrap),
                fixedCosts = FvgFinancialEngine.dOrZero(fixedCosts),
                goodUnits = FvgFinancialEngine.d(goodUnits),
                sellingPrice = FvgFinancialEngine.dOrZero(sellingPrice)
            )
        }.getOrNull()
    }

    val targetMarginDecimal = remember(targetPct) {
        runCatching { FvgFinancialEngine.d(targetPct) }.getOrDefault(BigDecimal("20"))
    }
    val completedEquivDecimal = remember(completedEquivUnitsStr, method) {
        if (method == "process") runCatching { FvgFinancialEngine.d(completedEquivUnitsStr) }.getOrNull() else null
    }

    val methodOutcome = remember(liveInput, method, targetMarginDecimal, completedEquivDecimal) {
        liveInput?.let {
            runCatching {
                FvgFinancialEngine.calculateCostingByMethod(
                    method = method,
                    input = it,
                    targetMarginPct = targetMarginDecimal,
                    completedEquivUnits = completedEquivDecimal
                )
            }.getOrNull()
        }
    }

    val latestInfPct = state.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
        runCatching { FvgFinancialEngine.d(it) }.getOrNull()
    }
    val liveRisk = remember(methodOutcome, latestInfPct, state.statementsReport) {
        methodOutcome?.coreResult?.let { res ->
            FvgFinancialEngine.evaluateFinancialRisk(
                profitMarginPct = res.profitMarginPct,
                inflationRatePct = latestInfPct,
                totalDebt = state.statementsReport?.totalLiabilities ?: BigDecimal.ZERO,
                totalEquity = state.statementsReport?.totalEquityWithNetIncome ?: BigDecimal.ZERO
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val entitlements = state.activePlanEntitlements()
            FvgCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "حاسبة التكلفة والأساليب الـ 8 والمخاطر" else "8-Method Product Costing & Pricing Calculator",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FvgFieldHelpIcon(
                        title = if (isArabic) "أساليب التكاليف والهامش مقابل الإضافة" else "8 Costing Methods: Margin vs Markup",
                        explanation = if (isArabic)
                            "• الهامش % = (السعر − التكلفة) ÷ السعر.\n• الإضافة % = (السعر − التكلفة) ÷ التكلفة.\n• الباقة الأساسية تتيح (الفعلي والمعياري)، بينما الباقة الاحترافية والمؤسسات والتجربة تفتح جميع الأساليب الـ 8 والتركيبات."
                        else
                            "• Margin % = (Price − Cost) / Price.\n• Markup % = (Price − Cost) / Cost.\n• Starter allows Actual & Standard; Professional, Enterprise & Trial unlock all 8 methods & BOM.",
                        isArabic = isArabic
                    )
                }
                if (state.products.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.products.map { it.id },
                        selected = selectedProductId,
                        onSelect = { id ->
                            selectedProductId = id
                            state.products.firstOrNull { it.id == id }?.let { productName = it.name }
                        },
                        labelResolver = { id -> state.products.firstOrNull { it.id == id }?.name ?: id }
                    )
                }
                FvgField(productName, { productName = it }, if (isArabic) "اسم المنتج" else "Product Name", testTag = "costing_product_name_input")
                Text(
                    text = if (isArabic) "أسلوب التكلفة (8 أساليب محاسبية):" else "Costing Method (8 Management Accounting Methods):",
                    style = MaterialTheme.typography.labelLarge
                )
                if (!entitlements.allowAllCostingMethods) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic)
                                "ملاحظة الباقة الأساسية: يُسمح بالحفظ بطريقتي (الفعلي والمعياري). الأساليب المتقدمة تتطلب الباقة الاحترافية أو المؤسسات."
                            else
                                "Starter Tier Notice: Saving is enabled for Actual & Standard costing. Upgrade to Professional for all 8 methods.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Box(modifier = Modifier.testTag("costing_method_selector")) {
                    FvgChipSelector(
                        options = FvgFinancialEngine.ALL_COSTING_METHODS,
                        selected = method,
                        onSelect = { method = it },
                        labelResolver = { m ->
                            val lockedSuffix = if (!entitlements.allowAllCostingMethods && m !in setOf("actual", "standard")) " 🔒" else ""
                            val label = when (m) {
                                "actual" -> if (isArabic) "فعلي" else "Actual"
                                "standard" -> if (isArabic) "معياري" else "Standard"
                                "target" -> if (isArabic) "مستهدف" else "Target"
                                "abc" -> if (isArabic) "الأنشطة" else "ABC"
                                "job_order" -> if (isArabic) "أوامر إنتاج" else "Job Order"
                                "process" -> if (isArabic) "مراحل" else "Process"
                                "absorption" -> if (isArabic) "كلي" else "Absorption"
                                "variable" -> if (isArabic) "متغير" else "Variable"
                                else -> m.uppercase()
                            }
                            label + lockedSuffix
                        }
                    )
                }

                if (method == "process") {
                    FvgField(
                        value = completedEquivUnitsStr,
                        onValueChange = { completedEquivUnitsStr = it },
                        label = if (isArabic) "الوحدات المكافئة المكتملة" else "Completed Equivalent Units (Process)",
                        numeric = true,
                        testTag = "costing_equiv_units_input"
                    )
                }
                if (method == "target") {
                    FvgField(
                        value = targetPct,
                        onValueChange = { targetPct = it },
                        label = if (isArabic) "هامش الربح المستهدف %" else "Target Profit Margin % (Target Costing)",
                        numeric = true,
                        testTag = "costing_target_margin_input"
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(directMat, { directMat = it }, if (isArabic) "مواد مباشرة" else "Direct Material", numeric = true, modifier = Modifier.weight(1f), testTag = "costing_direct_mat_input")
                    FvgField(indirectMat, { indirectMat = it }, if (isArabic) "مواد غير مباشرة" else "Indirect Material", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(directLab, { directLab = it }, if (isArabic) "عمل مباشر" else "Direct Labor", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(indirectLab, { indirectLab = it }, if (isArabic) "عمل غير مباشر" else "Indirect Labor", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(overhead, { overhead = it }, if (isArabic) "تكاليف غير مباشرة" else "Overhead", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(energyFuel, { energyFuel = it }, if (isArabic) "طاقة ووقود" else "Energy & Fuel", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(packaging, { packaging = it }, if (isArabic) "تعبئة وتغليف" else "Packaging", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(transport, { transport = it }, if (isArabic) "نقل وتوزيع" else "Transport", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(wasteScrap, { wasteScrap = it }, if (isArabic) "هدر وتلف" else "Waste / Scrap", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(fixedCosts, { fixedCosts = it }, if (isArabic) "تكاليف ثابتة" else "Fixed Costs", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(goodUnits, { goodUnits = it }, if (isArabic) "الوحدات السليمة" else "Good Units", numeric = true, modifier = Modifier.weight(1f), testTag = "costing_units_input")
                    FvgField(sellingPrice, { sellingPrice = it }, if (isArabic) "سعر بيع الوحدة" else "Selling Price / Unit", numeric = true, modifier = Modifier.weight(1f), testTag = "costing_price_input")
                }

                if (methodOutcome != null) {
                    val liveResult = methodOutcome.coreResult
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(methodOutcome.methodNote, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.4f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "التكلفة الإجمالية" else "Total Cost", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("${liveResult.totalCost} $defaultCurrency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "تكلفة الوحدة (${methodOutcome.effectiveUnitsUsed})" else "Unit Cost (${methodOutcome.effectiveUnitsUsed} units)")
                                Text("${liveResult.unitCost} $defaultCurrency", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            if (methodOutcome.targetAllowableUnitCost != null && methodOutcome.targetCostGapPerUnit != null) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isArabic) "التكلفة المستهدفة المسموحة" else "Target Allowable Cost")
                                    Text("${methodOutcome.targetAllowableUnitCost} $defaultCurrency", fontWeight = FontWeight.SemiBold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (isArabic) "فجوة التكلفة المستهدفة" else "Target Cost Gap / Unit")
                                    val targetGap = methodOutcome.targetCostGapPerUnit
                                    Text(
                                        "$targetGap $defaultCurrency",
                                        fontWeight = FontWeight.Bold,
                                        color = if (targetGap != null && targetGap > BigDecimal.ZERO) MaterialTheme.colorScheme.error else FvgPositive
                                    )
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "هامش الربح %" else "Profit Margin % (of Price)")
                                Text("${liveResult.profitMarginPct ?: (if (isArabic) "غير متاح" else "N/A")}%", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "نسبة الإضافة %" else "Markup % (of Cost)")
                                Text("${liveResult.markupPct ?: (if (isArabic) "غير متاح" else "N/A")}%", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "نقطة التعادل (وحدات)" else "Break-Even (CEILING Units)")
                                Text("${liveResult.breakEvenUnits ?: (if (isArabic) "غير متاح" else "N/A")}", fontWeight = FontWeight.SemiBold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "صافي الربح" else "Net Profit", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    "${liveResult.netProfit} $defaultCurrency",
                                    fontWeight = FontWeight.Bold,
                                    color = if (liveResult.netProfit.signum() < 0) MaterialTheme.colorScheme.error else FvgPositive
                                )
                            }
                        }
                    }

                    if (liveRisk != null) {
                        val sigLevel = when (liveRisk.level.lowercase()) {
                            "high" -> "red"
                            "medium" -> "yellow"
                            else -> "green"
                        }
                        FvgStatusSignalBanner(
                            level = sigLevel,
                            title = if (isArabic) "${liveRisk.ruleName}: ${liveRisk.actualValue} (الحد ${liveRisk.thresholdValue})"
                            else "${liveRisk.ruleName}: ${liveRisk.actualValue} / ${liveRisk.thresholdValue}",
                            subtitle = liveRisk.explanation,
                            isArabic = isArabic,
                            modifier = Modifier.testTag("costing_risk_card")
                        )
                    }
                }

                FvgPrimaryButton(
                    text = if (isArabic) "حفظ حساب التكلفة" else "Save Calculation",
                    icon = Icons.Filled.Save,
                    enabled = state.canEditFinancialData && productName.isNotBlank() && liveInput != null && !state.isBusy,
                    onClick = {
                        liveInput?.let {
                            viewModel.saveCosting(
                                productId = selectedProductId,
                                productName = productName,
                                period = period,
                                currency = defaultCurrency,
                                method = method,
                                input = it,
                                completedEquivUnits = completedEquivDecimal,
                                targetMarginPct = targetMarginDecimal
                            )
                        }
                    },
                    testTag = "save_costing_button"
                )
            }
        }

        // BOM / Recipe Formula Builder
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "تركيبات المواد والوصفات (BOM) مع الهدر" else "Bill of Materials (BOM / Recipe Builder)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(bomName, { bomName = it }, if (isArabic) "اسم التركيبة / المنتج" else "Formula / Product Name", modifier = Modifier.weight(1.3f))
                    FvgField(bomUnit, { bomUnit = it }, if (isArabic) "وحدة الإنتاج" else "Output Unit", modifier = Modifier.weight(0.7f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(bomBatchSize, { bomBatchSize = it }, if (isArabic) "حجم التشغيلة" else "Batch Size", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(bomScrapPct, { bomScrapPct = it }, if (isArabic) "نسبة الهدر %" else "Scrap %", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FvgField(ing1Name, { ing1Name = it }, if (isArabic) "مكون 1" else "Ing. 1", modifier = Modifier.weight(1.3f))
                    FvgField(ing1Qty, { ing1Qty = it }, if (isArabic) "كمية" else "Qty", numeric = true, modifier = Modifier.weight(0.8f))
                    FvgField(ing1UnitCost, { ing1UnitCost = it }, if (isArabic) "سعر" else "Cost", numeric = true, modifier = Modifier.weight(0.9f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FvgField(ing2Name, { ing2Name = it }, if (isArabic) "مكون 2" else "Ing. 2", modifier = Modifier.weight(1.3f))
                    FvgField(ing2Qty, { ing2Qty = it }, if (isArabic) "كمية" else "Qty", numeric = true, modifier = Modifier.weight(0.8f))
                    FvgField(ing2UnitCost, { ing2UnitCost = it }, if (isArabic) "سعر" else "Cost", numeric = true, modifier = Modifier.weight(0.9f))
                }
                if (bomPreview != null) {
                    val (batchCostWithScrap, unitCostWithScrap) = bomPreview
                    Text(
                        text = if (isArabic) "تكلفة التشغيلة: $batchCostWithScrap $defaultCurrency  |  تكلفة الوحدة: $unitCostWithScrap $defaultCurrency"
                        else "Batch Cost: $batchCostWithScrap $defaultCurrency  |  Unit Cost: $unitCostWithScrap $defaultCurrency",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgGoldButton(
                        text = if (isArabic) "حفظ التركيبة" else "Save BOM",
                        enabled = state.canEditFinancialData && bomName.isNotBlank() && bomPreview != null && !state.isBusy,
                        onClick = {
                            bomPreview?.let { (batchCostWithScrap, unitCostWithScrap) ->
                                val ings = listOf(
                                    mapOf("name" to ing1Name, "unit" to "kg", "qty" to ing1Qty, "unitCost" to ing1UnitCost),
                                    mapOf("name" to ing2Name, "unit" to "pcs", "qty" to ing2Qty, "unitCost" to ing2UnitCost)
                                )
                                viewModel.saveBomFormula(
                                    existingId = null,
                                    productName = bomName,
                                    outputUnit = bomUnit,
                                    batchSize = bomBatchSize,
                                    ingredients = ings,
                                    unitMaterialCost = unitCostWithScrap.toPlainString(),
                                    scrapAllowancePct = bomScrapPct,
                                    notes = bomNotes
                                )
                                directMat = batchCostWithScrap.toPlainString()
                                bomName = ""
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    if (bomPreview != null) {
                        OutlinedButton(
                            onClick = { directMat = bomPreview.first.toPlainString() },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "تعبئة بالمباشر" else "Apply to Mat.")
                        }
                    }
                }
            }
        }

        items(state.bomFormulas, key = { "bom_${it.id}" }) { bom ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${bom.productName} (${bom.batchSize} ${bom.outputUnit})", style = MaterialTheme.typography.titleMedium)
                    if (state.canEditFinancialData) {
                        IconButton(onClick = { viewModel.deleteBomFormula(bom.id) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = if (isArabic) "حذف" else "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                Text(
                    text = if (isArabic) "تكلفة مواد الوحدة: ${bom.unitMaterialCost} $defaultCurrency • هدر ${bom.scrapAllowancePct}%"
                    else "Unit Material Cost: ${bom.unitMaterialCost} $defaultCurrency • Scrap ${bom.scrapAllowancePct}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Target Pricing & ABC Allocator Helpers
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "التسعير المستهدف وتوزيع التكاليف ABC" else "Target Pricing (Margin vs Markup) & ABC Allocator",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgField(targetPct, { targetPct = it }, if (isArabic) "نسبة الربح المستهدف %" else "Target Profit %", numeric = true)
                if (methodOutcome != null) {
                    val liveResult = methodOutcome.coreResult
                    val pPct = runCatching { FvgFinancialEngine.d(targetPct) }.getOrNull()
                    if (pPct != null) {
                        val byMargin = runCatching { FvgFinancialEngine.targetPriceByMargin(liveResult.unitCost, pPct).toPlainString() }.getOrElse { if (isArabic) "أقل من 100%" else "Invalid <100%" }
                        val byMarkup = runCatching { FvgFinancialEngine.targetPriceByMarkup(liveResult.unitCost, pPct).toPlainString() }.getOrElse { if (isArabic) "غير متاح" else "N/A" }
                        Text(
                            text = if (isArabic) "عند هامش $targetPct% ← سعر البيع $byMargin | عند إضافة $targetPct% ← سعر البيع $byMarkup"
                            else "At $targetPct% Margin -> Sell at $byMargin | At $targetPct% Markup -> Sell at $byMarkup",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(abcPool, { abcPool = it }, if (isArabic) "مجمع التكلفة" else "ABC Pool", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(abcTotalDriver, { abcTotalDriver = it }, if (isArabic) "إجمالي الموجه" else "Total Driver", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(abcSelectedDriver, { abcSelectedDriver = it }, if (isArabic) "موجه المنتج" else "Product Driver", numeric = true, modifier = Modifier.weight(1f))
                }
                val abcShare = runCatching {
                    FvgFinancialEngine.allocateAbcCost(
                        FvgFinancialEngine.d(abcPool),
                        listOf(FvgFinancialEngine.d(abcTotalDriver)),
                        FvgFinancialEngine.d(abcSelectedDriver)
                    )
                }.getOrNull()
                Text(
                    text = if (isArabic) "نصيب المنتج من التكلفة غير المباشرة ABC: ${abcShare ?: "موجه غير صالح"} $defaultCurrency"
                    else "Allocated ABC Overhead: ${abcShare ?: "Invalid driver"} $defaultCurrency",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        items(state.costSnapshots, key = { it.id }) { snap ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${snap.productName} (${snap.period})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        FvgRiskBadge(level = snap.riskLevel, isArabic = isArabic)
                        FvgRoleBadge(snap.costingMethod)
                    }
                }
                Text(
                    text = if (isArabic) "تكلفة الوحدة: ${snap.unitCost} | السعر: ${snap.sellingPrice} | الهامش: ${snap.profitMarginPct}% | الإضافة: ${snap.markupPct}%"
                    else "Unit Cost: ${snap.unitCost} | Price: ${snap.sellingPrice} | Margin: ${snap.profitMarginPct}% | Markup: ${snap.markupPct}%",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (isArabic) "نقطة التعادل: ${snap.breakEvenUnits} وحدة | صافي الربح: ${snap.netProfit} ${snap.currency}"
                    else "Break-Even: ${snap.breakEvenUnits} units | Total Net Profit: ${snap.netProfit} ${snap.currency}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CvpAndRiskTab(
    state: FvgUiState,
    isArabic: Boolean,
    currency: String
) {
    var mode by remember { mutableStateOf("cvp") }
    var selectedSnap by remember(state.costSnapshots) { mutableStateOf<CostSnapshot?>(state.costSnapshots.firstOrNull()) }

    val volatility = remember(state.costSnapshots) {
        val unitCosts = state.costSnapshots.mapNotNull { runCatching { FvgFinancialEngine.d(it.unitCost) }.getOrNull() }
        FvgFinancialEngine.computeCostVolatility(unitCosts)
    }
    val latestInfPct = state.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
        runCatching { FvgFinancialEngine.d(it) }.getOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgChipSelector(
                options = listOf("cvp", "profitability"),
                selected = mode,
                onSelect = { mode = it },
                labelResolver = {
                    if (it == "cvp") (if (isArabic) "تحليل التعادل CVP" else "CVP Analysis")
                    else (if (isArabic) "الربحية والمخاطر والتقلب" else "Profitability, Risk & Volatility")
                }
            )
        }

        if (state.costSnapshots.isEmpty()) {
            item {
                FvgEmptyStateCard(
                    if (isArabic) "احفظ لقطة تكلفة في التبويب الأول لعرض منحنى التعادل وتقييم المخاطر."
                    else "Save at least one Cost Calculation in 8 Costing Methods to render CVP curve, Margin of Safety, Risk & Volatility."
                )
            }
        } else {
            item {
                FvgCard {
                    Text(if (isArabic) "اختر لقطة التكلفة للتحليل" else "Select Cost Snapshot", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = state.costSnapshots.map { it.id },
                        selected = selectedSnap?.id ?: "",
                        onSelect = { id -> selectedSnap = state.costSnapshots.firstOrNull { it.id == id } },
                        labelResolver = { id -> state.costSnapshots.firstOrNull { it.id == id }?.let { "${it.productName} (${it.period})" } ?: id }
                    )

                    selectedSnap?.let { snap ->
                        val goodUnits = FvgFinancialEngine.dOrZero(snap.goodUnits).max(BigDecimal.ONE)
                        val fixed = FvgFinancialEngine.dOrZero(snap.fixedCosts)
                        val totCost = FvgFinancialEngine.dOrZero(snap.totalCost)
                        val varTotal = totCost.subtract(fixed).max(BigDecimal.ZERO)
                        val varUnit = varTotal.divide(goodUnits, FvgFinancialEngine.MC)
                        val price = FvgFinancialEngine.dOrZero(snap.sellingPrice)
                        val beUnits = snap.breakEvenUnits.toFloatOrNull()
                        val maxUnits = (beUnits?.let { BigDecimal(it.toDouble() * 1.8) } ?: goodUnits).max(goodUnits)

                        val risk = FvgFinancialEngine.evaluateFinancialRisk(
                            profitMarginPct = runCatching { FvgFinancialEngine.d(snap.profitMarginPct) }.getOrNull(),
                            inflationRatePct = latestInfPct,
                            totalDebt = state.statementsReport?.totalLiabilities ?: BigDecimal.ZERO,
                            totalEquity = state.statementsReport?.totalEquityWithNetIncome ?: BigDecimal.ZERO
                        )

                        val sigLevel = when (risk.level.lowercase()) {
                            "high" -> "red"
                            "medium" -> "yellow"
                            else -> "green"
                        }
                        FvgStatusSignalBanner(
                            level = sigLevel,
                            title = if (isArabic) "إشارة المخاطر والتعادل: ${snap.productName}" else "CVP & Risk Signal: ${snap.productName}",
                            subtitle = "${risk.ruleName}: ${risk.actualValue} — ${risk.explanation}",
                            isArabic = isArabic
                        )

                        if (mode == "cvp") {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(if (isArabic) "نقطة التعادل" else "Break-Even Point", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = if (isArabic) "${snap.breakEvenUnits} وحدة" else "${snap.breakEvenUnits} units",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    FvgRiskBadge(level = risk.level, isArabic = isArabic)
                                }
                            }
                            val curve = FvgFinancialEngine.generateCvpCurvePoints(fixed, varUnit, price, maxUnits)
                            FvgCvpChartCanvas(cvpPoints = curve, breakEvenUnits = beUnits, isArabic = isArabic)

                            val contribMarginPct = if (price > BigDecimal.ZERO) {
                                FvgFinancialEngine.pct4(price.subtract(varUnit).divide(price, FvgFinancialEngine.MC).multiply(BigDecimal(100))).toPlainString()
                            } else (if (isArabic) "غير متاح" else "N/A")
                            val mosPct = if (beUnits != null && goodUnits.toFloat() > 0f) {
                                val mos = ((goodUnits.toFloat() - beUnits) / goodUnits.toFloat()) * 100f
                                String.format("%.2f", mos)
                            } else (if (isArabic) "غير متاح" else "N/A")

                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(if (isArabic) "مؤشرات الأمان والهامش" else "Key CVP Metrics", style = MaterialTheme.typography.titleMedium)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (isArabic) "نسبة هامش المساهمة %" else "Contribution Margin %")
                                        Text("$contribMarginPct%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(if (isArabic) "هامش الأمان %" else "Margin of Safety %")
                                        Text("$mosPct%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Text("${risk.ruleName}: ${risk.explanation}", style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                                }
                            }
                        } else {
                            val trendPts = state.costSnapshots.takeLast(8).mapNotNull { cs ->
                                cs.unitCost.toFloatOrNull()?.let { cs.period to it }
                            }
                            if (trendPts.size >= 2) {
                                Text(if (isArabic) "تطور تكلفة الوحدة" else "Historical Unit Cost Trend", style = MaterialTheme.typography.titleMedium)
                                FvgSplineLineChartCanvas(points = trendPts)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "مجمل الربح" else "Gross Profit")
                                Text("${snap.grossProfit} $currency", fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "صافي الربح" else "Net Profit")
                                Text("${snap.netProfit} $currency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "الهامش مقابل الإضافة" else "Profit Margin % vs Markup %")
                                Text("${snap.profitMarginPct}% / ${snap.markupPct}%", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (volatility != null) {
                item {
                    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(if (isArabic) "تقلب تكلفة الوحدة" else "Unit Cost Volatility Index", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isArabic) "اللقطات: ${volatility.count} | الأدنى: ${volatility.minCost} | الأعلى: ${volatility.maxCost} | المتوسط: ${volatility.meanCost} $currency"
                            else "Snapshots: ${volatility.count} | Min: ${volatility.minCost} | Max: ${volatility.maxCost} | Mean: ${volatility.meanCost} $currency",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isArabic) "المدى: ${volatility.spreadPct}% | الانحراف المعياري: ${volatility.stdDev} $currency"
                            else "Spread: ${volatility.spreadPct}% | StdDev: ${volatility.stdDev} $currency",
                            style = MaterialTheme.typography.labelLarge,
                            color = FvgAntiqueGold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StandardsAndVarianceTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    currentPeriod: String,
    defaultCurrency: String
) {
    var stdProductName by remember { mutableStateOf("") }
    var stdMat by remember { mutableStateOf("10.00") }
    var stdHrs by remember { mutableStateOf("1.5") }
    var stdRate by remember { mutableStateOf("6.00") }
    var stdOvh by remember { mutableStateOf("4.00") }
    var stdNotes by remember(isArabic) { mutableStateOf(if (isArabic) "وصفة معيارية أساسية" else "Baseline standard recipe") }

    var poProductName by remember { mutableStateOf("") }
    var poPeriod by remember { mutableStateOf(currentPeriod) }
    var poPlanned by remember { mutableStateOf("100") }
    var poGood by remember { mutableStateOf("95") }
    var poScrap by remember { mutableStateOf("5") }
    var poActMat by remember { mutableStateOf("1050") }
    var poActLab by remember { mutableStateOf("890") }
    var poActOvh by remember { mutableStateOf("390") }

    var stdMatQtyPerUnit by remember { mutableStateOf("2") }
    var stdMatPrice by remember { mutableStateOf("5.00") }
    var actMatQty by remember { mutableStateOf("200") }
    var actMatPrice by remember { mutableStateOf("5.25") }
    var actLaborHrs by remember { mutableStateOf("145") }
    var actLaborRate by remember { mutableStateOf("6.15") }

    var selectedStd by remember(state.standardCosts) { mutableStateOf<StandardCostSheet?>(state.standardCosts.firstOrNull()) }
    var selectedPo by remember(state.productionOrders) { mutableStateOf<ProductionOrderRecord?>(state.productionOrders.firstOrNull()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(if (isArabic) "التكلفة المعيارية للمنتج" else "Standard Cost Sheet (Versioned)", style = MaterialTheme.typography.titleMedium)
                FvgField(stdProductName, { stdProductName = it }, if (isArabic) "اسم المنتج" else "Product Name", testTag = "std_product_input")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(stdMat, { stdMat = it }, if (isArabic) "مواد معيارية/وحدة" else "Std Material/Unit", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(stdOvh, { stdOvh = it }, if (isArabic) "غير مباشر معياري" else "Std Overhead/Unit", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(stdHrs, { stdHrs = it }, if (isArabic) "ساعات عمل معيارية" else "Std Labor Hrs", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(stdRate, { stdRate = it }, if (isArabic) "معدل أجر الساعة" else "Std Labor Rate", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(stdNotes, { stdNotes = it }, if (isArabic) "ملاحظات هندسية" else "Engineering Notes")
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ بطاقة التكلفة المعيارية" else "Save Standard Cost Sheet",
                    icon = Icons.Filled.Save,
                    enabled = state.canEditFinancialData && stdProductName.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createStandardCost("", stdProductName, stdMat, stdHrs, stdRate, stdOvh, stdNotes)
                        poProductName = stdProductName
                    },
                    testTag = "save_standard_cost_button"
                )
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "أمر الإنتاج والمراحل والهدر الفعلي" else "Production Order & Multi-Stage Tracking", style = MaterialTheme.typography.titleMedium)
                FvgField(poProductName, { poProductName = it }, if (isArabic) "اسم المنتج" else "Product Name", testTag = "po_product_input")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(poPeriod, { poPeriod = it }, if (isArabic) "الفترة" else "Period", modifier = Modifier.weight(0.9f))
                    FvgField(poPlanned, { poPlanned = it }, if (isArabic) "المخطط" else "Planned Units", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(poGood, { poGood = it }, if (isArabic) "السليم" else "Good Units", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(poScrap, { poScrap = it }, if (isArabic) "الهدر" else "Scrap Units", numeric = true, modifier = Modifier.weight(0.9f))
                    FvgField(poActMat, { poActMat = it }, if (isArabic) "مواد فعلية" else "Actual Material", numeric = true, modifier = Modifier.weight(1.1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(poActLab, { poActLab = it }, if (isArabic) "عمل فعلي" else "Actual Labor", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(poActOvh, { poActOvh = it }, if (isArabic) "غير مباشر فعلي" else "Actual Overhead", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgGoldButton(
                    text = if (isArabic) "تسجيل أمر إنتاج" else "Create Production Order",
                    icon = Icons.Filled.Factory,
                    enabled = state.canEditFinancialData && poProductName.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createProductionOrder("", poProductName, poPeriod, poPlanned, poGood, poScrap, poActMat, poActLab, poActOvh)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "create_po_button"
                )
            }
        }

        if (selectedStd != null && selectedPo != null) {
            item {
                val stdSheet = selectedStd!!
                val po = selectedPo!!
                val detailed = remember(stdSheet, po, stdMatQtyPerUnit, stdMatPrice, actMatQty, actMatPrice, actLaborHrs, actLaborRate) {
                    runCatching {
                        FvgFinancialEngine.computeDetailedVariance(
                            stdMaterialQtyPerUnit = FvgFinancialEngine.dOrZero(stdMatQtyPerUnit),
                            stdMaterialPrice = FvgFinancialEngine.dOrZero(stdMatPrice),
                            stdLaborHoursPerUnit = FvgFinancialEngine.dOrZero(stdSheet.standardLaborHours),
                            stdLaborRate = FvgFinancialEngine.dOrZero(stdSheet.standardLaborRate),
                            stdOverheadPerUnit = FvgFinancialEngine.dOrZero(stdSheet.standardOverheadPerUnit),
                            goodUnits = FvgFinancialEngine.d(po.goodUnits),
                            actualMaterialQty = FvgFinancialEngine.dOrZero(actMatQty),
                            actualMaterialPrice = FvgFinancialEngine.dOrZero(actMatPrice),
                            actualLaborHours = FvgFinancialEngine.dOrZero(actLaborHrs),
                            actualLaborRate = FvgFinancialEngine.dOrZero(actLaborRate),
                            actualOverheadCost = FvgFinancialEngine.dOrZero(po.actualOverheadCost)
                        )
                    }.getOrNull()
                }
                val yieldRes = FvgFinancialEngine.computeScrapYieldAnalysis(
                    plannedUnits = FvgFinancialEngine.d(po.plannedUnits),
                    goodUnits = FvgFinancialEngine.d(po.goodUnits),
                    recordedScrapUnits = FvgFinancialEngine.dOrZero(po.scrapUnits),
                    totalCost = FvgFinancialEngine.dOrZero(po.actualTotalCost)
                )
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = if (isArabic) "تحليل انحراف السعر والكمية وكفاءة العمل" else "Detailed Material & Labor Variance Decomposition",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(stdMatQtyPerUnit, { stdMatQtyPerUnit = it }, if (isArabic) "كمية معيارية/وحدة" else "Std Mat Qty/U", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(stdMatPrice, { stdMatPrice = it }, if (isArabic) "سعر مادة معياري" else "Std Mat Price", numeric = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(actMatQty, { actMatQty = it }, if (isArabic) "كمية مواد فعلية" else "Act Mat Qty", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(actMatPrice, { actMatPrice = it }, if (isArabic) "سعر مادة فعلي" else "Act Mat Price", numeric = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(actLaborHrs, { actLaborHrs = it }, if (isArabic) "ساعات عمل فعلية" else "Act Labor Hrs", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(actLaborRate, { actLaborRate = it }, if (isArabic) "أجر ساعة فعلي" else "Act Labor Rate", numeric = true, modifier = Modifier.weight(1f))
                    }
                    if (detailed != null) {
                        val v = detailed.summary
                        Text(
                            text = if (isArabic) "التكلفة المعيارية المسموحة: ${v.standardTotalAllowed} $defaultCurrency"
                            else "Standard Total Allowed: ${v.standardTotalAllowed} $defaultCurrency",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (isArabic) "انحراف سعر المواد: ${detailed.materialPriceVariance ?: "غير متاح"} | انحراف كمية المواد: ${detailed.materialUsageVariance ?: "غير متاح"}"
                            else "Mat Price Var: ${detailed.materialPriceVariance ?: "N/A"} | Mat Usage Var: ${detailed.materialUsageVariance ?: "N/A"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isArabic) "انحراف معدل الأجر: ${detailed.laborRateVariance ?: "غير متاح"} | انحراف الكفاءة: ${detailed.laborEfficiencyVariance ?: "غير متاح"}"
                            else "Labor Rate Var: ${detailed.laborRateVariance ?: "N/A"} | Labor Eff Var: ${detailed.laborEfficiencyVariance ?: "N/A"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isArabic) "إجمالي انحراف التكلفة: ${v.totalVariance ?: "غير مكتمل"} $defaultCurrency"
                            else "Total Cost Variance: ${v.totalVariance ?: "Incomplete"} $defaultCurrency",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if ((v.totalVariance ?: BigDecimal.ZERO) > BigDecimal.ZERO) MaterialTheme.colorScheme.error else FvgPositive
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = if (isArabic) "كفاءة الإنتاج: ${yieldRes.yieldPct}% | نسبة الهدر: ${yieldRes.scrapRatePct}%"
                        else "Yield: ${yieldRes.yieldPct}% | Scrap Rate: ${yieldRes.scrapRatePct}% (${yieldRes.recordedScrapUnits} units)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = if (isArabic) "تكلفة الوحدة السليمة: ${yieldRes.currentCostPerGoodUnit} | بعد الهدر: ${yieldRes.indicativeCostAfterScrap} $defaultCurrency"
                        else "Unit Cost (Good): ${yieldRes.currentCostPerGoodUnit} | Indicative After Scrap: ${yieldRes.indicativeCostAfterScrap} $defaultCurrency",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        items(state.productionOrders, key = { it.id }) { po ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${po.productName} (${po.period})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(po.status)
                }
                Text(
                    text = if (isArabic) "المخطط: ${po.plannedUnits} | السليم: ${po.goodUnits} | الهدر: ${po.scrapUnits} | الفعلي: ${po.actualTotalCost} ${po.currency}"
                    else "Planned: ${po.plannedUnits} | Good: ${po.goodUnits} | Scrap: ${po.scrapUnits} | Actual Total: ${po.actualTotalCost} ${po.currency}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (po.status == "in_progress" && state.canEditFinancialData) {
                    OutlinedButton(
                        onClick = {
                            viewModel.completeProductionOrder(po, po.scrapUnits, po.actualMaterialCost, po.actualLaborCost, po.actualOverheadCost)
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "إتمام مراحل الإنتاج" else "Complete Stages")
                    }
                }
            }
        }

        items(state.standardCosts, key = { it.id }) { std ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${std.productName} (${if (isArabic) "معياري" else "Std"} v${std.version})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge("${std.standardUnitCost} ${std.currency}")
                }
                Text(
                    text = if (isArabic) "مواد: ${std.standardMaterialCost} | عمل: ${std.standardLaborHours}س × ${std.standardLaborRate} | غير مباشر: ${std.standardOverheadPerUnit}"
                    else "Mat: ${std.standardMaterialCost} | Lab: ${std.standardLaborHours}h × ${std.standardLaborRate} | Ovh: ${std.standardOverheadPerUnit}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun WhatIfScenariosTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val s = fvgStrings(isArabic)
    val defaultFallbackSnapshot = remember(isArabic, state.activeOrg) {
        CostSnapshot(
            id = "demo_baseline",
            orgId = state.activeOrg?.id ?: "demo_org",
            productId = "demo_prod",
            productName = if (isArabic) "منتج نمطي افتراضي (دفعة 100 وحدة)" else "Standard Demo Product (100 units)",
            period = "Current",
            costingMethod = "actual",
            directMaterial = "5000",
            indirectMaterial = "500",
            directLabor = "2500",
            indirectLabor = "500",
            overhead = "1500",
            energyFuel = "500",
            packaging = "800",
            transport = "700",
            wasteScrap = "200",
            fixedCosts = "1000",
            goodUnits = "100",
            totalCost = "12700",
            unitCost = "127",
            sellingPrice = "175",
            revenue = "17500",
            grossProfit = "4800",
            netProfit = "3800",
            profitMarginPct = "21.7",
            markupPct = "29.9",
            breakEvenUnits = "21"
        )
    }

    val availableSnapshots = remember(state.costSnapshots, defaultFallbackSnapshot) {
        if (state.costSnapshots.isNotEmpty()) state.costSnapshots else listOf(defaultFallbackSnapshot)
    }
    var selectedSnap by remember(availableSnapshots) { mutableStateOf<CostSnapshot?>(availableSnapshots.firstOrNull()) }
    var selectedPresetId by remember { mutableStateOf("fx_shock_30") }
    var scenName by remember(isArabic) { mutableStateOf(if (isArabic) "صدمة صرف مفاجئة +30%" else "Sudden FX Shock +30%") }
    var scenType by remember { mutableStateOf("FX Shock +30%") }
    var matPct by remember { mutableStateOf("0") }
    var labPct by remember { mutableStateOf("0") }
    var ovhPct by remember { mutableStateOf("0") }
    var pricePct by remember { mutableStateOf("0") }
    var fxPct by remember { mutableStateOf("30") }
    var volumePct by remember { mutableStateOf("0") }

    val previewScenario = remember(selectedSnap, matPct, labPct, ovhPct, pricePct, fxPct, volumePct) {
        selectedSnap?.let { snap ->
            runCatching {
                val baseMat = FvgFinancialEngine.dOrZero(snap.directMaterial).add(FvgFinancialEngine.dOrZero(snap.indirectMaterial))
                val baseLab = FvgFinancialEngine.dOrZero(snap.directLabor).add(FvgFinancialEngine.dOrZero(snap.indirectLabor))
                val baseOther = FvgFinancialEngine.dOrZero(snap.totalCost).subtract(baseMat).subtract(baseLab).max(BigDecimal.ZERO)
                FvgFinancialEngine.runExtendedScenario(
                    baseMaterial = baseMat,
                    baseLabor = baseLab,
                    baseOverheadAndOther = baseOther,
                    baseRevenue = FvgFinancialEngine.dOrZero(snap.revenue),
                    materialChangePct = FvgFinancialEngine.dOrZero(matPct),
                    laborChangePct = FvgFinancialEngine.dOrZero(labPct),
                    overheadChangePct = FvgFinancialEngine.dOrZero(ovhPct),
                    priceChangePct = FvgFinancialEngine.dOrZero(pricePct),
                    fxChangePct = FvgFinancialEngine.dOrZero(fxPct),
                    volumeChangePct = FvgFinancialEngine.dOrZero(volumePct)
                )
            }.getOrNull()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Explanatory Guidance Banner with Help Icon (!)
        item {
            FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = if (isArabic) "محاكي السيناريوهات والقرارات (ماذا لو؟)" else "What-If Decision Simulator",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isArabic) "اختبر صدمات الصرف وتغير الأسعار والركود قبل تطبيقها على أرض الواقع" else "Stress-test FX, price hikes, inflation, and volume drops safely",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FvgFieldHelpIcon(
                        title = if (isArabic) "دليل محاكاة ماذا لو (What-If)" else "What-If Simulator Guide",
                        explanation = if (isArabic)
                            "أداة محاكاة افتراضية غير مدمّرة لا تؤثر على بياناتك الأصلية. تتيح لك الإجابة المسبقة عن:\n• كم سينخفض ربحي إذا هبط سعر الصرف بنسبة 30%؟\n• ماذا لو تراجع حجم المبيعات بنسبة 25%؟\n• كم يجب أن أرفع سعر البيع إذا قفزت أسعار المواد الخام بنسبة 35% للحفاظ على ربحي؟"
                        else
                            "A safe non-destructive sandbox. Answers questions like: How will a 30% currency drop or a 25% sales slump impact my bottom line before taking real-world risks?",
                        isArabic = isArabic
                    )
                }

                // 3 Instant High-Impact Scenario Shortcuts
                Text(
                    text = if (isArabic) "سيناريوهات صدمات السوق السريعة (بنقرة واحدة):" else "Instant Market Shock Presets (1-Click):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            selectedPresetId = "fx_shock_30"
                            scenName = if (isArabic) "صدمة صرف مفاجئة +30%" else "Sudden FX Shock +30%"
                            scenType = "FX Shock +30%"
                            matPct = "0"; labPct = "0"; ovhPct = "0"; pricePct = "0"; fxPct = "30"; volumePct = "0"
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Text(if (isArabic) "صدمة صرف +30%" else "FX +30%", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            selectedPresetId = "sales_drop_25"
                            scenName = if (isArabic) "ركود مبيعات انكماشي -25%" else "Sales Slump -25%"
                            scenType = "Sales Slump -25%"
                            matPct = "0"; labPct = "0"; ovhPct = "0"; pricePct = "0"; fxPct = "0"; volumePct = "-25"
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Text(if (isArabic) "ركود مبيعات -25%" else "Slump -25%", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            selectedPresetId = "mat_surge_35"
                            scenName = if (isArabic) "قفزة مواد خام +35%" else "Raw Material Surge +35%"
                            scenType = "Raw Material Surge +35%"
                            matPct = "35"; labPct = "0"; ovhPct = "0"; pricePct = "0"; fxPct = "0"; volumePct = "0"
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                    ) {
                        Text(if (isArabic) "قفزة مواد +35%" else "Material +35%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (state.scenarios.isNotEmpty() && selectedSnap != null) {
            item {
                val baseP = FvgFinancialEngine.dOrZero(selectedSnap!!.netProfit).toFloat()
                val s2 = state.scenarios.getOrNull(0)?.scenarioProfit?.toFloatOrNull() ?: baseP
                val s3 = state.scenarios.getOrNull(1)?.scenarioProfit?.toFloatOrNull() ?: previewScenario?.scenarioProfit?.toFloat() ?: baseP
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (isArabic) "مقارنة السيناريو 1 (الحالي) مقابل 2 و 3" else "Scenario 1 (Current) vs Scenario 2 vs Scenario 3", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgBarComparisonCanvas(
                        bars = listOf(
                            (if (isArabic) "الحالي" else "S1 Current") to baseP,
                            (if (isArabic) "سيناريو 2" else "S2 Saved") to s2,
                            (if (isArabic) "سيناريو 3" else "S3 Alt") to s3
                        )
                    )
                }
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "تخصيص مدخلات المحاكاة (التضخم، الصرف، الحجم، التسعير)" else "What-If Scenario Simulation Parameters",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = s.scenarioDisclaimer,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = FvgAntiqueGold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Text(
                    text = if (isArabic) "اختر المنتج المراد محاكاته:" else "Select Product for Simulation:",
                    style = MaterialTheme.typography.labelLarge
                )
                FvgChipSelector(
                    options = availableSnapshots.map { it.id },
                    selected = selectedSnap?.id ?: "",
                    onSelect = { id -> selectedSnap = availableSnapshots.firstOrNull { it.id == id } },
                    labelResolver = { id -> availableSnapshots.firstOrNull { it.id == id }?.productName ?: id }
                )
                Text(if (isArabic) "قوالب السيناريوهات القياسية:" else "Quick Scenario Presets:", style = MaterialTheme.typography.labelLarge)
                FvgChipSelector(
                    options = FvgFinancialEngine.SPEC_SCENARIO_PRESETS.map { it.id },
                    selected = selectedPresetId,
                    onSelect = { id ->
                        selectedPresetId = id
                        FvgFinancialEngine.SPEC_SCENARIO_PRESETS.firstOrNull { it.id == id }?.let { p ->
                            scenType = if (isArabic) p.labelAr else p.labelEn
                            scenName = if (isArabic) p.labelAr else p.labelEn
                            matPct = p.materialPct; labPct = p.laborPct; ovhPct = p.overheadPct
                            pricePct = p.pricePct; fxPct = p.fxPct; volumePct = p.volumePct
                        }
                    },
                    labelResolver = { id ->
                        val p = FvgFinancialEngine.SPEC_SCENARIO_PRESETS.first { it.id == id }
                        if (isArabic) p.labelAr else p.labelEn
                    }
                )

                FvgField(scenName, { scenName = it }, if (isArabic) "اسم السيناريو" else "Scenario Name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(matPct, { matPct = it }, if (isArabic) "تغير المواد %" else "Material Δ %", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(labPct, { labPct = it }, if (isArabic) "تغير العمل %" else "Labor Δ %", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(ovhPct, { ovhPct = it }, if (isArabic) "غير المباشر %" else "Overhead Δ %", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(pricePct, { pricePct = it }, if (isArabic) "سعر البيع %" else "Selling Price Δ %", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(fxPct, { fxPct = it }, if (isArabic) "صدمة الصرف %" else "FX Shock Δ %", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(volumePct, { volumePct = it }, if (isArabic) "حجم المبيعات %" else "Volume Δ %", numeric = true, modifier = Modifier.weight(1f))
                }

                if (previewScenario != null) {
                    FvgBarComparisonCanvas(
                        bars = listOf(
                            (if (isArabic) "تكلفة أساس" else "Base Cost") to previewScenario.baseTotalCost.toFloat(),
                            (if (isArabic) "تكلفة متوقعة" else "Scen. Cost") to previewScenario.scenarioTotalCost.toFloat(),
                            (if (isArabic) "ربح أساس" else "Base Profit") to previewScenario.baseProfit.toFloat(),
                            (if (isArabic) "ربح السيناريو" else "Scen. Profit") to previewScenario.scenarioProfit.toFloat()
                        )
                    )
                    Text(
                        text = if (isArabic) "أثر الربح: ${previewScenario.profitDelta} (أثر التكلفة: ${previewScenario.costDelta})"
                        else "Profit Impact: ${previewScenario.profitDelta} (Cost Δ: ${previewScenario.costDelta})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (previewScenario.profitDelta.signum() < 0) MaterialTheme.colorScheme.error else FvgPositive
                    )
                }
                FvgGoldButton(
                    text = if (isArabic) "حفظ السيناريو" else "Save What-If Scenario",
                    enabled = state.canEditFinancialData && selectedSnap != null && scenName.isNotBlank() && !state.isBusy,
                    onClick = {
                        selectedSnap?.let {
                            viewModel.saveScenario(it, scenName, matPct, labPct, ovhPct, pricePct, scenType, fxPct, volumePct)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(state.scenarios, key = { it.id }) { scn ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(scn.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FvgRoleBadge(scn.scenarioType)
                }
                Text(
                    text = if (isArabic) "المدخلات: مواد ${scn.materialChangePct}% | عمل ${scn.laborChangePct}% | غير مباشر ${scn.overheadChangePct}% | سعر ${scn.priceChangePct}% | صرف ${scn.fxChangePct}%"
                    else "Inputs: Mat ${scn.materialChangePct}% | Lab ${scn.laborChangePct}% | Ovh ${scn.overheadChangePct}% | Price ${scn.priceChangePct}% | FX ${scn.fxChangePct}% | Vol ${scn.volumeChangePct}%",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = if (isArabic) "الربح الأساسي: ${scn.baseProfit} -> ربح السيناريو: ${scn.scenarioProfit} (${s.scenarioDisclaimer})"
                    else "Base Profit: ${scn.baseProfit} -> Scenario Profit: ${scn.scenarioProfit} (${s.scenarioDisclaimer})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
