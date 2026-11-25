package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.engine.FvgFinancialEngine
import com.example.ui.theme.*
import com.example.domain.math.BigDecimal

@Composable
fun FvgSectorCalculatorSheet(
    sectorKey: String,
    isArabic: Boolean,
    defaultCurrency: String = "SDG",
    onApplyPreset: () -> Unit,
    onDismiss: () -> Unit,
    onGoToCosting: (() -> Unit)? = null
) {
    BackHandler { onDismiss() }

    val secInfo = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == sectorKey }
    val title = if (isArabic) (secInfo?.nameAr ?: sectorKey) else (secInfo?.nameEn ?: sectorKey)
    val templateAccounts = FvgFinancialEngine.sectorAccountsTemplate(sectorKey)

    var inputA by remember { mutableStateOf(if (sectorKey == "manufacturing") "50000" else "40000") }
    var inputB by remember { mutableStateOf(if (sectorKey == "manufacturing") "15000" else "8000") }
    var inputC by remember { mutableStateOf(if (sectorKey == "manufacturing") "20" else "8") }
    var inputUnits by remember { mutableStateOf(if (sectorKey == "manufacturing") "500" else "250") }

    val numA = runCatching { FvgFinancialEngine.d(inputA) }.getOrNull() ?: BigDecimal.ZERO
    val numB = runCatching { FvgFinancialEngine.d(inputB) }.getOrNull() ?: BigDecimal.ZERO
    val numC = runCatching { FvgFinancialEngine.d(inputC) }.getOrNull() ?: BigDecimal.ZERO
    val units = runCatching { FvgFinancialEngine.d(inputUnits) }.getOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE

    data class SectorCalcOutcome(
        val totalBatchCost: BigDecimal,
        val unitCost: BigDecimal,
        val suggestedPrice: BigDecimal,
        val marginExplanation: String
    )

    val outcome = when (sectorKey) {
        "manufacturing" -> {
            val overhead = numA.multiply(numC.divide(BigDecimal(100), FvgFinancialEngine.MC))
            val batchTotal = numA.add(numB).add(overhead)
            val cpu = batchTotal.divide(units, FvgFinancialEngine.MC)
            val price = cpu.divide(BigDecimal("0.75"), FvgFinancialEngine.MC)
            SectorCalcOutcome(
                totalBatchCost = FvgFinancialEngine.money(batchTotal),
                unitCost = FvgFinancialEngine.money(cpu),
                suggestedPrice = FvgFinancialEngine.money(price),
                marginExplanation = if (isArabic) "إجمالي تكلفة الدفعة = مواد ($numA) + أجور ($numB) + أعباء صناعية ($overhead) | سعر البيع بهامش 25%"
                else "Batch Cost = Materials ($numA) + Labor ($numB) + Overhead ($overhead) | Target 25% Margin"
            )
        }
        "food_beverage" -> {
            val baseCost = numA.add(numB)
            val wasteFactor = BigDecimal.ONE.subtract(numC.coerceAtMost(BigDecimal("50")).divide(BigDecimal(100), FvgFinancialEngine.MC))
            val batchTotal = if (wasteFactor > BigDecimal.ZERO) baseCost.divide(wasteFactor, FvgFinancialEngine.MC) else baseCost
            val cpu = batchTotal.divide(units, FvgFinancialEngine.MC)
            val price = cpu.divide(BigDecimal("0.70"), FvgFinancialEngine.MC)
            SectorCalcOutcome(
                totalBatchCost = FvgFinancialEngine.money(batchTotal),
                unitCost = FvgFinancialEngine.money(cpu),
                suggestedPrice = FvgFinancialEngine.money(price),
                marginExplanation = if (isArabic) "تكلفة مع استيعاب التلف ($numC%) = ($baseCost) ÷ (1 - $numC%) | سعر البيع بهامش 30% بمبدأ FEFO"
                else "Cost Absorbing Spoilage ($numC%) = ($baseCost) ÷ (1 - $numC%) | FEFO 30% Target Price"
            )
        }
        else -> {
            val batchTotal = numA.multiply(units)
            val cpu = numA
            val price = numA.multiply(BigDecimal.ONE.add(numC.divide(BigDecimal(100), FvgFinancialEngine.MC)))
            SectorCalcOutcome(
                totalBatchCost = FvgFinancialEngine.money(batchTotal),
                unitCost = FvgFinancialEngine.money(cpu),
                suggestedPrice = FvgFinancialEngine.money(price),
                marginExplanation = if (isArabic) "تكلفة إجمالية = $numA × $units | سعر البيع بهامش $numC%"
                else "Total Cost = $numA × $units | Price with $numC% Markup"
            )
        }
    }
    val totalBatchCost = outcome.totalBatchCost
    val unitCost = outcome.unitCost
    val suggestedPrice = outcome.suggestedPrice
    val marginExplanation = outcome.marginExplanation

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("fvg_sector_calculator_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = FvgDeepGreen,
            border = BorderStroke(2.dp, FvgWarmGold),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(FvgDeepGreen, Color(0xFF031D1A))))
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) "واجهة وحاسبة قطاع: $title" else "Dedicated Sector Workspace: $title",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FvgWarmGold
                        )
                        Text(
                            text = if (isArabic) "إدخال بيانات التكاليف وحساب تكلفة الوحدة وسعر البيع" else "Enter Sector Inputs & Compute Unit Cost & Price",
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgWarmIvory.copy(alpha = 0.8f)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_sector_calc_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = FvgWarmIvory)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = if (isArabic) "المميزات والحسابات الخاصة بالقطاع:" else "Sector Features & Specialized Logic:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        when (sectorKey) {
                            "manufacturing" -> {
                                Text(
                                    text = if (isArabic)
                                        "• حساب تكلفة الدفعة الصناعية مع تحميل الأعباء غير المباشرة (Overhead Allocation).\n• احتساب تكلفة القطعة/الوحدة المنتجة الصالحة وسعر البيع المستهدف.\n• دليل حسابات المواد الخام والإنتاج تحت التشغيل (WIP)."
                                    else
                                        "• Batch manufacturing cost with factory overhead allocation.\n• Output unit cost calculation & target selling price.\n• Raw Materials & WIP Inventory COA.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            "food_beverage" -> {
                                Text(
                                    text = if (isArabic)
                                        "• استيعاب نسبة الهدر والتلف السريع (Perishable Spoilage Absorption) في تكلفة الدفعة.\n• حساب تكلفة الوجبة أو العبوة الصالحة للبيع ومبدأ الصلاحية FEFO.\n• دليل حسابات المكونات وسلسلة التبريد."
                                    else
                                        "• Spoilage absorption factoring into batch cost.\n• Usable meal/pack unit cost & FEFO expiry pricing.\n• Fresh ingredients & Cold-Chain COA.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                Text(
                                    text = if (isArabic) "• حسابات الهامش والأسعار وإدارة رأس المال العامل." else "• Commercial margin & working capital pricing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                FvgCard(containerColor = Color(0xFF0A1F1C)) {
                    Text(
                        text = if (isArabic) "إدخال بيانات التكاليف للقطاع" else "Sector Costing Inputs",
                        style = MaterialTheme.typography.titleSmall,
                        color = FvgWarmGold,
                        fontWeight = FontWeight.Bold
                    )
                    when (sectorKey) {
                        "manufacturing" -> {
                            FvgField(inputA, { inputA = it }, if (isArabic) "تكلفة المواد الخام المباشرة ($defaultCurrency)" else "Direct Materials Cost ($defaultCurrency)", numeric = true)
                            FvgField(inputB, { inputB = it }, if (isArabic) "أجور العمالة والتشغيل المباشر ($defaultCurrency)" else "Direct Labor Cost ($defaultCurrency)", numeric = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FvgField(inputC, { inputC = it }, if (isArabic) "نسبة الأعباء الصناعية (%)" else "Overhead Rate (%)", numeric = true, modifier = Modifier.weight(1f))
                                FvgField(inputUnits, { inputUnits = it }, if (isArabic) "عدد الوحدات التامة" else "Produced Units", numeric = true, modifier = Modifier.weight(1f))
                            }
                        }
                        "food_beverage" -> {
                            FvgField(inputA, { inputA = it }, if (isArabic) "تكلفة المكونات الطازجة ($defaultCurrency)" else "Fresh Ingredients Cost ($defaultCurrency)", numeric = true)
                            FvgField(inputB, { inputB = it }, if (isArabic) "تكلفة التعبئة والتبريد والنقل ($defaultCurrency)" else "Packaging & Cold-Chain ($defaultCurrency)", numeric = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FvgField(inputC, { inputC = it }, if (isArabic) "نسبة التلف/الهالك المتوقعة (%)" else "Spoilage/Waste %", numeric = true, modifier = Modifier.weight(1f))
                                FvgField(inputUnits, { inputUnits = it }, if (isArabic) "عدد الوحدات/الوجبات الصالحة" else "Usable Packs/Servings", numeric = true, modifier = Modifier.weight(1f))
                            }
                        }
                        else -> {
                            FvgField(inputA, { inputA = it }, if (isArabic) "التكلفة الأساسية للوحدة" else "Base Unit Cost", numeric = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FvgField(inputUnits, { inputUnits = it }, if (isArabic) "الكمية" else "Quantity", numeric = true, modifier = Modifier.weight(1f))
                                FvgField(inputC, { inputC = it }, if (isArabic) "هامش الربح (%)" else "Margin %", numeric = true, modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = FvgWarmGold.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "إجمالي تكلفة الدفعة:" else "Total Batch Cost:", style = MaterialTheme.typography.bodyMedium, color = FvgWarmIvory)
                                Text("$totalBatchCost $defaultCurrency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = FvgWarmGold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "تكلفة الوحدة الواحدة:" else "Unit Production Cost:", style = MaterialTheme.typography.bodyMedium, color = FvgWarmIvory)
                                Text("$unitCost $defaultCurrency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(if (isArabic) "سعر البيع الموصى به:" else "Target Selling Price:", style = MaterialTheme.typography.bodyMedium, color = FvgWarmIvory)
                                Text("$suggestedPrice $defaultCurrency", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = FvgPositive)
                            }
                            HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.3f))
                            Text(marginExplanation, style = MaterialTheme.typography.labelSmall, color = FvgWarmGold)
                        }
                    }
                }

                Text(
                    text = if (isArabic) "حسابات الدليل المقترحة لهذا القطاع (${templateAccounts.size} حساب):" else "Starter COA (${templateAccounts.size} accounts):",
                    style = MaterialTheme.typography.labelLarge,
                    color = FvgWarmGold
                )
                templateAccounts.take(5).forEach { acc ->
                    Text(
                        text = "• ${acc.code} — ${if (isArabic) acc.nameAr else acc.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = FvgWarmIvory.copy(alpha = 0.85f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "إغلاق" else "Close", color = FvgWarmIvory)
                    }
                    Button(
                        onClick = {
                            onApplyPreset()
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FvgWarmGold, contentColor = FvgDeepGreen),
                        modifier = Modifier.weight(1.2f).heightIn(min = 48.dp).testTag("apply_sector_preset_confirm_button")
                    ) {
                        Icon(Icons.Filled.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "تطبيق على الدليل" else "Apply to COA", fontWeight = FontWeight.Bold)
                    }
                }
                if (onGoToCosting != null) {
                    OutlinedButton(
                        onClick = {
                            onApplyPreset()
                            onDismiss()
                            onGoToCosting()
                        },
                        border = BorderStroke(1.dp, FvgWarmGold),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sector_go_to_costing_button")
                    ) {
                        Text(if (isArabic) "تطبيق والانتقال لحساب التكاليف" else "Apply & Open Costing", color = FvgWarmGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
