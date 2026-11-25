package com.example

import android.app.Activity
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.configureAppCheck
import com.example.data.repository.FvgAiRepository
import com.example.data.repository.FvgRepository
import com.example.ui.auth.AdminCredentialHelper
import com.example.ui.auth.AuthHelper
import com.example.ui.components.FvgCalculatorSheet
import com.example.ui.components.FvgFloatingAiSheet
import com.example.ui.components.FvgTrialExpiredGateCard
import com.example.ui.components.FvgWalkthroughOverlay
import com.example.ui.i18n.fvgStrings
import com.example.ui.screens.*
import com.example.ui.theme.FvgDeepGreen
import com.example.ui.theme.FvgTheme
import com.example.ui.theme.FvgWarmGold
import com.example.ui.theme.FvgWarmIvory
import com.example.ui.viewmodel.FvgViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        val openAdminRoute = intent?.data?.let { uri ->
            uri.host == "admin" || uri.path?.startsWith("/admin") == true
        } == true || intent?.getBooleanExtra("open_admin", false) == true
        setContent {
            FvgRootApp(openAdminOnLaunch = openAdminRoute)
        }
    }
}

private data class FvgNavItem(
    val index: Int,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private fun getOrCreateSessionId(context: Context): String {
    val prefs = context.getSharedPreferences("fvg_session_prefs", Context.MODE_PRIVATE)
    val existing = prefs.getString("fvg_session_id", null)
    if (!existing.isNullOrBlank()) return existing
    val created = UUID.randomUUID().toString()
    prefs.edit().putString("fvg_session_id", created).apply()
    return created
}

@Composable
fun FvgRootApp(openAdminOnLaunch: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val publicAiRepository = remember { runCatching { FvgAiRepository() }.getOrNull() }

    var currentUser by remember { mutableStateOf<FirebaseUser?>(auth.currentUser) }
    val localAuthPrefs = remember(context) { context.getSharedPreferences("fvg_local_auth", Context.MODE_PRIVATE) }
    var isLocalAuthenticated by remember { mutableStateOf(localAuthPrefs.getBoolean("is_logged_in", false)) }
    var localEmail by remember { mutableStateOf(localAuthPrefs.getString("logged_email", "") ?: "") }
    var localDisplayName by remember { mutableStateOf(localAuthPrefs.getString("display_name", "") ?: "") }

    var showSplash by rememberSaveable { mutableStateOf(true) }
    var isArabic by rememberSaveable { mutableStateOf(true) }
    var themeMode by rememberSaveable { mutableStateOf("light") }
    var fontScale by rememberSaveable { mutableFloatStateOf(1.0f) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    var phoneVerificationId by remember { mutableStateOf<String?>(null) }
    var pendingPhoneDisplayName by remember { mutableStateOf("") }
    var pendingPhoneOptionalEmail by remember { mutableStateOf("") }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        delay(400L)
        if (auth.currentUser == null && !isLocalAuthenticated) {
            AuthHelper.attemptSilentGoogleSignIn(context, auth)
        }
    }

    val resolvedDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    FvgTheme(themeMode = themeMode, isArabic = isArabic, fontScale = fontScale) {
        if (showSplash) {
            FvgSplashScreen(isArabic = isArabic) {
                showSplash = false
            }
        } else if (currentUser == null && !isLocalAuthenticated) {
            PublicWebsiteAndAuthScreen(
                isArabic = isArabic,
                isDark = resolvedDark,
                isSigningIn = isSigningIn,
                authError = authError,
                phoneVerificationId = phoneVerificationId,
                onToggleLanguage = { isArabic = !isArabic },
                onToggleTheme = {
                    themeMode = if (resolvedDark) "light" else "dark"
                },
                onClearAuthError = { authError = null },
                onResetPhoneOtp = { phoneVerificationId = null },
                onGoogleSignInClick = {
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            AuthHelper.signInWithGoogleExplicit(context, auth)
                                .onFailure { e ->
                                    if (e !is AuthHelper.CancelledAuthException) {
                                        authError = AuthHelper.formatFirebaseAuthError(e, isArabic, "Google")
                                    }
                                }
                            isSigningIn = false
                        }
                    }
                },
                onAppleSignInClick = {
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            AuthHelper.signInWithOAuthProvider(context as? Activity, auth, "apple.com")
                                .onFailure { e ->
                                    if (e !is AuthHelper.CancelledAuthException) {
                                        authError = AuthHelper.formatFirebaseAuthError(e, isArabic, "Apple")
                                    }
                                }
                            isSigningIn = false
                        }
                    }
                },
                onFacebookSignInClick = {
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            AuthHelper.signInWithOAuthProvider(context as? Activity, auth, "facebook.com")
                                .onFailure { e ->
                                    if (e !is AuthHelper.CancelledAuthException) {
                                        authError = AuthHelper.formatFirebaseAuthError(e, isArabic, "Facebook")
                                    }
                                }
                            isSigningIn = false
                        }
                    }
                },
                onEmailSignInClick = { email, password, isSignUp, displayName, onSuccess ->
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            val cleanEmail = email.trim()
                            val nameToUse = displayName.ifBlank { cleanEmail.substringBefore("@") }
                            try {
                                require(cleanEmail.contains("@")) {
                                    if (isArabic) "يرجى إدخال بريد إلكتروني صحيح" else "Valid email address required"
                                }
                                require(password.length >= 6) {
                                    if (isArabic) "كلمة المرور يجب أن تكون 6 أحرف على الأقل" else "Password must be at least 6 characters"
                                }
                                if (isSignUp) {
                                    val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                                    runCatching {
                                        result.user?.updateProfile(
                                            UserProfileChangeRequest.Builder()
                                                .setDisplayName(nameToUse)
                                                .build()
                                        )?.await()
                                    }
                                } else {
                                    auth.signInWithEmailAndPassword(cleanEmail, password).await()
                                }
                                onSuccess()
                            } catch (e: Exception) {
                                authError = AuthHelper.formatFirebaseAuthError(e, isArabic, if (isArabic) "البريد الإلكتروني" else "Email/Password")
                            }
                            isSigningIn = false
                        }
                    }
                },
                onSendPhoneOtpClick = { rawPhone, displayName, optionalEmail ->
                    if (!isSigningIn) {
                        isSigningIn = true
                        authError = null
                        pendingPhoneDisplayName = displayName
                        pendingPhoneOptionalEmail = optionalEmail
                        AuthHelper.startPhoneVerification(
                            activity = context as? Activity,
                            auth = auth,
                            rawPhone = rawPhone,
                            onCodeSent = { vid ->
                                phoneVerificationId = vid
                                isSigningIn = false
                            },
                            onAutoCredential = { credential ->
                                scope.launch {
                                    AuthHelper.signInWithPhoneCredentialAndProfile(auth, credential, pendingPhoneDisplayName)
                                        .onSuccess {
                                            val uid = auth.currentUser?.uid
                                            if (!uid.isNullOrBlank() && pendingPhoneOptionalEmail.contains("@")) {
                                                localAuthPrefs.edit().putString("phone_email_$uid", pendingPhoneOptionalEmail.trim()).apply()
                                            }
                                            phoneVerificationId = null
                                        }
                                        .onFailure { e ->
                                            authError = AuthHelper.formatFirebaseAuthError(e, isArabic, if (isArabic) "الهاتف" else "Phone")
                                        }
                                    isSigningIn = false
                                }
                            },
                            onFailed = { e ->
                                authError = AuthHelper.formatFirebaseAuthError(e, isArabic, if (isArabic) "الهاتف" else "Phone")
                                isSigningIn = false
                            }
                        )
                    }
                },
                onVerifyPhoneOtpClick = { otpCode, displayName, optionalEmail, onSuccess ->
                    val vid = phoneVerificationId
                    if (!isSigningIn && !vid.isNullOrBlank()) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            val cred = PhoneAuthProvider.getCredential(vid, otpCode.trim())
                            AuthHelper.signInWithPhoneCredentialAndProfile(auth, cred, displayName)
                                .onSuccess {
                                    val uid = auth.currentUser?.uid
                                    val optMail = optionalEmail.trim().ifBlank { pendingPhoneOptionalEmail.trim() }
                                    if (!uid.isNullOrBlank() && optMail.contains("@")) {
                                        localAuthPrefs.edit().putString("phone_email_$uid", optMail).apply()
                                    }
                                    phoneVerificationId = null
                                    onSuccess()
                                }
                                .onFailure { e ->
                                    authError = AuthHelper.formatFirebaseAuthError(e, isArabic, if (isArabic) "الهاتف" else "Phone")
                                }
                            isSigningIn = false
                        }
                    }
                },
                onAdminSignInClick = { email, password, onSuccess ->
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            val cleanEmail = email.trim()
                            val lockoutSec = AdminCredentialHelper.getLockoutRemainingSeconds(context)
                            if (lockoutSec > 0L) {
                                authError = if (isArabic)
                                    "تم تقييد المحاولات مؤقتاً لأسباب أمنية. انتظر $lockoutSec ثانية."
                                else
                                    "Too many failed attempts. Try again in ${lockoutSec}s."
                                isSigningIn = false
                                return@launch
                            }
                            if (!AdminCredentialHelper.isAuthorizedAdminEmail(cleanEmail)) {
                                val failN = AdminCredentialHelper.recordFailedAdminAttempt(context, cleanEmail, "unauthorized_email")
                                authError = if (isArabic)
                                    "بيانات الدخول غير مصرح بها لبوابة الإدارة (المحاولة $failN)."
                                else
                                    "Unauthorized administrator credentials (attempt #$failN)."
                                isSigningIn = false
                                return@launch
                            }
                            if (!AdminCredentialHelper.isAdminSetupCompleted(context)) {
                                authError = if (isArabic)
                                    "يجب إكمال إعداد كلمة مرور الإدارة للمرة الأولى أولاً."
                                else
                                    "Complete the one-time initial administrator password setup first."
                                isSigningIn = false
                                return@launch
                            }
                            val localOk = AdminCredentialHelper.verifyAdminPassword(context, password)
                            var firebaseOk = false
                            var lastFirebaseErr: Exception? = null
                            try {
                                auth.signInWithEmailAndPassword(cleanEmail, password).await()
                                firebaseOk = true
                            } catch (e: Exception) {
                                lastFirebaseErr = e
                                if (localOk) {
                                    try {
                                        auth.createUserWithEmailAndPassword(cleanEmail, password).await()
                                        firebaseOk = true
                                    } catch (_: Exception) { }
                                }
                            }
                            if (firebaseOk || localOk) {
                                AdminCredentialHelper.resetFailedAdminAttempts(context)
                                val adminName = "المالكة زهراء يوسف"
                                localAuthPrefs.edit()
                                    .putBoolean("is_logged_in", true)
                                    .putString("logged_email", cleanEmail)
                                    .putString("display_name", adminName)
                                    .apply()
                                localEmail = cleanEmail
                                localDisplayName = adminName
                                if (!firebaseOk) {
                                    isLocalAuthenticated = true
                                }
                                onSuccess()
                            } else {
                                val failN = AdminCredentialHelper.recordFailedAdminAttempt(context, cleanEmail, "wrong_password")
                                authError = lastFirebaseErr?.let {
                                    AuthHelper.formatFirebaseAuthError(it, isArabic, if (isArabic) "الإدارة" else "Admin")
                                } ?: if (isArabic)
                                    "كلمة مرور الإدارة غير صحيحة (المحاولة $failN)."
                                else
                                    "Incorrect administrator password (attempt #$failN)."
                            }
                            isSigningIn = false
                        }
                    }
                },
                onAdminInitialSetupClick = { email, newPassword, confirmPassword, onSuccess ->
                    if (!isSigningIn) {
                        scope.launch {
                            isSigningIn = true
                            authError = null
                            val cleanEmail = email.trim()
                            val setupRes = AdminCredentialHelper.completeOneTimeInitialSetup(
                                context = context,
                                email = cleanEmail,
                                newPassword = newPassword,
                                confirmPassword = confirmPassword,
                                isArabic = isArabic
                            )
                            if (setupRes.isFailure) {
                                authError = setupRes.exceptionOrNull()?.message
                                    ?: if (isArabic) "تعذّر حفظ كلمة المرور الإدارية." else "Failed to complete admin setup."
                                isSigningIn = false
                                return@launch
                            }
                            var firebaseOk = false
                            try {
                                auth.signInWithEmailAndPassword(cleanEmail, newPassword).await()
                                firebaseOk = true
                            } catch (_: Exception) {
                                try {
                                    auth.createUserWithEmailAndPassword(cleanEmail, newPassword).await()
                                    firebaseOk = true
                                } catch (_: Exception) { }
                            }
                            val adminName = "المالكة زهراء يوسف"
                            localAuthPrefs.edit()
                                .putBoolean("is_logged_in", true)
                                .putString("logged_email", cleanEmail)
                                .putString("display_name", adminName)
                                .apply()
                            localEmail = cleanEmail
                            localDisplayName = adminName
                            if (!firebaseOk) {
                                isLocalAuthenticated = true
                            }
                            onSuccess()
                            isSigningIn = false
                        }
                    }
                },
                onPasswordResetClick = { email ->
                    scope.launch {
                        authError = null
                        val clean = email.trim()
                        val isAdmin = AdminCredentialHelper.isAuthorizedAdminEmail(clean)
                        AuthHelper.sendPasswordReset(auth, clean)
                            .onSuccess {
                                if (isAdmin) {
                                    AdminCredentialHelper.resetAdminSetupForRecovery(context, clean)
                                    authError = if (isArabic)
                                        "تم إرسال رابط استعادة كلمة المرور إلى $clean. يمكنك الآن تعيين كلمة مرور الإدارة الجديدة."
                                    else
                                        "Password reset link sent to $clean. You may now establish a new administrator password."
                                } else {
                                    authError = if (isArabic) "تم إرسال رابط استعادة كلمة المرور إلى $clean" else "Password reset email sent to $clean"
                                }
                            }
                            .onFailure { e ->
                                if (isAdmin) {
                                    AdminCredentialHelper.resetAdminSetupForRecovery(context, clean)
                                    authError = if (isArabic)
                                        "تم تفعيل إعادة تعيين كلمة مرور الإدارة لـ $clean. يمكنك تعيين كلمة المرور الجديدة الآن."
                                    else
                                        "Admin recovery unlocked for $clean. You can establish a new password now."
                                } else {
                                    authError = AuthHelper.formatFirebaseAuthError(e, isArabic, if (isArabic) "استعادة كلمة المرور" else "Password Reset")
                                }
                            }
                    }
                },
                aiRepository = publicAiRepository
            )
        } else {
            val storedPhoneEmail = currentUser?.uid?.let { uid ->
                localAuthPrefs.getString("phone_email_$uid", "")
            }.orEmpty()
            val userEmailStr = currentUser?.email?.takeIf { it.isNotBlank() }
                ?: storedPhoneEmail.takeIf { it.contains("@") }
                ?: currentUser?.phoneNumber?.takeIf { it.isNotBlank() }
                ?: localEmail
            val userDisplayNameStr = currentUser?.displayName?.takeIf { it.isNotBlank() }
                ?: localDisplayName.takeIf { it.isNotBlank() }
                ?: currentUser?.phoneNumber
                ?: ""
            val userIdStr = currentUser?.uid ?: "local_usr_${userEmailStr.hashCode()}"
            data class FvgUserInfo(
                val uid: String,
                val displayName: String?,
                val email: String?
            )
            val user = if (currentUser != null) {
                FvgUserInfo(currentUser!!.uid, userDisplayNameStr.ifBlank { null }, userEmailStr.ifBlank { null })
            } else {
                FvgUserInfo(userIdStr, userDisplayNameStr, userEmailStr)
            }
            val repository = remember(context) { FvgRepository(context.applicationContext) }
            val aiRepository = remember { FvgAiRepository() }
            key(userIdStr) {
                val vm: FvgViewModel = viewModel(
                    key = userIdStr,
                    factory = FvgViewModel.Factory(
                        repository = repository,
                        aiRepository = aiRepository,
                        currentUserId = userIdStr,
                        userEmail = userEmailStr,
                        userDisplayName = userDisplayNameStr,
                        appContext = context.applicationContext
                    )
                )
                val uiState by vm.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(user.uid) {
                    vm.restartWorkspaceIfInactive()
                    val sid = withContext(Dispatchers.IO) { getOrCreateSessionId(context.applicationContext) }
                    vm.registerSession(sid, AuthHelper.currentDeviceLabel())
                    vm.logAppInstallOnce(context.applicationContext)
                    vm.logPlatformVisit("dashboard")
                }

                DisposableEffect(user.uid) {
                    onDispose {
                        if ((context as? Activity)?.isChangingConfigurations != true) {
                            vm.stopWorkspaceListeners()
                        }
                    }
                }

                LaunchedEffect(uiState.userProfile?.preferredLanguage) {
                    uiState.userProfile?.preferredLanguage?.let { lang ->
                        isArabic = (lang == "ar")
                    }
                }
                LaunchedEffect(uiState.userProfile?.themeMode, uiState.userProfile?.darkMode) {
                    uiState.userProfile?.let { prof ->
                        themeMode = prof.themeMode.ifBlank { if (prof.darkMode) "dark" else "light" }
                    }
                }
                LaunchedEffect(uiState.userProfile?.fontScale) {
                    uiState.userProfile?.fontScale?.let { scale ->
                        fontScale = scale.toFloat().coerceIn(0.85f, 1.30f)
                    }
                }

                FvgAuthenticatedShell(
                    vm = vm,
                    uiState = uiState,
                    openAdminOnLaunch = openAdminOnLaunch,
                    userDisplayName = uiState.userProfile?.displayName?.ifBlank { null }
                        ?: user.displayName
                        ?: user.email
                        ?: "FVG User",
                    isArabic = isArabic,
                    isDark = resolvedDark,
                    onToggleLanguage = {
                        val next = !isArabic
                        isArabic = next
                        vm.setLanguage(if (next) "ar" else "en")
                    },
                    onSetLanguage = { chooseArabic ->
                        isArabic = chooseArabic
                        vm.setLanguage(if (chooseArabic) "ar" else "en")
                    },
                    onToggleTheme = {
                        val nextMode = if (resolvedDark) "light" else "dark"
                        themeMode = nextMode
                        vm.setLanguage(if (isArabic) "ar" else "en")
                        vm.setThemeMode(nextMode)
                    },
                    onSignOut = {
                        scope.launch {
                            localAuthPrefs.edit().clear().apply()
                            isLocalAuthenticated = false
                            vm.stopWorkspaceListeners()
                            yield()
                            AuthHelper.signOut(context, auth)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun FvgAuthenticatedShell(
    vm: FvgViewModel,
    uiState: com.example.ui.viewmodel.FvgUiState,
    openAdminOnLaunch: Boolean = false,
    userDisplayName: String,
    isArabic: Boolean,
    isDark: Boolean,
    onToggleLanguage: () -> Unit,
    onSetLanguage: (Boolean) -> Unit,
    onToggleTheme: () -> Unit,
    onSignOut: () -> Unit
) {
    val s = fvgStrings(isArabic)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var activeSecondaryScreen by rememberSaveable { mutableStateOf<String?>(null) } // "profile", "help", "owner_admin"
    var showCalculatorSheet by rememberSaveable { mutableStateOf(false) }
    var showFloatingAiSheet by rememberSaveable { mutableStateOf(false) }
    var showNotificationsDialog by rememberSaveable { mutableStateOf(false) }
    var showWalkthrough by rememberSaveable { mutableStateOf(false) }
    var autoTourEvaluated by rememberSaveable { mutableStateOf(false) }
    var showWelcomePopup by rememberSaveable { mutableStateOf(!uiState.isPlatformOwner) }
    var adminPreviewUserApp by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(openAdminOnLaunch, uiState.isPlatformOwner) {
        if (openAdminOnLaunch && uiState.isPlatformOwner) {
            adminPreviewUserApp = false
        } else if (openAdminOnLaunch && !uiState.isPlatformOwner && uiState.userProfile != null) {
            vm.reportAdminSecurityAlert(
                signalType = "unauthorized_admin_attempt",
                severity = "critical",
                titleEn = "Unauthorized /admin Route Attempt",
                titleAr = "محاولة فتح مسار الإدارة /admin بدون صلاحية",
                bodyEn = "Signed-in non-owner account (${uiState.userProfile.email}) attempted to launch the admin route.",
                bodyAr = "حاول حساب مستخدم عادي (${uiState.userProfile.email}) فتح مسار لوحة الإدارة المحمي."
            )
        }
    }

    if (uiState.isPlatformOwner && !adminPreviewUserApp) {
        OwnerAdminScreen(
            state = uiState,
            viewModel = vm,
            isArabic = isArabic,
            isDark = isDark,
            onToggleLanguage = onToggleLanguage,
            onToggleTheme = onToggleTheme,
            onPreviewUserApp = { adminPreviewUserApp = true },
            onSignOut = onSignOut,
            onBack = { adminPreviewUserApp = true }
        )
        return
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val notifications = remember(
        uiState.activeOrg,
        uiState.priceObservations,
        uiState.fxRates,
        uiState.counterpartyItems,
        uiState.scrapLosses,
        uiState.stocktakes,
        uiState.ocrDrafts,
        uiState.journalEntries,
        uiState.statementsReport,
        uiState.paymentRequests,
        uiState.inventoryItems,
        uiState.userProfile?.notificationsEnabled
    ) { uiState.buildNotifications() }

    LaunchedEffect(uiState.userProfile?.tourCompleted) {
        val profile = uiState.userProfile
        if (profile != null && !autoTourEvaluated && !uiState.isPlatformOwner) {
            autoTourEvaluated = true
            if (!profile.tourCompleted && !showWelcomePopup) {
                showWalkthrough = true
            }
        }
    }

    BackHandler(enabled = activeSecondaryScreen != null || selectedTab != 0) {
        if (activeSecondaryScreen != null) {
            activeSecondaryScreen = null
        } else {
            selectedTab = 0
        }
    }

    LaunchedEffect(uiState.statusMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.statusMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            vm.clearMessages()
        }
    }

    val navItems = listOf(
        FvgNavItem(0, s.navDashboard, Icons.Filled.Dashboard, "nav_dashboard"),
        FvgNavItem(1, s.navPrices, Icons.AutoMirrored.Filled.TrendingUp, "nav_prices"),
        FvgNavItem(2, s.navCosting, Icons.Filled.Calculate, "nav_costing"),
        FvgNavItem(3, s.navAccounting, Icons.Filled.AccountBalance, "nav_accounting"),
        FvgNavItem(4, s.navInventory, Icons.Filled.Inventory2, "nav_inventory"),
        FvgNavItem(5, s.navAssistantReports, Icons.Filled.AutoAwesome, "nav_reports")
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        if (showWelcomePopup) {
            AlertDialog(
                onDismissRequest = {
                    showWelcomePopup = false
                    showWalkthrough = true
                },
                title = {
                    Text(
                        text = if (isArabic) "أهلاً بك، $userDisplayName" else "Welcome, $userDisplayName",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Text(
                        text = if (isArabic)
                            "تم تسجيل دخولك بنجاح وإنشاء مساحة عملك الخاصة مع تجربة 7 أيام مفعّلة بالكامل. استمتع بإدارة التكاليف والقطاعات والمساعد الذكي!"
                        else
                            "Your workspace has been successfully initialized with a fully active 7-day trial. Enjoy exploring your sector calculators and FVG AI Assistant!",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showWelcomePopup = false
                            showWalkthrough = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.testTag("welcome_dialog_start_tour")
                    ) {
                        Text(if (isArabic) "ابدأ الجولة الاستكشافية" else "Start Guided Tour", color = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showWelcomePopup = false
                        }
                    ) {
                        Text(if (isArabic) "تخطي" else "Skip")
                    }
                }
            )
        }

        if (activeSecondaryScreen == "profile") {
            ProfileScreen(
                state = uiState,
                viewModel = vm,
                isArabic = isArabic,
                onBack = { activeSecondaryScreen = null },
                onSignOutClick = onSignOut
            )
        } else if (activeSecondaryScreen == "help") {
            HelpAndSupportScreen(
                state = uiState,
                viewModel = vm,
                isArabic = isArabic,
                onReplayTour = {
                    activeSecondaryScreen = null
                    selectedTab = 0
                    showWalkthrough = true
                },
                onBack = { activeSecondaryScreen = null }
            )
        } else if (activeSecondaryScreen == "owner_admin" && uiState.isPlatformOwner) {
            OwnerAdminScreen(
                state = uiState,
                viewModel = vm,
                isArabic = isArabic,
                isDark = isDark,
                onToggleLanguage = onToggleLanguage,
                onToggleTheme = onToggleTheme,
                onPreviewUserApp = { activeSecondaryScreen = null },
                onSignOut = onSignOut,
                onBack = { activeSecondaryScreen = null }
            )
        }

        if (activeSecondaryScreen != null) {
            FloatingActionButton(
                onClick = { showFloatingAiSheet = true },
                containerColor = FvgDeepGreen,
                contentColor = FvgWarmGold,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .testTag("floating_ai_chat_fab")
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = if (isArabic) "المساعد الذكي" else "FVG AI Assistant"
                )
            }
        } else {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showFloatingAiSheet = true },
                        containerColor = FvgDeepGreen,
                        contentColor = FvgWarmGold,
                        modifier = Modifier.testTag("floating_ai_chat_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = if (isArabic) "المساعد الذكي" else "FVG AI Assistant"
                        )
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = FvgDeepGreen,
                            contentColor = FvgWarmIvory
                        ) {
                            navItems.forEach { item ->
                                val selected = selectedTab == item.index
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { selectedTab = item.index },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = FvgDeepGreen,
                                        selectedTextColor = FvgWarmGold,
                                        indicatorColor = FvgWarmGold,
                                        unselectedIconColor = FvgWarmIvory.copy(alpha = 0.72f),
                                        unselectedTextColor = FvgWarmIvory.copy(alpha = 0.72f)
                                    ),
                                    modifier = Modifier.testTag(item.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = FvgDeepGreen,
                            contentColor = FvgWarmIvory
                        ) {
                            navItems.forEach { item ->
                                val selected = selectedTab == item.index
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { selectedTab = item.index },
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = FvgDeepGreen,
                                        selectedTextColor = FvgWarmGold,
                                        indicatorColor = FvgWarmGold,
                                        unselectedIconColor = FvgWarmIvory.copy(alpha = 0.72f),
                                        unselectedTextColor = FvgWarmIvory.copy(alpha = 0.72f)
                                    ),
                                    modifier = Modifier.testTag(item.tag)
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val trialLocked = uiState.isTrialExpiredAndLocked()
                        if (trialLocked && selectedTab in 1..4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                FvgTrialExpiredGateCard(
                                    isArabic = isArabic,
                                    orgName = uiState.activeOrg?.name ?: "FVG Workspace",
                                    onUpgradeClick = { selectedTab = 5 },
                                    onSignOutClick = onSignOut
                                )
                            }
                        } else {
                            when (selectedTab) {
                                0 -> DashboardScreen(
                                    state = uiState,
                                    userDisplayName = userDisplayName,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    unreadNotificationsCount = notifications.size,
                                    onNotificationsClick = { showNotificationsDialog = true },
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenProfile = { activeSecondaryScreen = "profile" },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenOwnerAdmin = { if (uiState.isPlatformOwner) adminPreviewUserApp = false },
                                    onReplayTour = { showWalkthrough = true },
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onCreateOrganization = { name, curr, sector -> vm.createOrganization(name, curr, sector) },
                                    onApplySectorPreset = { sectorKey -> vm.applySectorPreset(sectorKey) },
                                    onFlushOfflineQueue = { vm.flushOfflineQueue() },
                                    onResolveOfflineConflict = { conflictId, keepLocal -> vm.resolveOfflineConflict(conflictId, keepLocal) },
                                    onNavigateTab = { selectedTab = it }
                                )
                                1 -> PricesInflationScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                2 -> CostingScenariosScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                3 -> AccountingStatementsScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                4 -> InventoryBudgetsScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenProfile = { activeSecondaryScreen = "profile" }
                                )
                                else -> ReportsAiSettingsScreen(
                                    state = uiState,
                                    viewModel = vm,
                                    isArabic = isArabic,
                                    isDark = isDark,
                                    onToggleLanguage = onToggleLanguage,
                                    onToggleTheme = onToggleTheme,
                                    onOpenCalculator = { showCalculatorSheet = true },
                                    onOpenProfile = { activeSecondaryScreen = "profile" },
                                    onOpenHelp = { activeSecondaryScreen = "help" },
                                    onOpenOwnerAdmin = { if (uiState.isPlatformOwner) adminPreviewUserApp = false },
                                    onSignOutClick = onSignOut
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCalculatorSheet) {
        FvgCalculatorSheet(
            isArabic = isArabic,
            defaultCurrency = uiState.activeOrg?.baseCurrency ?: "SDG",
            onDismiss = { showCalculatorSheet = false }
        )
    }

    if (showFloatingAiSheet) {
        FvgFloatingAiSheet(
            state = uiState,
            viewModel = vm,
            isArabic = isArabic,
            onDismiss = { showFloatingAiSheet = false }
        )
    }

    if (showWalkthrough) {
        FvgWalkthroughOverlay(
            isArabic = isArabic,
            onSelectLanguage = { chooseArabic ->
                onSetLanguage(chooseArabic)
            },
            onHighlightTab = { tabIdx ->
                activeSecondaryScreen = null
                selectedTab = tabIdx.coerceIn(0, 5)
            },
            onDismissComplete = {
                showWalkthrough = false
                vm.markTourCompleted()
            }
        )
    }

    if (showNotificationsDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationsDialog = false },
            title = {
                Text(s.notificationsTitle, fontWeight = FontWeight.Bold)
            },
            text = {
                if (notifications.isEmpty()) {
                    Text(
                        text = if (isArabic) "لا توجد تنبيهات نشطة حالياً." else "All financial checks look clear — no active alerts.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notifications, key = { it.id }) { alert ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTab = alert.targetTab.coerceIn(0, 5)
                                        activeSecondaryScreen = null
                                        showNotificationsDialog = false
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isArabic) alert.titleAr else alert.titleEn,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (alert.severity == "critical") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(alert.severity.uppercase(), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(
                                        text = if (isArabic) alert.detailAr else alert.detailEn,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showNotificationsDialog = false }) {
                    Text(if (isArabic) "إغلاق" else "Close")
                }
            }
        )
    }
}
