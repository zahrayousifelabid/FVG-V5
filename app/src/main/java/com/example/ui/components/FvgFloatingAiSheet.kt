package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FvgFloatingAiSheet(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val listState = rememberLazyListState()
    var question by remember { mutableStateOf("") }
    var showSavedList by remember { mutableStateOf(false) }
    var newChatNotice by remember { mutableStateOf(false) }
    val currentMode = state.aiMode.ifBlank { "general" }

    LaunchedEffect(state.chatTranscript.size, state.aiResponse) {
        if (state.chatTranscript.isNotEmpty()) {
            newChatNotice = false
            listState.animateScrollToItem((state.chatTranscript.size - 1).coerceAtLeast(0))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .widthIn(max = 620.dp)
                .padding(12.dp)
                .testTag("fvg_floating_ai_dialog"),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(2.dp, FvgWarmGold),
            shadowElevation = 18.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FvgDeepGreen)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = FvgWarmGold)
                        Column {
                            Text(
                                text = if (isArabic) "المستشار والمساعد الذكي FVG AI" else "FVG AI Advisor & Supervisor",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FvgWarmIvory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (isArabic) "يحفظ محادثاتك • يتابع عملك • يجيب داخل وخارج المنصة"
                                else "Saves chats • Guides workspace • General & Financial AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = FvgWarmGold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (state.aiConversations.isNotEmpty()) {
                        IconButton(
                            onClick = { showSavedList = !showSavedList },
                            modifier = Modifier.size(48.dp).testTag("floating_ai_history_button")
                        ) {
                            Icon(
                                Icons.Filled.History,
                                contentDescription = if (isArabic) "المحادثات المحفوظة" else "Saved Chats",
                                tint = if (showSavedList) FvgWarmGold else FvgWarmIvory
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            viewModel.clearMessages()
                            viewModel.startNewAiConversation()
                            newChatNotice = true
                        },
                        modifier = Modifier.size(48.dp).testTag("floating_ai_new_chat_button")
                    ) {
                        Icon(
                            Icons.Filled.AddComment,
                            contentDescription = if (isArabic) "محادثة جديدة" else "New Chat",
                            tint = FvgWarmGold
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp).testTag("floating_ai_close_button")
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = if (isArabic) "إغلاق" else "Close",
                            tint = FvgWarmIvory
                        )
                    }
                }

                // Saved Conversations Switcher (when toggled)
                if (showSavedList && state.aiConversations.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isArabic) "المحادثات المحفوظة (${state.aiConversations.size})" else "Saved Conversations (${state.aiConversations.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (state.activeConversationId != null) {
                                    TextButton(onClick = { viewModel.deleteAiConversation(state.activeConversationId) }) {
                                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isArabic) "حذف" else "Delete", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                            FvgChipSelector(
                                options = state.aiConversations.map { it.id },
                                selected = state.activeConversationId ?: "",
                                onSelect = { id ->
                                    state.aiConversations.firstOrNull { it.id == id }?.let {
                                        viewModel.selectAiConversation(it)
                                        showSavedList = false
                                    }
                                },
                                labelResolver = { id ->
                                    state.aiConversations.firstOrNull { it.id == id }?.title?.ifBlank { id.take(8) } ?: id
                                }
                            )
                        }
                    }
                }

                // Mode & Quick Starters
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FvgChipSelector(
                        options = listOf("general", "financial", "ocr"),
                        selected = currentMode,
                        onSelect = {
                            viewModel.clearMessages()
                            viewModel.setAiMode(it)
                        },
                        labelResolver = { m ->
                            when (m) {
                                "general" -> if (isArabic) "ذكاء عام (شامل)" else "General Open AI"
                                "financial" -> if (isArabic) "مشرف ومحلل مالي" else "Workspace Supervisor"
                                else -> if (isArabic) "فحص الفواتير" else "Invoice / OCR"
                            }
                        }
                    )
                    val starters = if (isArabic) listOf(
                        "راجع أرقامي وأعطني خطة عمل وتوصيات",
                        "ما هي الخطوات المتبقية لإكمال بيانات مشروعي؟",
                        "اشرح هامش الربح ونقطة التعادل بمثال مبسط"
                    ) else listOf(
                        "Review my metrics & give actionable next steps",
                        "What workspace setup steps are missing?",
                        "Explain margin vs break-even with a quick example"
                    )
                    FvgChipSelector(
                        options = starters,
                        selected = "",
                        onSelect = { prompt ->
                            val targetMode = if (prompt.contains("أرقامي") || prompt.contains("مشروعي") || prompt.contains("metrics") || prompt.contains("workspace")) "financial" else "general"
                            viewModel.askAiFromFloatingSheet(prompt, targetMode, if (isArabic) "ar" else "en")
                        }
                    )
                }

                if (!state.errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (currentMode != "general") {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearMessages()
                                        viewModel.setAiMode("general")
                                    },
                                    modifier = Modifier.heightIn(min = 40.dp)
                                ) {
                                    Text(if (isArabic) "التبديل للوضع العام والإرسال" else "Switch to General Mode")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))

                // Conversation transcript
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (newChatNotice) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = FvgPositive.copy(alpha = 0.14f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isArabic) "✓ تم بدء محادثة جديدة وحفظ المحادثات السابقة في السجل أعلاه." else "✓ Started a fresh conversation. Previous chats remain saved in History.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = FvgPositive,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    if (state.chatTranscript.isEmpty() && !state.isAiStreaming) {
                        item {
                            FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    text = if (isArabic)
                                        "مرحباً بك! أنا المساعد والمشرف الذكي لـ FVG:\n• أفهم حالياً أن لديك ${state.products.size} منتج/سعر، ${state.costSnapshots.size} حساب تكلفة، ${state.journalEntries.size} قيد محاسبي، و${state.inventoryItems.size} صنف مخزون.\n• اسألني عن أي موضوع عام أو اطلب مراجعة وتوصيات لعملك!"
                                    else
                                        "Welcome! I am your FVG AI Supervisor:\n• Current workspace: ${state.products.size} products/prices, ${state.costSnapshots.size} costings, ${state.journalEntries.size} journals, and ${state.inventoryItems.size} inventory items.\n• Ask any general topic or request actionable advice on your business!",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    itemsIndexed(state.chatTranscript) { _, (role, msg) ->
                        val isUser = role.startsWith("User", ignoreCase = true) || role.equals("user", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isUser) (if (isArabic) "أنت" else "You") else (if (isArabic) "المشرف الذكي FVG" else "FVG AI"),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (!isUser && msg.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                cm?.setPrimaryClip(ClipData.newPlainText("FVG AI Response", msg))
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = if (isArabic) "نسخ" else "Copy", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                                Text(msg, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (state.isAiStreaming) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text(
                                            text = if (isArabic) "جاري التحليل والكتابة..." else "Analyzing & generating answer...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (state.aiResponse.isNotBlank()) {
                                        Text(state.aiResponse, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    if (state.aiSearchQueries.isNotEmpty() || state.aiGroundingSources.isNotEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.55f)),
                                modifier = Modifier.fillMaxWidth().testTag("ai_grounding_sources_card")
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (isArabic) "مصادر البحث المباشر عبر الإنترنت (Google Search Grounding)" else "Live Web Grounding Sources",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    if (state.aiSearchQueries.isNotEmpty()) {
                                        Text(
                                            text = (if (isArabic) "استعلامات البحث: " else "Search Queries: ") + state.aiSearchQueries.joinToString(" • "),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (state.aiGroundingSources.isNotEmpty()) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            state.aiGroundingSources.take(5).forEach { src ->
                                                AssistChip(
                                                    onClick = { runCatching { uriHandler.openUri(src.uri) } },
                                                    label = {
                                                        Text(
                                                            text = src.title.ifBlank { src.uri },
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    },
                                                    trailingIcon = {
                                                        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Controls + Input Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.chatTranscript.isNotEmpty() || state.isAiStreaming) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.isAiStreaming) {
                                OutlinedButton(
                                    onClick = { viewModel.stopAiStreaming() },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isArabic) "إيقاف" else "Stop")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { viewModel.regenerateLastAiResponse(if (isArabic) "ar" else "en") },
                                    modifier = Modifier.heightIn(min = 44.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isArabic) "إعادة توليد" else "Regenerate")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = question,
                            onValueChange = { question = it },
                            placeholder = {
                                Text(if (isArabic) "اكتب أي سؤال داخل أو خارج التطبيق..." else "Ask any question...")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("floating_ai_input"),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = false,
                            maxLines = 3
                        )
                        FilledIconButton(
                            onClick = {
                                if (question.isNotBlank()) {
                                    val q = question
                                    question = ""
                                    viewModel.askAiFromFloatingSheet(q, currentMode, if (isArabic) "ar" else "en")
                                }
                            },
                            enabled = !state.isAiStreaming && question.isNotBlank(),
                            modifier = Modifier.size(52.dp).testTag("floating_ai_send_button"),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = FvgDeepGreen,
                                contentColor = FvgWarmGold
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = if (isArabic) "إرسال" else "Send"
                            )
                        }
                    }
                }
            }
        }
    }
}

