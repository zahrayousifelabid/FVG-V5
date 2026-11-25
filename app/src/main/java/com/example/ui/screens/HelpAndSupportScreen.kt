package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel

@Composable
fun HelpAndSupportScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    onReplayTour: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    val s = fvgStrings(isArabic)
    var message by remember { mutableStateOf("") }
    var submittedNote by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().testTag("help_support_screen")) {
        Surface(
            color = FvgDeepGreen,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("help_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.backHome, tint = FvgWarmGold)
                }
                Text(
                    text = s.helpTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = FvgWarmIvory,
                    fontWeight = FontWeight.Bold
                )
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
                        text = if (isArabic) "الجولة التعريفية والتوضيح التفاعلي" else "Interactive Spotlight Walkthrough",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isArabic)
                            "أعد تشغيل الجولة الإرشادية لتتبع كل شاشة في التطبيق خطوة بخطوة بالعربية أو الإنجليزية."
                        else
                            "Replay the step-by-step interactive tour highlighting every financial screen in Arabic or English.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    FvgGoldButton(
                        text = if (isArabic) "إعادة تشغيل الجولة التفاعلية" else "Replay Interactive Walkthrough",
                        icon = Icons.Filled.Explore,
                        onClick = {
                            onBack()
                            onReplayTour()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        testTag = "replay_tour_button"
                    )
                }
            }

            item {
                FvgCard {
                    Text(
                        text = if (isArabic) "شرح المعادلات ومنهجية التكاليف والتسعير" else "Formulas, Costing Methods & Decision Disclaimers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val guides = if (isArabic) listOf(
                        "طرق التكاليف الـ 8" to "التكلفة الفعلية، المعيارية، المستهدفة، ABC، الأوامر الإنتاجية، المراحل الإنتاجية (الوحدات المكافئة)، الكلية (Absorption)، والمتغيرة (Variable).",
                        "الهامش (Margin) مقابل الإضافة (Markup)" to "الهامش % = (السعر − التكلفة) ÷ السعر. نسبة الإضافة % = (السعر − التكلفة) ÷ التكلفة. سعر البيع بالهامش المستهدف = التكلفة ÷ (1 − نسبة الهامش).",
                        "التضخم والصرف اليدوي" to "التضخم التراكمي = Π(1 + المعدل الشهري) − 1. أسعار الصرف تُسجّل يدوياً لتفادي أخطاء المزودات الإلكترونية ويُنبه عن أي سعر أقدم من 7 أيام.",
                        "حدود الدعم الذكي والسيناريوهات" to "سيناريوهات 'ماذا لو' عمليات حتمية افتراضية وليست توقعات مستقبلية، ومسودات الفواتير (OCR) تحتاج مراجعة بشرية قبل الاعتماد."
                    ) else listOf(
                        "8 Costing Methods" to "Actual, Standard, Target (allowable cost & gap), ABC, Job Order, Process (equivalent units), Absorption, and Variable costing.",
                        "Margin % vs Markup %" to "Margin % = (Price − Cost) / Price. Markup % = (Price − Cost) / Cost. Target price from margin = Unit Cost / (1 − Margin).",
                        "Inflation & Manual FX" to "Cumulative compounded inflation = Π(1 + rᵢ) − 1. FX rates are entered manually (draft/approved/cancelled) with >7-day staleness warnings.",
                        "Decision Support Scope" to "What-If simulations are deterministic models, not forecasts. AI explanations and OCR invoice drafts require explicit human review."
                    )
                    guides.forEach { (title, body) ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            // Simplified Contact Us Form -> Real Firestore Support Ticket
            item {
                FvgCard {
                    Text(
                        text = if (isArabic) "تواصل مع مالكة المنصة (${s.supportEmail})" else "Contact Platform Owner (${s.supportEmail})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isArabic)
                            "اكتب ملاحظاتك أو طلبك للإدارة وسيتم حفظه فوراً في حسابك المسجل لمراجعته من مالكة المنصة."
                        else
                            "Write your notes or inquiry for management; it will be saved directly under your signed-in account for review by the Platform Owner.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FvgField(
                        value = message,
                        onValueChange = { message = it },
                        label = if (isArabic) "اكتب رسالتك أو استفسارك أو طلب المساعدة" else "Write your message or support inquiry",
                        singleLine = false,
                        testTag = "support_message_input"
                    )
                    if (submittedNote != null) {
                        Text(submittedNote!!, style = MaterialTheme.typography.bodyMedium, color = FvgPositive, fontWeight = FontWeight.SemiBold)
                    }
                    FvgPrimaryButton(
                        text = if (isArabic) "إرسال طلب المساعدة" else "Send Support Request",
                        icon = Icons.Filled.Send,
                        enabled = message.isNotBlank() && !state.isBusy,
                        onClick = {
                            viewModel.submitSupportTicket(
                                senderName = state.userProfile?.displayName ?: viewModel.userDisplayName,
                                senderEmail = viewModel.userEmail,
                                message = message
                            )
                            submittedNote = if (isArabic) "تم إرسال الرسالة وتسجيلها للمراجعة." else "Support ticket saved and queued for platform owner review."
                            message = ""
                        },
                        testTag = "submit_support_ticket_button"
                    )
                }
            }

            if (state.supportTickets.isNotEmpty()) {
                item {
                    Text(
                        text = if (isArabic) "رسائلي وطلبات الدعم السابقة (${state.supportTickets.size})" else "My Support Tickets (${state.supportTickets.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                items(state.supportTickets.reversed(), key = { it.id }) { tk ->
                    FvgCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(tk.orgName.ifBlank { "FVG Account" }, style = MaterialTheme.typography.labelLarge)
                            FvgRoleBadge(tk.status)
                        }
                        Text(tk.message, style = MaterialTheme.typography.bodyMedium)
                        if (tk.ownerReply.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = (if (isArabic) "رد الإدارة: " else "Owner Reply: ") + tk.ownerReply,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
