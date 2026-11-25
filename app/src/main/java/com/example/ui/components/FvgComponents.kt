package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.engine.FvgFinancialEngine
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun FvgTopHeaderBanner(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    orgName: String? = null,
    roleBadge: String? = null,
    userInitials: String? = null,
    unreadNotificationsCount: Int = 0,
    onNotificationsClick: (() -> Unit)? = null,
    onCalculatorClick: (() -> Unit)? = null,
    onHelpClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(FvgDeepGreen, FvgForestGreen)
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .let { if (onProfileClick != null) it.clickable(onClick = onProfileClick) else it },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = FvgWarmGold.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.45f)),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!userInitials.isNullOrBlank()) {
                                Text(
                                    text = userInitials.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FvgWarmGold
                                )
                            } else {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = title,
                                    tint = FvgWarmGold,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = FvgWarmIvory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (!orgName.isNullOrBlank()) "$orgName • $subtitle" else subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = FvgWarmGold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!roleBadge.isNullOrBlank()) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = FvgWarmIvory.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = roleBadge.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = FvgWarmIvory,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onCalculatorClick != null) {
                        IconButton(
                            onClick = onCalculatorClick,
                            modifier = Modifier.size(44.dp).testTag("open_calculator_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Calculate,
                                contentDescription = if (isArabic) "الآلة الحاسبة" else "Calculator",
                                tint = FvgWarmGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onHelpClick != null) {
                        IconButton(
                            onClick = onHelpClick,
                            modifier = Modifier.size(44.dp).testTag("open_help_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.HelpOutline,
                                contentDescription = if (isArabic) "المساعدة" else "Help",
                                tint = FvgWarmIvory,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onNotificationsClick != null) {
                        IconButton(
                            onClick = onNotificationsClick,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("notifications_bell_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (unreadNotificationsCount > 0) {
                                        Badge(containerColor = FvgWarmGold, contentColor = FvgDeepGreen) {
                                            Text(unreadNotificationsCount.coerceAtMost(99).toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Notifications,
                                    contentDescription = if (isArabic) "الإشعارات" else "Notifications",
                                    tint = FvgWarmIvory,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = onToggleLanguage,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("toggle_language_button")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.Language,
                                contentDescription = if (isArabic) "Switch to English" else "التبديل إلى العربية",
                                tint = FvgWarmGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (isArabic) "EN" else "AR",
                                style = MaterialTheme.typography.labelSmall,
                                color = FvgWarmIvory
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isArabic) "تغيير المظهر" else "Toggle theme",
                            tint = FvgWarmIvory,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FvgSubTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    testTagPrefix: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { idx, label ->
            val selected = idx == selectedIndex
            Surface(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Tab) { onSelect(idx) }
                    .testTag("${testTagPrefix}_tab_$idx"),
                shape = RoundedCornerShape(24.dp),
                color = if (selected) FvgDeepGreen else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(
                    1.dp,
                    if (selected) FvgWarmGold else MaterialTheme.colorScheme.outline
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) FvgWarmGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun FvgCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@Composable
fun FvgField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    testTag: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = singleLine,
        enabled = enabled,
        keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .let { if (testTag != null) it.testTag(testTag) else it }
    )
}

@Composable
fun FvgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FvgDeepGreen,
            contentColor = FvgWarmIvory
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FvgGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    testTag: String? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = FvgWarmGold,
            contentColor = FvgDeepGreen
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .let { if (testTag != null) it.testTag(testTag) else it }
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FvgChipSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelResolver: (String) -> String = { it }
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = { Text(labelResolver(opt)) },
                modifier = Modifier.heightIn(min = 48.dp)
            )
        }
    }
}

@Composable
fun FvgEmptyStateCard(message: String) {
    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = FvgAntiqueGold,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FvgRiskBadge(
    level: String,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val norm = level.lowercase()
    val (bg, fg, label) = when (norm) {
        "high" -> Triple(FvgNegative.copy(alpha = 0.16f), Color(0xFFE57373), if (isArabic) "خطر مرتفع" else "HIGH RISK")
        "medium" -> Triple(FvgWarning.copy(alpha = 0.18f), FvgWarmGold, if (isArabic) "خطر متوسط" else "MEDIUM RISK")
        else -> Triple(FvgPositive.copy(alpha = 0.16f), Color(0xFF4CAF50), if (isArabic) "مخاطر منخفضة" else "LOW RISK")
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, fg.copy(alpha = 0.45f)),
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FvgTrialExpiredGateCard(
    isArabic: Boolean,
    orgName: String,
    durationHours: Long = FvgFinancialEngine.TRIAL_DURATION_HOURS,
    onUpgradeClick: () -> Unit,
    onSignOutClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val days = (durationHours / 24L).coerceAtLeast(1L)
    FvgCard(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.testTag("trial_expired_gate_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                shape = CircleShape,
                color = FvgNegative.copy(alpha = 0.16f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isArabic) "انتهت الفترة التجريبية ($days أيام / $durationHours ساعة)" else "$days-Day ($durationHours-Hour) Free Trial Ended",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = orgName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = if (isArabic)
                "لحماية سلامة بياناتك، يتطلب الإدخال والتعديل في الوحدات المالية تفعيل باقة عبر تقديم رقم المرجع وآخر 4 أرقام من المعاملة لمراجعة مالكة المنصة. يظل استعراض الملخص والتقارير وتصديرها متاحاً."
            else
                "Your server-synced workspace trial has expired. Submit your transfer reference and last 4 digits in Assistant & Billing for platform owner verification. Read-only dashboard viewing and exports remain available.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        FvgGoldButton(
            text = if (isArabic) "ترقية الباقة وإرسال إثبات الدفع" else "Upgrade Plan & Submit Payment Proof",
            onClick = onUpgradeClick,
            modifier = Modifier.fillMaxWidth(),
            testTag = "trial_gate_upgrade_button"
        )
        if (onSignOutClick != null) {
            OutlinedButton(
                onClick = onSignOutClick,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text(if (isArabic) "تسجيل الخروج" else "Sign Out")
            }
        }
    }
}

/**
 * Live 7-day (168-hour) default (or legacy 72-hour) Server-Synced & Monotonic Trial Countdown displaying Days, Hours, Minutes, and Seconds.
 */
@Composable
fun FvgTrialCountdownBanner(
    trialEndsAtMillis: Long?,
    isArabic: Boolean,
    isPreviewTrial: Boolean = false,
    durationHours: Long = FvgFinancialEngine.TRIAL_DURATION_HOURS,
    serverAnchorMillis: Long? = null,
    elapsedBaselineMillis: Long? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val daysCount = (durationHours / 24L).coerceAtLeast(1L)
    val previewEndMillis = remember(isPreviewTrial, durationHours) {
        if (isPreviewTrial) SystemClock.elapsedRealtime() + durationHours * 3600_000L else null
    }
    var elapsedNow by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(trialEndsAtMillis, isPreviewTrial) {
        while (true) {
            elapsedNow = SystemClock.elapsedRealtime()
            delay(1000L)
        }
    }

    val cd: FvgFinancialEngine.TrialCountdownBreakdown? = remember(
        trialEndsAtMillis,
        isPreviewTrial,
        previewEndMillis,
        serverAnchorMillis,
        elapsedBaselineMillis,
        elapsedNow
    ) {
        when {
            isPreviewTrial && previewEndMillis != null ->
                FvgFinancialEngine.computeTrialCountdown(previewEndMillis, elapsedNow)
            trialEndsAtMillis != null && serverAnchorMillis != null && elapsedBaselineMillis != null ->
                FvgFinancialEngine.computeMonotonicTrialCountdown(
                    trialEndsAtMillis = trialEndsAtMillis,
                    anchorServerNowMillis = serverAnchorMillis,
                    elapsedDeltaMillis = elapsedNow - elapsedBaselineMillis
                )
            trialEndsAtMillis != null ->
                FvgFinancialEngine.computeTrialCountdown(trialEndsAtMillis, System.currentTimeMillis())
            else -> null
        }
    }

    fun pad(v: Long): String = v.toString().padStart(2, '0')

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trial_countdown_banner"),
        shape = RoundedCornerShape(18.dp),
        color = if (cd?.isExpired == true) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (cd?.isExpired == true) MaterialTheme.colorScheme.error else FvgWarmGold)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = if (cd?.isExpired == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = when {
                            cd == null && isArabic -> "جاري مزامنة ساعة الخادم للفترة التجريبية..."
                            cd == null -> "Syncing server trial clock..."
                            cd.isExpired && isArabic -> "انتهت الفترة التجريبية ($daysCount أيام / $durationHours ساعة)"
                            cd.isExpired -> "$daysCount-Day (${durationHours}h) Trial Ended"
                            isPreviewTrial && isArabic -> "تجربة مجانية كاملة $daysCount أيام ($durationHours ساعة)"
                            isPreviewTrial -> "Full $daysCount-Day ($durationHours-Hour) Free Trial Included"
                            isArabic -> "الفترة التجريبية النشطة ($daysCount أيام بتوقيت الخادم)"
                            else -> "$daysCount-Day Active Trial (Server-Synced)"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (cd?.isExpired == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                if (onActionClick != null) {
                    TextButton(onClick = onActionClick, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(
                            text = if (isArabic) "الباقات" else "Plans",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (cd != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val units = listOf(
                        pad(cd.days) to (if (isArabic) "أيام" else "Days"),
                        pad(cd.hours) to (if (isArabic) "ساعات" else "Hours"),
                        pad(cd.minutes) to (if (isArabic) "دقائق" else "Mins"),
                        pad(cd.seconds) to (if (isArabic) "ثواني" else "Secs")
                    )
                    units.forEach { (num, lbl) ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = FvgDeepGreen
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = num,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FvgWarmGold
                                )
                                Text(
                                    text = lbl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FvgWarmIvory
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FvgSparklineCanvas(
    values: List<Float>,
    lineColor: Color = FvgWarmGold,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (values.size < 2) {
            drawLine(
                color = lineColor.copy(alpha = 0.5f),
                start = Offset(0f, size.height * 0.6f),
                end = Offset(size.width, size.height * 0.6f),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
            return@Canvas
        }
        val minV = values.minOrNull() ?: 0f
        val maxV = values.maxOrNull() ?: 1f
        val span = (maxV - minV).takeIf { it > 0.0001f } ?: 1f
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { idx, v ->
            val x = idx * stepX
            val norm = (v - minV) / span
            val y = size.height - (norm * size.height * 0.78f + size.height * 0.11f)
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 4.5f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun FvgBarComparisonCanvas(
    bars: List<Pair<String, Float>>,
    barColorPrimary: Color = FvgForestGreen,
    barColorSecondary: Color = FvgWarmGold,
    modifier: Modifier = Modifier
) {
    val labelPositiveColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            if (bars.isEmpty()) return@Canvas
            val maxAbs = bars.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(1f)
            val slotW = size.width / bars.size
            val barW = (slotW * 0.54f).coerceAtMost(56f)
            val baselineY = size.height * 0.88f

            drawLine(
                color = FvgWarmStone,
                start = Offset(0f, baselineY),
                end = Offset(size.width, baselineY),
                strokeWidth = 2f
            )

            bars.forEachIndexed { i, pair ->
                val ratio = (kotlin.math.abs(pair.second) / maxAbs).coerceIn(0.04f, 1f)
                val barH = ratio * (baselineY - 12f)
                val left = i * slotW + (slotW - barW) / 2f
                val top = baselineY - barH
                drawRoundRect(
                    color = if (pair.second < 0f) FvgNegative else if (i % 2 == 0) barColorPrimary else barColorSecondary,
                    topLeft = Offset(left, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(8f, 8f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bars.forEach { (label, value) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format("%.1f", value),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (value < 0f) MaterialTheme.colorScheme.error else labelPositiveColor
                    )
                }
            }
        }
    }
}

@Composable
fun FvgDonutBreakdownCanvas(
    slices: List<Triple<String, Float, Color>>,
    centerTitle: String,
    centerValue: String,
    modifier: Modifier = Modifier
) {
    val total = remember(slices) { slices.sumOf { it.second.toDouble().coerceAtLeast(0.0) }.toFloat() }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 24f
                    val diameter = size.minDimension - strokeW
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    if (total <= 0f) {
                        drawArc(
                            color = FvgWarmStone.copy(alpha = 0.45f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeW, cap = StrokeCap.Butt)
                        )
                        return@Canvas
                    }
                    var startAngle = -90f
                    slices.filter { it.second > 0f }.forEach { (_, value, color) ->
                        val sweep = (value / total) * 360f
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )
                        startAngle += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = centerValue,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = centerTitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                slices.forEach { (label, value, color) ->
                    val pct = if (total > 0f) ((value / total) * 100f).toInt() else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "${value.toInt()} ($pct%)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FvgRoleBadge(role: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = BorderStroke(1.dp, FvgAntiqueGold.copy(alpha = 0.5f))
    ) {
        Text(
            text = role.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FvgSplineLineChartCanvas(
    points: List<Pair<String, Float>>,
    lineColor: Color = FvgForestGreen,
    fillColor: Color = FvgWarmGold,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(155.dp)
        ) {
            if (points.size < 2) return@Canvas
            val vals = points.map { it.second }
            val minV = vals.minOrNull() ?: 0f
            val maxV = vals.maxOrNull() ?: 1f
            val span = (maxV - minV).takeIf { it > 0.001f } ?: 1f
            val padH = 18f
            val padV = 18f
            val usableW = (size.width - padH * 2f).coerceAtLeast(1f)
            val usableH = (size.height - padV * 2f).coerceAtLeast(1f)
            val stepX = usableW / (points.size - 1).coerceAtLeast(1)

            val coords = points.mapIndexed { idx, (_, v) ->
                val x = padH + idx * stepX
                val norm = (v - minV) / span
                val y = size.height - padV - norm * usableH
                Offset(x, y)
            }

            val linePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 0 until coords.size - 1) {
                    val p0 = coords[i]
                    val p1 = coords[i + 1]
                    val cx = (p0.x + p1.x) / 2f
                    cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(linePath)
                lineTo(coords.last().x, size.height - padV)
                lineTo(coords.first().x, size.height - padV)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor.copy(alpha = 0.28f), fillColor.copy(alpha = 0.02f))
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
            coords.forEachIndexed { i, c ->
                val isLast = i == coords.lastIndex
                drawCircle(
                    color = if (isLast) FvgWarmGold else lineColor,
                    radius = if (isLast) 8f else 5f,
                    center = c
                )
            }
        }
        if (points.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                points.take(6).forEach { (lbl, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(lbl.take(7), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format("%.1f", value), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun FvgCvpChartCanvas(
    cvpPoints: List<FvgFinancialEngine.CvpPoint>,
    breakEvenUnits: Float?,
    isArabic: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
        ) {
            if (cvpPoints.size < 2) return@Canvas
            val maxU = cvpPoints.maxOf { it.units }.coerceAtLeast(1f)
            val maxY = cvpPoints.maxOf { maxOf(it.totalCost, it.totalRevenue) }.coerceAtLeast(1f)
            val pad = 20f
            val w = size.width - pad * 2f
            val h = size.height - pad * 2f

            fun toPt(u: Float, yVal: Float): Offset =
                Offset(pad + (u / maxU) * w, size.height - pad - (yVal / maxY) * h)

            drawLine(FvgWarmStone, Offset(pad, size.height - pad), Offset(size.width - pad, size.height - pad), strokeWidth = 2f)
            drawLine(FvgWarmStone, Offset(pad, pad), Offset(pad, size.height - pad), strokeWidth = 2f)

            val costPath = Path()
            val revPath = Path()
            cvpPoints.forEachIndexed { idx, pt ->
                val cOff = toPt(pt.units, pt.totalCost)
                val rOff = toPt(pt.units, pt.totalRevenue)
                if (idx == 0) {
                    costPath.moveTo(cOff.x, cOff.y)
                    revPath.moveTo(rOff.x, rOff.y)
                } else {
                    costPath.lineTo(cOff.x, cOff.y)
                    revPath.lineTo(rOff.x, rOff.y)
                }
            }
            drawPath(costPath, color = FvgNegative, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
            drawPath(revPath, color = FvgForestGreen, style = Stroke(width = 5f, cap = StrokeCap.Round))

            if (breakEvenUnits != null && breakEvenUnits in 0f..maxU) {
                val closest = cvpPoints.minByOrNull { kotlin.math.abs(it.units - breakEvenUnits) }
                if (closest != null) {
                    val beY = (closest.totalRevenue + closest.totalCost) / 2f
                    val bePt = toPt(breakEvenUnits, beY)
                    drawCircle(color = FvgWarmGold, radius = 9f, center = bePt)
                    drawCircle(color = FvgDeepGreen, radius = 4.5f, center = bePt)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(FvgForestGreen))
                Spacer(Modifier.width(4.dp))
                Text(if (isArabic) "خط الإيرادات" else "Revenue Line", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(FvgNegative))
                Spacer(Modifier.width(4.dp))
                Text(if (isArabic) "التكلفة الكلية" else "Total Cost Line", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(FvgWarmGold))
                Spacer(Modifier.width(4.dp))
                Text(if (isArabic) "نقطة التعادل" else "Break-Even", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * Full-width rectangular Traffic-Light Status Banner (Green / Yellow-Amber / Red)
 * providing immediate, unmistakable visual risk & accounting health feedback.
 */
@Composable
fun FvgStatusSignalBanner(
    level: String,
    title: String,
    subtitle: String,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    val norm = level.lowercase()
    val (bgColor, icon, badgeText) = when (norm) {
        "red", "high", "critical", "error" -> Triple(
            Color(0xFFB3261E),
            Icons.Filled.ErrorOutline,
            if (isArabic) "إشارة حمراء • تنبيه حرج" else "RED SIGNAL • CRITICAL"
        )
        "yellow", "amber", "medium", "warning" -> Triple(
            Color(0xFF9E660B),
            Icons.Filled.Warning,
            if (isArabic) "إشارة صفراء • انتباه" else "YELLOW SIGNAL • CAUTION"
        )
        else -> Triple(
            Color(0xFF1B6B4A),
            Icons.Filled.CheckCircle,
            if (isArabic) "إشارة خضراء • وضع آمن" else "GREEN SIGNAL • HEALTHY"
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("fvg_status_signal_banner_$norm"),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.20f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = badgeText,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.92f)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.94f)
                    )
                }
            }
        }
    }
}

/**
 * Compact (!) Contextual Field & Formula Tooltip Trigger opening an accessible explanation dialog.
 */
@Composable
fun FvgFieldHelpIcon(
    title: String,
    explanation: String,
    isArabic: Boolean,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    IconButton(
        onClick = { open = true },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(48.dp)
            .testTag("field_help_icon")
    ) {
        Surface(
            shape = CircleShape,
            color = FvgWarmGold.copy(alpha = 0.18f),
            border = BorderStroke(1.2.dp, FvgAntiqueGold),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            icon = {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = FvgAntiqueGold)
            },
            title = {
                Text(title, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(explanation, style = MaterialTheme.typography.bodyMedium)
            },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(if (isArabic) "فهمت" else "Got it", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Restored Founder & Researcher Identity Card (Zahra Yousif) across Public Home, About, Contact & Dashboard.
 */
@Composable
fun FvgFounderCard(
    isArabic: Boolean,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ownerPhone = "00249911569966"
    val ownerEmail = "Financialvisionguide@gmail.com"
    val visionQuote = if (isArabic) {
        "في ظل متغيّرات اقتصادية متسارعة وضغوط تضخمية حادة تهدد استمرارية المشاريع، يأتي 'دليل رؤيتك المالي' ليكون بوصلتك نحو الاستقرار المالي، محولاً معادلات التكاليف المعقدة إلى أدوات عملية دقيقة تضمن الحفاظ على رأس المال وتعظيم الأرباح."
    } else {
        "Amid rapid economic shifts and acute inflationary pressures, Financial Vision Guide (FVG) translates complex cost equations into clear, actionable decisions that safeguard capital and strengthen profitability."
    }

    FvgCard(
        modifier = modifier.testTag("fvg_founder_card"),
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = CircleShape,
                border = BorderStroke(2.5.dp, FvgWarmGold),
                shadowElevation = 4.dp,
                modifier = Modifier.size(if (compact) 68.dp else 88.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.zahra_yousif_portrait),
                    contentDescription = if (isArabic) "صورة الباحثة زهراء يوسف" else "Founder Portrait Zahra Yousif",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = FvgDeepGreen
                ) {
                    Text(
                        text = if (isArabic) "مؤسسة ومطوّرة المنصة" else "Platform Founder & Developer",
                        style = MaterialTheme.typography.labelSmall,
                        color = FvgWarmGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Text(
                    text = if (isArabic) "زهراء يوسف | Zahra Yousif" else "Zahra Yousif | زهراء يوسف",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (isArabic) "مطوّرة ومؤسسة FVG • خريجة محاسبة — جامعة النيل"
                    else "FVG Founder & Developer • Accounting Graduate — Nile University",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!compact) {
            Text(
                text = visionQuote,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ownerPhone")))
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("founder_phone_button")
            ) {
                Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(ownerPhone, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$ownerEmail")))
                    }
                },
                modifier = Modifier
                    .weight(1.1f)
                    .heightIn(min = 48.dp)
                    .testTag("founder_email_button")
            ) {
                Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(16.dp), tint = FvgAntiqueGold)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (isArabic) "مراسلة الإدارة" else "Email Owner",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
