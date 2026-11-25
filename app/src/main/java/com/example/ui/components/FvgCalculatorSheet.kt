package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.engine.FvgFinancialEngine
import com.example.ui.theme.*
import com.example.domain.math.BigDecimal

@Composable
fun FvgCalculatorSheet(
    isArabic: Boolean,
    defaultCurrency: String = "SDG",
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    var expression by remember { mutableStateOf("") }
    var displayResult by remember { mutableStateOf("0") }
    var errorText by remember { mutableStateOf<String?>(null) }
    var memoryValue by remember { mutableStateOf(BigDecimal.ZERO) }
    var formatAsCurrency by remember { mutableStateOf(true) }
    var history by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var openParenNext by remember { mutableStateOf(true) }

    fun evaluateNow(saveToHistory: Boolean) {
        if (expression.isBlank()) {
            displayResult = "0"
            errorText = null
            return
        }
        val eval = runCatching { FvgFinancialEngine.evaluateCalculatorExpression(expression) }
        eval.onFailure { err ->
            errorText = when (err) {
                is ArithmeticException -> if (isArabic) "لا يمكن القسمة على صفر" else "Cannot divide by zero"
                else -> if (isArabic) "صيغة حسابية غير صالحة" else "Invalid mathematical expression"
            }
        }.onSuccess { value ->
            errorText = null
            val formatted = if (formatAsCurrency) {
                FvgFinancialEngine.money(value).toPlainString()
            } else {
                FvgFinancialEngine.formatCalculatorResult(value)
            }
            displayResult = formatted
            if (saveToHistory) {
                history = (listOf(expression to formatted) + history).take(15)
                expression = formatted
            }
        }
    }

    fun appendToken(token: String) {
        errorText = null
        expression += token
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("fvg_calculator_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = FvgDeepGreen,
            border = BorderStroke(2.dp, FvgWarmGold),
            shadowElevation = 18.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(FvgDeepGreen, Color(0xFF031D1A))
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isArabic) "الآلة الحاسبة المالية FVG" else "FVG Financial Calculator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FvgWarmGold
                        )
                        Text(
                            text = if (isArabic) "عملية مستقلة لا ترحل للدفتر تلقائياً" else "Standalone tool — does not auto-post to ledger",
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgWarmIvory.copy(alpha = 0.72f)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("close_calculator_button")
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = if (isArabic) "إغلاق" else "Close", tint = FvgWarmIvory)
                    }
                }

                // Recessed 3D LCD Screen
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF0A1F1C),
                    border = BorderStroke(1.5.dp, FvgAntiqueGold.copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Math expression and numbers always render LTR inside screen
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (memoryValue.compareTo(BigDecimal.ZERO) != 0) "M: ${memoryValue.stripTrailingZeros().toPlainString()}" else "M: 0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FvgWarmGold.copy(alpha = 0.85f)
                                )
                                FilterChip(
                                    selected = formatAsCurrency,
                                    onClick = { formatAsCurrency = !formatAsCurrency },
                                    label = { Text(if (formatAsCurrency) "$defaultCurrency 0.00" else "Raw", style = MaterialTheme.typography.labelSmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = FvgWarmGold.copy(alpha = 0.22f),
                                        selectedLabelColor = FvgWarmGold,
                                        labelColor = FvgWarmIvory
                                    )
                                )
                            }
                            Text(
                                text = expression.ifBlank { "0" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = FvgWarmIvory.copy(alpha = 0.75f),
                                textAlign = TextAlign.End,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().testTag("calc_expression_display")
                            )
                            Text(
                                text = errorText ?: if (formatAsCurrency) "$displayResult $defaultCurrency" else displayResult,
                                fontFamily = MontserratFontFamily,
                                fontSize = if (errorText != null) 16.sp else 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (errorText != null) Color(0xFFF2B8B5) else FvgWarmGold,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().testTag("calc_result_display")
                            )
                        }
                    }
                }

                // Memory & History bar
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("MC", "MR", "M+", "M-").forEach { mBtn ->
                            Calc3DKey(
                                label = mBtn,
                                containerTop = FvgForestGreen,
                                containerBottom = Color(0xFF0E302A),
                                textColor = FvgWarmGold,
                                modifier = Modifier.weight(1f),
                                heightDp = 42,
                                onClick = {
                                    when (mBtn) {
                                        "MC" -> memoryValue = BigDecimal.ZERO
                                        "MR" -> appendToken(memoryValue.stripTrailingZeros().toPlainString())
                                        "M+" -> {
                                            val cur = runCatching { FvgFinancialEngine.evaluateCalculatorExpression(expression) }.getOrNull()
                                                ?: FvgFinancialEngine.dOrZero(displayResult)
                                            memoryValue = memoryValue.add(cur)
                                        }
                                        "M-" -> {
                                            val cur = runCatching { FvgFinancialEngine.evaluateCalculatorExpression(expression) }.getOrNull()
                                                ?: FvgFinancialEngine.dOrZero(displayResult)
                                            memoryValue = memoryValue.subtract(cur)
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // Realistic 3D Keypad Grid
                    val rows = listOf(
                        listOf("AC", "⌫", "( )", "÷"),
                        listOf("7", "8", "9", "×"),
                        listOf("4", "5", "6", "-"),
                        listOf("1", "2", "3", "+"),
                        listOf("0", ".", "%", "=")
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { key ->
                                    val isEquals = key == "="
                                    val isClearOrDel = key == "AC" || key == "⌫"
                                    val isOp = key in setOf("÷", "×", "-", "+", "%", "( )")
                                    val topCol = when {
                                        isEquals -> FvgWarmGold
                                        isClearOrDel -> FvgAntiqueGold
                                        isOp -> FvgForestGreen
                                        else -> FvgSoftCream
                                    }
                                    val botCol = when {
                                        isEquals -> Color(0xFFB58638)
                                        isClearOrDel -> Color(0xFF8C6329)
                                        isOp -> Color(0xFF0D2E28)
                                        else -> FvgWarmStone
                                    }
                                    val fg = when {
                                        isEquals || isClearOrDel -> FvgDeepGreen
                                        isOp -> FvgWarmGold
                                        else -> FvgDeepGreen
                                    }
                                    Calc3DKey(
                                        label = key,
                                        containerTop = topCol,
                                        containerBottom = botCol,
                                        textColor = fg,
                                        modifier = Modifier.weight(1f).testTag("calc_key_$key"),
                                        onClick = {
                                            when (key) {
                                                "AC" -> {
                                                    expression = ""
                                                    displayResult = "0"
                                                    errorText = null
                                                    openParenNext = true
                                                }
                                                "⌫" -> {
                                                    if (expression.isNotEmpty()) {
                                                        expression = expression.dropLast(1)
                                                        errorText = null
                                                    }
                                                }
                                                "( )" -> {
                                                    appendToken(if (openParenNext) "(" else ")")
                                                    openParenNext = !openParenNext
                                                }
                                                "=" -> evaluateNow(saveToHistory = true)
                                                else -> {
                                                    appendToken(key)
                                                    evaluateNow(saveToHistory = false)
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Tape History
                if (history.isNotEmpty()) {
                    HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.25f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (isArabic) "سجل العمليات الأخيرة" else "Calculation History",
                            style = MaterialTheme.typography.labelLarge,
                            color = FvgWarmGold
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        history.take(5).forEach { (exp, res) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = FvgForestGreen.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expression = res; displayResult = res }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(exp, style = MaterialTheme.typography.labelSmall, color = FvgWarmIvory.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                                    Text("= $res", style = MaterialTheme.typography.labelLarge, color = FvgWarmGold, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Calc3DKey(
    label: String,
    containerTop: Color,
    containerBottom: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    heightDp: Int = 48,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = heightDp.dp)
            .shadow(4.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = heightDp.dp)
                .background(Brush.verticalGradient(listOf(containerTop, containerBottom))),
            contentAlignment = Alignment.Center
        ) {
            if (label == "⌫") {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", tint = textColor, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = label,
                    fontFamily = MontserratFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}
