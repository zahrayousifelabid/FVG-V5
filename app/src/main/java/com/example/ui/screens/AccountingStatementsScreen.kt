package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.export.FvgExportService
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import com.example.domain.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun localizeStatus(status: String, isArabic: Boolean): String = if (!isArabic) status else when (status.lowercase()) {
    "draft" -> "مسودة"
    "posted" -> "مرحّل"
    "reversed" -> "معكوس"
    "open" -> "مفتوح"
    "partial" -> "سداد جزئي"
    "paid" -> "مسدد"
    "receivable" -> "ذمم مدينة"
    "payable" -> "ذمم دائنة"
    else -> status
}

private fun localizeAccountType(type: String, isArabic: Boolean): String = if (!isArabic) type else when (type.lowercase()) {
    "asset" -> "أصل"
    "liability" -> "التزام"
    "equity" -> "ملكية"
    "revenue" -> "إيراد"
    "cogs" -> "تكلفة مبيعات"
    "expense" -> "مصروف"
    else -> type
}

private fun localizeRatioName(key: String, isArabic: Boolean): String {
    if (!isArabic) return key.replace("_", " ").uppercase()
    return when (key) {
        "gross_margin_pct" -> "هامش مجمل الربح"
        "operating_margin_pct" -> "هامش الربح التشغيلي"
        "net_profit_margin_pct" -> "هامش صافي الربح"
        "roa_pct" -> "العائد على الأصول (ROA)"
        "roe_pct" -> "العائد على حقوق الملكية (ROE)"
        "current_ratio" -> "نسبة التداول الجارية"
        "quick_ratio" -> "نسبة السيولة السريعة"
        "cash_ratio" -> "نسبة النقدية"
        "working_capital" -> "رأس المال العامل"
        "debt_to_equity" -> "الدين إلى حقوق الملكية"
        "debt_to_assets_pct" -> "نسبة الديون إلى الأصول"
        "equity_ratio_pct" -> "نسبة حقوق الملكية"
        "interest_coverage" -> "تغطية الفوائد"
        "asset_turnover" -> "معدل دوران الأصول"
        "inventory_turnover" -> "معدل دوران المخزون"
        "dio_days" -> "أيام بقاء المخزون (DIO)"
        "dso_days" -> "أيام تحصيل الذمم (DSO)"
        "ocf_to_net_income" -> "التدفق التشغيلي إلى صافي الربح"
        else -> key.replace("_", " ")
    }
}

@Composable
fun AccountingStatementsScreen(
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

    Column(modifier = Modifier.fillMaxSize().testTag("accounting_statements_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "المحاسبة والأستاذ والقوائم والنسب" else "Accounting, GL & Statements",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.AccountBalance,
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
            tabs = listOf(s.tabJournalCoa, s.tabGeneralLedger, s.tabStatements, s.tabArApAging, s.tabFinancialRatios),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "accounting"
        )

        when (subTab) {
            0 -> JournalAndCoaTab(state, viewModel, isArabic, today, defaultCurrency)
            1 -> GeneralLedgerTab(state, isArabic, defaultCurrency)
            2 -> StatementsAndTrialBalanceTab(state, viewModel, isArabic, today.take(7), defaultCurrency)
            3 -> ArApAgingTab(state, viewModel, isArabic, today, defaultCurrency)
            4 -> FinancialRatiosTab(state, isArabic)
        }
    }
}

