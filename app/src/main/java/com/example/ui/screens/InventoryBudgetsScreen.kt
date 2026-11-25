package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.InventoryItemRecord
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

private fun localizeInvType(code: String, isArabic: Boolean): String = if (!isArabic) code.replace("_", " ").uppercase() else when (code) {
    "raw_material" -> "مادة خام"
    "finished_good" -> "منتج تام"
    "fifo" -> "الوارد أولاً صادر أولاً (FIFO)"
    "moving_avg" -> "المتوسط المرجح المتحرك"
    "receipt" -> "استلام"
    "issue" -> "صرف"
    "transfer" -> "تحويل"
    "return" -> "مرتجع"
    "adjustment" -> "تسوية"
    "scrap" -> "هدر"
    "damaged" -> "تالف"
    "expired" -> "منتهي الصلاحية"
    "operating" -> "تشغيلية"
    "annual" -> "سنوية"
    "monthly" -> "شهرية"
    "revenue" -> "إيرادات"
    "expense" -> "مصروفات"
    "cash" -> "نقدية"
    "in_review" -> "قيد المراجعة"
    "posted" -> "مرحّل"
    "approved" -> "معتمد"
    "rejected" -> "مرفوض"
    "pending_approval" -> "بانتظار الاعتماد"
    else -> code
}

@Composable
fun InventoryBudgetsScreen(
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
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val defaultCurrency = state.activeOrg?.baseCurrency ?: "SDG"

    Column(modifier = Modifier.fillMaxSize().testTag("inventory_budgets_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "المخزون والجرد والهدر والموازنات" else "Inventory, Scrap & Budgets",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.Inventory2,
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
            tabs = listOf(s.tabInventoryValuation, s.tabStocktaking, s.tabScrapLosses, s.tabBudgets),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "inventory"
        )

        when (subTab) {
            0 -> InventoryValuationTab(state, viewModel, isArabic, today, defaultCurrency)
            1 -> StocktakingTab(state, viewModel, isArabic, today)
            2 -> ScrapLossesTab(state, viewModel, isArabic, today, defaultCurrency)
            3 -> BudgetsTab(state, viewModel, isArabic, today, defaultCurrency)
        }
    }
}

