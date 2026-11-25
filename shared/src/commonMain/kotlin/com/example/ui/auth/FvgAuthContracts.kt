package com.example.ui.auth

import com.example.platform.FvgKeyValueStore
import com.example.platform.FvgSha256
import com.example.platform.currentTimeMillis
import kotlinx.coroutines.flow.StateFlow

data class FvgAuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val phoneNumber: String?,
    val isEmailVerified: Boolean = false
)

interface FvgAuthGateway {
    val authState: StateFlow<FvgAuthUser?>
    val currentUser: FvgAuthUser?
    val isFirebaseBacked: Boolean

    suspend fun attemptSilentGoogleSignIn(): Boolean
    suspend fun signInWithGoogleExplicit(): Result<Unit>
    suspend fun signInWithOAuthProvider(providerId: String): Result<Unit>
    suspend fun signInWithEmail(
        email: String,
        password: String,
        isSignUp: Boolean,
        displayName: String,
        isArabic: Boolean
    ): Result<Unit>

    fun startPhoneVerification(
        rawPhone: String,
        isArabic: Boolean,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: () -> Unit,
        onFailed: (String) -> Unit
    )

    suspend fun verifyPhoneOtp(
        verificationId: String,
        otpCode: String,
        displayName: String,
        optionalEmail: String,
        isArabic: Boolean
    ): Result<Unit>

    suspend fun signInWithAdminEmail(
        email: String,
        password: String,
        createIfMissing: Boolean
    ): Result<Unit>

    suspend fun sendPasswordReset(email: String, isArabic: Boolean): Result<Unit>
    suspend fun sendEmailVerification(): Result<Unit>
    suspend fun verifyBeforeUpdateEmail(newEmail: String): Result<Unit>
    suspend fun reloadCurrentUser(): Boolean
    suspend fun updateDisplayName(displayName: String): Result<Unit>
    suspend fun signOut()
    fun formatAuthError(error: Throwable, isArabic: Boolean, providerLabel: String): String

    class CancelledAuthException : Exception("Sign-in cancelled")
}

object FvgAdminCredentialStore {
    const val PREFS_NAME = "fvg_admin_sec"
    private const val KEY_HASH = "admin_pass_hash_v2"
    private const val KEY_SETUP_COMPLETED = "admin_setup_completed_v2"
    private const val SALT = "FVG_OWNER_SEC_2026_V7"
    const val OWNER_EMAIL_CANONICAL = "Financialvisionguide@gmail.com"
    private const val KEY_FAIL_COUNT = "admin_fail_count_v1"
    private const val KEY_LOCKOUT_UNTIL = "admin_lockout_until_v1"
    private const val KEY_PENDING_SEC_EVENTS = "admin_pending_sec_events_v1"

    private fun sha256(raw: String): String = FvgSha256.digestHex("$SALT::$raw")

    fun isAuthorizedAdminEmail(email: String): Boolean =
        email.trim().equals(OWNER_EMAIL_CANONICAL, ignoreCase = true)

    fun isAdminSetupCompleted(store: FvgKeyValueStore): Boolean {
        val completed = store.getBoolean(KEY_SETUP_COMPLETED, false)
        val hash = store.getString(KEY_HASH, "")
        return completed && hash.isNotEmpty()
    }

    fun isAdminEmailPendingInitialSetup(store: FvgKeyValueStore, email: String): Boolean =
        isAuthorizedAdminEmail(email) && !isAdminSetupCompleted(store)

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
        store: FvgKeyValueStore,
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
        if (isAdminSetupCompleted(store)) {
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
        store.remove("admin_pass_hash_v1")
        store.remove("admin_pass_changed")
        store.putString(KEY_HASH, sha256(newPassword))
        store.putBoolean(KEY_SETUP_COMPLETED, true)
        return Result.success(Unit)
    }

    fun resetAdminSetupForRecovery(store: FvgKeyValueStore, email: String): Boolean {
        if (!isAuthorizedAdminEmail(email)) return false
        store.remove(KEY_HASH)
        store.remove(KEY_SETUP_COMPLETED)
        return true
    }

    fun verifyAdminPassword(store: FvgKeyValueStore, password: String): Boolean {
        if (password.isBlank() || !isAdminSetupCompleted(store)) return false
        val expectedHash = store.getString(KEY_HASH, "")
        return expectedHash.isNotEmpty() && sha256(password) == expectedHash
    }

    fun changeAdminPassword(
        store: FvgKeyValueStore,
        currentPassword: String,
        newPassword: String,
        confirmPassword: String = newPassword,
        isArabic: Boolean = false
    ): Result<Unit> {
        if (!isAdminSetupCompleted(store)) {
            return Result.failure(
                IllegalStateException(
                    if (isArabic) "يجب إكمال الإعداد الأولي أولاً." else "Complete initial admin setup first."
                )
            )
        }
        if (!verifyAdminPassword(store, currentPassword)) {
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
        store.putString(KEY_HASH, sha256(newPassword))
        store.putBoolean(KEY_SETUP_COMPLETED, true)
        return Result.success(Unit)
    }

    fun getLockoutRemainingSeconds(store: FvgKeyValueStore): Long {
        val until = store.getLong(KEY_LOCKOUT_UNTIL, 0L)
        val rem = until - currentTimeMillis()
        return if (rem > 0L) (rem / 1000L) + 1L else 0L
    }

    fun recordFailedAdminAttempt(store: FvgKeyValueStore, attemptedEmail: String, reason: String): Int {
        val nextCount = store.getInt(KEY_FAIL_COUNT, 0) + 1
        store.putInt(KEY_FAIL_COUNT, nextCount)
        if (nextCount >= 3) {
            store.putLong(KEY_LOCKOUT_UNTIL, currentTimeMillis() + 60_000L)
        }
        val existing = store.getStringSet(KEY_PENDING_SEC_EVENTS).toMutableSet()
        val cleanEmail = attemptedEmail.trim().ifBlank { "unknown" }.take(120)
        existing.add("${currentTimeMillis()}|$cleanEmail|$reason|$nextCount")
        store.putStringSet(KEY_PENDING_SEC_EVENTS, existing.take(20).toSet())
        return nextCount
    }

    fun resetFailedAdminAttempts(store: FvgKeyValueStore) {
        store.remove(KEY_FAIL_COUNT)
        store.remove(KEY_LOCKOUT_UNTIL)
    }

    fun consumePendingSecurityEvents(store: FvgKeyValueStore): List<String> {
        val events = store.getStringSet(KEY_PENDING_SEC_EVENTS).toList()
        if (events.isNotEmpty()) {
            store.remove(KEY_PENDING_SEC_EVENTS)
        }
        return events
    }
}
