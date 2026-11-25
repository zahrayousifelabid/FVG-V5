package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private const val TAG = "FvgAuthHelper"

object AuthHelper {

    suspend fun attemptSilentGoogleSignIn(context: Context, auth: FirebaseAuth): Boolean {
        if (auth.currentUser != null) return true
        return try {
            val credentialManager = CredentialManager.create(context)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(context.getString(R.string.default_web_client_id))
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(context, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            auth.signInWithCredential(firebaseCred).await()
            true
        } catch (e: NoCredentialException) {
            Log.d(TAG, "No authorized Google credentials for silent sign-in: ${e.message}")
            false
        } catch (e: GetCredentialCancellationException) {
            Log.d(TAG, "Silent sign-in cancelled")
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Silent sign-in skipped", e)
            false
        }
    }

    suspend fun signInWithGoogleExplicit(
        context: Context,
        auth: FirebaseAuth
    ): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(context)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(
                context.getString(R.string.default_web_client_id)
            ).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()
            val result = credentialManager.getCredential(context, request)
            val googleCred = GoogleIdTokenCredential.createFrom(result.credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            auth.signInWithCredential(firebaseCred).await()
            Result.success(Unit)
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "User cancelled Google Sign-In")
            Result.failure(CancelledAuthException())
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager Google Sign-In error", e)
            Result.failure(IllegalStateException(e.message ?: "Google Sign-In failed. Ensure a Google account is signed in on this device."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Google Sign-In error", e)
            Result.failure(e)
        }
    }

    suspend fun signInWithOAuthProvider(
        activity: Activity?,
        auth: FirebaseAuth,
        providerId: String
    ): Result<Unit> {
        if (activity == null) {
            return Result.failure(IllegalStateException("Activity context required for $providerId sign-in"))
        }
        return try {
            val providerBuilder = OAuthProvider.newBuilder(providerId)
            if (providerId == "facebook.com") {
                providerBuilder.setScopes(listOf("email", "public_profile"))
            } else if (providerId == "apple.com") {
                providerBuilder.setScopes(listOf("email", "name"))
            }
            val provider = providerBuilder.build()
            val pending = auth.pendingAuthResult
            if (pending != null) {
                pending.await()
            } else {
                auth.startActivityForSignInWithProvider(activity, provider).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            if (msg.contains("cancel")) {
                Result.failure(CancelledAuthException())
            } else {
                Log.w(TAG, "OAuth sign-in error for $providerId", e)
                Result.failure(e)
            }
        }
    }

    fun normalizePhoneToE164(raw: String): String {
        val trimmed = raw.trim().replace(Regex("[\\s\\-()]"), "")
        return when {
            trimmed.startsWith("+") -> "+" + trimmed.drop(1).filter { it.isDigit() }
            trimmed.startsWith("00") -> "+" + trimmed.drop(2).filter { it.isDigit() }
            trimmed.startsWith("0") && trimmed.filter { it.isDigit() }.length == 10 ->
                "+249" + trimmed.filter { it.isDigit() }.drop(1)
            else -> "+" + trimmed.filter { it.isDigit() }
        }
    }

    fun startPhoneVerification(
        activity: Activity?,
        auth: FirebaseAuth,
        rawPhone: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoCredential: (PhoneAuthCredential) -> Unit,
        onFailed: (Throwable) -> Unit
    ) {
        if (activity == null) {
            onFailed(IllegalStateException("Activity context required for Phone Sign-In"))
            return
        }
        val e164 = normalizePhoneToE164(rawPhone)
        if (e164.length < 9) {
            onFailed(IllegalArgumentException("Enter a valid international phone number (e.g. +249912345678)."))
            return
        }
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                onAutoCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "Phone verification failed", e)
                onFailed(e)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                onCodeSent(verificationId)
            }
        }
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun signInWithPhoneCredentialAndProfile(
        auth: FirebaseAuth,
        credential: PhoneAuthCredential,
        displayName: String
    ): Result<Unit> = runCatching {
        val result = auth.signInWithCredential(credential).await()
        val cleanName = displayName.trim()
        if (cleanName.isNotBlank() && result.user?.displayName.isNullOrBlank()) {
            runCatching {
                result.user?.updateProfile(
                    UserProfileChangeRequest.Builder().setDisplayName(cleanName).build()
                )?.await()
            }
        }
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    fun formatFirebaseAuthError(
        error: Throwable,
        isArabic: Boolean,
        providerLabel: String
    ): String {
        val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            code == "ERROR_OPERATION_NOT_ALLOWED" || lower.contains("operation is not allowed") || lower.contains("operation-not-allowed") ->
                if (isArabic)
                    "طريقة الدخول ($providerLabel) غير مفعّلة حالياً في Firebase Console. يرجى تفعيلها من Authentication -> Sign-in method."
                else
                    "The $providerLabel sign-in provider is not enabled yet in Firebase Console (Authentication -> Sign-in method)."
            code == "ERROR_INVALID_CREDENTIAL" || code == "ERROR_WRONG_PASSWORD" ->
                if (isArabic) "البريد الإلكتروني أو كلمة المرور غير صحيحة." else "Incorrect email or password."
            code == "ERROR_USER_NOT_FOUND" ->
                if (isArabic) "لا يوجد حساب مسجل بهذا البريد. اختر 'حساب جديد' للتسجيل." else "No account found for this email. Switch to Register to create one."
            code == "ERROR_EMAIL_ALREADY_IN_USE" ->
                if (isArabic) "هذا البريد مسجل بالفعل. يرجى تسجيل الدخول بدلاً من ذلك." else "This email is already registered. Please sign in instead."
            code == "ERROR_INVALID_PHONE_NUMBER" ->
                if (isArabic) "صيغة رقم الهاتف غير صحيحة. أدخل الرمز الدولي مثل +249912345678." else "Invalid phone number format. Use international format e.g. +249912345678."
            code == "ERROR_INVALID_VERIFICATION_CODE" ->
                if (isArabic) "رمز التحقق SMS غير صحيح." else "Invalid SMS verification code."
            code == "ERROR_TOO_MANY_REQUESTS" ->
                if (isArabic) "محاولات كثيرة مؤقتاً. انتظر قليلاً ثم أعد المحاولة." else "Too many requests. Please wait a moment and try again."
            lower.contains("network") ->
                if (isArabic) "تعذّر الاتصال بالشبكة أثناء المصادقة. تحقق من الإنترنت وأعد المحاولة." else "Network error during authentication. Check connection and try again."
            else -> raw.ifBlank {
                if (isArabic) "تعذّرت المصادقة عبر $providerLabel." else "$providerLabel sign-in failed."
            }
        }
    }

