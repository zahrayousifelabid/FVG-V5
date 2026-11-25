package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

data class FvgTourStep(
    val id: String,
    val targetTabIndex: Int?,
    val icon: ImageVector,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val tipAr: String,
    val tipEn: String
)

private val FvgWalkthroughSteps = listOf(
    FvgTourStep(
        id = "lang_select",
        targetTabIndex = 0,
        icon = Icons.Filled.Translate,
        titleAr = "اختر لغة العرض والشرح أولاً",
        titleEn = "Choose Your Language First",
        descriptionAr = "يمكنك استخدام المنصة بالكامل باللغة العربية (واجهة وأزرار ومعادلات وشروحات بالعربية دون نصوص إنجليزية)، أو التبديل إلى الإنجليزية في أي وقت.",
        descriptionEn = "Use FVG entirely in Arabic (100% Arabic labels, formulas & explanations) or English anytime. Select your preferred language below to continue.",
        tipAr = "اضغط العربية أو English أدناه وسيتم تحديث التطبيق والجولة فوراً.",
        tipEn = "Tap العربية or English below to update the app and walkthrough instantly."
    ),
    FvgTourStep(
        id = "welcome_lang",
        targetTabIndex = 0,
        icon = Icons.Filled.Language,
        titleAr = "مرحباً بك في دليل رؤيتك المالي",
        titleEn = "Welcome to Financial Vision Guide (FVG)",
        descriptionAr = "هنا لوحة التحكم الرئيسية لمتابعة السيولة والمخزون وصافي الربح ومؤشرات الأمان الخضراء والصفراء والحمراء. يمكنك تغيير اللغة والمظهر الفاتح أو الداكن من الشريط العلوي.",
        descriptionEn = "Your operational financial hub for cash, inventory valuation, profit KPIs, and Green/Yellow/Red health signals. Switch language or Light/Dark mode anytime from the top bar.",
        tipAr = "تبدأ منظمتك بفترة تجربة كاملة لمدة 7 أيام (168 ساعة بتوقيت الخادم).",
        tipEn = "Your workspace starts with a full 7-day (168-hour) server-synced free trial."
    ),
    FvgTourStep(
        id = "prices_fx",
        targetTabIndex = 1,
        icon = Icons.AutoMirrored.Filled.TrendingUp,
        titleAr = "مراقبة الأسعار والتضخم والصرف اليدوي",
        titleEn = "Prices, Inflation & Manual FX Registry",
        descriptionAr = "سجّل تغيرات أسعار المواد والمنتجات دون مسح السجل التاريخي، واحسب السلات المرجحة للتضخم. يتم إدخال أسعار الصرف يدوياً لتفادي الأخطاء الإلكترونية.",
        descriptionEn = "Log product and material prices as an append-only history, build weighted inflation baskets, and record verified manual FX rates (draft / approved / cancelled) to avoid automated rate errors.",
        tipAr = "ينبّهك النظام تلقائياً لأي قفزة سعر >= 15% أو سعر صرف أقدم من 7 أيام.",
        tipEn = "Automatic alerts flag price spikes >= 15% and FX rates older than 7 days."
    ),
    FvgTourStep(
        id = "costing_bom",
        targetTabIndex = 2,
        icon = Icons.Filled.Calculate,
        titleAr = "أساليب التكاليف الـ 8 والتركيبات والمخاطر",
        titleEn = "8 Costing Methods, BOM Recipes & What-If",
        descriptionAr = "احسب تكلفة الوحدة بـ 8 طرق محاسبية، وأنشئ وصفات المواد مع نسبة الهدر، وحلّل التعادل، وحاكِ صدمات التضخم والصرف في سيناريوهات 'ماذا لو'.",
        descriptionEn = "Compute unit costs across 8 management accounting methods, manage BOM formulas with scrap %, analyze CVP break-even curves, and simulate inflation/FX shocks safely.",
        tipAr = "الهامش يُقسم على سعر البيع؛ أما نسبة الإضافة فتُقسم على التكلفة.",
        tipEn = "Margin divides profit by selling price; Markup divides profit by unit cost."
    ),
    FvgTourStep(
        id = "accounting_stock",
        targetTabIndex = 3,
        icon = Icons.Filled.AccountBalance,
        titleAr = "المحاسبة المزدوجة والقوائم والـ 18 نسبة",
        titleEn = "Double-Entry Accounting, Statements & 18 Ratios",
        descriptionAr = "رحّل القيود المزدوجة المتوازنة أو احفظها كمسودات، وتابع دفتر الأستاذ العام وقائمة الدخل والميزانية والتدفقات النقدية وأعمار الذمم و18 نسبة مالية.",
        descriptionEn = "Post balanced double-entry journals or drafts, review Running Balance GL, Income/Balance/Cash Flow statements, AR/AP aging buckets, and 18 financial ratios.",
        tipAr = "القيود المرحّلة لا تُعدل بل تُصحح بقيد عكس للحفاظ على سجل التدقيق.",
        tipEn = "Posted entries stay immutable for audit integrity; use reversal entries to correct."
    ),
    FvgTourStep(
        id = "inventory_batches",
        targetTabIndex = 4,
        icon = Icons.Filled.Inventory2,
        titleAr = "المخزون والدفعات والجرد والهدر",
        titleEn = "Inventory Valuation, Batches, Stocktaking & Scrap",
        descriptionAr = "تابع الكميات بطريقة الوارد أولاً صادر أولاً أو المتوسط المرجح وقارن القيمة الدفترية بتكلفة الاستبدال، وسجل الدفعة والصلاحية، واعتمد طلبات الهدر والجرد.",
        descriptionEn = "Track quantities using FIFO or Moving Weighted Average, compare Book vs Replacement cost, log batch numbers and expiry dates, and approve stocktakes & scrap requests.",
        tipAr = "يُنبّهك النظام عند اقتراب انتهاء صلاحية الدفعات خلال 30 يوماً.",
        tipEn = "Expiry alerts notify you when batches are within 30 days of expiration."
    ),
    FvgTourStep(
        id = "assistant_tools",
        targetTabIndex = 5,
        icon = Icons.Filled.AutoAwesome,
        titleAr = "المساعد المالي وفحص الفواتير والآلة الحاسبة",
        titleEn = "Financial Assistant, Camera OCR & 3D Calculator",
        descriptionAr = "تحاور مع المساعد المالي بشكل تفاعلي متعدد الرسائل، وصوّر الفواتير مباشرة بالكاميرا لمراجعتها قبل الحفظ، واستعن بالآلة الحاسبة ثلاثية الأبعاد ورموز التوضيح (!) في كل قسم.",
        descriptionEn = "Chat conversationally with the Financial Assistant, scan invoices camera-first for human review, and access the 3D Calculator and (!) field help buttons everywhere.",
        tipAr = "يمكنك إعادة تشغيل هذه الجولة في أي وقت من صفحة المساعدة والتواصل.",
        tipEn = "You can replay this interactive walkthrough anytime from Help & Contact."
    )
)

