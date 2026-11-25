package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.example.data.model.PlanEntitlements
import com.example.domain.engine.FvgFinancialEngine
import com.example.domain.export.FvgExportService
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun rotateBitmap90(bitmap: Bitmap): Bitmap {
    val matrix = Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun localizePayStatus(status: String, isArabic: Boolean): String = if (!isArabic) status else when (status.lowercase()) {
    "pending_verification" -> "قيد التحقق"
    "approved" -> "مقبول"
    "rejected" -> "مرفوض"
    "active" -> "نشط"
    "trial" -> "تجربة"
    "expired" -> "منتهي"
    "cancelled" -> "ملغى"
    "pending_review" -> "قيد المراجعة"
    "verified" -> "معتمد"
    else -> status
}

@Composable
fun ReportsAiSettingsScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenCalculator: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
    onOpenOwnerAdmin: () -> Unit = {},
    onSignOutClick: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var subTab by remember { mutableIntStateOf(1) } // Default to Financial Assistant / OCR when entering Assistant tab

    Column(modifier = Modifier.fillMaxSize().testTag("reports_ai_settings_screen")) {
        FvgTopHeaderBanner(
            title = if (isArabic) "المساعد المالي والتقارير والاشتراك" else "Financial Assistant, Reports & Billing",
            subtitle = s.brandTaglineEn,
            icon = Icons.Filled.AutoAwesome,
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
            tabs = listOf(s.tabReportsExports, s.tabAssistantOcr, s.tabOrgSettings),
            selectedIndex = subTab,
            onSelect = { subTab = it },
            testTagPrefix = "reports"
        )

        when (subTab) {
            0 -> ReportsExportTab(state, viewModel, isArabic)
            1 -> AiAssistantAndOcrTab(state, viewModel, isArabic)
            2 -> OrgRbacBillingSettingsTab(
                state = state,
                viewModel = viewModel,
                isArabic = isArabic,
                onOpenProfile = onOpenProfile,
                onOpenHelp = onOpenHelp,
                onOpenOwnerAdmin = onOpenOwnerAdmin,
                onSignOutClick = onSignOutClick
            )
        }
    }
}