    suspend fun sendEmailVerification(auth: FirebaseAuth): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No authenticated user")
        user.sendEmailVerification().await()
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun sendPasswordReset(auth: FirebaseAuth, email: String): Result<Unit> = runCatching {
        require(email.contains("@")) { "Valid email address is required" }
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun verifyBeforeUpdateEmail(auth: FirebaseAuth, newEmail: String): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("No authenticated user")
        require(newEmail.contains("@")) { "Valid new email address is required" }
        user.verifyBeforeUpdateEmail(newEmail.trim()).await()
        Unit
    }.onFailure { if (it is CancellationException) throw it }

    suspend fun reloadCurrentUser(auth: FirebaseAuth): Boolean = runCatching {
        auth.currentUser?.reload()?.await()
        true
    }.getOrDefault(false)

    fun currentDeviceLabel(): String {
        val manu = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return listOf(manu, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android Device" }
    }

    suspend fun signOut(context: Context, auth: FirebaseAuth) {
        withContext(NonCancellable) {
            auth.signOut()
            try {
                CredentialManager.create(context)
                    .clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed clearing CredentialManager state", e)
            }
        }
    }

    class CancelledAuthException : Exception("Sign-in cancelled")
}

object AdminCredentialHelper {
    private const val PREFS_NAME = "fvg_admin_sec"
    private const val KEY_HASH = "admin_pass_hash_v2"
    private const val KEY_SETUP_COMPLETED = "admin_setup_completed_v2"
    private const val SALT = "FVG_OWNER_SEC_2026_V7"
    const val OWNER_EMAIL_CANONICAL = "Financialvisionguide@gmail.com"