@Composable
fun FvgWalkthroughOverlay(
    isArabic: Boolean,
    onSelectLanguage: (Boolean) -> Unit = {},
    onHighlightTab: (Int) -> Unit,
    onDismissComplete: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val step = FvgWalkthroughSteps[stepIndex]

    LaunchedEffect(stepIndex) {
        step.targetTabIndex?.let { onHighlightTab(it) }
    }

    BackHandler {
        onDismissComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f))
            .padding(20.dp)
            .testTag("walkthrough_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, FvgWarmGold),
            shadowElevation = 12.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = FvgDeepGreen,
                            border = BorderStroke(1.dp, FvgWarmGold),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(step.icon, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column {
                            Text(
                                text = if (isArabic) "الجولة التفاعلية • ${stepIndex + 1} / ${FvgWalkthroughSteps.size}"
                                else "Spotlight Guide • ${stepIndex + 1} / ${FvgWalkthroughSteps.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = FvgAntiqueGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isArabic) "توضيح تفاعلي لنظام دليل رؤيتك المالي" else "Interactive Feature Walkthrough",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(
                        onClick = onDismissComplete,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("walkthrough_skip_button")
                    ) {
                        Text(if (isArabic) "تخطي" else "Skip", color = MaterialTheme.colorScheme.primary)
                    }
                }

                // Step Indicator Dots
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
                ) {
                    FvgWalkthroughSteps.forEachIndexed { idx, _ ->
                        Box(
                            modifier = Modifier
                                .height(7.dp)
                                .width(if (idx == stepIndex) 26.dp else 10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx == stepIndex) FvgWarmGold
                                    else MaterialTheme.colorScheme.outline
                                )
                        )
                    }
                }

                AnimatedContent(targetState = step, label = "tour_step") { cur ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (isArabic) cur.titleAr else cur.titleEn,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isArabic) cur.descriptionAr else cur.descriptionEn,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (cur.id == "lang_select") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isArabic) {
                                    FvgPrimaryButton(
                                        text = "العربية (كامل الواجهة بالعربي)",
                                        onClick = { onSelectLanguage(true) },
                                        modifier = Modifier.weight(1f),
                                        testTag = "walkthrough_lang_ar_button"
                                    )
                                    OutlinedButton(
                                        onClick = { onSelectLanguage(false) },
                                        modifier = Modifier.weight(0.8f).heightIn(min = 48.dp).testTag("walkthrough_lang_en_button")
                                    ) {
                                        Text("English")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { onSelectLanguage(true) },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("walkthrough_lang_ar_button")
                                    ) {
                                        Text("العربية (كامل الواجهة)")
                                    }
                                    FvgPrimaryButton(
                                        text = "English (Full Interface)",
                                        onClick = { onSelectLanguage(false) },
                                        modifier = Modifier.weight(1f),
                                        testTag = "walkthrough_lang_en_button"
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = FvgAntiqueGold, modifier = Modifier.size(18.dp))
                                Text(
                                    text = if (isArabic) cur.tipAr else cur.tipEn,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (stepIndex > 0) {
                        OutlinedButton(
                            onClick = { stepIndex -= 1 },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("walkthrough_prev_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isArabic) "السابق" else "Previous")
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    val isLast = stepIndex == FvgWalkthroughSteps.lastIndex
                    FvgGoldButton(
                        text = if (isLast) (if (isArabic) "ابدأ العمل الآن" else "Finish Tour")
                        else (if (isArabic) "التالي" else "Next"),
                        icon = if (isLast) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = {
                            if (isLast) onDismissComplete() else stepIndex += 1
                        },
                        modifier = Modifier.weight(1.2f),
                        testTag = "walkthrough_next_button"
                    )
                }
            }
        }
    }
}
