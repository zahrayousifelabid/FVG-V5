package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AdminNotificationRecord
import com.example.data.model.AuditLogRecord
import com.example.data.model.PaymentRequestRecord
import com.example.data.model.UserProfile
import com.example.ui.auth.AdminCredentialHelper
import com.example.ui.components.FvgCard
import com.example.ui.components.FvgChipSelector
import com.example.ui.components.FvgGoldButton
import com.example.ui.components.FvgRoleBadge
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OwnerOneTimeSetupGateScreen(
    isArabic: Boolean,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var adminEmail by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showNewPass by remember { mutableStateOf(false) }
    var showConfirmPass by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .testTag("owner_one_time_setup_gate"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(listOf(FvgInkDark, FvgDeepGreen, FvgForestGreen)),
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = FvgWarmGold.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, FvgWarmGold)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AdminPanelSettings,
                            contentDescription = null,
                            tint = FvgWarmGold,
                            modifier = Modifier.padding(10.dp).size(28.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isArabic) "الإعداد الأمني الأولي للإدارة" else "One-Time Admin Security Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = FvgWarmIvory
                        )
                        Text(
                            text = if (isArabic) "تعيين كلمة مرور خاصة قبل فتح لوحة التحكم" else "Establish permanent credentials to unlock /admin",
                            style = MaterialTheme.typography.labelSmall,
                            color = FvgWarmGold
                        )
                    }
                }
            }

            FvgCard {
                Text(
                    text = if (isArabic)
                        "لأقصى درجات الأمان، لا توجد كلمة مرور افتراضية مسبقة. أدخلي بريد الإدارة المعتمد وحددي كلمة مرور دائمة (8 أحرف على الأقل تتضمن أحرفاً وأرقاماً/رموزاً) لإتمام الإعداد لمرة واحدة."
                    else
                        "For maximum security, no default admin password is shipped. Enter the authorized administrator email and choose a permanent password (8+ characters with letters and numbers/symbols) to complete one-time setup.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = adminEmail,
                    onValueChange = { adminEmail = it },
                    label = { Text(if (isArabic) "بريد المسؤول المعتمد" else "Authorized Administrator Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("owner_gate_email_input")
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(if (isArabic) "كلمة المرور الإدارية الجديدة" else "New Admin Password") },
                    visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showNewPass = !showNewPass }) {
                            Icon(
                                imageVector = if (showNewPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isArabic) "إظهار أو إخفاء كلمة المرور" else "Toggle password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("owner_gate_new_pass_input")
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(if (isArabic) "تأكيد كلمة المرور الإدارية" else "Confirm Admin Password") },
                    visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { showConfirmPass = !showConfirmPass }) {
                            Icon(
                                imageVector = if (showConfirmPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (isArabic) "إظهار أو إخفاء التأكيد" else "Toggle confirm password visibility"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("owner_gate_confirm_pass_input")
                )

                setupError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = FvgNegative.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = FvgNegative,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                FvgGoldButton(
                    text = if (isArabic) "حفظ كلمة المرور وفتح لوحة الإدارة" else "Save Credentials & Unlock Admin",
                    icon = Icons.Filled.Security,
                    enabled = !isSaving && adminEmail.isNotBlank() && newPassword.isNotBlank() && confirmPassword.isNotBlank(),
                    onClick = {
                        setupError = null
                        isSaving = true
                        AdminCredentialHelper.completeOneTimeInitialSetup(
                            context = context,
                            email = adminEmail,
                            newPassword = newPassword,
                            confirmPassword = confirmPassword,
                            isArabic = isArabic
                        ).onSuccess {
                            scope.launch {
                                runCatching { FirebaseAuth.getInstance().currentUser?.updatePassword(newPassword)?.await() }
                                isSaving = false
                                onCompleted()
                            }
                        }.onFailure { e ->
                            isSaving = false
                            setupError = e.message ?: if (isArabic) "تعذر إتمام الإعداد الأمني." else "Failed to complete admin setup."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("owner_gate_submit_button")
                )
            }
        }
    }
}

@Composable
fun AdminMetricTile(
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = accent)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun AdminLabeledLineChart(
    points: List<Pair<String, Float>>,
    lineColor: Color,
    secondaryPoints: List<Pair<String, Float>>,
    secondaryColor: Color,
    legendPrimary: String,
    legendSecondary: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).background(lineColor, RoundedCornerShape(3.dp)))
                Text(legendPrimary, style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(10.dp).background(secondaryColor, RoundedCornerShape(3.dp)))
                Text(legendSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
        ) {
            if (points.isEmpty()) return@Canvas
            val maxV = maxOf(
                points.maxOfOrNull { it.second } ?: 1f,
                secondaryPoints.maxOfOrNull { it.second } ?: 1f,
                1f
            )
            val padTop = 12f
            val padBottom = 12f
            val usableH = (size.height - padTop - padBottom).coerceAtLeast(10f)
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)

            for (g in 0..3) {
                val y = padTop + usableH * (g / 3f)
                drawLine(
                    color = FvgWarmStone.copy(alpha = 0.45f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5f
                )
            }

            fun drawSeries(series: List<Pair<String, Float>>, col: Color) {
                if (series.isEmpty()) return
                val path = Path()
                series.forEachIndexed { idx, (_, v) ->
                    val x = idx * stepX
                    val y = size.height - padBottom - ((v / maxV) * usableH)
                    if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = col, style = Stroke(width = 5f, cap = StrokeCap.Round))
                series.forEachIndexed { idx, (_, v) ->
                    val x = idx * stepX
                    val y = size.height - padBottom - ((v / maxV) * usableH)
                    drawCircle(color = col, radius = 7f, center = Offset(x, y))
                }
            }

            drawSeries(points, lineColor)
            drawSeries(secondaryPoints, secondaryColor)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            points.forEach { (lbl, _) ->
                Text(lbl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

fun buildDailySeries(
    userDates: List<Date>,
    visitDates: List<Date>,
    isArabic: Boolean
): List<Triple<String, Int, Int>> {
    val fmtKey = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val fmtLbl = SimpleDateFormat("MM/dd", Locale.US)
    val dayMillis = 86400_000L
    val todayStart = System.currentTimeMillis()
    val userMap = userDates.groupingBy { fmtKey.format(it) }.eachCount()
    val visitMap = visitDates.groupingBy { fmtKey.format(it) }.eachCount()
    return (5 downTo 0).map { offset ->
        val d = Date(todayStart - offset * dayMillis)
        val key = fmtKey.format(d)
        val lbl = if (offset == 0) (if (isArabic) "اليوم" else "Today") else fmtLbl.format(d)
        Triple(lbl, userMap[key] ?: 0, visitMap[key] ?: 0)
    }
}

@Composable
fun OwnerAuditLogsDialog(
    orgName: String,
    logs: List<AuditLogRecord>,
    isLoading: Boolean,
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isArabic) "سجل التدقيق: $orgName" else "Audit Trail: $orgName",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 380.dp)) {
                when {
                    isLoading -> {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Text(if (isArabic) "جاري تحميل السجل..." else "Loading audit logs...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    logs.isEmpty() -> {
                        Text(
                            text = if (isArabic) "لا توجد سجلات تدقيق مسجلة لهذه المنظمة بعد." else "No audit log entries recorded for this organization yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(logs, key = { it.id.ifBlank { "${it.action}_${it.entityId}_${it.createdAt?.seconds ?: 0L}" } }) { log ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            FvgRoleBadge(log.action.ifBlank { "ACTION" })
                                            Text(
                                                text = log.createdAt?.toDate()?.let { fmt.format(it) } ?: "",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = log.summary.ifBlank { "${log.entityType} (${log.entityId})" },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                        if (log.actorEmail.isNotBlank()) {
                                            Text(
                                                text = "${if (isArabic) "المنفّذ:" else "Actor:"} ${log.actorEmail}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("owner_audit_dialog_close")) {
                Text(if (isArabic) "إغلاق" else "Close")
            }
        }
    )
}

fun buildAdminAnalyticsCsv(state: FvgUiState, isArabic: Boolean): String {
    fun esc(v: String): String = "\"${v.replace("\"", "\"\"")}\""
    val sb = StringBuilder()
    sb.appendLine("# FVG Platform Executive Admin Analytics & Traffic Report")
    sb.appendLine("# Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
    sb.appendLine("# Organization: دليل رؤيتك المالي | Financial Vision Guide (FVG)")
    sb.appendLine()
    
    // KPI Overview
    sb.appendLine(if (isArabic) "القسم,المؤشر,القيمة,ملاحظات" else "Section,Metric,Value,Notes")
    val activeOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "active" }
    val trialOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "trial" }
    val expiredOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "expired" }
    val cancelledOrgs = state.ownerAllOrgs.count { it.subscriptionStatus == "cancelled" }
    val androidVisits = state.ownerPlatformVisits.count { it.platform == "android" && it.screen != "android_install" }
    val webVisits = state.ownerPlatformVisits.count { it.platform == "web_pwa" && it.screen != "pwa_install" }
    val androidInstalls = state.ownerPlatformVisits.count { it.screen == "android_install" }
    val pwaInstalls = state.ownerPlatformVisits.count { it.screen == "pwa_install" }
    val pendingPays = state.ownerAllPaymentRequests.count { it.status == "pending_verification" }
    val approvedPays = state.ownerAllPaymentRequests.count { it.status == "approved" }
    val rejectedPays = state.ownerAllPaymentRequests.count { it.status == "rejected" }

    sb.appendLine("SUMMARY,Registered Users,${state.ownerAllUsers.size},Total authenticated profiles")
    sb.appendLine("SUMMARY,Total Organizations,${state.ownerAllOrgs.size},Active=$activeOrgs Trial=$trialOrgs Expired=$expiredOrgs Suspended=$cancelledOrgs")
    sb.appendLine("SUMMARY,Platform Visits,${androidVisits + webVisits},Android=$androidVisits Web=$webVisits")
    sb.appendLine("SUMMARY,App Downloads & Installs,${androidInstalls + pwaInstalls},Android APK=$androidInstalls Web PWA=$pwaInstalls")
    sb.appendLine("SUMMARY,Payment Requests,${state.ownerAllPaymentRequests.size},Approved=$approvedPays Pending=$pendingPays Rejected=$rejectedPays")
    sb.appendLine("SUMMARY,Support Tickets,${state.ownerSupportTickets.size},Open=${state.ownerSupportTickets.count { it.status == "open" }} Resolved=${state.ownerSupportTickets.count { it.status == "resolved" }}")
    sb.appendLine("SUMMARY,Security & System Alerts,${state.ownerAdminNotifications.size},Unread=${state.ownerAdminNotifications.count { !it.acknowledged }}")
    sb.appendLine()

    // Site Traffic & Download Events
    sb.appendLine(if (isArabic) "نوع_الحدث,معرف_الحدث,المنصة,الشاشة_أو_الإجراء,رمز_الزائر,معرف_المستخدم,التاريخ_والوقت" else "EVENT_TYPE,EventId,Platform,ScreenOrAction,VisitorKey,UserId,Timestamp")
    state.ownerPlatformVisits.forEach { v ->
        val eventType = if (v.screen.contains("install")) "APP_INSTALL" else "SITE_VISIT"
        sb.appendLine(listOf(eventType, v.id, v.platform, v.screen, v.visitorKey, v.userId, v.createdAt?.toDate()?.toString() ?: "").joinToString(",") { esc(it) })
    }
    sb.appendLine()

    // Registered Users Directory
    sb.appendLine(if (isArabic) "المستخدم,المعرف,الاسم_الظاهر,البريد_الإلكتروني,اللغة_المفضلة,حالة_بريد_الترحيب,عدد_المنظمات" else "USER_ROW,UserId,DisplayName,Email,Language,WelcomeMailStatus,OrgsCount")
    state.ownerAllUsers.forEach { u ->
        val mail = state.ownerMailOutbox.firstOrNull { m ->
            m.mailType == "welcome" && m.to.any { it.equals(u.email, ignoreCase = true) }
        }
        val mailState = mail?.delivery?.get("state")?.toString() ?: if (mail != null) "QUEUED" else "NOT_QUEUED"
        val userOrgCount = state.ownerAllOrgs.count { it.ownerId == u.userId || u.userId in it.memberUids }
        sb.appendLine(listOf("USER", u.userId, u.displayName, u.email, u.preferredLanguage, mailState, userOrgCount.toString()).joinToString(",") { esc(it) })
    }
    sb.appendLine()

    // Organizations Directory
    sb.appendLine(if (isArabic) "المنظمة,المعرف,اسم_المنظمة,القطاع,العملة_الأساسية,باقة_الاشتراك,حالة_الاشتراك,الأعضاء" else "ORG_ROW,OrgId,OrgName,Sector,Currency,PlanTier,Status,MembersCount")
    state.ownerAllOrgs.forEach { o ->
        sb.appendLine(listOf("ORG", o.id, o.name, o.sectorType, o.baseCurrency, o.planTier, o.subscriptionStatus, o.memberUids.size.toString()).joinToString(",") { esc(it) })
    }
    sb.appendLine()

    // Payments Directory
    sb.appendLine(if (isArabic) "الدفع,المعرف,معرف_المنظمة,الباقة,المبلغ,العملة,طريقة_الدفع,رقم_المرجع,آخر_4_أرقام,الحالة,ملاحظات_المراجعة" else "PAY_ROW,RequestId,OrgId,PlanTier,Amount,Currency,Method,ReferenceNo,Last4,Status,ReviewNotes")
    state.ownerAllPaymentRequests.forEach { p ->
        sb.appendLine(listOf("PAY", p.id, p.orgId, p.planTier, p.amount, p.currency, p.paymentMethod, p.referenceNo, p.last4Digits, p.status, p.reviewNotes).joinToString(",") { esc(it) })
    }
    return sb.toString()
}

@Composable
fun OwnerNotificationsCenterDialog(
    notifications: List<AdminNotificationRecord>,
    ownerAllUsers: List<UserProfile>,
    ownerAllPaymentRequests: List<PaymentRequestRecord>,
    isBusy: Boolean,
    isArabic: Boolean,
    onMarkRead: (String, Boolean) -> Unit,
    onMarkAllRead: () -> Unit,
    onDeleteNotification: (String) -> Unit,
    onRunSecurityScan: () -> Unit,
    onJumpToTab: (Int) -> Unit,
    onInspectOrgAuditLogs: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var categoryFilter by remember { mutableStateOf("all") }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }

    // Combine real-time Firestore trigger notifications with immediate client-detected signals
    // (avoiding duplicates when reg_{uid} or pay_{id} already exists from Cloud Functions).
    val combinedNotifications = remember(notifications, ownerAllUsers, ownerAllPaymentRequests) {
        val existingIds = notifications.map { it.id }.toSet()
        val nowMs = System.currentTimeMillis()
        val recentCutoffMs = nowMs - 48L * 3600_000L
        val synthUsers = ownerAllUsers
            .filter { u ->
                val docId = "reg_${u.userId}"
                val createdMs = u.createdAt?.toDate()?.time ?: 0L
                docId !in existingIds && createdMs >= recentCutoffMs
            }
            .map { u ->
                AdminNotificationRecord(
                    id = "synth_reg_${u.userId}",
                    category = "new_user",
                    severity = "info",
                    signalType = "user_registered_live",
                    titleEn = "New User Registered: ${u.displayName.ifBlank { "FVG User" }}",
                    titleAr = "تسجيل مستخدم جديد: ${u.displayName.ifBlank { "مستخدم" }}",
                    bodyEn = "Email: ${u.email.ifBlank { "N/A" }} • Live client feed",
                    bodyAr = "البريد: ${u.email.ifBlank { "غير متوفر" }} • رصد مباشر",
                    actorUid = u.userId,
                    actorEmail = u.email,
                    sourcePlatform = "live_listener",
                    acknowledged = false,
                    createdAt = u.createdAt
                )
            }
        val synthPayments = ownerAllPaymentRequests
            .filter { p ->
                val docId = "pay_${p.id}"
                p.status == "pending_verification" && docId !in existingIds
            }
            .map { p ->
                AdminNotificationRecord(
                    id = "synth_pay_${p.id}",
                    category = "payment_request",
                    severity = "warning",
                    signalType = "payment_proof_submitted",
                    titleEn = "Pending Payment Proof (${p.planTier})",
                    titleAr = "إثبات دفع معلق (${p.planTier})",
                    bodyEn = "${p.amount} ${p.currency} • Ref: ${p.referenceNo.ifBlank { "-" }}",
                    bodyAr = "${p.amount} ${p.currency} • مرجع: ${p.referenceNo.ifBlank { "-" }}",
                    actorUid = p.createdBy,
                    actorEmail = p.senderName.ifBlank { p.createdBy },
                    orgId = p.orgId,
                    sourcePlatform = "live_listener",
                    acknowledged = false,
                    createdAt = p.createdAt
                )
            }
        notifications + synthPayments + synthUsers
    }

    val filtered = remember(combinedNotifications, categoryFilter) {
        combinedNotifications.filter { categoryFilter == "all" || it.category == categoryFilter }
    }
    val unreadFirestoreCount = notifications.count { !it.acknowledged }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = FvgWarmGold)
                    Column {
                        Text(
                            text = if (isArabic) "مركز إشعارات الإدارة الفوري" else "Real-Time Admin Alerts",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isArabic) "مراقبة التسجيلات والأنشطة المشبوهة" else "Live Firestore triggers & security telemetry",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRunSecurityScan,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp).testTag("admin_alerts_scan_btn")
                    ) {
                        Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isArabic) "فحص أمني" else "Security Scan", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onMarkAllRead,
                        enabled = unreadFirestoreCount > 0 && !isBusy,
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp).testTag("admin_alerts_mark_all_btn")
                    ) {
                        Icon(Icons.Filled.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isArabic) "تحديد كمقروء" else "Mark All Read", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }

                FvgChipSelector(
                    options = listOf("all", "new_user", "security_alert", "payment_request"),
                    selected = categoryFilter,
                    onSelect = { categoryFilter = it },
                    labelResolver = { cat ->
                        when (cat) {
                            "all" -> if (isArabic) "الكل (${combinedNotifications.size})" else "All (${combinedNotifications.size})"
                            "new_user" -> if (isArabic) "مستخدمون" else "New Users"
                            "security_alert" -> if (isArabic) "أمان" else "Security"
                            else -> if (isArabic) "مدفوعات" else "Payments"
                        }
                    }
                )

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isArabic) "لا توجد تنبيهات ضمن هذا التصنيف حالياً." else "No notifications in this category yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { n ->
                            val isSynthetic = n.id.startsWith("synth_")
                            val accent = when (n.severity.lowercase()) {
                                "critical" -> FvgNegative
                                "warning" -> FvgWarning
                                else -> FvgPositive
                            }
                            val catIcon = when (n.category) {
                                "new_user" -> Icons.Filled.PersonAdd
                                "payment_request" -> Icons.Filled.ReceiptLong
                                else -> Icons.Filled.Warning
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (!n.acknowledged) accent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(if (!n.acknowledged) 1.5.dp else 1.dp, if (!n.acknowledged) accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth().testTag("admin_alert_item_${n.id}")
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(catIcon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = if (isArabic) n.titleAr.ifBlank { n.titleEn } else n.titleEn.ifBlank { n.titleAr },
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = accent
                                            )
                                        }
                                        FvgRoleBadge(n.severity.uppercase())
                                    }

                                    Text(
                                        text = if (isArabic) n.bodyAr.ifBlank { n.bodyEn } else n.bodyEn.ifBlank { n.bodyAr },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = buildString {
                                                append(n.sourcePlatform)
                                                val ts = n.createdAt?.toDate()?.let { fmt.format(it) }
                                                if (!ts.isNullOrBlank()) append(" • $ts")
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (!isSynthetic) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                TextButton(
                                                    onClick = { onMarkRead(n.id, !n.acknowledged) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                    modifier = Modifier.heightIn(min = 36.dp)
                                                ) {
                                                    Text(
                                                        text = if (n.acknowledged) (if (isArabic) "غير مقروء" else "Unread") else (if (isArabic) "تمت القراءة" else "Mark Read"),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { onDeleteNotification(n.id) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = if (isArabic) "حذف الإشعار" else "Delete alert",
                                                        tint = FvgNegative,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (n.category == "payment_request" || n.orgId.isNotBlank()) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (n.category == "payment_request") {
                                                OutlinedButton(
                                                    onClick = { onJumpToTab(1); onDismiss() },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.heightIn(min = 36.dp)
                                                ) {
                                                    Text(if (isArabic) "مراجعة الدفع" else "Review Payment", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                            if (n.orgId.isNotBlank()) {
                                                OutlinedButton(
                                                    onClick = { onInspectOrgAuditLogs(n.orgId) },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                    modifier = Modifier.heightIn(min = 36.dp)
                                                ) {
                                                    Text(if (isArabic) "فحص التدقيق" else "Inspect Audit", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("admin_alerts_dialog_close")) {
                Text(if (isArabic) "إغلاق" else "Close")
            }
        }
    )
}