@Composable
private fun ReportsExportTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val s = fvgStrings(isArabic)
    var selectedReport by remember { mutableStateOf("statements") }
    var exportNotice by remember { mutableStateOf<String?>(null) }
    var intercompanyRevCost by remember { mutableStateOf("0") }
    var intercompanyArAp by remember { mutableStateOf("0") }

    val payload = remember(selectedReport, state, isArabic) {
        viewModel.buildReportPayload(selectedReport, isArabic)
    }

    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FvgExportService.writePdf(out, payload)
                }
                exportNotice = if (isArabic) "تم تصدير ملف PDF بنجاح (${payload.rows.size} صف)." else "PDF exported (${payload.rows.size} rows)."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير PDF: ${it.message}" else "PDF export failed: ${it.message}" }
        }
    }
    val xlsxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FvgExportService.writeXlsx(out, payload)
                }
                exportNotice = if (isArabic) "تم تصدير ملف XLSX بنجاح (${payload.rows.size} صف)." else "XLSX exported (${payload.rows.size} rows)."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير XLSX: ${it.message}" else "XLSX export failed: ${it.message}" }
        }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, FvgExportService.toCsvString(payload))
                exportNotice = if (isArabic) "تم تصدير ملف CSV بنجاح." else "CSV exported."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير CSV: ${it.message}" else "CSV export failed: ${it.message}" }
        }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, FvgExportService.toJsonString(payload))
                exportNotice = if (isArabic) "تم تصدير ملف JSON بنجاح." else "JSON exported."
            }.onFailure { exportNotice = if (isArabic) "فشل تصدير JSON: ${it.message}" else "JSON export failed: ${it.message}" }
        }
    }
    val backupJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                FvgExportService.writeTextToUri(context, uri, viewModel.buildWorkspaceBackupJson(isArabic))
                exportNotice = if (isArabic) "تم تصدير النسخة الاحتياطية الكاملة (JSON) بنجاح." else "Full Workspace Backup (JSON) exported cleanly."
            }.onFailure { exportNotice = if (isArabic) "فشل النسخ الاحتياطي: ${it.message}" else "Backup failed: ${it.message}" }
        }
    }

    val stamp = remember { SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()) }
    val stmt = state.statementsReport

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stmt != null) {
            item {
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = if (isArabic) "ملخص الأداء السنوي / التراكمي" else "Financial Performance Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FvgBarComparisonCanvas(
                        bars = listOf(
                            (if (isArabic) "إيرادات" else "Rev") to stmt.revenue.toFloat(),
                            (if (isArabic) "تكلفة" else "COGS") to stmt.cogs.toFloat(),
                            (if (isArabic) "مجمل" else "Gross") to stmt.grossProfit.toFloat(),
                            (if (isArabic) "تشغيل" else "OpEx") to stmt.operatingExpenses.toFloat(),
                            (if (isArabic) "صافي" else "Net") to stmt.netProfit.toFloat()
                        )
                    )
                }
            }
        }

        item {
            FvgCard {
                Text(
                    text = if (isArabic) "التقارير المالية والتصديرات (12 تقريراً موثقاً)" else "Financial Reports & Verified Exports (12 Reports)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FvgChipSelector(
                    options = listOf(
                        "statements", "trial_balance", "ledger", "ratios",
                        "prices", "inflation", "fx", "costing",
                        "inventory", "stocktakes", "scrap", "budgets"
                    ),
                    selected = selectedReport,
                    onSelect = { selectedReport = it },
                    labelResolver = {
                        when (it) {
                            "statements" -> if (isArabic) "القوائم" else "Statements"
                            "trial_balance" -> if (isArabic) "ميزان المراجعة" else "Trial Balance"
                            "ledger" -> if (isArabic) "الأستاذ" else "Ledger"
                            "ratios" -> if (isArabic) "النسب" else "Ratios"
                            "prices" -> if (isArabic) "الأسعار" else "Prices"
                            "inflation" -> if (isArabic) "التضخم" else "Inflation"
                            "fx" -> if (isArabic) "الصرف" else "FX Rates"
                            "costing" -> if (isArabic) "التكاليف" else "Costing"
                            "inventory" -> if (isArabic) "المخزون" else "Inventory"
                            "stocktakes" -> if (isArabic) "الجرد" else "Stocktakes"
                            "scrap" -> if (isArabic) "الهدر" else "Scrap"
                            else -> if (isArabic) "الموازنات" else "Budgets"
                        }
                    }
                )
                Text(payload.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text("${payload.organizationName} • ${payload.currency} • ${payload.generatedDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(payload.methodNotes, style = MaterialTheme.typography.bodyMedium)

                if (payload.missingDataNotes.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = payload.missingDataNotes.joinToString("\n"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (exportNotice != null) {
                    Text(exportNotice!!, style = MaterialTheme.typography.bodyMedium, color = FvgPositive, fontWeight = FontWeight.SemiBold)
                }

                val ent = state.activePlanEntitlements()
                if (!ent.allowPdfXlsxExport || !ent.allowAuditAndBackup) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic)
                                "مزايا الباقة (${ent.planTier}): تصدير CSV و JSON متاح دائماً. يتطلب تصدير PDF و XLSX الباقة الاحترافية، والنسخ الاحتياطي الشامل يتطلب باقة المؤسسات."
                            else
                                "Plan (${ent.planTier}): CSV & JSON are included. PDF/XLSX requires Professional, and Full Workspace Backup requires Enterprise.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = s.exportPdf,
                        icon = Icons.Filled.PictureAsPdf,
                        enabled = ent.allowPdfXlsxExport,
                        onClick = { pdfLauncher.launch("fvg_${selectedReport}_$stamp.pdf") },
                        modifier = Modifier.weight(1f),
                        testTag = "export_pdf_button"
                    )
                    FvgGoldButton(
                        text = s.exportXlsx,
                        icon = Icons.Filled.TableChart,
                        enabled = ent.allowPdfXlsxExport,
                        onClick = { xlsxLauncher.launch("fvg_${selectedReport}_$stamp.xlsx") },
                        modifier = Modifier.weight(1f),
                        testTag = "export_xlsx_button"
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { csvLauncher.launch("fvg_${selectedReport}_$stamp.csv") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(s.exportCsv) }
                    OutlinedButton(
                        onClick = { jsonLauncher.launch("fvg_${selectedReport}_$stamp.json") },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    ) { Text(s.exportJson) }
                }
                OutlinedButton(
                    enabled = ent.allowAuditAndBackup,
                    onClick = { backupJsonLauncher.launch("fvg_workspace_backup_$stamp.json") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isArabic) "تصدير نسخة احتياطية شاملة" else "Export Full Workspace Backup (JSON)")
                }
            }
        }

        // Multi-Branch Consolidation & Inter-Company Eliminations (Enterprise / Trial)
        item {
            val ent = state.activePlanEntitlements()
            val curr = state.activeOrg?.baseCurrency ?: "SDG"
            FvgCard(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.testTag("multi_branch_consolidation_card")
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "توحيد الأداء متعدد الفروع والمصانع (${state.organizations.size})" else "Multi-Branch Consolidation Report (${state.organizations.size} Branches)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FvgFieldHelpIcon(
                        title = if (isArabic) "التوحيد المالي متعدد الفروع" else "Multi-Branch Consolidation & Eliminations",
                        explanation = if (isArabic)
                            "يقارن الإيرادات وهوامش الربح ونسب الهدر بين جميع الفروع والمصانع التابعة لك مع إلغاء المعاملات والذمم البينية تلقائياً."
                        else
                            "Compares revenue, net profit margin %, and scrap/waste % across all workspaces/branches and automatically eliminates inter-branch transactions.",
                        isArabic = isArabic
                    )
                }
                if (!ent.allowAuditAndBackup) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic)
                                "مقارنة الفروع وإلغاء المعاملات البينية متاحان لمشتركي باقة المؤسسات (Enterprise) وفترة التجربة."
                            else
                                "Multi-Branch Consolidation & Inter-company Eliminations require Trial or Enterprise tier.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(
                        value = intercompanyRevCost,
                        onValueChange = { intercompanyRevCost = it },
                        label = if (isArabic) "استبعاد مبيعات/تكلفة بينية" else "Eliminate Inter-Co Rev/COGS",
                        numeric = true,
                        modifier = Modifier.weight(1f)
                    )
                    FvgField(
                        value = intercompanyArAp,
                        onValueChange = { intercompanyArAp = it },
                        label = if (isArabic) "استبعاد ذمم بينية" else "Eliminate Inter-Co AR/AP",
                        numeric = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                FvgPrimaryButton(
                    text = if (state.isConsolidatingBranches) {
                        if (isArabic) "جاري توحيد الفروع..." else "Consolidating Branches..."
                    } else {
                        if (isArabic) "توحيد ومقارنة جميع الفروع" else "Consolidate All Branches"
                    },
                    icon = Icons.Filled.AccountTree,
                    enabled = ent.allowAuditAndBackup && !state.isConsolidatingBranches && state.organizations.isNotEmpty(),
                    onClick = { viewModel.runMultiBranchConsolidation(intercompanyRevCost, intercompanyArAp) },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "consolidate_branches_button"
                )

                val cons = state.branchConsolidation
                if (cons != null) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = if (isArabic)
                                    "صافي الإيراد الموحّد: ${cons.consolidatedRevenue} $curr | صافي الربح: ${cons.consolidatedNetProfit} $curr (${cons.consolidatedMarginPct}%)"
                                else
                                    "Consolidated Revenue: ${cons.consolidatedRevenue} $curr | Net Profit: ${cons.consolidatedNetProfit} $curr (${cons.consolidatedMarginPct}%)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isArabic)
                                    "إجمالي الهدر: ${cons.totalScrapLoss} $curr | مستبعدات بينية (إيراد/تكلفة): ${cons.intercompanyEliminated} | إجمالي الإيراد قبل الاستبعاد: ${cons.grossGroupRevenue} $curr"
                                else
                                    "Total Scrap: ${cons.totalScrapLoss} $curr | Eliminated Rev/COGS: ${cons.intercompanyEliminated} | Gross Group Rev: ${cons.grossGroupRevenue} $curr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (isArabic)
                                    "أعلى هامش ربح: ${cons.bestMarginBranch.ifBlank { "-" }} | أعلى نسبة هدر: ${cons.highestWasteBranch.ifBlank { "-" }}"
                                else
                                    "Best Margin Branch: ${cons.bestMarginBranch.ifBlank { "-" }} | Highest Waste: ${cons.highestWasteBranch.ifBlank { "-" }}",
                                style = MaterialTheme.typography.labelMedium,
                                color = FvgAntiqueGold,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    FvgBarComparisonCanvas(
                        bars = cons.rows.map { b -> b.branchName.take(10) to b.netProfit.toFloat() }
                    )
                    cons.rows.forEach { b ->
                        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${b.branchName} (${b.currency})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (isArabic) "الهامش ${b.profitMarginPct}% | الهدر ${b.scrapPctOfRevenue}%" else "Margin ${b.profitMarginPct}% | Waste ${b.scrapPctOfRevenue}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (b.netProfit < com.example.domain.math.BigDecimal.ZERO) FvgNegative else FvgPositive
                                    )
                                }
                                Text(
                                    text = if (isArabic)
                                        "إيراد: ${b.revenue} | إجمالي التكلفة: ${b.totalCost} | صافي الربح: ${b.netProfit}"
                                    else
                                        "Rev: ${b.revenue} | Total Cost: ${b.totalCost} | Net Profit: ${b.netProfit}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        items(payload.rows.take(25)) { row ->
            FvgCard {
                Text(row.joinToString("  |  "), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiAssistantAndOcrTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var question by remember { mutableStateOf("") }
    var transcriptNotice by remember { mutableStateOf<String?>(null) }
    var pendingOcrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingOcrLabel by remember { mutableStateOf("camera_invoice.jpg") }
    var cameraNotice by remember { mutableStateOf<String?>(null) }
    val stamp = remember { SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) }
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }

    val transcriptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching {
                val txt = FvgExportService.toChatTranscriptText(
                    organizationName = state.activeOrg?.name ?: "FVG Workspace",
                    aiMode = state.aiMode,
                    generatedDate = today,
                    messages = state.chatTranscript
                )
                FvgExportService.writeTextToUri(context, uri, txt)
                transcriptNotice = if (isArabic) "تم تصدير سجل محادثة المساعد المالي بنجاح." else "Assistant transcript exported (.txt)."
            }.onFailure { transcriptNotice = if (isArabic) "فشل التصدير: ${it.message}" else "Export failed: ${it.message}" }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            pendingOcrBitmap = bmp
            pendingOcrLabel = "camera_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            cameraNotice = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraLauncher.launch(null)
        } else {
            cameraNotice = if (isArabic)
                "تم رفض إذن الكاميرا — يمكنك فتح اختيار صورة بديل أدناه."
            else
                "Camera permission denied — use the gallery fallback button below."
        }
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                pendingOcrBitmap = bitmap
                pendingOcrLabel = uri.lastPathSegment ?: "invoice_doc.jpg"
                cameraNotice = null
            }.onFailure {
                cameraNotice = if (isArabic) "تعذّر فتح الصورة: ${it.message}" else "Unable to decode image: ${it.message}"
            }
        }
    }

    val ent = state.activePlanEntitlements()
    val suggestions = if (isArabic) listOf(
        "حلّل هوامش الربح ومخاطر التسعير الحالية للمنظمة",
        "كيف يؤثر معدل التضخم المسجل على تكلفة المخزون؟",
        "اشرح لي نقطة التعادل لآخر منتج تم تسعيره",
        "ما الفجوة بين القيمة الدفترية وتكلفة الاستبدال؟"
    ) else listOf(
        "Analyze our current profit margins and pricing risk",
        "How does recorded inflation impact our inventory replacement cost?",
        "Explain the Break-Even CVP for our latest costed product",
        "Summarize Book Value vs Replacement Capital Gap"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "المساعد المالي المحادثاتي الذكي" else "Conversational Financial Assistant",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FvgFieldHelpIcon(
                        title = if (isArabic) "آلية عمل المساعد المالي" else "How FVG Financial Assistant Works",
                        explanation = if (isArabic)
                            "يحاورك المساعد بسياق متواصل كما في تطبيقات الدردشة الحديثة، مستنداً حصراً على أرقام منظمتك الموثقة ومعادلات الدليل المالي دون اختلاق أرقام."
                        else
                            "Supports multi-turn conversational context anchored strictly in your verified workspace ledger, costing snapshots, and inventory data.",
                        isArabic = isArabic
                    )
                    OutlinedButton(
                        onClick = { viewModel.startNewAiConversation() },
                        modifier = Modifier.heightIn(min = 42.dp).testTag("new_conversation_button")
                    ) {
                        Icon(Icons.Filled.AddComment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isArabic) "محادثة جديدة" else "New Chat")
                    }
                }

                if (!ent.allowAiAssistant) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic)
                                "المساعد المالي الذكي مخصص للتجربة والباقة الاحترافية والمؤسسات. يرجى الترقية من تبويب المنظمة والدفع."
                            else
                                "Conversational Financial Assistant requires Trial, Professional, or Enterprise tier.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (state.aiConversations.isNotEmpty()) {
                    Text(if (isArabic) "المحادثات المحفوظة:" else "Saved Conversations:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = state.aiConversations.map { it.id },
                        selected = state.activeConversationId ?: "",
                        onSelect = { id ->
                            state.aiConversations.firstOrNull { it.id == id }?.let { viewModel.selectAiConversation(it) }
                        },
                        labelResolver = { id ->
                            state.aiConversations.firstOrNull { it.id == id }?.title?.ifBlank { id.take(8) } ?: id
                        }
                    )
                    if (state.activeConversationId != null) {
                        TextButton(onClick = { viewModel.deleteAiConversation(state.activeConversationId) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "حذف المحادثة الحالية" else "Delete Current Conversation", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Text(
                    text = if (isArabic) "وضع المساعد المالي:" else "Select Assistant Mode:",
                    style = MaterialTheme.typography.labelLarge
                )
                Box(modifier = Modifier.testTag("ai_mode_selector")) {
                    FvgChipSelector(
                        options = listOf("general", "financial", "ocr"),
                        selected = state.aiMode,
                        onSelect = { viewModel.setAiMode(it) },
                        labelResolver = { mode ->
                            when (mode) {
                                "general" -> if (isArabic) "إرشاد عام ومنهجي" else "General Methodology"
                                "financial" -> if (isArabic) "تحليل مالي حي" else "Financial Analyst"
                                else -> if (isArabic) "مساعدة الفواتير والوثائق" else "Invoice & Docs Helper"
                            }
                        }
                    )
                }

                Text(
                    text = if (isArabic) "أسئلة سريعة مقترحة:" else "Quick Prompt Suggestions:",
                    style = MaterialTheme.typography.labelLarge
                )
                FvgChipSelector(
                    options = suggestions,
                    selected = question,
                    onSelect = { question = it }
                )

                // Multi-Turn ChatGPT-Style Message Bubbles
                if (state.chatTranscript.isNotEmpty() || state.isAiStreaming) {
                    HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.35f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isArabic) "المحادثة الحية (${state.chatTranscript.size})" else "Live Chat Thread (${state.chatTranscript.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedButton(
                            onClick = { transcriptLauncher.launch("fvg_assistant_transcript_$stamp.txt") },
                            modifier = Modifier.heightIn(min = 42.dp).testTag("export_transcript_button")
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "تصدير المحادثة" else "Export TXT")
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        state.chatTranscript.forEachIndexed { idx, (role, msg) ->
                            val isUserMsg = role.startsWith("User", ignoreCase = true) || role.equals("user", ignoreCase = true)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUserMsg) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 18.dp,
                                        topEnd = 18.dp,
                                        bottomStart = if (isUserMsg) 18.dp else 4.dp,
                                        bottomEnd = if (isUserMsg) 4.dp else 18.dp
                                    ),
                                    color = if (isUserMsg) FvgDeepGreen else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(0.92f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = if (isUserMsg) Icons.Filled.Person else Icons.Filled.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = if (isUserMsg) FvgWarmGold else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = if (isUserMsg) (if (isArabic) "أنت" else "You")
                                                    else (if (isArabic) "المساعد المالي FVG" else "FVG Assistant"),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isUserMsg) FvgWarmGold else MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (!isUserMsg) {
                                                Row {
                                                    IconButton(
                                                        onClick = {
                                                            clipboard.setText(AnnotatedString(msg))
                                                            transcriptNotice = if (isArabic) "تم نسخ الرد." else "Copied reply."
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(Icons.Filled.ContentCopy, contentDescription = if (isArabic) "نسخ" else "Copy", modifier = Modifier.size(16.dp))
                                                    }
                                                    if (idx == state.chatTranscript.lastIndex && !state.isAiStreaming) {
                                                        IconButton(
                                                            onClick = { viewModel.regenerateLastAiResponse(if (isArabic) "ar" else "en") },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Filled.Refresh, contentDescription = if (isArabic) "إعادة التوليد" else "Regenerate", modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Text(
                                            text = msg,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isUserMsg) FvgWarmIvory else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (state.isAiStreaming) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth(0.92f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = FvgAntiqueGold)
                                            Text(
                                                text = if (isArabic) "جاري تحليل بياناتك..." else "Analyzing your records...",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (state.aiResponse.isNotBlank()) {
                                            Text(
                                                text = state.aiResponse,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.aiSearchQueries.isNotEmpty() || state.aiGroundingSources.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (state.aiSearchQueries.isNotEmpty()) {
                                Text(
                                    text = (if (isArabic) "استعلامات البحث المباشر: " else "Web Search Queries: ") + state.aiSearchQueries.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (state.aiGroundingSources.isNotEmpty()) {
                                Text(
                                    text = if (isArabic) "المصادر المرجعية عبر الويب:" else "Grounded Web Sources:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    state.aiGroundingSources.forEach { src ->
                                        AssistChip(
                                            onClick = { runCatching { uriHandler.openUri(src.uri) } },
                                            label = { Text(src.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.isAiOfflineFallback) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic) "تنبيه: تم تشغيل التحليل الحتمي المحلي غير المتصل لعرض الأرقام الحقيقية المخزنة." else "Notice: Offline deterministic analysis rendered from stored verified records.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                FvgField(
                    value = question,
                    onValueChange = { question = it },
                    label = if (isArabic) "اكتب سؤالك المالي هنا..." else "Ask about margins, inflation, 8 costing methods, or inventory...",
                    singleLine = false,
                    testTag = "ai_question_input"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = if (state.isAiStreaming) (if (isArabic) "جاري الرد..." else "Generating...")
                        else if (isArabic) "إرسال للمساعد" else "Send Message",
                        icon = Icons.Filled.AutoAwesome,
                        enabled = (state.aiMode == "general" || ent.allowAiAssistant) && !state.isAiStreaming && question.isNotBlank(),
                        onClick = {
                            val prompt = question
                            question = ""
                            viewModel.askAiAssistant(prompt, if (isArabic) "ar" else "en")
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "ask_ai_button"
                    )
                    if (state.isAiStreaming) {
                        OutlinedButton(
                            onClick = { viewModel.stopAiStreaming() },
                            modifier = Modifier.heightIn(min = 50.dp).testTag("stop_ai_button")
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "إيقاف" else "Stop")
                        }
                    }
                }

                if (transcriptNotice != null) {
                    Text(transcriptNotice!!, style = MaterialTheme.typography.labelLarge, color = FvgPositive)
                }
            }
        }

        // Camera-first Invoice OCR Card
        item {
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isArabic) "تصوير الفواتير والوثائق بالكاميرا (للمراجعة البشرية)" else "Camera Invoice Capture & OCR (Human Review Required)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    FvgFieldHelpIcon(
                        title = if (isArabic) "استخراج الفواتير بالكاميرا" else "Camera-First Invoice Extraction",
                        explanation = if (isArabic)
                            "التقط صورة الفاتورة مباشرة بالكاميرا ليتم استخراج المورد والتاريخ والإجمالي تلقائياً كمسودة، ثم راجعها واعتمدها قبل الترحيل."
                        else
                            "Capture invoices directly via camera to extract vendor, invoice number, date and totals as a draft for human review.",
                        isArabic = isArabic
                    )
                }
                if (!ent.allowOcr) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic) "قراءة الفواتير بالكاميرا متاحة في فترة التجربة والباقة الاحترافية والمؤسسات."
                            else "Camera Invoice OCR is enabled on Trial, Professional & Enterprise tiers.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                Text(
                    text = if (isArabic) "صوّر الفاتورة بالكاميرا مباشرة أو اختر ملفاً احتياطياً ثم راجع الحقول المستخرجة وتأكد من المعادلة قبل الحفظ." else "Capture invoice directly with camera (recommended) or pick image fallback, verify extracted totals & duplicates before approval.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (cameraNotice != null) {
                    Text(cameraNotice!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgGoldButton(
                        text = if (state.isOcrExtracting) (if (isArabic) "جاري الاستخراج..." else "Extracting...")
                        else if (isArabic) "تصوير الفاتورة بالكاميرا" else "Capture with Camera",
                        icon = Icons.Filled.CameraAlt,
                        enabled = ent.allowOcr && state.canEditFinancialData && !state.isOcrExtracting && state.activeOrg != null,
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                cameraLauncher.launch(null)
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        testTag = "capture_ocr_camera_button"
                    )
                    OutlinedButton(
                        enabled = ent.allowOcr && state.canEditFinancialData && !state.isOcrExtracting && state.activeOrg != null,
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.weight(0.8f).heightIn(min = 50.dp).testTag("pick_ocr_photo_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isArabic) "صورة" else "Gallery")
                    }
                }

                if (pendingOcrBitmap != null) {
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = pendingOcrBitmap!!.asImageBitmap(),
                        contentDescription = if (isArabic) "معاينة الفاتورة" else "Invoice preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pendingOcrBitmap = rotateBitmap90(pendingOcrBitmap!!) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "تدوير 90°" else "Rotate 90°")
                        }
                        FvgPrimaryButton(
                            text = if (isArabic) "استخراج حقول الفاتورة" else "Extract Invoice Fields",
                            enabled = !state.isOcrExtracting,
                            onClick = {
                                val bmp = pendingOcrBitmap
                                if (bmp != null) {
                                    viewModel.extractOcrInvoice(bmp, pendingOcrLabel)
                                    pendingOcrBitmap = null
                                }
                            },
                            modifier = Modifier.weight(1f),
                            testTag = "run_ocr_extract_button"
                        )
                    }
                }
            }
        }

        items(state.ocrDrafts.reversed(), key = { it.id }) { draft ->
            var vName by remember(draft.id) { mutableStateOf(draft.vendorName) }
            var invNo by remember(draft.id) { mutableStateOf(draft.invoiceNumber) }
            var dDate by remember(draft.id) { mutableStateOf(draft.documentDate) }
            var dCurr by remember(draft.id) { mutableStateOf(draft.currency) }
            var dSub by remember(draft.id) { mutableStateOf(draft.subtotalAmount) }
            var dDisc by remember(draft.id) { mutableStateOf(draft.discountAmount) }
            var dTax by remember(draft.id) { mutableStateOf(draft.taxAmount) }
            var dAmt by remember(draft.id) { mutableStateOf(draft.totalAmount) }
            var dSum by remember(draft.id) { mutableStateOf(draft.lineSummary) }

            val mathCheck = remember(dSub, dDisc, dTax, dAmt) {
                FvgFinancialEngine.validateOcrArithmetic(dSub, dDisc, dTax, dAmt)
            }

            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(draft.fileName, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgRoleBadge(localizePayStatus(draft.reviewStatus, isArabic))
                }
                if (draft.confidenceNote.isNotBlank() || !mathCheck.isConsistent || draft.duplicateWarning.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!mathCheck.isConsistent || draft.duplicateWarning.isNotBlank()) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (draft.confidenceNote.isNotBlank()) {
                                Text(
                                    text = if (isArabic) "ملاحظة الاستخراج: ${draft.confidenceNote}" else "OCR Confidence: ${draft.confidenceNote}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (!mathCheck.isConsistent) {
                                Text(
                                    text = if (isArabic) "⚠ عدم تطابق حسابي: المتوقع ${mathCheck.expectedTotal} مقابل الإجمالي ${mathCheck.extractedTotal}"
                                    else "⚠ Math discrepancy: expected ${mathCheck.expectedTotal} vs entered ${mathCheck.extractedTotal}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (draft.duplicateWarning.isNotBlank()) {
                                Text(
                                    text = "⚠ ${draft.duplicateWarning}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(vName, { vName = it }, if (isArabic) "اسم المورد" else "Vendor Name", modifier = Modifier.weight(1.3f))
                    FvgField(invNo, { invNo = it }, if (isArabic) "رقم الفاتورة" else "Invoice #", modifier = Modifier.weight(0.9f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(dDate, { dDate = it }, if (isArabic) "التاريخ" else "Date", modifier = Modifier.weight(1f))
                    FvgField(dCurr, { dCurr = it }, if (isArabic) "العملة" else "Currency", modifier = Modifier.weight(0.7f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(dSub, { dSub = it }, if (isArabic) "المجموع الفرعي" else "Subtotal", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(dDisc, { dDisc = it }, if (isArabic) "الخصم" else "Discount", numeric = true, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgField(dTax, { dTax = it }, if (isArabic) "الضريبة" else "Tax", numeric = true, modifier = Modifier.weight(1f))
                    FvgField(dAmt, { dAmt = it }, if (isArabic) "الإجمالي النهائي" else "Final Total", numeric = true, modifier = Modifier.weight(1f))
                }
                FvgField(dSum, { dSum = it }, if (isArabic) "ملخص البنود المستخرجة" else "Extracted Line Summary", singleLine = false)
                if (draft.reviewStatus == "pending_review" && state.canEditFinancialData) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgPrimaryButton(
                            text = if (isArabic) "اعتماد المسودة" else "Verify Draft",
                            onClick = { viewModel.reviewOcrDraft(draft, vName, invNo, dDate, dCurr, dSub, dDisc, dTax, dAmt, dSum, "verified") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.reviewOcrDraft(draft, vName, invNo, dDate, dCurr, dSub, dDisc, dTax, dAmt, dSum, "rejected") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) { Text(if (isArabic) "رفض" else "Reject") }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrgRbacBillingSettingsTab(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    onOpenProfile: () -> Unit,
    onOpenHelp: () -> Unit,
    onOpenOwnerAdmin: () -> Unit,
    onSignOutClick: () -> Unit
) {
    val org = state.activeOrg
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var editOrgName by remember(org?.id) { mutableStateOf(org?.name ?: "") }
    var editCurrency by remember(org?.id) { mutableStateOf(org?.baseCurrency ?: "SDG") }
    var editSector by remember(org?.id) { mutableStateOf(org?.sectorType ?: "manufacturing") }
    var editTagline by remember(org?.id) { mutableStateOf(org?.brandTagline.orEmpty()) }
    var editCountry by remember(org?.id) { mutableStateOf(org?.countryState.orEmpty()) }
    var editFiscalStart by remember(org?.id) { mutableStateOf(org?.fiscalYearStart ?: "01-01") }
    var editTaxEnabled by remember(org?.id) { mutableStateOf(org?.taxEnabled ?: false) }
    var editTaxRate by remember(org?.id) { mutableStateOf(org?.defaultTaxRatePct ?: "0.00") }

    var inviteUid by remember { mutableStateOf("") }
    var inviteRole by remember { mutableStateOf("accountant") }
    var newWorkspaceName by remember { mutableStateOf("") }
    var newWorkspaceSector by remember { mutableStateOf("manufacturing") }

    // Manual Sudan Payment Proof submission state (Customer side ONLY submits pending_verification)
    val context = LocalContext.current
    var payPlanTier by remember { mutableStateOf("professional") }
    var payMethod by remember { mutableStateOf("bankak") }
    var payAmount by remember { mutableStateOf("29.00") }
    var payCurrency by remember { mutableStateOf("USD") }
    var senderName by remember { mutableStateOf("") }
    var referenceNo by remember { mutableStateOf("") }
    var last4Digits by remember { mutableStateOf("") }
    var transferDate by remember { mutableStateOf(today) }
    var attachmentNote by remember { mutableStateOf("") }
    var payNotes by remember { mutableStateOf("") }
    var showSmsParser by remember { mutableStateOf(false) }
    var smsPasteText by remember { mutableStateOf("") }
    var receiptScanNotice by remember { mutableStateOf<String?>(null) }

    fun applyParsedReceipt(parsed: FvgFinancialEngine.BankReceiptParsed) {
        if (parsed.paymentMethod.isNotBlank()) payMethod = parsed.paymentMethod
        if (parsed.amount.isNotBlank()) payAmount = parsed.amount
        if (parsed.currency.isNotBlank()) payCurrency = parsed.currency
        if (parsed.referenceNo.isNotBlank()) referenceNo = parsed.referenceNo
        if (parsed.senderName.isNotBlank()) senderName = parsed.senderName
        if (parsed.last4Digits.isNotBlank()) last4Digits = parsed.last4Digits.takeLast(4)
        if (parsed.transferDate.isNotBlank()) transferDate = parsed.transferDate
        if (parsed.confidenceNote.isNotBlank()) attachmentNote = parsed.confidenceNote
    }

    val receiptCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            receiptScanNotice = null
            viewModel.parseBankReceiptBitmap(bmp, payPlanTier) { parsed ->
                applyParsedReceipt(parsed)
            }
        }
    }
    val receiptCamPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            receiptCameraLauncher.launch(null)
        } else {
            receiptScanNotice = if (isArabic) "تم رفض إذن الكاميرا — استخدم اختيار صورة الإيصال أو لصق نص الإشعار." else "Camera permission denied — use image picker or SMS paste below."
        }
    }
    val receiptPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                receiptScanNotice = null
                viewModel.parseBankReceiptBitmap(bmp, payPlanTier) { parsed ->
                    applyParsedReceipt(parsed)
                }
            }.onFailure {
                receiptScanNotice = if (isArabic) "تعذر قراءة صورة الإيصال: ${it.message}" else "Could not decode receipt image: ${it.message}"
            }
        }
    }

    LaunchedEffect(payPlanTier, payAmount, payCurrency, referenceNo) {
        if (payAmount.isNotBlank() || referenceNo.isNotBlank()) {
            viewModel.updateReceiptLiveMatch(payPlanTier, payAmount, payCurrency, referenceNo)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.isOnline || state.offlineQueue.isNotEmpty() || state.offlineConflicts.isNotEmpty()) {
            item {
                FvgCard(
                    containerColor = if (state.offlineConflicts.isNotEmpty()) MaterialTheme.colorScheme.errorContainer else FvgAntiqueGold.copy(alpha = 0.16f)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isArabic)
                                "المزامنة الهجينة: ${if (state.isOnline) "متصل" else "غير متصل"} | طابور الانتظار: ${state.offlineQueue.size} | تعارضات: ${state.offlineConflicts.size}"
                            else
                                "Hybrid Sync: ${if (state.isOnline) "Online" else "Offline"} | Queued: ${state.offlineQueue.size} | Conflicts: ${state.offlineConflicts.size}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (state.offlineQueue.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.flushOfflineQueue() },
                                enabled = state.isOnline && !state.isBusy,
                                modifier = Modifier.heightIn(min = 42.dp)
                            ) {
                                Text(if (isArabic) "مزامنة" else "Sync")
                            }
                        }
                    }
                }
            }
        }

        if (state.organizations.size > 1) {
            item {
                FvgCard {
                    Text(if (isArabic) "تبديل المنظمة النشطة" else "Switch Active Workspace", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    FvgChipSelector(
                        options = state.organizations.map { it.id },
                        selected = org?.id ?: "",
                        onSelect = { viewModel.switchOrganization(it) },
                        labelResolver = { id -> state.organizations.firstOrNull { it.id == id }?.name ?: id }
                    )
                }
            }
        }

        if (org != null) {
            item {
                FvgTrialCountdownBanner(
                    trialEndsAtMillis = state.effectiveTrialEndsMillis(),
                    isArabic = isArabic,
                    durationHours = state.effectiveTrialDurationHours(),
                    serverAnchorMillis = state.serverAnchorMillis,
                    elapsedBaselineMillis = state.elapsedBaselineRealtimeMillis
                )
            }

            // Payment Proof Submission Card (Zero customer self-approval)
            item {
                FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = if (isArabic) "رفع إثبات الدفع للتفعيل (بنكك / فوري / أوكاش / تحويل بنكي)" else "Submit Payment Proof (Bankak / Fawry / O-Cash / Bank Transfer)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = if (isArabic)
                                "ملاحظة التوثيق: يتم إرسال طلب الدفع بحالة [قيد التحقق] إلى مالكة المنصة للمراجعة والاعتماد عبر البريد الموثق Financialvisionguide@gmail.com. لا يتم جمع أرقام بطاقات بنكية كاملة أو CVV — يمكنك مسح إشعار بنكك/فوري بالكاميرا أو لصق الرسالة للمطابقة الفورية."
                            else
                                "Verification Notice: Payment proofs are queued as [Pending Verification] for the Platform Owner (Financialvisionguide@gmail.com). Scan a Bankak/Fawry receipt or paste an SMS notification for automated pre-matching.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    val dynamicPlans = state.platformPlans.ifEmpty { PlanEntitlements.defaultPlans() }
                    FvgChipSelector(
                        options = listOf("starter", "professional", "enterprise"),
                        selected = payPlanTier,
                        onSelect = { tierKey ->
                            payPlanTier = tierKey
                            val plan = dynamicPlans.firstOrNull { it.key == tierKey }
                            payAmount = if (payCurrency == "SDG") (plan?.priceSdg ?: "65000") else (plan?.priceUsd ?: "29.00")
                        },
                        labelResolver = { tier ->
                            val plan = dynamicPlans.firstOrNull { it.key == tier }
                            val usd = plan?.priceUsd ?: when (tier) { "starter" -> "12"; "professional" -> "29"; else -> "79" }
                            val sdg = plan?.priceSdg ?: when (tier) { "starter" -> "28000"; "professional" -> "65000"; else -> "175000" }
                            val name = if (isArabic) (plan?.nameAr ?: tier) else (plan?.nameEn ?: tier)
                            "$name ($usd$ / $sdg ج.س)"
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !state.isParsingReceipt,
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    receiptCameraLauncher.launch(null)
                                } else {
                                    receiptCamPermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("scan_receipt_camera_button")
                        ) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "تصوير الإيصال" else "Scan Receipt", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            enabled = !state.isParsingReceipt,
                            onClick = {
                                receiptPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("pick_receipt_photo_button")
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isArabic) "اختيار صورة" else "Pick Receipt", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    OutlinedButton(
                        onClick = { showSmsParser = !showSmsParser },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp)
                    ) {
                        Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "تحليل رسالة / إشعار بنكك أو فوري النصي" else "Paste Bankak / Fawry SMS Notification")
                    }
                    if (showSmsParser) {
                        FvgField(
                            value = smsPasteText,
                            onValueChange = { smsPasteText = it },
                            label = if (isArabic) "ألصق نص إشعار بنكك أو فوري هنا..." else "Paste Bankak or Fawry transfer SMS/notice...",
                            singleLine = false,
                            testTag = "payment_sms_paste_input"
                        )
                        FvgPrimaryButton(
                            text = if (isArabic) "استخراج ومطابقة البيانات آلياً" else "Extract & Auto-Match SMS",
                            icon = Icons.Filled.AutoAwesome,
                            enabled = smsPasteText.isNotBlank(),
                            onClick = {
                                val parsed = viewModel.parseBankReceiptSmsText(smsPasteText, payPlanTier)
                                applyParsedReceipt(parsed)
                                receiptScanNotice = if (isArabic)
                                    "تم استخراج بيانات الإيصال وتعبئة الحقول تلقائياً — راجعها قبل الإرسال."
                                else
                                    "Extracted receipt fields automatically — verify before submitting."
                            },
                            modifier = Modifier.fillMaxWidth(),
                            testTag = "parse_payment_sms_button"
                        )
                    }
                    if (state.isParsingReceipt) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(if (isArabic) "جاري تحليل صورة الإيصال بالذكاء الاصطناعي..." else "Extracting transfer details from receipt...", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (receiptScanNotice != null) {
                        Text(receiptScanNotice!!, style = MaterialTheme.typography.labelMedium, color = FvgPositive)
                    }

                    FvgChipSelector(
                        options = listOf("bankak", "fawry", "ocash", "bank_transfer"),
                        selected = payMethod,
                        onSelect = { payMethod = it },
                        labelResolver = { m ->
                            when (m) {
                                "bankak" -> if (isArabic) "بنكك" else "Bankak"
                                "fawry" -> if (isArabic) "فوري" else "Fawry"
                                "ocash" -> if (isArabic) "أوكاش" else "O-Cash"
                                else -> if (isArabic) "تحويل بنكي" else "Bank Transfer"
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(payAmount, { payAmount = it }, if (isArabic) "المبلغ" else "Amount", numeric = true, modifier = Modifier.weight(1f), testTag = "payment_amount_input")
                        FvgField(payCurrency, { payCurrency = it }, if (isArabic) "العملة" else "Currency", modifier = Modifier.weight(0.7f))
                        FvgField(transferDate, { transferDate = it }, if (isArabic) "التاريخ" else "Date", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(senderName, { senderName = it }, if (isArabic) "اسم المحوّل" else "Sender Name", modifier = Modifier.weight(1f), testTag = "payment_sender_input")
                        FvgField(referenceNo, { referenceNo = it }, if (isArabic) "رقم المرجع" else "Reference No", modifier = Modifier.weight(1f), testTag = "payment_ref_input")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(
                            value = last4Digits,
                            onValueChange = { if (it.length <= 4 && it.all { ch -> ch.isDigit() }) last4Digits = it },
                            label = if (isArabic) "آخر 4 أرقام للمعاملة/الحساب" else "Last 4 Digits",
                            numeric = true,
                            modifier = Modifier.weight(1f),
                            testTag = "payment_last4_input"
                        )
                        FvgField(
                            value = attachmentNote,
                            onValueChange = { attachmentNote = it },
                            label = if (isArabic) "بيان الإيصال / الحساب" else "Receipt / Account Note",
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                    FvgField(payNotes, { payNotes = it }, if (isArabic) "ملاحظة باسم المستخدم والمبلغ" else "User Name & Transfer Notes")

                    val liveMatch = state.lastParsedReceiptMatch
                    if (liveMatch != null && (payAmount.isNotBlank() || referenceNo.isNotBlank())) {
                        val matchBg = when (liveMatch.status) {
                            "exact_match" -> FvgPositive.copy(alpha = 0.14f)
                            "close_match" -> FvgAntiqueGold.copy(alpha = 0.16f)
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                        val matchFg = when (liveMatch.status) {
                            "exact_match" -> FvgPositive
                            "close_match" -> FvgAntiqueGold
                            else -> MaterialTheme.colorScheme.error
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = matchBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("live_receipt_match_banner")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isArabic) liveMatch.noteAr else liveMatch.noteEn,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = matchFg
                                )
                                Text(
                                    text = if (isArabic)
                                        "سعر الباقة المتوقع: ${liveMatch.expectedAmountLabel}"
                                    else
                                        "Expected Plan Fee: ${liveMatch.expectedAmountLabel}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    FvgGoldButton(
                        text = if (isArabic) "إرسال إثبات الدفع للمراجعة" else "Submit Payment Proof [Pending Verification]",
                        icon = Icons.Filled.ReceiptLong,
                        enabled = state.canAdminOrg && senderName.isNotBlank() && referenceNo.trim().length >= 2 && !state.isBusy,
                        onClick = {
                            viewModel.submitPaymentRequest(
                                payPlanTier, payMethod, payAmount, payCurrency,
                                senderName, referenceNo, transferDate, payNotes,
                                last4Digits, attachmentNote
                            )
                            senderName = ""; referenceNo = ""; payNotes = ""; last4Digits = ""; attachmentNote = ""; smsPasteText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "submit_payment_proof_button"
                    )
                }
            }

            // Read-Only Payment Requests History on Customer Screen
            if (state.paymentRequests.isNotEmpty()) {
                items(state.paymentRequests.reversed(), key = { it.id }) { req ->
                    FvgCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${req.paymentMethod.uppercase()} • ${req.referenceNo}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            FvgRoleBadge(localizePayStatus(req.status, isArabic))
                        }
                        Text(
                            text = if (isArabic) "الباقة: ${req.planTier} | المبلغ: ${req.amount} ${req.currency} | المحوّل: ${req.senderName} (${req.transferDate})"
                            else "Plan: ${req.planTier} | Amount: ${req.amount} ${req.currency} | Sender: ${req.senderName} (${req.transferDate})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (req.last4Digits.isNotBlank()) {
                            Text(
                                text = if (isArabic) "آخر 4 أرقام: ${req.last4Digits}" else "Last 4 Digits: ${req.last4Digits}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (req.reviewNotes.isNotBlank()) {
                            Text(
                                text = if (isArabic) "ملاحظة المراجعة: ${req.reviewNotes}" else "Owner Review Note: ${req.reviewNotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = FvgAntiqueGold
                            )
                        }
                    }
                }
            }

            // Organization Metadata Settings (NO self-service planTier or subscriptionStatus editing)
            item {
                FvgCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isArabic) "إعدادات المنظمة والضرائب والهوية" else "Organization & Fiscal Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        FvgRoleBadge("${org.planTier} • ${localizePayStatus(org.subscriptionStatus, isArabic)}")
                    }
                    FvgField(editOrgName, { editOrgName = it }, if (isArabic) "اسم المنظمة" else "Organization Name")
                    FvgField(editTagline, { editTagline = it }, if (isArabic) "الشعار الفرعي للمنظمة" else "Organization Tagline")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(editCountry, { editCountry = it }, if (isArabic) "الدولة / الولاية" else "Country / State", modifier = Modifier.weight(1f))
                        FvgField(editFiscalStart, { editFiscalStart = it }, if (isArabic) "بداية السنة (MM-DD)" else "Fiscal Start", modifier = Modifier.weight(1f))
                    }
                    Text(if (isArabic) "القطاع:" else "Business Sector:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = FvgFinancialEngine.FVG_SECTORS.map { it.key },
                        selected = editSector,
                        onSelect = { editSector = it },
                        labelResolver = { key ->
                            val sec = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == key }
                            if (sec == null) key else if (isArabic) sec.nameAr else sec.nameEn
                        }
                    )
                    Text(if (isArabic) "العملة الأساسية:" else "Base Currency:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(listOf("SDG", "USD", "SAR", "AED", "EUR", "EGP"), editCurrency, { editCurrency = it })
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(checked = editTaxEnabled, onCheckedChange = { editTaxEnabled = it })
                        Text(if (isArabic) "تفعيل الضريبة الافتراضية" else "Enable Default Tax", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        FvgField(editTaxRate, { editTaxRate = it }, if (isArabic) "النسبة %" else "Tax %", numeric = true, modifier = Modifier.weight(0.8f))
                    }
                    FvgPrimaryButton(
                        text = if (isArabic) "حفظ إعدادات المنظمة" else "Save Organization Settings",
                        enabled = state.canAdminOrg && editOrgName.isNotBlank() && !state.isBusy,
                        onClick = {
                            viewModel.updateOrganizationSettings(
                                name = editOrgName,
                                currency = editCurrency,
                                sectorType = editSector,
                                brandTagline = editTagline,
                                countryState = editCountry,
                                fiscalYearStart = editFiscalStart,
                                taxEnabled = editTaxEnabled,
                                defaultTaxRatePct = editTaxRate
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.canAdminOrg) {
                item {
                    FvgCard {
                        Text(if (isArabic) "إدارة أعضاء المنظمة ومصفوفة الصلاحيات" else "Team & Role Access Matrix (RBAC)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (isArabic) "المالك/المدير: تحكم كامل والاعتمادات | المحاسب: القيود والتكاليف والمخزون | المشاهد: عرض فقط"
                            else "Owner/Admin: Full write, Approvals & Roles | Accountant: Journals, Costing, Prices, Inventory | Viewer: Read-only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FvgField(inviteUid, { inviteUid = it }, if (isArabic) "معرّف العضو (Firebase UID)" else "Member Firebase UID")
                        FvgChipSelector(listOf("admin", "accountant", "viewer"), inviteRole, { inviteRole = it })
                        FvgGoldButton(
                            text = if (isArabic) "تحديث صلاحية العضو" else "Grant / Update Member Role",
                            enabled = inviteUid.isNotBlank() && !state.isBusy,
                            onClick = {
                                viewModel.updateMemberRole(inviteUid, inviteRole)
                                inviteUid = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "إنشاء مساحة عمل إضافية" else "Create Additional Organization", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                FvgField(newWorkspaceName, { newWorkspaceName = it }, if (isArabic) "اسم المنظمة الجديدة" else "New Organization Name")
                FvgChipSelector(
                    options = FvgFinancialEngine.FVG_SECTORS.map { it.key },
                    selected = newWorkspaceSector,
                    onSelect = { newWorkspaceSector = it },
                    labelResolver = { key ->
                        val sec = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == key }
                        if (sec == null) key else if (isArabic) sec.nameAr else sec.nameEn
                    }
                )
                FvgGoldButton(
                    text = if (isArabic) "إنشاء منظمة جديدة (تجربة 7 أيام / SDG)" else "Create Workspace (7-Day Trial / SDG)",
                    enabled = newWorkspaceName.isNotBlank() && !state.isBusy,
                    onClick = {
                        viewModel.createOrganization(newWorkspaceName, "SDG", newWorkspaceSector)
                        newWorkspaceName = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item {
            FvgCard {
                Text(if (isArabic) "الحساب والدعم ولوحة التحكم" else "Profile, Help & Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenProfile, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "ملفي الشخصي" else "My Profile")
                    }
                    OutlinedButton(onClick = onOpenHelp, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isArabic) "المساعدة" else "Help & Support")
                    }
                }
                if (state.isPlatformOwner) {
                    FvgGoldButton(
                        text = if (isArabic) "فتح لوحة مالكة المنصة (الإدارة العامة)" else "Open Platform Owner Console",
                        icon = Icons.Filled.AdminPanelSettings,
                        onClick = onOpenOwnerAdmin,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = onSignOutClick,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(fvgStrings(isArabic).signOut)
                }
            }
        }

        item {
            Text(
                text = if (isArabic) "سجل التدقيق غير القابل للتعديل (${state.auditLogs.size})" else "Immutable Audit Trail (${state.auditLogs.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        items(state.auditLogs.reversed().take(30), key = { it.id }) { log ->
            FvgCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(log.action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(log.entityType, style = MaterialTheme.typography.labelSmall, color = FvgAntiqueGold)
                }
                Text(log.summary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = if (isArabic) "بواسطة: ${log.actorEmail}" else "By: ${log.actorEmail}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