@Composable
private fun JournalAndCoaTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    val context = LocalContext.current
    var refNo by remember { mutableStateOf("JE-${(state.journalEntries.size + 1).toString().padStart(3, '0')}") }
    var entryDate by remember { mutableStateOf(today) }
    var description by remember { mutableStateOf("") }
    var debitCode by remember { mutableStateOf("1010") }
    var creditCode by remember { mutableStateOf("4010") }
    var amountStr by remember { mutableStateOf("") }
    var fxRateStr by remember { mutableStateOf("1.0000") }

    var newAccCode by remember { mutableStateOf("") }
    var newAccName by remember { mutableStateOf("") }
    var newAccNameAr by remember { mutableStateOf("") }
    var newAccType by remember { mutableStateOf("expense") }

    val accCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val text = if (mime.contains("spreadsheet") || uri.toString().endsWith(".xlsx", true)) {
                    context.contentResolver.openInputStream(uri)?.use { FvgExportService.extractXlsxToCsvLines(it) }.orEmpty()
                } else {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                viewModel.previewAccountingCsvImport(text)
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "إنشاء / ترحيل قيد يومية مزدوج متوازن" else "Create / Post Balanced Double-Entry Journal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FvgFieldHelpIcon(
                        title = if (isArabic) "القيد المزدوج والمسودة والعكس" else "Double-Entry Accounting & Reversals",
                        explanation = if (isArabic)
                            "يجب أن يتساوى إجمالي المدين مع الدائن دائماً. القيود المرحّلة محمية ولا تُعدل مباشرة، بل يُنشأ لها قيد عكسي للحفاظ على سجل التدقيق."
                        else
                            "Total Debits must equal Total Credits. Posted entries remain immutable for audit safety; corrections are made using formal reversal entries.",
                        isArabic = isArabic
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(refNo, { refNo = it }, if (isArabic) "رقم المرجع" else "Reference No", modifier = Modifier.weight(1f))
                    FvgField(entryDate, { entryDate = it }, if (isArabic) "التاريخ" else "Date", modifier = Modifier.weight(1f))
                }
                FvgField(description, { description = it }, if (isArabic) "بيان ووصف القيد" else "Journal Description", testTag = "je_desc_input")

                Text(if (isArabic) "حساب المدين" else "Debit Account", style = MaterialTheme.typography.labelLarge)
                if (state.accounts.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.accounts.map { it.code },
                        selected = debitCode,
                        onSelect = { debitCode = it },
                        labelResolver = { c ->
                            val acc = state.accounts.firstOrNull { it.code == c }
                            if (acc != null) "${acc.code} ${if (isArabic && acc.nameAr.isNotBlank()) acc.nameAr else acc.name}" else c
                        }
                    )
                }
                Text(if (isArabic) "حساب الدائن" else "Credit Account", style = MaterialTheme.typography.labelLarge)
                if (state.accounts.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.accounts.map { it.code },
                        selected = creditCode,
                        onSelect = { creditCode = it },
                        labelResolver = { c ->
                            val acc = state.accounts.firstOrNull { it.code == c }
                            if (acc != null) "${acc.code} ${if (isArabic && acc.nameAr.isNotBlank()) acc.nameAr else acc.name}" else c
                        }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(
                        amountStr, { amountStr = it },
                        if (isArabic) "المبلغ ($defaultCurrency)" else "Amount ($defaultCurrency)",
                        numeric = true, modifier = Modifier.weight(1f), testTag = "je_amount_input"
                    )
                    FvgField(
                        fxRateStr, { fxRateStr = it },
                        if (isArabic) "سعر الصرف اليدوي" else "Manual FX Rate",
                        numeric = true, modifier = Modifier.weight(0.75f)
                    )
                }

                fun submitJe(status: String) {
                    val amt = FvgFinancialEngine.money(FvgFinancialEngine.d(amountStr))
                    val drName = state.accounts.firstOrNull { it.code == debitCode }?.name ?: debitCode
                    val crName = state.accounts.firstOrNull { it.code == creditCode }?.name ?: creditCode
                    viewModel.postJournalEntry(
                        referenceNo = refNo,
                        entryDate = entryDate,
                        description = description,
                        currency = defaultCurrency,
                        exchangeRate = fxRateStr,
                        originalCurrency = defaultCurrency,
                        originalAmount = amt.toPlainString(),
                        lines = listOf(
                            FvgFinancialEngine.JournalLineInput(debitCode, drName, amt, BigDecimal.ZERO),
                            FvgFinancialEngine.JournalLineInput(creditCode, crName, BigDecimal.ZERO, amt)
                        ),
                        status = status
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = state.canEditFinancialData && description.isNotBlank() && amountStr.isNotBlank() && debitCode != creditCode && !state.isBusy,
                        onClick = { submitJe("draft") },
                        modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "حفظ كمسودة" else "Save Draft")
                    }
                    FvgPrimaryButton(
                        text = if (isArabic) "ترحيل القيد" else "Post Entry",
                        icon = Icons.Filled.CheckCircle,
                        enabled = state.canEditFinancialData && description.isNotBlank() && amountStr.isNotBlank() && debitCode != creditCode && !state.isBusy,
                        onClick = { submitJe("posted") },
                        modifier = Modifier.weight(1.2f),
                        testTag = "post_journal_button"
                    )
                }
            }
        }

        // Atomic Accounting CSV Import Card
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "استيراد القيود المحاسبية CSV / XLSX بأمان" else "Sanitized Accounting CSV / XLSX Import",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { accCsvLauncher.launch(arrayOf("text/csv", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "*/*")) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "اختيار ملف قيود" else "Pick Journal File")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.previewAccountingCsvImport(
                                if (isArabic)
                                    "date,refNo,description,debitCode,creditCode,amount\n$today,JE-IMP-1,إيراد استشارات نقدي,1010,4010,1200.00\n$today,JE-IMP-2,مصروف إيجار المكتب,6010,1010,350.00"
                                else
                                    "date,refNo,description,debitCode,creditCode,amount\n$today,JE-IMP-1,Cash Consulting Revenue,1010,4010,1200.00\n$today,JE-IMP-2,Office Rent Expense,6010,1010,350.00"
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "معاينة نموذج قيود" else "Preview Sample CSV")
                    }
                }
                if (state.accountingCsvPreviewRows.isNotEmpty()) {
                    state.accountingCsvPreviewRows.take(6).forEach { r ->
                        val ok = r.error == null
                        Text(
                            text = if (ok) {
                                if (isArabic) "✓ صف ${r.rowNumber}: ${r.referenceNo} مدين ${r.debitAccountCode} / دائن ${r.creditAccountCode} = ${r.amount}"
                                else "✓ Row ${r.rowNumber}: ${r.referenceNo} Dr ${r.debitAccountCode} / Cr ${r.creditAccountCode} = ${r.amount}"
                            } else "✗ ${r.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgGoldButton(
                            text = if (isArabic) "اعتماد وترحيل القيود" else "Commit Accounting Entries",
                            enabled = state.canEditFinancialData && state.accountingCsvPreviewRows.any { it.error == null },
                            onClick = { viewModel.commitAccountingCsvImport() },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { viewModel.clearAccountingCsvPreview() }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(if (isArabic) "مسح" else "Clear")
                        }
                    }
                }
            }
        }

        // Add custom account
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "إضافة حساب إلى دليل الحسابات (${state.accounts.size})" else "Chart of Accounts (${state.accounts.size} accounts)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgChipSelector(
                    options = listOf("asset", "liability", "equity", "revenue", "cogs", "expense"),
                    selected = newAccType,
                    onSelect = { newAccType = it },
                    labelResolver = { localizeAccountType(it, isArabic) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(newAccCode, { newAccCode = it }, if (isArabic) "الرمز (مثال 6310)" else "Code (e.g. 6310)", modifier = Modifier.weight(0.8f))
                    FvgField(newAccName, { newAccName = it }, if (isArabic) "اسم الحساب (EN/عام)" else "Account Name", modifier = Modifier.weight(1.4f))
                }
                FvgField(newAccNameAr, { newAccNameAr = it }, if (isArabic) "الاسم بالعربية (اختياري)" else "Arabic Name (optional)")
                FvgGoldButton(
                    text = if (isArabic) "إضافة حساب جديد" else "Add Account",
                    enabled = state.canEditFinancialData && newAccCode.isNotBlank() && newAccName.isNotBlank() && !state.isBusy,
                    onClick = {
                        val sub = when (newAccType) {
                            "asset" -> "current_asset"
                            "liability" -> "current_liability"
                            "equity" -> "equity"
                            "revenue" -> "revenue"
                            "cogs" -> "cogs"
                            else -> "operating_expense"
                        }
                        val norm = if (newAccType in setOf("asset", "cogs", "expense")) "debit" else "credit"
                        viewModel.createAccount(newAccCode, newAccName, newAccNameAr.ifBlank { newAccName }, newAccType, sub, norm, "0.00")
                        newAccCode = ""; newAccName = ""; newAccNameAr = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        items(state.journalEntries.reversed(), key = { it.id }) { je ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${je.referenceNo} • ${je.entryDate}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgRoleBadge(localizeStatus(je.status, isArabic))
                }
                Text(je.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (isArabic) "إجمالي المدين: ${je.totalDebit} = إجمالي الدائن: ${je.totalCredit} ${je.currency}"
                    else "Debits: ${je.totalDebit} = Credits: ${je.totalCredit} ${je.currency}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (je.status == "draft" && state.canEditFinancialData) {
                    FvgGoldButton(
                        text = if (isArabic) "اعتماد وترحيل المسودة" else "Approve & Post Draft",
                        onClick = { viewModel.approveAndPostDraft(je) },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (je.status == "posted" && state.canEditFinancialData) {
                    OutlinedButton(
                        onClick = { viewModel.reverseJournalEntry(je, today) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "إنشاء قيد عكسي" else "Create Reversal Entry")
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneralLedgerTab(
    state: FvgUiState,
    isArabic: Boolean,
    currency: String
) {
    var selectedCode by remember(state.accounts) { mutableStateOf(state.accounts.firstOrNull()?.code ?: "1010") }
    var periodFilter by remember { mutableStateOf("") }

    val selectedAcc = state.accounts.firstOrNull { it.code == selectedCode }
    val statement = remember(selectedAcc, state.journalEntries, periodFilter) {
        selectedAcc?.let { acc ->
            val accInput = FvgFinancialEngine.AccountInput(
                acc.code,
                if (isArabic && acc.nameAr.isNotBlank()) acc.nameAr else acc.name,
                acc.accountType, acc.subType, acc.normalBalance,
                FvgFinancialEngine.dOrZero(acc.openingBalance)
            )
            val posted = state.journalEntries.filter { it.status == "posted" || it.status == "reversed" }.map { je ->
                val lines = je.lines.map { l ->
                    FvgFinancialEngine.JournalLineInput(
                        accountCode = l["accountCode"]?.toString().orEmpty(),
                        accountName = l["accountName"]?.toString().orEmpty(),
                        debit = FvgFinancialEngine.dOrZero(l["debit"]?.toString()),
                        credit = FvgFinancialEngine.dOrZero(l["credit"]?.toString())
                    )
                }
                Triple(je.entryDate, je.referenceNo, je.description to lines)
            }
            FvgFinancialEngine.computeAccountLedgerStatement(accInput, posted, periodFilter)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Text(
                    text = if (isArabic) "دفتر الأستاذ العام والرصيد المتحرك" else "General Ledger & Running Balance Statement",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (state.accounts.isNotEmpty()) {
                    FvgChipSelector(
                        options = state.accounts.map { it.code },
                        selected = selectedCode,
                        onSelect = { selectedCode = it },
                        labelResolver = { c ->
                            val a = state.accounts.firstOrNull { it.code == c }
                            if (a != null) "${a.code} ${if (isArabic && a.nameAr.isNotBlank()) a.nameAr else a.name}" else c
                        }
                    )
                }
                FvgField(
                    periodFilter, { periodFilter = it },
                    if (isArabic) "تصفية الفترة (اختياري مثال 2026-11)" else "Period Filter (optional YYYY-MM)"
                )
            }
        }

        if (statement == null) {
            item { FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords) }
        } else {
            item {
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("${statement.accountCode} — ${statement.accountName}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    StatementRow(if (isArabic) "الرصيد الافتتاحي" else "Opening Balance", "${statement.openingBalance} $currency")
                    StatementRow(if (isArabic) "إجمالي المدين" else "Total Debits", "${statement.totalDebits} $currency")
                    StatementRow(if (isArabic) "إجمالي الدائن" else "Total Credits", "${statement.totalCredits} $currency")
                    HorizontalDivider()
                    StatementRow(if (isArabic) "الرصيد الختامي" else "Closing Balance", "${statement.closingBalance} $currency", bold = true)
                }
            }
            if (statement.movements.isEmpty()) {
                item { FvgEmptyStateCard(if (isArabic) "لا توجد حركات مرحّلة لهذا الحساب في الفترة المحددة." else "No posted movements for this account/period.") }
            } else {
                items(statement.movements) { mv ->
                    FvgCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${mv.entryDate} • ${mv.referenceNo}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isArabic) "الرصيد: ${mv.runningBalance} $currency" else "Bal: ${mv.runningBalance} $currency",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(mv.description, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (isArabic) "مدين: ${mv.debit}  |  دائن: ${mv.credit}" else "Dr: ${mv.debit}  |  Cr: ${mv.credit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementsAndTrialBalanceTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    defaultPeriod: String,
    currency: String
) {
    val report = state.statementsReport
    var periodA by remember { mutableStateOf(defaultPeriod) }
    var periodB by remember { mutableStateOf(defaultPeriod) }

    val defaultStartCash = report?.cashAndBank?.takeIf { it > BigDecimal.ZERO }?.toPlainString() ?: "100000"
    val defaultWeeklyIn = if (report != null && report.revenue > BigDecimal.ZERO) {
        FvgFinancialEngine.money(report.revenue.divide(BigDecimal("4"), 4, com.example.domain.math.RoundingMode.HALF_UP)).toPlainString()
    } else "25000"
    val defaultWeeklyOut = if (report != null && report.cogs.add(report.operatingExpenses) > BigDecimal.ZERO) {
        FvgFinancialEngine.money(report.cogs.add(report.operatingExpenses).divide(BigDecimal("4"), 4, com.example.domain.math.RoundingMode.HALF_UP)).toPlainString()
    } else "30000"

    var startingCashInput by remember(report?.cashAndBank) { mutableStateOf(defaultStartCash) }
    var weeklyInflowInput by remember(report?.revenue) { mutableStateOf(defaultWeeklyIn) }
    var weeklyOutflowInput by remember(report?.cogs, report?.operatingExpenses) { mutableStateOf(defaultWeeklyOut) }
    var openArInput by remember(report?.receivables) { mutableStateOf((report?.receivables ?: BigDecimal.ZERO).toPlainString()) }
    var fxShockPctInput by remember { mutableStateOf("0") }
    var salesDropPctInput by remember { mutableStateOf("20") }
    var arDelayWeeksInput by remember { mutableIntStateOf(1) }

    val profitBridge = remember(state.accounts, state.journalEntries, periodA, periodB) {
        if (state.accounts.isEmpty() || periodA.isBlank() || periodB.isBlank()) null
        else {
            val accInputs = state.accounts.map {
                FvgFinancialEngine.AccountInput(it.code, it.name, it.accountType, it.subType, it.normalBalance, FvgFinancialEngine.dOrZero(it.openingBalance))
            }
            val dated = state.journalEntries.filter { it.status == "posted" || it.status == "reversed" }.map { je ->
                val p = je.period.ifBlank { je.entryDate.take(7) }
                val lines = je.lines.map { l ->
                    FvgFinancialEngine.JournalLineInput(
                        l["accountCode"]?.toString().orEmpty(),
                        l["accountName"]?.toString().orEmpty(),
                        FvgFinancialEngine.dOrZero(l["debit"]?.toString()),
                        FvgFinancialEngine.dOrZero(l["credit"]?.toString())
                    )
                }
                p to lines
            }
            FvgFinancialEngine.comparePeriodsProfitBridge(accInputs, dated, periodA, periodB)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (report == null) {
            item { FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords) }
        } else {
            item {
                val eqLevel = when {
                    !report.isAccountingEquationBalanced -> "red"
                    report.netProfit < BigDecimal.ZERO -> "yellow"
                    else -> "green"
                }
                FvgStatusSignalBanner(
                    level = eqLevel,
                    title = if (!report.isAccountingEquationBalanced) {
                        if (isArabic) "تنبيه حرج: معادلة الميزانية غير متوازنة!" else "Critical Alert: Balance Sheet Out of Balance!"
                    } else if (report.netProfit < BigDecimal.ZERO) {
                        if (isArabic) "تنبيه ربحي: الميزانية متوازنة مع صافي خسارة" else "Caution: Equation Balanced with Net Loss"
                    } else {
                        if (isArabic) "الأصول = الالتزامات + حقوق الملكية (متوازنة ✓)" else "Accounting Equation Balanced (A = L + E ✓)"
                    },
                    subtitle = if (isArabic)
                        "الأصول: ${report.totalAssets} $currency • الخصوم والملكية: ${report.totalLiabilitiesAndEquity} $currency • صافي الربح: ${report.netProfit} $currency"
                    else
                        "Assets: ${report.totalAssets} $currency • L+E: ${report.totalLiabilitiesAndEquity} $currency • Net Profit: ${report.netProfit} $currency",
                    isArabic = isArabic
                )
            }
            item {
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (isArabic) "قائمة الدخل" else "Income Statement", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    StatementRow(if (isArabic) "الإيرادات التشغيلية" else "Operating Revenue", "${report.revenue} $currency")
                    StatementRow(if (isArabic) "تكلفة المبيعات" else "Cost of Goods Sold", "(${report.cogs}) $currency")
                    HorizontalDivider()
                    StatementRow(if (isArabic) "مجمل الربح" else "Gross Profit", "${report.grossProfit} $currency", bold = true)
                    StatementRow(if (isArabic) "المصروفات التشغيلية" else "Operating Expenses", "(${report.operatingExpenses}) $currency")
                    StatementRow(if (isArabic) "الربح التشغيلي" else "Operating Profit", "${report.operatingProfit} $currency")
                    StatementRow(if (isArabic) "الفوائد وبنود أخرى" else "Interest & Other", "(${report.interestAndOther}) $currency")
                    HorizontalDivider()
                    StatementRow(if (isArabic) "صافي الربح / (الخسارة)" else "Net Profit / (Loss)", "${report.netProfit} $currency", bold = true)
                }
            }

            item {
                FvgCard {
                    Text(
                        text = if (isArabic) "جسر مقارنة الربح بين فترتين" else "Period A vs Period B Net Profit Bridge",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(periodA, { periodA = it }, if (isArabic) "الفترة أ (YYYY-MM)" else "Period A (YYYY-MM)", modifier = Modifier.weight(1f))
                        FvgField(periodB, { periodB = it }, if (isArabic) "الفترة ب (YYYY-MM)" else "Period B (YYYY-MM)", modifier = Modifier.weight(1f))
                    }
                    if (profitBridge != null) {
                        StatementRow(if (isArabic) "فرق الإيرادات" else "Revenue Delta", "${profitBridge.revenueDelta} $currency")
                        StatementRow(if (isArabic) "فرق تكلفة المبيعات" else "COGS Delta", "${profitBridge.cogsDelta} $currency")
                        StatementRow(if (isArabic) "فرق المصروفات التشغيلية" else "OpEx Delta", "${profitBridge.opExDelta} $currency")
                        StatementRow(if (isArabic) "صافي فرق الربح" else "Net Profit Delta", "${profitBridge.netProfitDelta} $currency", bold = true)
                        Text(profitBridge.explanation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item {
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isArabic) "الميزانية العمومية" else "Balance Sheet", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (report.isAccountingEquationBalanced) FvgPositive.copy(alpha = 0.14f) else MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = if (report.isAccountingEquationBalanced) {
                                    if (isArabic) "الأصول = الالتزامات + الملكية ✓" else "A = L + E ✓"
                                } else {
                                    if (isArabic) "عدم توازن!" else "Out of Balance!"
                                },
                                color = if (report.isAccountingEquationBalanced) FvgPositive else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    StatementRow(if (isArabic) "النقدية والبنك" else "Cash & Bank", "${report.cashAndBank} $currency")
                    StatementRow(if (isArabic) "الذمم المدينة" else "Accounts Receivable", "${report.receivables} $currency")
                    StatementRow(if (isArabic) "المخزون (القيمة الدفترية)" else "Inventory (Book Value)", "${report.inventoryBookValue} $currency")
                    StatementRow(if (isArabic) "الأصول غير المتداولة" else "Non-Current Assets", "${report.nonCurrentAssets} $currency")
                    StatementRow(if (isArabic) "إجمالي الأصول" else "Total Assets", "${report.totalAssets} $currency", bold = true)
                    HorizontalDivider()
                    StatementRow(if (isArabic) "إجمالي الالتزامات" else "Total Liabilities", "${report.totalLiabilities} $currency")
                    StatementRow(if (isArabic) "إجمالي حقوق الملكية (شامل الربح)" else "Total Equity (incl. Net Income)", "${report.totalEquityWithNetIncome} $currency")
                    StatementRow(if (isArabic) "إجمالي الالتزامات وحقوق الملكية" else "Total Liabilities & Equity", "${report.totalLiabilitiesAndEquity} $currency", bold = true)
                }
            }

            item {
                FvgCard {
                    Text(if (isArabic) "قائمة التدفقات النقدية" else "Cash Flow Statement", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    StatementRow(if (isArabic) "التدفق النقدي التشغيلي" else "Operating Cash Flow", "${report.operatingCashFlow} $currency")
                    StatementRow(if (isArabic) "التدفق النقدي الاستثماري" else "Investing Cash Flow", "${report.investingCashFlow} $currency")
                    StatementRow(if (isArabic) "التدفق النقدي التمويلي" else "Financing Cash Flow", "${report.financingCashFlow} $currency")
                    HorizontalDivider()
                    StatementRow(if (isArabic) "رصيد النقدية الختامي" else "Closing Cash Balance", "${report.closingCash} $currency", bold = true)
                }
            }

            item {
                FvgCard {
                    Text(
                        text = if (isArabic) "ميزان المراجعة (مدين ${report.totalTrialDebits} | دائن ${report.totalTrialCredits})"
                        else "Trial Balance (Dr ${report.totalTrialDebits} = Cr ${report.totalTrialCredits})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    report.trialBalance.forEach { row ->
                        val accAr = state.accounts.firstOrNull { it.code == row.code }?.nameAr.orEmpty()
                        val dispName = if (isArabic && accAr.isNotBlank()) accAr else row.name
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${row.code} $dispName", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
                            Text("${row.debitBalance}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                            Text("${row.creditBalance}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                        }
                    }
                }
            }
        }

        // Interactive 13-Week Cash Runway & Liquidity Stress Test
        item {
            FvgCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("cash_runway_stress_card")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isArabic) "توقع السيولة والتدفق النقدي لـ 13 أسبوعاً (اختبار الضغط)" else "13-Week Cash Runway & Liquidity Stress Test",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FvgFieldHelpIcon(
                        title = if (isArabic) "اختبار ضغط السيولة لـ 13 أسبوعاً" else "13-Week Liquidity Stress Test",
                        explanation = if (isArabic)
                            "يحاكي تطور الرصيد النقدي الأسبوعي عند تراجع المبيعات (20%–40%)، صدمة سعر الصرف، أو تأخر تحصيل الذمم المدينة لتحديد أسابيع كفاية السيولة."
                        else
                            "Deterministic 13-week stress model simulating sales declines (-20% to -40%), FX cost shocks, and A/R collection delays to project cash runway weeks.",
                        isArabic = isArabic
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(startingCashInput, { startingCashInput = it }, if (isArabic) "النقدية الابتدائية" else "Starting Cash", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(openArInput, { openArInput = it }, if (isArabic) "الذمم المدينة" else "Open A/R", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(weeklyInflowInput, { weeklyInflowInput = it }, if (isArabic) "التدفق الداخل/أسبوع" else "Weekly Inflow", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(weeklyOutflowInput, { weeklyOutflowInput = it }, if (isArabic) "التدفق الخارج/أسبوع" else "Weekly Outflow", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(fxShockPctInput, { fxShockPctInput = it }, if (isArabic) "صدمة الصرف %" else "FX Shock %", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(salesDropPctInput, { salesDropPctInput = it }, if (isArabic) "تراجع المبيعات %" else "Sales Drop %", numeric = true, modifier = Modifier.weight(1f))
                }
                Text(
                    text = if (isArabic) "سيناريو تراجع المبيعات السريع:" else "Quick Sales Drop Preset:",
                    style = MaterialTheme.typography.labelMedium
                )
                FvgChipSelector(
                    options = listOf("0", "20", "30", "40"),
                    selected = salesDropPctInput,
                    onSelect = { salesDropPctInput = it },
                    labelResolver = { pct -> if (pct == "0") (if (isArabic) "أساسي 0%" else "Base 0%") else "-$pct%" }
                )
                Text(
                    text = if (isArabic) "تأخر تحصيل الذمم (بالأسابيع):" else "A/R Collection Delay (Weeks):",
                    style = MaterialTheme.typography.labelMedium
                )
                FvgChipSelector(
                    options = listOf("0", "1", "2", "3", "4"),
                    selected = arDelayWeeksInput.toString(),
                    onSelect = { arDelayWeeksInput = it.toIntOrNull() ?: 1 },
                    labelResolver = { w -> if (isArabic) "$w أسبوع" else "${w}w" }
                )

                FvgPrimaryButton(
                    text = if (isArabic) "احسب كفاية السيولة (13 أسبوعاً)" else "Simulate 13-Week Cash Runway",
                    icon = Icons.Filled.Timeline,
                    onClick = {
                        viewModel.run13WeekCashRunway(
                            startingCashStr = startingCashInput,
                            weeklyInflowStr = weeklyInflowInput,
                            weeklyOutflowStr = weeklyOutflowInput,
                            openArStr = openArInput,
                            openApStr = (report?.totalLiabilities ?: BigDecimal.ZERO).toPlainString(),
                            fxShockPctStr = fxShockPctInput,
                            salesDropPctStr = salesDropPctInput,
                            arDelayWeeks = arDelayWeeksInput
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "run_cash_runway_button"
                )

                val runway = state.cashRunwayResult
                if (runway != null) {
                    val signalLevel = when (runway.riskLevel) {
                        "healthy" -> "green"
                        "tight" -> "yellow"
                        else -> "red"
                    }
                    FvgStatusSignalBanner(
                        level = signalLevel,
                        title = if (isArabic)
                            "كفاية السيولة المتوقعة: ${runway.runwayWeeks} أسبوعاً (أدنى رصيد: ${runway.minimumCash} $currency)"
                        else
                            "Projected Runway: ${runway.runwayWeeks} Weeks (Min Cash: ${runway.minimumCash} $currency)",
                        subtitle = if (isArabic)
                            "${runway.summaryAr} • نتيجة اختبار ضغط افتراضي"
                        else
                            "${runway.summaryEn} • Scenario outcome — not a prediction",
                        isArabic = isArabic
                    )
                    FvgBarComparisonCanvas(
                        bars = runway.weeks.map { "W${it.weekIndex}" to it.endingCash.toFloat() }
                    )
                    runway.weeks.forEach { w ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isArabic) "أسبوع ${w.weekIndex}" else "Week ${w.weekIndex}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(0.8f)
                            )
                            Text(
                                text = "+${w.inflow} / -${w.outflow}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1.3f)
                            )
                            Text(
                                text = "${w.endingCash} $currency",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (w.endingCash < BigDecimal.ZERO) FvgNegative else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatementRow(label: String, value: String, bold: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun ArApAgingTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    today: String,
    defaultCurrency: String
) {
    var cpType by remember { mutableStateOf("receivable") }
    var partyName by remember { mutableStateOf("") }
    var invoiceNo by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(today) }

    val agingSummary = remember(state.counterpartyItems) {
        FvgFinancialEngine.computeArApAgingBuckets(
            items = state.counterpartyItems.map {
                val rem = FvgFinancialEngine.dOrZero(it.amount).subtract(FvgFinancialEngine.dOrZero(it.paidAmount))
                Triple(it.dueDate, rem, it.status)
            },
            asOfEpochDays = System.currentTimeMillis() / 86400_000L
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = if (isArabic) "تقرير أعمار الذمم والفواتير" else "AR / AP Aging Buckets Summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                StatementRow(if (isArabic) "جاري (غير متأخر)" else "Current (Not Past Due)", "${agingSummary.currentAmount} $defaultCurrency")
                StatementRow(if (isArabic) "متأخر 1–30 يوماً" else "1–30 Days Past Due", "${agingSummary.days1To30Amount} $defaultCurrency")
                StatementRow(if (isArabic) "متأخر 31–60 يوماً" else "31–60 Days Past Due", "${agingSummary.days31To60Amount} $defaultCurrency")
                StatementRow(if (isArabic) "متأخر 61–90 يوماً" else "61–90 Days Past Due", "${agingSummary.days61To90Amount} $defaultCurrency")
                StatementRow(if (isArabic) "أكثر من 90 يوماً" else "90+ Days Past Due", "${agingSummary.days90PlusAmount} $defaultCurrency", bold = true)
                Text(
                    text = if (isArabic) "الفواتير المتأخرة المفتوحة: ${agingSummary.overdueCount}" else "Overdue Open Invoices: ${agingSummary.overdueCount}",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (agingSummary.overdueCount > 0) MaterialTheme.colorScheme.error else FvgPositive
                )
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "تسجيل فاتورة ذمم مدينة / دائنة" else "Record AR / AP Invoice", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                FvgChipSelector(
                    listOf("receivable", "payable"),
                    cpType,
                    { cpType = it },
                    labelResolver = { localizeStatus(it, isArabic) }
                )
                FvgField(partyName, { partyName = it }, if (isArabic) "اسم العميل / المورد" else "Customer / Supplier Name")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(invoiceNo, { invoiceNo = it }, if (isArabic) "رقم الفاتورة" else "Invoice No", modifier = Modifier.weight(1f))
                    FvgField(amountStr, { amountStr = it }, if (isArabic) "المبلغ" else "Amount", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(dueDate, { dueDate = it }, if (isArabic) "تاريخ الاستحقاق (YYYY-MM-DD)" else "Due Date (YYYY-MM-DD)")
                FvgPrimaryButton(
                    text = if (isArabic) "حفظ الفاتورة" else "Save AR/AP Item",
                    enabled = state.canEditFinancialData && partyName.isNotBlank() && invoiceNo.isNotBlank() && amountStr.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.saveCounterpartyItem(cpType, partyName, invoiceNo, defaultCurrency, amountStr, dueDate)
                        partyName = ""; invoiceNo = ""; amountStr = ""
                    },
                    testTag = "save_arap_button"
                )
            }
        }

        items(state.counterpartyItems, key = { it.id }) { item ->
            val bal = FvgFinancialEngine.money(
                FvgFinancialEngine.dOrZero(item.amount).subtract(FvgFinancialEngine.dOrZero(item.paidAmount))
            )
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${item.partyName} (${item.invoiceNo})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgRoleBadge("${localizeStatus(item.type, isArabic)} • ${localizeStatus(item.status, isArabic)}")
                }
                Text(
                    text = if (isArabic) "الإجمالي: ${item.amount} | المدفوع: ${item.paidAmount} | المتبقي: $bal ${item.currency} (الاستحقاق ${item.dueDate})"
                    else "Total: ${item.amount} | Paid: ${item.paidAmount} | Remaining: $bal ${item.currency} (Due ${item.dueDate})",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (bal > BigDecimal.ZERO && state.canEditFinancialData) {
                    FvgGoldButton(
                        text = if (isArabic) "تسديد المتبقي بالكامل ($bal)" else "Record Full Settlement ($bal)",
                        onClick = { viewModel.recordCounterpartyPayment(item, bal.toPlainString()) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialRatiosTab(
    state: FvgUiState,
    isArabic: Boolean
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (state.financialRatios.isEmpty()) {
            item { FvgEmptyStateCard(fvgStrings(isArabic).emptyNoRecords) }
        } else {
            items(state.financialRatios, key = { it.key }) { ratio ->
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(localizeRatioName(ratio.key, isArabic), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(ratio.category, style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                        }
                        Text(
                            text = ratio.value?.let { "${it.toPlainString()} ${ratio.unitSuffix}" } ?: if (isArabic) "غير متاح" else "N/A",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (ratio.value == null) FvgWarning else FvgPositive
                        )
                    }
                    Text(
                        text = if (isArabic) "المعادلة: ${ratio.formula}  |  المدخلات: ${ratio.inputsSummary}" else "Formula: ${ratio.formula}  |  Inputs: ${ratio.inputsSummary}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(ratio.explanation, style = MaterialTheme.typography.bodyMedium)
                    if (ratio.missingDataWarning != null) {
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "⚠ ${ratio.missingDataWarning}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
