package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.repository.FvgAiRepository
import com.example.data.repository.GroundedSource
import com.example.domain.engine.FvgFinancialEngine
import com.example.ui.auth.AdminCredentialHelper
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

const val APPROVED_ARABIC_ABOUT_US =
    "في ظل متغيّرات اقتصادية متسارعة وضغوط تضخمية حادة تهدد استمرارية المشاريع، يأتي 'دليل رؤيتك المالي' ليكون بوصلتك نحو الاستقرار المالي. نحن لا نكتفي بحساب الأرقام، بل نسهل لك اتخاذ قرارات التسعير الصحيحة بشكل تلقائي ومدروس، لنحول معادلات التكاليف المعقدة إلى أدوات عملية دقيقة تضمن لك الحفاظ على رأس المال، تعظيم الأرباح، والعبور بمشروعك نحو بر الأمان بكل ثقة واحترافية."

@Composable
fun FvgSplashScreen(
    isArabic: Boolean,
    onSplashFinished: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2000L)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FvgWarmIvory)
            .clickable { onSplashFinished() }
            .testTag("fvg_splash_screen")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val ray1 = Path().apply {
                moveTo(w * 0.56f, 0f)
                lineTo(w * 0.80f, 0f)
                lineTo(w * 0.34f, h * 0.44f)
                lineTo(w * 0.16f, h * 0.44f)
                close()
            }
            drawPath(ray1, color = FvgSoftTeal.copy(alpha = 0.08f))

            val ray2 = Path().apply {
                moveTo(w * 0.84f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.14f)
                lineTo(w * 0.52f, h * 0.50f)
                lineTo(w * 0.38f, h * 0.50f)
                close()
            }
            drawPath(ray2, color = FvgSoftTeal.copy(alpha = 0.06f))

            val candles = listOf(
                Triple(0.75f, 0.14f, 0.055f),
                Triple(0.81f, 0.11f, 0.065f),
                Triple(0.87f, 0.085f, 0.075f),
                Triple(0.93f, 0.06f, 0.085f)
            )
            candles.forEachIndexed { idx, (xFrac, topFrac, bodyHFrac) ->
                val cx = w * xFrac
                val bodyW = w * 0.024f
                val bodyTop = h * topFrac
                val bodyH = h * bodyHFrac
                val wickColor = if (idx == candles.lastIndex) FvgWarmGold.copy(alpha = 0.22f) else FvgSoftTeal.copy(alpha = 0.14f)
                drawLine(
                    color = wickColor,
                    start = Offset(cx, bodyTop - h * 0.02f),
                    end = Offset(cx, bodyTop + bodyH + h * 0.02f),
                    strokeWidth = 3f
                )
                drawRoundRect(
                    color = wickColor,
                    topLeft = Offset(cx - bodyW / 2f, bodyTop),
                    size = Size(bodyW, bodyH),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }

            val forestCrest = Path().apply {
                moveTo(0f, h * 0.79f)
                cubicTo(w * 0.28f, h * 0.73f, w * 0.68f, h * 0.86f, w, h * 0.77f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(forestCrest, color = FvgForestGreen)

            val goldRibbon = Path().apply {
                moveTo(0f, h * 0.835f)
                cubicTo(w * 0.30f, h * 0.78f, w * 0.70f, h * 0.90f, w, h * 0.815f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(goldRibbon, color = FvgWarmGold)

            val deepGreenBase = Path().apply {
                moveTo(0f, h * 0.855f)
                cubicTo(w * 0.30f, h * 0.80f, w * 0.70f, h * 0.92f, w, h * 0.835f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(deepGreenBase, color = FvgDeepGreen)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val topPad = (maxHeight * 0.11f).coerceAtLeast(36.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = topPad, start = 28.dp, end = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.fvg_brand_symbol),
                    contentDescription = "FVG Financial Vision Guide Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(134.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "FVG",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = FvgDeepGreen
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Financial Vision Guide",
                    fontFamily = MontserratFontFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = FvgDeepGreen,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.width(34.dp), thickness = 1.5.dp, color = FvgWarmGold)
                    Text(
                        text = "دليل رؤيتك المالي",
                        fontFamily = CairoFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = FvgForestGreen
                    )
                    HorizontalDivider(modifier = Modifier.width(34.dp), thickness = 1.5.dp, color = FvgWarmGold)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (isArabic) "قرارات أذكى.\nمستقبل مالي أقوى." else "Smarter Decisions.\nStronger Financial Future.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = FvgMutedForest,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    color = FvgWarmGold,
                    trackColor = FvgWarmIvory.copy(alpha = 0.25f),
                    strokeWidth = 2.8.dp,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = if (isArabic) "انقر للمتابعة" else "Tap anywhere to continue",
                    style = MaterialTheme.typography.labelSmall,
                    color = FvgWarmIvory.copy(alpha = 0.88f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicWebsiteAndAuthScreen(
    isArabic: Boolean,
    isDark: Boolean,
    isSigningIn: Boolean,
    authError: String?,
    phoneVerificationId: String? = null,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onGoogleSignInClick: () -> Unit,
    onAppleSignInClick: () -> Unit = onGoogleSignInClick,
    onFacebookSignInClick: () -> Unit = onGoogleSignInClick,
    onEmailSignInClick: (email: String, password: String, isSignUp: Boolean, displayName: String, onSuccess: () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onSendPhoneOtpClick: (phone: String, displayName: String, optionalEmail: String) -> Unit = { _, _, _ -> },
    onVerifyPhoneOtpClick: (otpCode: String, displayName: String, optionalEmail: String, onSuccess: () -> Unit) -> Unit = { _, _, _, _ -> },
    onResetPhoneOtp: () -> Unit = {},
    onAdminSignInClick: (email: String, password: String, onSuccess: () -> Unit) -> Unit = { _, _, _ -> },
    onAdminInitialSetupClick: (email: String, newPassword: String, confirmPassword: String, onSuccess: () -> Unit) -> Unit = { _, _, _, _ -> },
    onPasswordResetClick: (email: String) -> Unit = {},
    onClearAuthError: () -> Unit = {},
    aiRepository: FvgAiRepository? = null
) {
    val s = fvgStrings(isArabic)
    val context = LocalContext.current
    var selectedPage by remember { mutableIntStateOf(0) }
    var emailDialogChannel by remember { mutableStateOf<String?>(null) }
    var showPublicAiDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    LaunchedEffect(selectedPage) {
        scrollState.scrollTo(0)
    }
    val pageTitles = listOf(
        s.publicHome, s.publicFeatures, s.publicPricing,
        s.publicAbout, s.publicFaq, s.publicContact,
        s.publicPrivacy, s.publicTerms
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showPublicAiDialog = true },
                containerColor = FvgDeepGreen,
                contentColor = FvgWarmGold,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("public_floating_ai_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = if (isArabic) "المساعد الذكي FVG" else "FVG AI Assistant"
                )
            }
        },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                FvgTopHeaderBanner(
                    title = "${s.brandShort} • ${s.brandTaglineEn}",
                    subtitle = s.brandTaglineAr,
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    isArabic = isArabic,
                    isDark = isDark,
                    onToggleLanguage = onToggleLanguage,
                    onToggleTheme = onToggleTheme
                )
                FvgSubTabRow(
                    tabs = pageTitles,
                    selectedIndex = selectedPage,
                    onSelect = { selectedPage = it },
                    testTagPrefix = "public_nav"
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!authError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = authError,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            when (selectedPage) {
                0 -> PublicHomeSection(
                    isArabic = isArabic,
                    isSigningIn = isSigningIn,
                    aiRepository = aiRepository,
                    onGoogleSignInClick = onGoogleSignInClick,
                    onAppleSignInClick = onAppleSignInClick,
                    onFacebookSignInClick = onFacebookSignInClick,
                    onOpenAuthChannel = { ch -> emailDialogChannel = ch },
                    onNavigatePage = { selectedPage = it }
                )
                1 -> PublicFeaturesAndSectorsSection(isArabic)
                2 -> PublicPricingSection(isArabic, isSigningIn, onGoogleSignInClick) { emailDialogChannel = "email" }
                3 -> PublicAboutSection(isArabic)
                4 -> PublicGuideAndFaqSection(isArabic)
                5 -> PublicContactSection(isArabic) { subject, body ->
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:Financialvisionguide@gmail.com")
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    runCatching { context.startActivity(intent) }
                }
                6 -> PublicPrivacySection(isArabic)
                7 -> PublicTermsSection(isArabic)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    emailDialogChannel?.let { initialChannel ->
        EmailAuthDialog(
            isArabic = isArabic,
            isSigningIn = isSigningIn,
            authError = authError,
            phoneVerificationId = phoneVerificationId,
            initialChannel = initialChannel,
            onDismiss = {
                onClearAuthError()
                onResetPhoneOtp()
                emailDialogChannel = null
            },
            onSubmit = { email, pass, isSignUp, dName ->
                onEmailSignInClick(email, pass, isSignUp, dName) {
                    emailDialogChannel = null
                }
            },
            onSendPhoneOtp = onSendPhoneOtpClick,
            onVerifyPhoneOtp = { code, dName, optEmail ->
                onVerifyPhoneOtpClick(code, dName, optEmail) {
                    emailDialogChannel = null
                }
            },
            onResetPhoneOtp = onResetPhoneOtp,
            onAdminSubmit = { email, pass ->
                onAdminSignInClick(email, pass) {
                    emailDialogChannel = null
                }
            },
            onAdminInitialSetup = { email, newPass, confirmPass ->
                onAdminInitialSetupClick(email, newPass, confirmPass) {
                    emailDialogChannel = null
                }
            },
            onResetPassword = onPasswordResetClick,
            onClearError = onClearAuthError
        )
    }

    if (showPublicAiDialog) {
        PublicFloatingAiDialog(
            isArabic = isArabic,
            aiRepository = aiRepository,
            onDismiss = { showPublicAiDialog = false }
        )
    }
}

@Composable
private fun EmailAuthDialog(
    isArabic: Boolean,
    isSigningIn: Boolean,
    authError: String? = null,
    phoneVerificationId: String? = null,
    initialChannel: String = "email",
    onDismiss: () -> Unit,
    onSubmit: (email: String, password: String, isSignUp: Boolean, displayName: String) -> Unit,
    onSendPhoneOtp: (phone: String, displayName: String, optionalEmail: String) -> Unit,
    onVerifyPhoneOtp: (otpCode: String, displayName: String, optionalEmail: String) -> Unit,
    onResetPhoneOtp: () -> Unit,
    onAdminSubmit: (email: String, password: String) -> Unit,
    onAdminInitialSetup: (email: String, newPassword: String, confirmPassword: String) -> Unit,
    onResetPassword: (String) -> Unit,
    onClearError: () -> Unit = {}
) {
    val context = LocalContext.current
    var authChannel by remember(initialChannel) { mutableStateOf(initialChannel) } // "email", "phone", "admin"
    var isSignUp by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var optionalWelcomeEmail by remember { mutableStateOf("") }
    var phoneOtpCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmAdminPassword by remember { mutableStateOf("") }

    val isAdminInitialSetup = authChannel == "admin" &&
        AdminCredentialHelper.isAdminEmailPendingInitialSetup(context, email)
    val isPhoneOtpSent = authChannel == "phone" && !phoneVerificationId.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (authChannel) {
                    "admin" -> if (isAdminInitialSetup) {
                        if (isArabic) "تعيين كلمة مرور الإدارة (للمرة الأولى)" else "One-Time Initial Admin Setup"
                    } else {
                        if (isArabic) "بوابة إدارة المنصة" else "Platform Admin Console"
                    }
                    "phone" -> if (isPhoneOtpSent) {
                        if (isArabic) "أدخل رمز التحقق SMS" else "Enter SMS Verification Code"
                    } else if (isSignUp) {
                        if (isArabic) "تسجيل حساب برقم الهاتف" else "Register with Phone Number"
                    } else {
                        if (isArabic) "دخول برقم الهاتف" else "Sign In with Phone"
                    }
                    else -> if (isSignUp) (if (isArabic) "إنشاء حساب جديد بالبريد الإلكتروني" else "Create Account with Email") else (if (isArabic) "تسجيل الدخول بالبريد" else "Sign In with Email")
                },
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FvgChipSelector(
                    options = listOf("email", "phone", "admin"),
                    selected = authChannel,
                    onSelect = { ch ->
                        authChannel = ch
                        onClearError()
                        if (ch != "phone") onResetPhoneOtp()
                        if (ch == "admin") {
                            isSignUp = false
                        }
                    },
                    labelResolver = {
                        when (it) {
                            "phone" -> if (isArabic) "رقم الهاتف" else "Phone"
                            "admin" -> if (isArabic) "الإدارة" else "Admin"
                            else -> if (isArabic) "البريد" else "Email"
                        }
                    }
                )

                if (!authError.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = authError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (authChannel == "admin") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isAdminInitialSetup) FvgWarmGold.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isAdminInitialSetup) {
                                if (isArabic)
                                    "إعداد الأمان لمرة واحدة: لا توجد أي كلمة مرور افتراضية. أنشئ كلمة مرور إدارية قوية الآن (8 أحرف على الأقل وتشمل حرفاً ورقماً/رمزاً) لحفظها وتفعيل اللوحة."
                                else
                                    "One-Time Security Setup: No default password is active. Choose a strong permanent administrator password now (8+ chars, letter & number/symbol)."
                            } else {
                                if (isArabic)
                                    "بوابة مشفرة لإدارة المنصة. أدخل البريد الإداري وكلمة المرور يدوياً للتحقق."
                                else
                                    "Secured Admin Portal. Enter administrator email and password manually to verify access."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (authChannel != "admin" && !isPhoneOtpSent) {
                    FvgChipSelector(
                        options = listOf("signin", "signup"),
                        selected = if (isSignUp) "signup" else "signin",
                        onSelect = { isSignUp = (it == "signup") },
                        labelResolver = {
                            if (it == "signin") (if (isArabic) "دخول" else "Sign In")
                            else (if (isArabic) "حساب جديد (تجربة 7 أيام)" else "Register (7-Day Trial)")
                        }
                    )
                }

                if (isSignUp && authChannel != "admin" && !isPhoneOtpSent) {
                    FvgField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = if (isArabic) "الاسم الكامل (سيظهر في رسائل الترحيب والتقارير)" else "Full Name (Used in Welcome & Reports)",
                        testTag = "auth_display_name_input"
                    )
                }

                if (authChannel == "phone") {
                    if (!isPhoneOtpSent) {
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            label = { Text(if (isArabic) "رقم الهاتف الدولي (مثال: +249912345678)" else "Phone Number (e.g. +249912345678)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("phone_auth_input")
                        )
                        if (isSignUp) {
                            OutlinedTextField(
                                value = optionalWelcomeEmail,
                                onValueChange = { optionalWelcomeEmail = it },
                                label = { Text(if (isArabic) "البريد الإلكتروني لاستلام رسالة الترحيب (اختياري)" else "Email for Welcome Message (Optional)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("phone_optional_email_input")
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (isArabic) "تم إرسال رمز تحقق SMS إلى $phoneNumber" else "SMS verification code sent to $phoneNumber",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(
                                    onClick = {
                                        phoneOtpCode = ""
                                        onResetPhoneOtp()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(if (isArabic) "تغيير رقم الهاتف" else "Change phone number", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        OutlinedTextField(
                            value = phoneOtpCode,
                            onValueChange = { ch -> phoneOtpCode = ch.filter { it.isDigit() }.take(8) },
                            label = { Text(if (isArabic) "رمز التحقق المكون من 6 أرقام" else "6-Digit SMS Verification Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("phone_otp_input")
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text(if (isArabic) "البريد الإلكتروني" else "Email Address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        readOnly = false,
                        modifier = Modifier.fillMaxWidth().testTag("email_auth_input")
                    )

                    var passwordVisible by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = {
                            Text(
                                if (isAdminInitialSetup) {
                                    if (isArabic) "كلمة مرور الإدارة الجديدة (8 أحرف+)" else "New Admin Password (min 8 chars)"
                                } else {
                                    if (isArabic) "كلمة المرور (6 أحرف+)" else "Password (min 6 chars)"
                                }
                            )
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { passwordVisible = !passwordVisible },
                                modifier = Modifier.testTag("toggle_password_visibility")
                            ) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (passwordVisible) "Hide password" else "Show password"
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("password_auth_input")
                    )

                    if (isAdminInitialSetup) {
                        var confirmVisible by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = confirmAdminPassword,
                            onValueChange = { confirmAdminPassword = it },
                            label = { Text(if (isArabic) "تأكيد كلمة مرور الإدارة" else "Confirm Admin Password") },
                            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { confirmVisible = !confirmVisible },
                                    modifier = Modifier.testTag("toggle_confirm_admin_password_visibility")
                                ) {
                                    Icon(
                                        imageVector = if (confirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (confirmVisible) "Hide confirm password" else "Show confirm password"
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("confirm_admin_password_input")
                        )
                    }
                }

                if (authChannel == "admin" && !isAdminInitialSetup) {
                    TextButton(
                        onClick = {
                            val targetEmail = email.trim().ifBlank { AdminCredentialHelper.OWNER_EMAIL_CANONICAL }
                            onResetPassword(targetEmail)
                        },
                        modifier = Modifier.testTag("admin_forgot_password_button")
                    ) {
                        Text(
                            text = if (isArabic) "نسيت كلمة المرور؟ إرسال رابط استعادة إلى البريد الإداري" else "Forgot Admin Password? Send Reset Link",
                            color = FvgWarmGold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (authChannel == "email") {
                    TextButton(
                        onClick = { if (email.contains("@")) onResetPassword(email.trim()) },
                        enabled = email.contains("@"),
                        modifier = Modifier.testTag("auth_forgot_password_button")
                    ) {
                        Text(
                            text = if (isSignUp) {
                                if (isArabic) "لديك حساب ولكن نسيت كلمة المرور؟ استعادة الحساب" else "Have an account but forgot password? Reset here"
                            } else {
                                if (isArabic) "نسيت كلمة المرور؟ إرسال رابط إعادة التعيين" else "Forgot password? Send reset link"
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                if (authChannel == "phone" && optionalWelcomeEmail.contains("@")) {
                    TextButton(
                        onClick = { onResetPassword(optionalWelcomeEmail.trim()) },
                        modifier = Modifier.testTag("phone_forgot_password_button")
                    ) {
                        Text(
                            text = if (isArabic) "نسيت كلمة المرور؟ استعادة عبر البريد الإلكتروني" else "Forgot password? Reset via email",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            val strengthError = if (isAdminInitialSetup) AdminCredentialHelper.validatePasswordStrength(password, isArabic) else null
            val isValid = when (authChannel) {
                "phone" -> if (isPhoneOtpSent) phoneOtpCode.length >= 4 else phoneNumber.filter { it.isDigit() }.length >= 9
                "admin" -> if (isAdminInitialSetup) {
                    email.contains("@") && strengthError == null && password == confirmAdminPassword
                } else {
                    email.contains("@") && password.length >= 6
                }
                else -> email.contains("@") && password.length >= 6
            }
            FvgPrimaryButton(
                text = when {
                    authChannel == "admin" && isAdminInitialSetup -> if (isArabic) "اعتماد كلمة المرور وفتح الإدارة" else "Set Password & Open Admin"
                    authChannel == "admin" -> if (isArabic) "تحقق ودخول لوحة الإدارة" else "Verify & Open Admin"
                    authChannel == "phone" && !isPhoneOtpSent -> if (isArabic) "إرسال رمز SMS" else "Send SMS Code"
                    authChannel == "phone" && isPhoneOtpSent -> if (isArabic) "تأكيد الرمز والدخول" else "Verify & Sign In"
                    isSignUp -> if (isArabic) "إنشاء وبدء التجربة المجانية" else "Create Account & Start Trial"
                    else -> if (isArabic) "تسجيل الدخول" else "Sign In"
                },
                enabled = !isSigningIn && isValid,
                onClick = {
                    onClearError()
                    when (authChannel) {
                        "admin" -> if (isAdminInitialSetup) {
                            onAdminInitialSetup(email.trim(), password, confirmAdminPassword)
                        } else {
                            onAdminSubmit(email.trim(), password)
                        }
                        "phone" -> {
                            val resolvedName = displayName.trim().ifBlank { phoneNumber.trim() }
                            if (!isPhoneOtpSent) {
                                onSendPhoneOtp(phoneNumber.trim(), resolvedName, optionalWelcomeEmail.trim())
                            } else {
                                onVerifyPhoneOtp(phoneOtpCode.trim(), resolvedName, optionalWelcomeEmail.trim())
                            }
                        }
                        else -> {
                            val resolvedName = displayName.trim().ifBlank {
                                email.substringBefore("@").replace(".", " ")
                            }
                            onSubmit(email.trim(), password, isSignUp, resolvedName)
                        }
                    }
                },
                testTag = "email_auth_submit_button"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isArabic) "إلغاء" else "Cancel")
            }
        }
    )
}

@Composable
private fun PublicHomeSection(
    isArabic: Boolean,
    isSigningIn: Boolean,
    aiRepository: FvgAiRepository? = null,
    onGoogleSignInClick: () -> Unit,
    onAppleSignInClick: () -> Unit,
    onFacebookSignInClick: () -> Unit,
    onOpenAuthChannel: (String) -> Unit,
    onNavigatePage: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = FvgDeepGreen,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.fvg_brand_symbol),
                    contentDescription = "FVG Brand Symbol",
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(FvgWarmIvory)
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isArabic) "منصة FVG للمحاسبة الإدارية والتسعير" else "FVG Management Accounting & Pricing",
                        style = MaterialTheme.typography.headlineMedium,
                        color = FvgWarmIvory
                    )
                    Text(
                        text = if (isArabic) "دليل رؤيتك المالي • قرارات أذكى ومستقبل مالي أقوى" else "Financial Vision Guide • Smarter Decisions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = FvgWarmGold
                    )
                }
            }
            Text(
                text = if (isArabic)
                    "مراقبة تغيرات الأسعار والتضخم والصرف اليدوي، 8 طرق لحساب تكاليف المنتجات وتركيبات المواد وتحليل المخاطر، القيود المحاسبية المزدوجة والقوائم المالية، تقييم المخزون بـ FIFO والمتوسط المرجح وتكلفة الاستبدال، والمساعد المالي مع قراءة الفواتير."
                else
                    "Track prices, inflation indices & manual FX, compute 8 costing methods + BOM recipes + risk ratings + variances, post double-entry journals & IFRS-aligned statements, value inventory (FIFO, MWA & replacement cost), and query the 3-mode Financial Assistant & Camera OCR.",
                style = MaterialTheme.typography.bodyLarge,
                color = FvgWarmIvory.copy(alpha = 0.92f)
            )
            FvgGoldButton(
                text = if (isSigningIn) (if (isArabic) "جاري المصادقة..." else "Signing in...")
                else if (isArabic) "الدخول / بدء تجربة أسبوع عبر Google" else "Sign in / Start Trial with Google",
                onClick = onGoogleSignInClick,
                enabled = !isSigningIn,
                icon = Icons.Filled.AccountCircle,
                modifier = Modifier.fillMaxWidth(),
                testTag = "google_sign_in_button"
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onAppleSignInClick,
                    enabled = !isSigningIn,
                    border = BorderStroke(1.dp, FvgWarmGold),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("auth_apple_button")
                ) {
                    Icon(Icons.Filled.PhoneIphone, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Apple", color = FvgWarmIvory, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onFacebookSignInClick,
                    enabled = !isSigningIn,
                    border = BorderStroke(1.dp, FvgWarmGold),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("auth_fb_button")
                ) {
                    Icon(Icons.Filled.Facebook, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Facebook", color = FvgWarmIvory, style = MaterialTheme.typography.labelMedium)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onOpenAuthChannel("email") },
                    enabled = !isSigningIn,
                    border = BorderStroke(1.dp, FvgWarmGold),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_email_auth_button")
                ) {
                    Icon(Icons.Filled.Email, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isArabic) "البريد الإلكتروني" else "Email", color = FvgWarmIvory, style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { onOpenAuthChannel("phone") },
                    enabled = !isSigningIn,
                    border = BorderStroke(1.dp, FvgWarmGold),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("open_phone_auth_button")
                ) {
                    Icon(Icons.Filled.Phone, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (isArabic) "رقم الهاتف" else "Phone", color = FvgWarmIvory, style = MaterialTheme.typography.labelMedium)
                }
            }
            OutlinedButton(
                onClick = { onOpenAuthChannel("admin") },
                enabled = !isSigningIn,
                border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("open_admin_auth_button")
            ) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isArabic) "بوابة الإدارة (/admin)" else "Admin Portal (/admin)", color = FvgWarmGold, style = MaterialTheme.typography.labelMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onOpenAuthChannel("email") },
                    modifier = Modifier.testTag("home_forgot_password_button")
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = FvgWarmGold, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isArabic) "نسيت كلمة المرور؟ استعادة الحساب بالبريد الإلكتروني" else "Forgot Password? Reset via Email",
                        color = FvgWarmGold,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onNavigatePage(1) },
                    border = BorderStroke(1.dp, FvgWarmGold.copy(alpha = 0.7f)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (isArabic) "المزايا والقطاعات" else "Features & Sectors", color = FvgWarmGold)
                }
                OutlinedButton(
                    onClick = { onNavigatePage(2) },
                    border = BorderStroke(1.dp, FvgWarmIvory.copy(alpha = 0.6f)),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (isArabic) "الباقات وبنكك" else "Plans & Bankak", color = FvgWarmIvory)
                }
            }

            val preLoginScope = rememberCoroutineScope()
            val uriHandler = LocalUriHandler.current
            val resolvedAiRepo = remember(aiRepository) { aiRepository ?: runCatching { FvgAiRepository() }.getOrNull() }
            var preLoginQ by remember { mutableStateOf("") }
            var preLoginAns by remember {
                mutableStateOf(
                    if (isArabic) "مرحباً بك في المساعد الذكي FVG AI! اسألني أي سؤال — سواءً داخل FVG (الباقات، التجربة، التكاليف، القطاعات) أو أي استفسار معرفي عام مع البحث المباشر عبر الإنترنت."
                    else "Welcome to FVG AI Assistant! Ask any question — inside FVG (plans, trial, costing, sectors) or any live web & general knowledge topic."
                )
            }
            var preLoginQueries by remember { mutableStateOf<List<String>>(emptyList()) }
            var preLoginSources by remember { mutableStateOf<List<GroundedSource>>(emptyList()) }
            var preLoginStreaming by remember { mutableStateOf(false) }
            var preLoginJob by remember { mutableStateOf<Job?>(null) }
            var preLoginHistory by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

            fun submitPreLoginPrompt(rawPrompt: String) {
                val prompt = rawPrompt.trim()
                if (prompt.isBlank()) return
                preLoginJob?.cancel()
                val repo = resolvedAiRepo
                if (repo == null) {
                    preLoginAns = if (FvgFinancialEngine.isAccountingOrFvgTopic(prompt)) {
                        FvgFinancialEngine.evaluateQuickGuideAnswer(prompt, isArabic)
                    } else if (isArabic) {
                        "💡 المساعد الذكي FVG AI جاهز للإجابة على كافة الأسئلة العامة والاستفسارات الاقتصادية والمعرفية."
                    } else {
                        "💡 FVG AI Assistant is ready to answer all general knowledge and financial queries."
                    }
                    return
                }
                preLoginJob = preLoginScope.launch {
                    preLoginStreaming = true
                    preLoginAns = ""
                    preLoginQueries = emptyList()
                    preLoginSources = emptyList()
                    var latestText = ""
                    try {
                        repo.streamGroundedExplanationWithSources(
                            question = prompt,
                            systemDataContext = "",
                            languageCode = if (isArabic) "ar" else "en",
                            aiMode = "general",
                            history = preLoginHistory
                        )
                            .catch { e ->
                                if (e is CancellationException) throw e
                                latestText = repo.buildOfflineDeterministicFallback(
                                    question = prompt,
                                    systemDataContext = "",
                                    isArabic = isArabic,
                                    errorDetail = null
                                )
                                preLoginAns = latestText
                            }
                            .collect { update ->
                                latestText = update.text
                                preLoginAns = update.text
                                if (update.searchQueries.isNotEmpty()) preLoginQueries = update.searchQueries
                                if (update.sources.isNotEmpty()) preLoginSources = update.sources
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        preLoginStreaming = false
                        if (latestText.isNotBlank()) {
                            preLoginHistory = (preLoginHistory + ("user" to prompt) + ("model" to latestText)).takeLast(8)
                        }
                    }
                }
            }

            @OptIn(ExperimentalLayoutApi::class)
            FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = if (isArabic) "اسأل المساعد الذكي FVG AI (مع البحث المباشر)" else "Ask FVG AI Assistant (with Live Search)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                FvgChipSelector(
                    options = if (isArabic) listOf("كم مدة التجربة المجانية؟", "ما الفرق بين الهامش والإضافة؟", "ما هي أنواع الزهور وفوائدها؟")
                    else listOf("How long is the free trial?", "Margin vs Markup?", "What are the main types of flowers?"),
                    selected = preLoginQ,
                    onSelect = { q ->
                        preLoginQ = q
                        submitPreLoginPrompt(q)
                    }
                )
                FvgField(
                    value = preLoginQ,
                    onValueChange = { preLoginQ = it },
                    label = if (isArabic) "اكتب أي سؤال داخل أو خارج التطبيق..." else "Ask any inside or outside question...",
                    testTag = "pre_login_ai_input"
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = if (preLoginStreaming) (if (isArabic) "جاري توليد الإجابة..." else "Generating AI answer...")
                        else if (isArabic) "اسأل المساعد الذكي FVG AI" else "Ask FVG AI Assistant",
                        icon = Icons.Filled.AutoAwesome,
                        enabled = !preLoginStreaming && preLoginQ.isNotBlank(),
                        onClick = { submitPreLoginPrompt(preLoginQ) },
                        modifier = Modifier.weight(1f),
                        testTag = "pre_login_ai_send_button"
                    )
                    if (preLoginStreaming) {
                        OutlinedButton(
                            onClick = {
                                preLoginJob?.cancel()
                                preLoginStreaming = false
                            },
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "إيقاف" else "Stop")
                        }
                    }
                }
                if (preLoginStreaming && preLoginAns.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(if (isArabic) "يتواصل مع المساعد الذكي FVG AI والبحث المباشر..." else "Querying FVG AI Assistant with live search...", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (preLoginAns.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(preLoginAns, style = MaterialTheme.typography.bodyMedium)
                            if (preLoginQueries.isNotEmpty()) {
                                Text(
                                    text = (if (isArabic) "بحث الإنترنت: " else "Web Search: ") + preLoginQueries.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (preLoginSources.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    preLoginSources.take(5).forEach { src ->
                                        AssistChip(
                                            onClick = { runCatching { uriHandler.openUri(src.uri) } },
                                            label = { Text(src.title.ifBlank { src.uri }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
                                            trailingIcon = { Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    FvgTrialCountdownBanner(
        trialEndsAtMillis = null,
        isArabic = isArabic,
        isPreviewTrial = true,
        durationHours = 168L,
        onActionClick = { onNavigatePage(2) }
    )

    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = if (isArabic) "رؤية دليل رؤيتك المالي (FVG)" else "FVG Vision & Purpose",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isArabic) APPROVED_ARABIC_ABOUT_US else
                "Amid rapid economic shifts and acute inflationary pressures threatening business continuity, Financial Vision Guide (FVG) serves as your compass toward financial stability—turning complex cost equations into practical tools that safeguard capital, optimize pricing, and strengthen enterprise decisions.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }

    FvgFounderCard(isArabic = isArabic, compact = false)
}

@Composable
private fun PublicFeaturesAndSectorsSection(isArabic: Boolean) {
    val s = fvgStrings(isArabic)
    var inspectedSectorKey by remember { mutableStateOf<String?>(null) }

    val modules = if (isArabic) listOf(
        "مراقبة الأسعار والوحدات والتنبيهات" to "تسجيل تاريخي غير قابل للكتابة الفوقية، تنبيهات القفزات (>= 15%)، منع التكرار، الرسوم البيانية، واستيراد الجداول.",
        "التضخم والسلات المرجحة والصرف اليدوي" to "مؤشرات شهرية وسنوية وتراكمية وتوثيق يدوي لأسعار الصرف التاريخية مع تنبيه انتهاء الصلاحية بعد 7 أيام.",
        "طرق التكاليف الـ 8 والتركيبات والمخاطر" to "التكلفة الفعلية، المعيارية، المستهدفة، تكلفة الأنشطة، أوامر الإنتاج، المراحل، الكلية، والمتغيرة مع وصفات المواد وتقييم المخاطر وتحليل الانحراف.",
        "المحاسبة والقوائم المالية والأستاذ والنسب الـ 18" to "مسودة وترحيل وعكس القيود، دفتر الأستاذ العام بالرصيد المتحرك، مقارنة فترتين، أعمار الديون، و18 نسبة مالية.",
        "المخزون والدفعات والجرد والهدر والموازنات" to "تقييم الوارد أولاً صادر أولاً والمتوسط المرجح وتكلفة الاستبدال، تتبع الدفعة والصلاحية، طلبات الهدر، الجرد الفعلي، والموازنات.",
        "المساعد المالي وفحص الفواتير بالكاميرا والتصدير" to "المساعد المالي التفاعلي المحادثاتي + قراءة الفواتير من الكاميرا للمراجعة البشرية + الآلة الحاسبة ثلاثية الأبعاد + تصدير التقارير والنسخ الاحتياطي."
    ) else listOf(
        "Price Registry, Anomaly Alerts & Trends" to "Append-only observation trail, duplicate prevention, >=15% unusual movement alerts, historical spline charts, and sanitized CSV/XLSX import.",
        "Inflation, Weighted Baskets & Manual FX" to "Official/calculated/manual/provisional tagging, cumulative compounded inflation, weighted baskets, and verified manual FX ledger.",
        "8 Costing Methods, BOM Recipes & Variance" to "Actual, Standard, Target, ABC, Job Order, Process, Absorption & Variable costing plus BOM formulas, Risk Rating & Material/Labor Variances.",
        "Double-Entry Accounting, GL, Aging & 18 Ratios" to "Draft/Post/Reverse journals, CSV accounting import, Account GL running balance, Period A vs B profit bridge, AR/AP aging & 18 ratios.",
        "Inventory, Batch Expiry, Scrap & Budgets" to "FIFO/MWA valuation, Book vs Replacement comparison, batch & expiry warnings, admin-approved Scrap/Losses, stocktakes & budgets.",
        "Financial Assistant, Camera OCR & Exports" to "Multi-conversation Chat Assistant + camera-first Invoice OCR drafts + 3D Calculator + PDF/XLSX/CSV/JSON & Workspace backup."
    )

    modules.forEach { (title, desc) ->
        FvgCard {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(desc, style = MaterialTheme.typography.bodyMedium)
        }
    }

    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = if (isArabic) "القطاعات المفعّلة وخارطة القطاعات القادمة (اضغط على قطاع مفعّل للاستعراض)"
            else "Supported Sectors & Roadmap (Tap an Active Sector to Inspect)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        val activeSectors = listOf(
            "manufacturing" to (if (isArabic) "التصنيع والإنتاج الصناعي (خطوط الإنتاج والتركيبات والتكلفة المعيارية)" else "Manufacturing & Industrial Production (BOM, Standard, Process & Job Costing)"),
            "food_beverage" to (if (isArabic) "الأغذية والمشروبات والتعبئة والتجزئة (تتبع الصلاحية والدفعات والهدر)" else "Food, Beverage, Packaging & Retail Operations (Batch Expiry & Scrap Control)")
        )
        activeSectors.forEach { (secKey, secTitle) ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, FvgPositive.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { inspectedSectorKey = secKey }
                    .testTag("public_sector_card_$secKey")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(secTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = FvgPositive.copy(alpha = 0.16f)) {
                        Text(s.activeSectorBadge, style = MaterialTheme.typography.labelSmall, color = FvgPositive, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                }
            }
        }
        HorizontalDivider(color = FvgWarmGold.copy(alpha = 0.4f))
        val comingSoonSectors = if (isArabic) listOf(
            "التجارة والتجزئة والجملة",
            "الصيدلة والرعاية الصحية",
            "الزراعة والثروة الحيوانية",
            "المقاولات والإنشاءات",
            "الخدمات اللوجستية والنقل",
            "الضيافة والمطاعم",
            "الخدمات والاستشارات المهنية",
            "التجارة الإلكترونية والتوريد الرقمي"
        ) else listOf(
            "Retail & Wholesale Trade",
            "Pharmacy & Healthcare",
            "Agriculture & Livestock",
            "Construction & Contracting",
            "Logistics & Transport",
            "Hospitality & Catering",
            "Professional Services & Consulting",
            "E-Commerce & Digital Supply"
        )
        comingSoonSectors.forEach { sec ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("• $sec", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = FvgWarmGold.copy(alpha = 0.25f)) {
                    Text(s.comingSoonBadge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
    }

    inspectedSectorKey?.let { key ->
        val secInfo = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == key }
        val templateAccounts = FvgFinancialEngine.sectorAccountsTemplate(key)
        AlertDialog(
            onDismissRequest = { inspectedSectorKey = null },
            title = {
                Text(
                    text = if (isArabic) (secInfo?.nameAr ?: key) else (secInfo?.nameEn ?: key),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isArabic)
                            "دليل الحسابات التخصصي الجاهز لهذا القطاع (${templateAccounts.size} حسابات):"
                        else
                            "Ready-to-use Sector Chart of Accounts (${templateAccounts.size} accounts):",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    templateAccounts.forEach { acc ->
                        Text(
                            text = "• ${acc.code} — ${if (isArabic) acc.nameAr else acc.name} (${acc.accountType})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Text(
                        text = if (isArabic)
                            "سجّل الدخول لتطبيق قالب القطاع مباشرة على دليل حسابات منظمتك مع تفعيل طرق التكاليف الـ 8."
                        else
                            "Sign in to apply this starter COA template directly to your organization workspace.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedSectorKey = null }) {
                    Text(if (isArabic) "إغلاق" else "Close")
                }
            }
        )
    }
}

@Composable
private fun PublicPricingSection(
    isArabic: Boolean,
    isSigningIn: Boolean,
    onSignInClick: () -> Unit,
    onOpenEmailAuth: () -> Unit
) {
    FvgTrialCountdownBanner(
        trialEndsAtMillis = null,
        isArabic = isArabic,
        isPreviewTrial = true,
        durationHours = 168L
    )

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, FvgWarmGold),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (isArabic)
                "الشفافية في الدفع: تبدأ المنظمات الجديدة بفترة تجربة كاملة لمدة أسبوع واحد (7 أيام / 168 ساعة) متزامنة مع الخادم. التفعيل بعد التجربة يتم بإرسال مرجع التحويل وآخر 4 أرقام من المعاملة واسم المرسل لمراجعة مالكة المنصة (زهراء يوسف) والموافقة أو الرفض."
            else
                "Transparent Payment Model: Workspaces begin with a strict 7-day (168-hour) server-synced free trial. Subscriptions activate after submitting transfer reference, last 4 digits, and sender name for exclusive Platform Owner (Zahra Yousif) verification.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(14.dp)
        )
    }

    val plans = listOf(
        Triple(
            if (isArabic) "التجربة المجانية الكاملة (7 أيام)" else "7-Day (168h) Free Trial",
            if (isArabic) "0 مجاني" else "0 Free",
            listOf(
                if (isArabic) "تقييم كامل لـ 8 طرق تكلفة والقيود والمخزون خلال أسبوع التجربة" else "Full evaluation access during your 168-hour server-synced trial",
                if (isArabic) "عداد وقت حقيقي بتوقيت الخادم (أيام : ساعات : دقائق : ثواني)" else "Strict Server-Synced D:H:M:S Countdown Timer"
            )
        ),
        Triple(
            if (isArabic) "الباقة الأساسية (Starter)" else "Starter Plan",
            if (isArabic) "28,000 ج.س / $12 شهرياً" else "$12 / 28,000 SDG / mo",
            listOf(
                if (isArabic) "منظمة واحدة • حتى 25 منتجاً • مراقبة الأسعار والتضخم والصرف اليدوي" else "1 Organization • Up to 25 products • Prices, Inflation & Manual FX",
                if (isArabic) "طريقتا تكلفة (الفعلي والمعياري) + دفتر الأستاذ والمخزون الأساسي" else "2 Costing methods (Actual & Standard) + Basic Ledger & Stock",
                if (isArabic) "لا تشمل تركيبات المواد أو المساعد الذكي أو مسح الفواتير بالكاميرا" else "Excludes BOM/Production, Camera OCR, AI Assistant & PDF/XLSX"
            )
        ),
        Triple(
            if (isArabic) "الباقة الاحترافية (Professional)" else "Professional Plan",
            if (isArabic) "65,000 ج.س / $29 شهرياً" else "$29 / 65,000 SDG / mo",
            listOf(
                if (isArabic) "3 منظمات • حتى 250 منتجاً • جميع طرق التكاليف الـ 8 وتركيبات المواد" else "3 Organizations • Up to 250 products • All 8 Costing Methods & BOM",
                if (isArabic) "المساعد المالي الذكي + قراءة الفواتير بالكاميرا + تصدير PDF و XLSX + 18 نسبة" else "AI Financial Assistant + Camera OCR + PDF/XLSX + 18 Ratios + FIFO/MWA"
            )
        ),
        Triple(
            if (isArabic) "باقة المؤسسات الشاملة (Enterprise)" else "Enterprise Plan",
            if (isArabic) "175,000 ج.س / $79 شهرياً" else "$79 / 175,000 SDG / mo",
            listOf(
                if (isArabic) "حتى 50 منظمة و 10,000 منتج • كافة المزايا الاحترافية والذكاء المالي" else "Up to 50 Orgs & 10,000 Products • Everything in Professional",
                if (isArabic) "إدارة الفرق والصلاحيات وسجل التدقيق والنسخ الاحتياطي الشامل ودعم أولوية (00249911569966)" else "Full RBAC, Immutable Audit Trail, Workspace Backup & Priority Support"
            )
        )
    )

    plans.forEach { (name, price, bullets) ->
        FvgCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = FvgWarmGold) {
                    Text(
                        price,
                        style = MaterialTheme.typography.labelLarge,
                        color = FvgDeepGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            bullets.forEach { b ->
                Text("• $b", style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FvgPrimaryButton(
                    text = if (isArabic) "دخول عبر Google" else "Start with Google",
                    onClick = onSignInClick,
                    enabled = !isSigningIn,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = onOpenEmailAuth,
                    enabled = !isSigningIn,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                ) {
                    Text(if (isArabic) "دخول بالإيميل" else "Use Email")
                }
            }
        }
    }

    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = if (isArabic) "تثبيت نسخة الويب على الآيفون والتابلت والكمبيوتر" else "Install Web PWA on iPhone, Tablet & Desktop",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isArabic)
                "يعمل نظام دليل رؤيتك المالي عبر تطبيق أندرويد وعبر تطبيق ويب تقدمي متزامن على نفس قاعدة البيانات. على أجهزة iPhone افتح الرابط في Safari ثم اختر مشاركة ثم إضافة إلى الشاشة الرئيسية."
            else
                "FVG runs identically on Android and as an installable Web PWA synced to the same Firestore database. On iPhone, open in Safari -> Share -> Add to Home Screen.",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PublicAboutSection(isArabic: Boolean) {
    FvgFounderCard(isArabic = isArabic, compact = false)

    FvgCard {
        Text(
            text = if (isArabic) "عن دليل رؤيتك المالي (FVG)" else "About Financial Vision Guide (FVG)",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isArabic) APPROVED_ARABIC_ABOUT_US else
                "Financial Vision Guide (FVG) helps businesses navigate rapid inflation and cost volatility with transparent unit costing, double-entry accrual accounting, inventory valuation, and grounded decision support.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        HorizontalDivider()
        Text(
            text = if (isArabic)
                "• مبادئنا: الدقة الحسابية، منع اختلاق الأرقام، عزل بيانات كل منظمة، وشفافية الدفع اليدوي.\n• رقم التواصل المباشر: 00249911569966"
            else
                "• Core Principles: Deterministic financial precision, zero fabricated figures, strict multi-tenant isolation, and transparent manual payment verification.\n• Direct Owner Phone: 00249911569966",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PublicGuideAndFaqSection(isArabic: Boolean) {
    FvgCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = if (isArabic) "دليل التشغيل السريع في 9 خطوات" else "9-Step Quick Start User Guide",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        val steps = if (isArabic) listOf(
            "1. سجل الدخول عبر Google أو البريد لبدء تجربتك المجانية (7 أيام / 168 ساعة).",
            "2. أنشئ مساحة عمل المنظمة وحدد القطاع والعملة الأساسية (الافتراضي SDG).",
            "3. أضف المنتجات والمواد الخام وسجل ملاحظات الأسعار الدورية.",
            "4. أدخل معدلات التضخم وسلة الأسعار المرجحة وأسعار الصرف اليدوية.",
            "5. احسب التكلفة بأحد الأساليب الـ 8 وتركيبات BOM وراقب المخاطر والتعادل.",
            "6. سجّل التكلفة المعيارية وأوامر الإنتاج وحلل انحرافات السعر والكمية.",
            "7. رحّل القيود المزدوجة في المحاسبة وتابع القوائم المالية والـ 18 نسبة.",
            "8. قيّم المخزون بـ FIFO أو المتوسط المرجح وتابع تواريخ الصلاحية والهدر.",
            "9. استشر المساعد المالي وصوّر الفواتير بالكاميرا وصدّر PDF / XLSX / CSV / JSON."
        ) else listOf(
            "1. Sign in via Google or Email to start your 7-day (168h) server-synced workspace trial.",
            "2. Initialize your Organization Workspace, sector & base currency (SDG default).",
            "3. Register products/raw materials and record dated price observations.",
            "4. Track monthly/annual inflation rates, weighted baskets, and manual FX rates.",
            "5. Run any of the 8 Costing Methods & BOM formulas and review Risk & Break-Even CVP.",
            "6. Record Standard Cost Sheets & Production Orders to inspect Price/Usage variances.",
            "7. Post balanced double-entry journals to view Income, Balance Sheet & 18 Ratios.",
            "8. Manage FIFO/MWA inventory, expiry batches, stocktakes & scrap approvals.",
            "9. Query Financial Assistant, scan Camera Invoice OCR drafts, or export PDF/XLSX/CSV/JSON."
        )
        steps.forEach { st ->
            Text(st, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    val faqs = if (isArabic) listOf(
        "ما مدة الفترة التجريبية وكيف يُحسب العداد؟" to
            "تستمر الفترة التجريبية أسبوعاً كاملاً (7 أيام / 168 ساعة) بتوقيت الخادم للمنظمات الجديدة مع عداد أيام وساعات ودقائق وثواني.",
        "ما الفرق بين هامش الربح (Margin) ونسبة الإضافة (Markup)؟" to
            "الهامش يُحسب بقسمة الربح على سعر البيع، بينما Markup يُحسب بقسمة الربح على تكلفة الوحدة لمنع التسعير بأقل من المستهدف.",
        "كيف تتم مراجعة مدفوعات بنكك وأوكاش؟" to
            "يرسل المستخدم رقم المرجع وآخر 4 أرقام من المعاملة واسم المرسل، وتصل الطلبات مباشرة إلى لوحة مالكة المنصة للموافقة أو الرفض."
    ) else listOf(
        "How long is the free trial and how is it enforced?" to
            "New users receive a strict 7-day (168-hour) free trial anchored to Firestore server time with a live monotonic D:H:M:S countdown.",
        "Why does FVG separate Profit Margin % from Markup %?" to
            "Margin divides unit profit by selling price, whereas Markup divides unit profit by unit cost.",
        "How are Bankak & O-Cash payments verified?" to
            "Submit the transfer reference, last 4 digits, sender name, and date; only the Platform Owner can approve or reject verification requests."
    )

    faqs.forEach { (q, a) ->
        FvgCard {
            Text(q, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(a, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PublicContactSection(
    isArabic: Boolean,
    onSendEmail: (String, String) -> Unit
) {
    val s = fvgStrings(isArabic)
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<String?>(null) }

    FvgFounderCard(isArabic = isArabic, compact = true)

    FvgCard {
        Text(
            text = if (isArabic) "تواصل مع دليل رؤيتك المالي (FVG)" else "Contact FVG — Financial Vision Guide",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${if (isArabic) "البريد الإلكتروني:" else "Email:"} ${s.supportEmail}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${if (isArabic) "الهاتف والواتساب:" else "Phone / WhatsApp:"} 00249911569966", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Text(
                    text = if (isArabic)
                        "يتم فتح تطبيق البريد الإلكتروني على جهازك لإرسال رسالتك إلى ${s.supportEmail} (أو سجّل الدخول لإرسال تذكرة مباشرة داخل التطبيق)."
                    else
                        "Opens your device email client addressed to ${s.supportEmail} (or sign in to submit an in-app support ticket).",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        FvgField(name, { name = it }, if (isArabic) "الاسم الكامل" else "Full Name", testTag = "contact_name_input")
        FvgField(email, { email = it }, if (isArabic) "البريد الإلكتروني" else "Work Email", testTag = "contact_email_input")
        FvgField(topic, { topic = it }, if (isArabic) "الموضوع" else "Subject / Inquiry Topic")
        FvgField(message, { message = it }, if (isArabic) "تفاصيل الرسالة" else "Message Details", singleLine = false)

        if (feedback != null) {
            Text(feedback!!, style = MaterialTheme.typography.bodyMedium, color = FvgPositive)
        }

        FvgPrimaryButton(
            text = if (isArabic) "إرسال عبر البريد" else "Send Inquiry via Email App",
            icon = Icons.Filled.Email,
            onClick = {
                if (name.isBlank() || !email.contains("@") || message.isBlank()) {
                    feedback = if (isArabic) "يرجى إدخال الاسم وبريد صحيح ونص الرسالة." else "Please provide your name, a valid email, and your message."
                } else {
                    onSendEmail("FVG Inquiry: ${topic.ifBlank { name }}", "From: $name ($email)\n\n$message")
                    feedback = if (isArabic) "تم فتح تطبيق البريد لإرسال استفسارك." else "Prepared inquiry in your email client."
                }
            },
            testTag = "contact_submit_button"
        )
    }
}

@Composable
private fun PublicPrivacySection(isArabic: Boolean) {
    FvgCard {
        Text(
            text = if (isArabic) "سياسة الخصوصية وحماية البيانات" else "Privacy Policy & Tenant Isolation",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isArabic)
                "• تخضع كافة بيانات المنظمات لعزل صارم متعدد المستأجرين (Multi-Tenant Isolation) عبر قواعد أمان Firestore ومفاتيح العضوية.\n• لا يتم مشاركة بيانات الأسعار أو الحسابات أو الصور الشخصية مع أي منظمة أخرى.\n• المصادقة محمية عبر Google Identity و Firebase Auth و App Check."
            else
                "• Every organization's products, costs, journals, inventory, and audit logs are strictly isolated inside its tenant boundary via zero-trust Firestore rules.\n• FVG never mixes external tenants or fabricates financial data.\n• Authentication uses Google Identity & Firebase Auth with App Check protection.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun PublicTermsSection(isArabic: Boolean) {
    FvgCard {
        Text(
            text = if (isArabic) "شروط الخدمة والمنهجية المحاسبية" else "Terms of Service & Accounting Methodology",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = if (isArabic)
                "• القيم الناتجة عن المساعد المالي ومسودات الفواتير (OCR) تخضع للمراجعة البشرية قبل اعتمادها.\n• نتائج الآلة الحاسبة مستقلة ولا تُرحل تلقائياً إلى الدفتر المحاسبي.\n• نتائج تحليل 'ماذا لو' تمثل سيناريوهات افتراضية وليست توقعات مستقبلية.\n• تبقى القيود المرحّلة غير قابلة للتعديل لضمان سلامة المراجعة المالية، ويتم التصحيح عبر قيود العكس."
            else
                "• Assistant outputs and document OCR extractions are decision-support drafts requiring explicit human verification.\n• Standalone calculator outputs never auto-post to journals.\n• What-If outputs are explicit deterministic simulations ('Scenario outcome — not a prediction').\n• Posted accounting journal entries are immutable; corrections use formal reversal entries.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PublicFloatingAiDialog(
    isArabic: Boolean,
    aiRepository: FvgAiRepository?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var queries by remember { mutableStateOf<List<String>>(emptyList()) }
    var sources by remember { mutableStateOf<List<GroundedSource>>(emptyList()) }
    var streaming by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun sendQuestion(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        job?.cancel()
        streaming = true
        answer = ""
        queries = emptyList()
        sources = emptyList()
        job = scope.launch {
            if (aiRepository == null) {
                answer = if (isArabic) "خدمة المساعد الذكي غير مهيأة حالياً." else "AI Assistant service is unavailable."
                streaming = false
                return@launch
            }
            aiRepository.streamGroundedExplanationWithSources(
                question = clean,
                systemDataContext = "Public FVG Showcase Mode (Pre-login visitor): Answer both general-knowledge questions using Google Search grounding AND explain FVG capabilities (8 costing methods, FIFO/MWA inventory, 18 financial ratios, double-entry ledger, Bankak/Fawry verification) honestly.",
                languageCode = if (isArabic) "ar" else "en",
                aiMode = "general"
            ).catch { err ->
                if (err !is CancellationException) {
                    answer = if (isArabic) "تعذر الاتصال بالمساعد الذكي: ${err.message}" else "Assistant connection failed: ${err.message}"
                }
            }.collect { update ->
                if (update.text.isNotEmpty()) answer = update.text
                if (update.searchQueries.isNotEmpty()) queries = update.searchQueries
                if (update.sources.isNotEmpty()) sources = update.sources
            }
            streaming = false
        }
    }

    Dialog(
        onDismissRequest = {
            job?.cancel()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.82f)
                .testTag("public_floating_ai_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text(
                                text = if (isArabic) "المساعد الذكي FVG (بحث مباشر)" else "FVG Smart Assistant (Web Grounded)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isArabic) "اسأل أي سؤال عام أو مالي من أي صفحة" else "Ask any general or financial question from any page",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = {
                        job?.cancel()
                        onDismiss()
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = if (isArabic) "إغلاق" else "Close")
                    }
                }

                HorizontalDivider()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (answer.isBlank() && !streaming) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = if (isArabic)
                                        "مرحباً بك في دليل رؤيتك المالي! يمكنك طرح أي سؤال عام (مثل التاريخ، العلوم، أسعار الصرف العالمية) أو الاستفسار عن طرق التكلفة الثمانية، التضخم، وبنكك/فوري."
                                    else
                                        "Welcome to FVG! Ask any general question (science, markets, global news) or inquire about our 8 costing methods, inflation index, and Bankak/Fawry billing.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        if (streaming) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(
                                    text = if (isArabic) "جاري البحث والتحليل..." else "Searching & thinking...",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (answer.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = answer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        if (queries.isNotEmpty()) {
                            Text(
                                text = (if (isArabic) "استعلامات البحث: " else "Search queries: ") + queries.joinToString(" • "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (sources.isNotEmpty()) {
                            Text(
                                text = if (isArabic) "المصادر المرجعية:" else "Grounded Web Sources:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                sources.forEach { src ->
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

                FvgField(
                    value = question,
                    onValueChange = { question = it },
                    label = if (isArabic) "اكتب سؤالك العام أو المالي..." else "Ask any general or financial question...",
                    singleLine = false,
                    testTag = "public_floating_ai_input"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FvgPrimaryButton(
                        text = if (streaming) (if (isArabic) "جاري الرد..." else "Thinking...") else (if (isArabic) "إرسال" else "Send"),
                        icon = Icons.Filled.AutoAwesome,
                        enabled = !streaming && question.isNotBlank(),
                        onClick = {
                            val q = question
                            question = ""
                            sendQuestion(q)
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "public_floating_ai_send"
                    )
                    if (streaming) {
                        OutlinedButton(
                            onClick = {
                                job?.cancel()
                                streaming = false
                            },
                            modifier = Modifier.heightIn(min = 50.dp)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