@Composable
private fun InventoryValuationTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var sku by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var itemType by remember { mutableStateOf("raw_material") }
    var unit by remember { mutableStateOf(if (isArabic) "كجم" else "kg") }
    var warehouse by remember { mutableStateOf(if (isArabic) "المستودع الرئيسي" else "Main Warehouse") }
    var method by remember { mutableStateOf("fifo") }
    var openQty by remember { mutableStateOf("100") }
    var openCost by remember { mutableStateOf("2.50") }
    var marketPrice by remember { mutableStateOf("3.00") }
    var batchNumber by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }

    var selectedItem by remember(state.inventoryItems) { mutableStateOf<InventoryItemRecord?>(state.inventoryItems.firstOrNull()) }
    var moveType by remember { mutableStateOf("receipt") }
    var moveQty by remember { mutableStateOf("") }
    var moveCost by remember { mutableStateOf("") }
    var moveReason by remember { mutableStateOf(if (isArabic) "حركة مخزنية قياسية" else "Standard stock movement") }
    var postAutoJe by remember { mutableStateOf(true) }

    val thirtyDaysLimit = remember(state.serverAnchorMillis) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(state.estimatedServerNowMillis() + 30L * 86400_000L))
    }

    val latestInfRate = state.inflationRecords.firstOrNull()?.monthlyRatePct?.let {
        runCatching { BigDecimal.ONE.add(FvgFinancialEngine.d(it).divide(BigDecimal(100))) }.getOrNull()
    } ?: BigDecimal.ONE

    val replacementTotals = remember(state.inventoryItems, latestInfRate) {
        FvgFinancialEngine.analyzeReplacementCost(
            quantityOnHand = BigDecimal.ONE,
            bookValue = state.totalInventoryBookValue,
            currentMarketPrice = state.totalReplacementCost,
            inflationFactor = latestInfRate
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            val invSignal = when {
                state.inventoryItems.any { it.expiryDate.isNotBlank() && it.expiryDate <= thirtyDaysLimit } -> "red"
                replacementTotals.replacementGap > BigDecimal.ZERO && state.totalInventoryBookValue > BigDecimal.ZERO &&
                    replacementTotals.replacementGap > state.totalInventoryBookValue.multiply(BigDecimal("0.20")) -> "red"
                replacementTotals.replacementGap > BigDecimal.ZERO -> "yellow"
                else -> "green"
            }
            FvgStatusSignalBanner(
                level = invSignal,
                title = when (invSignal) {
                    "red" -> if (isArabic) "تنبيه حرج: فجوة استبدال عالية أو دفعات قريبة الانتهاء" else "Critical: Large Replacement Gap or Expiring Stock"
                    "yellow" -> if (isArabic) "انتباه: تكلفة الاستبدال تفوق القيمة الدفترية" else "Caution: Replacement Cost Exceeds Book Value"
                    else -> if (isArabic) "وضع المخزون والصلاحية آمن" else "Inventory Valuation & Expiry Healthy"
                },
                subtitle = if (isArabic)
                    "القيمة الدفترية: ${replacementTotals.bookValue} $defaultCurrency • الاستبدال: ${replacementTotals.currentReplacementCost} $defaultCurrency • الفجوة: ${replacementTotals.replacementGap} $defaultCurrency"
                else
                    "Book: ${replacementTotals.bookValue} $defaultCurrency • Replacement: ${replacementTotals.currentReplacementCost} $defaultCurrency • Gap: ${replacementTotals.replacementGap} $defaultCurrency",
                isArabic = isArabic
            )
        }

        item {
            FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "القيمة الدفترية مقابل تكلفة الاستبدال والتضخم" else "Inventory Book Value vs Replacement Cost (Separate from Ledger)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FvgFieldHelpIcon(
                        title = if (isArabic) "الفرق بين FIFO والمتوسط المرجح وتكلفة الاستبدال" else "FIFO, Moving Average & Replacement Cost",
                        explanation = if (isArabic)
                            "• الوارد أولاً صادر أولاً (FIFO): يصرف الدفعات الأقدم أولاً.\n• المتوسط المرجح: يحدث متوسط التكلفة مع كل استلام.\n• تكلفة الاستبدال تحذرك من فجوة رأس المال المطلوبة لإعادة شراء نفس الكمية عند التضخم دون تعديل الدفتر."
                        else
                            "• FIFO consumes earliest layers first; Moving Average recalculates avg unit cost on every receipt.\n• Replacement Cost highlights inflation capital erosion separately from accounting book value.",
                        isArabic = isArabic
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "القيمة الدفترية (FIFO/MWA)" else "Accounting Book Value:", color = MaterialTheme.colorScheme.onSurface)
                    Text("${replacementTotals.bookValue} $defaultCurrency", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "تكلفة الاستبدال الحالية" else "Current Replacement Cost:", color = MaterialTheme.colorScheme.onSurface)
                    Text("${replacementTotals.currentReplacementCost} $defaultCurrency", fontWeight = FontWeight.Bold, color = FvgAntiqueGold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "القيمة المعدلة بالتضخم" else "Inflation-Adjusted Value:", color = MaterialTheme.colorScheme.onSurface)
                    Text("${replacementTotals.inflationAdjustedCost} $defaultCurrency", fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (isArabic) "فجوة رأس المال المطلوب للاستبدال" else "Replacement Capital Gap:", color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${replacementTotals.replacementGap} $defaultCurrency (${replacementTotals.capitalTiedUpPctIncrease ?: "0.00"}%)",
                        fontWeight = FontWeight.Bold,
                        color = if (replacementTotals.replacementGap > BigDecimal.ZERO) MaterialTheme.colorScheme.error else FvgPositive
                    )
                }
                if (state.inventoryItems.isNotEmpty()) {
                    FvgBarComparisonCanvas(
                        bars = listOf(
                            (if (isArabic) "دفتري" else "Book Value") to replacementTotals.bookValue.toFloat(),
                            (if (isArabic) "استبدال" else "Replacement") to replacementTotals.currentReplacementCost.toFloat(),
                            (if (isArabic) "معدّل" else "Infl. Adj") to replacementTotals.inflationAdjustedCost.toFloat()
                        )
                    )
                }
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "تسجيل صنف مخزني / تشغيلة جديدة" else "Register Inventory Item & Batch", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                FvgChipSelector(listOf("raw_material", "finished_good"), itemType, { itemType = it }, labelResolver = { localizeInvType(it, isArabic) })
                FvgChipSelector(listOf("fifo", "moving_avg"), method, { method = it }, labelResolver = { localizeInvType(it, isArabic) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(sku, { sku = it }, if (isArabic) "رمز الصنف (SKU)" else "SKU", modifier = Modifier.weight(0.8f), testTag = "inv_sku_input")
                    FvgField(name, { name = it }, if (isArabic) "اسم الصنف" else "Item Name", modifier = Modifier.weight(1.4f), testTag = "inv_name_input")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(unit, { unit = it }, if (isArabic) "الوحدة" else "Unit", modifier = Modifier.weight(0.8f))
                    FvgField(warehouse, { warehouse = it }, if (isArabic) "المستودع" else "Warehouse", modifier = Modifier.weight(1.2f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(openQty, { openQty = it }, if (isArabic) "الكمية الافتتاحية" else "Opening Qty", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(openCost, { openCost = it }, if (isArabic) "تكلفة الوحدة" else "Unit Cost", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(marketPrice, { marketPrice = it }, if (isArabic) "سعر السوق" else "Market Price", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(batchNumber, { batchNumber = it }, if (isArabic) "رقم التشغيلة (Batch)" else "Batch Number (opt.)", modifier = Modifier.weight(1f))
                    FvgField(expiryDate, { expiryDate = it }, if (isArabic) "انتهاء الصلاحية YYYY-MM-DD" else "Expiry YYYY-MM-DD (opt.)", modifier = Modifier.weight(1f))
                }
                FvgPrimaryButton(
                    text = if (isArabic) "إضافة الصنف المخزني" else "Create Inventory Item",
                    enabled = state.canEditFinancialData && sku.isNotBlank() && name.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createInventoryItem(
                            sku, name, itemType, unit, warehouse, method,
                            openQty, openCost, marketPrice,
                            batchNumber.trim(), expiryDate.trim()
                        )
                        sku = ""; name = ""; batchNumber = ""; expiryDate = ""
                    },
                    testTag = "create_inv_item_button"
                )
            }
        }

        if (state.inventoryItems.isNotEmpty()) {
            item {
                FvgCard {
                    Text(if (isArabic) "تسجيل حركة مخزنية وترحيل قيد تلقائي" else "Record Stock Movement (Receipt / Issue / Transfer)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgChipSelector(
                        options = state.inventoryItems.map { it.id },
                        selected = selectedItem?.id ?: "",
                        onSelect = { id -> selectedItem = state.inventoryItems.firstOrNull { it.id == id } },
                        labelResolver = { id -> state.inventoryItems.firstOrNull { it.id == id }?.name ?: id }
                    )
                    FvgChipSelector(
                        options = listOf("receipt", "issue", "transfer", "return", "adjustment"),
                        selected = moveType,
                        onSelect = { moveType = it },
                        labelResolver = { localizeInvType(it, isArabic) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(moveQty, { moveQty = it }, if (isArabic) "الكمية" else "Quantity", numeric = true, modifier = Modifier.weight(1f))
                        FvgField(moveCost, { moveCost = it }, if (isArabic) "تكلفة الوحدة (للاستلام)" else "Unit Cost (if receipt)", numeric = true, modifier = Modifier.weight(1f))
                    }
                    FvgField(moveReason, { moveReason = it }, if (isArabic) "السبب / المرجع" else "Reason / Reference")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = postAutoJe, onCheckedChange = { postAutoJe = it })
                        Text(if (isArabic) "ترحيل قيد محاسبي مزدوج تلقائياً" else "Auto-post double-entry accounting journal", style = MaterialTheme.typography.bodyMedium)
                    }
                    FvgGoldButton(
                        text = if (isArabic) "تأكيد الحركة المخزنية" else "Post Inventory Movement",
                        enabled = state.canEditFinancialData && selectedItem != null && moveQty.isNotBlank() && !state.isBusy,
                        onClick = {
                            selectedItem?.let { item ->
                                viewModel.recordInventoryMovement(
                                    item, moveType, moveQty, moveCost.ifBlank { item.movingAvgCost },
                                    item.warehouse, moveReason, "MOV-$today", today, postAutoJe
                                )
                                moveQty = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(state.inventoryItems, key = { it.id }) { item ->
            val isNearExpiry = item.expiryDate.isNotBlank() && item.expiryDate <= thirtyDaysLimit
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${item.sku} • ${item.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    FvgRoleBadge(localizeInvType(item.valuationMethod, isArabic))
                }
                Text(
                    text = if (isArabic) "الكمية المتاحة: ${item.quantityOnHand} ${item.unit} (${item.warehouse})" else "Qty on Hand: ${item.quantityOnHand} ${item.unit} (${item.warehouse})",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = if (isArabic) "القيمة الدفترية: ${item.totalBookValue} ${item.currency} | متوسط التكلفة: ${item.movingAvgCost} | سعر السوق: ${item.currentMarketPrice}"
                    else "Book Value: ${item.totalBookValue} ${item.currency} | Avg Cost: ${item.movingAvgCost} | Market Price: ${item.currentMarketPrice}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (item.batchNumber.isNotBlank() || item.expiryDate.isNotBlank()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (item.batchNumber.isNotBlank()) {
                            Text(
                                text = if (isArabic) "تشغيلة: ${item.batchNumber}" else "Batch: ${item.batchNumber}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        if (item.expiryDate.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isNearExpiry) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = if (isNearExpiry) {
                                        if (isArabic) "⚠ صلاحية قريبة/منتهية: ${item.expiryDate}" else "⚠ Expiring/Expired: ${item.expiryDate}"
                                    } else {
                                        if (isArabic) "انتهاء الصلاحية: ${item.expiryDate}" else "Expiry: ${item.expiryDate}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isNearExpiry) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.inventoryMovements.isNotEmpty()) {
            item {
                Text(
                    text = if (isArabic) "سجل الحركات المخزنية (${state.inventoryMovements.size})" else "Stock Movement History Ledger (${state.inventoryMovements.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            items(state.inventoryMovements.reversed().take(20), key = { it.id }) { mv ->
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${mv.itemName} (${mv.movementDate})", style = MaterialTheme.typography.titleMedium)
                        FvgRoleBadge(localizeInvType(mv.movementType, isArabic))
                    }
                    Text(
                        text = if (isArabic) "الكمية: ${mv.quantity} × ${mv.unitCost} = الإجمالي ${mv.totalCost} $defaultCurrency • ${mv.warehouse}"
                        else "Qty: ${mv.quantity} @ ${mv.unitCost} = Total ${mv.totalCost} $defaultCurrency • ${mv.warehouse}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("${mv.referenceNo} — ${mv.reason}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StocktakingTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String
) {
    var sessionName by remember { mutableStateOf(if (isArabic) "الجرد الفعلي الشهري" else "Monthly Physical Count") }
    var warehouse by remember { mutableStateOf(if (isArabic) "المستودع الرئيسي" else "Main Warehouse") }
    val countInputs = remember { mutableStateMapOf<String, String>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (state.inventoryItems.isEmpty()) {
                FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords)
            } else {
                FvgCard {
                    Text(if (isArabic) "بدء جلسة جرد فعلي ومقارنتها بالنظام" else "Physical Stocktake Session (Draft -> Review -> Post)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(sessionName, { sessionName = it }, if (isArabic) "اسم الجلسة" else "Session Name", modifier = Modifier.weight(1.3f))
                        FvgField(warehouse, { warehouse = it }, if (isArabic) "المستودع" else "Warehouse", modifier = Modifier.weight(1f))
                    }
                    state.inventoryItems.take(8).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1.2f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isArabic) "رصيد النظام: ${item.quantityOnHand} ${item.unit}" else "System: ${item.quantityOnHand} ${item.unit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FvgField(
                                value = countInputs[item.id] ?: item.quantityOnHand,
                                onValueChange = { countInputs[item.id] = it },
                                label = if (isArabic) "الجرد الفعلي" else "Physical",
                                numeric = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                    FvgPrimaryButton(
                        text = if (isArabic) "إرسال الجرد للمراجعة" else "Submit Stocktake for Review",
                        enabled = state.canEditFinancialData && sessionName.isNotBlank() && !state.isBusy,
                        onClick = {
                            val counts = state.inventoryItems.associate { it.id to (countInputs[it.id] ?: it.quantityOnHand) }
                            viewModel.createStocktake(sessionName, warehouse, today, state.inventoryItems, counts)
                        }
                    )
                }
            }
        }

        items(state.stocktakes.reversed(), key = { it.id }) { stk ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stk.sessionName} (${stk.sessionDate})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge(localizeInvType(stk.status, isArabic))
                }
                Text(
                    text = if (isArabic) "المستودع: ${stk.warehouse} • قيمة الفروقات: ${stk.totalDiscrepancyValue}"
                    else "Warehouse: ${stk.warehouse} • Discrepancy Value: ${stk.totalDiscrepancyValue}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (stk.status != "posted" && state.canAdminOrg) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgGoldButton(
                            text = if (isArabic) "ترحيل التسوية والقيد" else "Approve & Post Adjustment",
                            onClick = { viewModel.processStocktakeDecision(stk, "posted") },
                            modifier = Modifier.weight(1f)
                        )
                        if (stk.status == "in_review") {
                            OutlinedButton(
                                onClick = { viewModel.processStocktakeDecision(stk, "rejected") },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text(if (isArabic) "رفض" else "Reject")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScrapLossesTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var selectedItem by remember(state.inventoryItems) { mutableStateOf<InventoryItemRecord?>(state.inventoryItems.firstOrNull()) }
    var lossType by remember { mutableStateOf("scrap") }
    var qtyStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var lossDate by remember { mutableStateOf(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            if (state.inventoryItems.isEmpty()) {
                FvgEmptyStateCard(if (isArabic) "سجل صنفاً مخزنياً أولاً لرفع طلبات الهدر والتوالف." else "Register an inventory item first before logging scrap or damage requests.")
            } else {
                FvgCard {
                    Text(if (isArabic) "طلب تسجيل هدر / تالف / منتهي الصلاحية" else "Submit Scrap, Damaged or Expired Loss (Requires Admin Approval)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgChipSelector(
                        options = state.inventoryItems.map { it.id },
                        selected = selectedItem?.id ?: "",
                        onSelect = { id -> selectedItem = state.inventoryItems.firstOrNull { it.id == id } },
                        labelResolver = { id -> state.inventoryItems.firstOrNull { it.id == id }?.name ?: id }
                    )
                    FvgChipSelector(listOf("scrap", "damaged", "expired"), lossType, { lossType = it }, labelResolver = { localizeInvType(it, isArabic) })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(qtyStr, { qtyStr = it }, if (isArabic) "كمية الهدر" else "Loss Quantity", numeric = true, modifier = Modifier.weight(1f), testTag = "scrap_qty_input")
                        FvgField(lossDate, { lossDate = it }, if (isArabic) "تاريخ الهدر" else "Loss Date", modifier = Modifier.weight(1f))
                    }
                    FvgField(reason, { reason = it }, if (isArabic) "سبب التلف أو الهدر" else "Loss Cause / Root Reason", testTag = "scrap_reason_input")
                    FvgPrimaryButton(
                        text = if (isArabic) "إرسال للاعتماد الإداري" else "Submit Loss Request for Approval",
                        icon = Icons.Filled.Warning,
                        enabled = state.canEditFinancialData && selectedItem != null && qtyStr.isNotBlank() && reason.isNotBlank() && !state.isBusy,
                        onClick = {
                            selectedItem?.let { item ->
                                viewModel.submitScrapLoss(item, lossType, qtyStr, reason, lossDate)
                                qtyStr = ""; reason = ""
                            }
                        },
                        testTag = "submit_scrap_button"
                    )
                }
            }
        }

        items(state.scrapLosses.reversed(), key = { it.id }) { scr ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${scr.itemName} • ${scr.lossDate}", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge("${localizeInvType(scr.lossType, isArabic)} • ${localizeInvType(scr.status, isArabic)}")
                }
                Text(
                    text = if (isArabic) "الكمية: ${scr.quantity} × ${scr.unitCost} = إجمالي الخسارة ${scr.totalLossCost} $defaultCurrency"
                    else "Qty: ${scr.quantity} × ${scr.unitCost} = Total Loss ${scr.totalLossCost} $defaultCurrency",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (isArabic) "السبب: ${scr.reason}" else "Reason: ${scr.reason}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (scr.status == "pending_approval" && state.canAdminOrg) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgGoldButton(
                            text = if (isArabic) "اعتماد وخصم المخزون وترحيل القيد" else "Approve & Post JE",
                            onClick = { viewModel.reviewScrapLoss(scr, "approved") },
                            modifier = Modifier.weight(1.3f),
                            testTag = "approve_scrap_button"
                        )
                        OutlinedButton(
                            onClick = { viewModel.reviewScrapLoss(scr, "rejected") },
                            modifier = Modifier.weight(0.7f).heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "رفض" else "Reject")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetsTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var name by remember { mutableStateOf("") }
    var budgetType by remember { mutableStateOf("operating") }
    var period by remember { mutableStateOf(today.take(7)) }
    var planned by remember { mutableStateOf("") }
    var actual by remember { mutableStateOf("0") }
    var explanation by remember { mutableStateOf("") }
    var approveNow by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(if (isArabic) "إنشاء موازنة ومقارنتها بالإنفاق الفعلي" else "Create Budget & Track Planned vs Actual Variance", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                FvgChipSelector(listOf("operating", "annual", "monthly", "revenue", "expense", "cash"), budgetType, { budgetType = it }, labelResolver = { localizeInvType(it, isArabic) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(name, { name = it }, if (isArabic) "اسم الموازنة" else "Budget Name", modifier = Modifier.weight(1.4f))
                    FvgField(period, { period = it }, if (isArabic) "الفترة" else "Period", modifier = Modifier.weight(0.8f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(planned, { planned = it }, if (isArabic) "المخطط ($defaultCurrency)" else "Planned ($defaultCurrency)", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(actual, { actual = it }, if (isArabic) "الفعلي ($defaultCurrency)" else "Actual ($defaultCurrency)", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(explanation, { explanation = it }, if (isArabic) "تفسير الانحراف / ملاحظات" else "Variance Explanation / Notes")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = approveNow, onCheckedChange = { approveNow = it })
                    Text(if (isArabic) "اعتماد الموازنة فوراً" else "Approve budget immediately", style = MaterialTheme.typography.bodyMedium)
                }
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ الموازنة" else "Save Budget",
                    enabled = state.canEditFinancialData && name.isNotBlank() && planned.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.saveBudget(name, budgetType, period, defaultCurrency, planned, actual, explanation, approveNow)
                        name = ""; planned = ""
                    }
                )
            }
        }

        items(state.budgets, key = { it.id }) { bdg ->
            var newActual by remember(bdg.id, bdg.actualAmount) { mutableStateOf(bdg.actualAmount) }
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${bdg.name} (${bdg.period})", style = MaterialTheme.typography.titleMedium)
                    FvgRoleBadge("${localizeInvType(bdg.budgetType, isArabic)} • ${localizeInvType(bdg.status, isArabic)}")
                }
                Text(
                    text = if (isArabic) "المخطط: ${bdg.plannedAmount} | الفعلي: ${bdg.actualAmount} | الانحراف: ${bdg.variance} (${bdg.variancePct}%) ${bdg.currency}"
                    else "Planned: ${bdg.plannedAmount} | Actual: ${bdg.actualAmount} | Variance: ${bdg.variance} (${bdg.variancePct}%) ${bdg.currency}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (bdg.explanation.isNotBlank()) {
                    Text(bdg.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.canEditFinancialData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FvgField(newActual, { newActual = it }, if (isArabic) "تحديث الفعلي" else "Update Actual", numeric = true, modifier = Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { viewModel.updateBudgetActuals(bdg, newActual, bdg.explanation, "approved") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "تحديث" else "Update")
                        }
                    }
                }
            }
        }
    }
}