    private fun sha256(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((SALT + "::" + raw).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun isAuthorizedAdminEmail(email: String): Boolean =
        email.trim().equals(OWNER_EMAIL_CANONICAL, ignoreCase = true)

    fun isAdminSetupCompleted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val completed = prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        val hash = prefs.getString(KEY_HASH, "").orEmpty()
        return completed && hash.isNotEmpty()
    }

    fun isAdminEmailPendingInitialSetup(context: Context, email: String): Boolean =
        isAuthorizedAdminEmail(email) && !isAdminSetupCompleted(context)

    fun validatePasswordStrength(password: String, isArabic: Boolean = false): String? {
        if (password.length < 8) {
            return if (isArabic) "كلمة المرور يجب أن تكون 8 أحرف على الأقل." else "Password must be at least 8 characters."
        }
        val hasLetter = password.any { it.isLetter() }
        val hasDigitOrSymbol = password.any { it.isDigit() || !it.isLetterOrDigit() }
        if (!hasLetter || !hasDigitOrSymbol) {
            return if (isArabic) "يجب أن تحتوي كلمة المرور على حرف واحد ورقم أو رمز واحد على الأقل." else "Password must include at least one letter and one number or symbol."
        }
        return null
    }

    fun completeOneTimeInitialSetup(
        context: Context,
        email: String,
        newPassword: String,
        confirmPassword: String,
        isArabic: Boolean = false
    ): Result<Unit> {
        if (!isAuthorizedAdminEmail(email)) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "البريد الإلكتروني غير مصرح له بإعداد بوابة الإدارة." else "Unauthorized administrator email."
                )
            )
        }
        if (isAdminSetupCompleted(context)) {
            return Result.failure(
                IllegalStateException(
                    if (isArabic) "تم إكمال الإعداد الأولي مسبقاً. استخدم كلمة المرور الحالية لتسجيل الدخول أو تغييرها." else "One-time initial admin setup is already completed."
                )
            )
        }
        if (newPassword != confirmPassword) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "كلمتا المرور غير متطابقتين." else "Passwords do not match."
                )
            )
        }
        validatePasswordStrength(newPassword, isArabic)?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("admin_pass_hash_v1")
            .remove("admin_pass_changed")
            .putString(KEY_HASH, sha256(newPassword))
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()
        return Result.success(Unit)
    }

    fun resetAdminSetupForRecovery(context: Context, email: String): Boolean {
        if (!isAuthorizedAdminEmail(email)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_HASH)
            .remove(KEY_SETUP_COMPLETED)
            .apply()
        return true
    }

    fun verifyAdminPassword(context: Context, password: String): Boolean {
        if (password.isBlank() || !isAdminSetupCompleted(context)) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expectedHash = prefs.getString(KEY_HASH, "") ?: ""
        return expectedHash.isNotEmpty() && sha256(password) == expectedHash
    }

    fun changeAdminPassword(
        context: Context,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String = newPassword,
        isArabic: Boolean = false
    ): Result<Unit> {
        if (!isAdminSetupCompleted(context)) {
            return Result.failure(
                IllegalStateException(
                    if (isArabic) "يجب إكمال الإعداد الأولي أولاً." else "Complete initial admin setup first."
                )
            )
        }
        if (!verifyAdminPassword(context, currentPassword)) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "كلمة المرور الإدارية الحالية غير صحيحة." else "Current admin password is incorrect."
                )
            )
        }
        if (newPassword != confirmPassword) {
            return Result.failure(
                IllegalArgumentException(
                    if (isArabic) "كلمتا المرور الجديدتان غير متطابقتين." else "New passwords do not match."
                )
            )
        }
        validatePasswordStrength(newPassword, isArabic)?.let { msg ->
            return Result.failure(IllegalArgumentException(msg))
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_HASH, sha256(newPassword))
            .putBoolean(KEY_SETUP_COMPLETED, true)
            .apply()
        return Result.success(Unit)
    }

    private const val KEY_FAIL_COUNT = "admin_fail_count_v1"
    private const val KEY_LOCKOUT_UNTIL = "admin_lockout_until_v1"
    private const val KEY_PENDING_SEC_EVENTS = "admin_pending_sec_events_v1"

    fun getLockoutRemainingSeconds(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val rem = until - System.currentTimeMillis()
        return if (rem > 0L) (rem / 1000L) + 1L else 0L
    }

    fun recordFailedAdminAttempt(context: Context, attemptedEmail: String, reason: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val nextCount = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
        val editor = prefs.edit().putInt(KEY_FAIL_COUNT, nextCount)
        if (nextCount >= 3) {
            editor.putLong(KEY_LOCKOUT_UNTIL, System.currentTimeMillis() + 60_000L)
        }
        val existing = prefs.getStringSet(KEY_PENDING_SEC_EVENTS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val cleanEmail = attemptedEmail.trim().ifBlank { "unknown" }.take(120)
        existing.add("${System.currentTimeMillis()}|$cleanEmail|$reason|$nextCount")
        editor.putStringSet(KEY_PENDING_SEC_EVENTS, existing.take(20).toSet())
        editor.apply()
        return nextCount
    }

    fun resetFailedAdminAttempts(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_FAIL_COUNT).remove(KEY_LOCKOUT_UNTIL).apply()
    }

    fun consumePendingSecurityEvents(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val events = prefs.getStringSet(KEY_PENDING_SEC_EVENTS, emptySet())?.toList().orEmpty()
        if (events.isNotEmpty()) {
            prefs.edit().remove(KEY_PENDING_SEC_EVENTS).apply()
        }
        return events
    }
}

