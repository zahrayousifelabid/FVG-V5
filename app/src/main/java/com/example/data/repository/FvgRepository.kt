package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.*
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.domain.engine.FvgFinancialEngine
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import com.example.domain.math.BigDecimal
import java.util.UUID

class FvgRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    val firebaseApp get() = db.app

    fun isSignedInAs(expectedUid: String): Boolean = try {
        expectedUid.isNotBlank() &&
            com.google.firebase.auth.FirebaseAuth.getInstance(db.app).currentUser?.uid == expectedUid
    } catch (_: Exception) {
        false
    }

    private fun newId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(16)}"

    private inline fun <reified T> observeOrgSubcollection(
        orgId: String,
        subcollection: String,
        currentUserId: String
    ): Flow<List<T>> {
        if (orgId.isBlank() || currentUserId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        val path = "organizations/$orgId/$subcollection"
        return db.collection("organizations").document(orgId).collection(subcollection)
            .whereArrayContains("memberUids", currentUserId)
            .snapshots()
            .map { snap ->
                snap.toObjects(T::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (isSignedInAs(currentUserId)) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, path)
                }
                throw error
            }
    }

    // --- User & Organization (Immutable 7-Day / 168h Trial Anchoring; Preserves Legacy 72h Records) ---

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.flowOf(null)
        return db.collection("users").document(userId)
            .snapshots()
            .map { snap ->
                if (snap.exists()) snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                else null
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (isSignedInAs(userId)) {
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "users/$userId")
                }
                throw error
            }
    }

    /**
     * Ensures UserProfile exists with immutable 168h (7-day) trial anchors for new accounts
     * and preserves existing legacy trial durations without auto-extending.
     */
    suspend fun ensureUserProfile(
        userId: String,
        email: String,
        displayName: String
    ): Result<Long> = runCatching {
        val docRef = db.collection("users").document(userId)
        val snap = docRef.get().await()
        if (!snap.exists()) {
            val payload = mapOf(
                "userId" to userId,
                "email" to email.ifBlank { "$userId@fvg.app" },
                "displayName" to displayName.ifBlank { "FVG User" },
                "preferredLanguage" to "ar",
                "darkMode" to false,
                "themeMode" to "light",
                "fontScale" to 1.0,
                "timezone" to "Africa/Khartoum",
                "dateFormat" to "yyyy-MM-dd",
                "numberFormat" to "latin",
                "defaultCurrency" to "SDG",
                "avatarLocalRef" to "",
                "contactPhoneOptional" to "",
                "tourCompleted" to false,
                "notificationsEnabled" to true,
                "trialDurationHours" to FvgFinancialEngine.TRIAL_DURATION_HOURS,
                "trialStartedAt" to FieldValue.serverTimestamp(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            queueWelcomeEmailIdempotent(userId, email, displayName)
            val committed = docRef.get().await()
            val serverTs = committed.getTimestamp("trialStartedAt")?.toDate()?.time
            if (serverTs != null) serverTs - System.currentTimeMillis() else 0L
        } else {
            queueWelcomeEmailIdempotent(userId, email, displayName)
            val updated = snap.getTimestamp("updatedAt")?.toDate()?.time
            if (updated != null && !snap.metadata.isFromCache) 0L else 0L
        }
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "users/$userId") }

    suspend fun queueWelcomeEmailIdempotent(userId: String, email: String, displayName: String) {
        val cleanEmail = email.trim()
        if (userId.isBlank() || !cleanEmail.contains("@")) return
        runCatching {
            val mailRef = db.collection("mail").document("welcome_$userId")
            val existing = mailRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifBlank { "عميلنا العزيز" }
                val bodyAr = """
                    مرحباً بك $safeName في منصة دليل رؤيتك المالي (FVG — Financial Vision Guide).
                    يسعدنا انضمامك إلى نظام الإدارة المالية والتكاليف والتسعير والتحليل الحتمي.
                    فترة التجربة المجانية الكاملة مفعلة لمدة 7 أيام (168 ساعة) من تاريخ التسجيل.
                    للاستفسارات أو تأكيد الاشتراكات (بنكك / أوكاش / تحويل بنكي): 00249911569966

                    مع أطيب التحيات،
                    المالكة زهراء يوسف
                    Financialvisionguide@gmail.com
                """.trimIndent()
                val bodyEn = """
                    Welcome $safeName to FVG — Financial Vision Guide.
                    Your 7-day (168-hour) trial workspace is ready for Price Monitoring, 8 Costing Methods, Ledger Statements, Inventory & Decision Support.
                    For verification or support: 00249911569966

                    Best regards,
                    Platform Owner: Zahra Yousif (المالكة زهراء يوسف)
                    Financialvisionguide@gmail.com
                """.trimIndent()
                mailRef.set(
                    mapOf(
                        "from" to "Financialvisionguide@gmail.com",
                        "to" to listOf(cleanEmail),
                        "createdBy" to userId,
                        "recipientName" to safeName,
                        "mailType" to "welcome",
                        "message" to mapOf(
                            "subject" to "مرحباً بك في FVG دليل رؤيتك المالي | Welcome to FVG",
                            "text" to "$bodyAr\n\n---\n\n$bodyEn"
                        ),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    suspend fun recordPlatformVisit(
        platform: String,
        screen: String,
        visitorKey: String,
        userId: String
    ) {
        runCatching {
            val id = newId("vis")
            db.collection("platformVisits").document(id).set(
                mapOf(
                    "platform" to if (platform == "web_pwa") "web_pwa" else "android",
                    "screen" to screen.take(80).ifBlank { "home" },
                    "visitorKey" to visitorKey.take(120).ifBlank { id },
                    "userId" to userId.take(120),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun syncServerClockOffset(userId: String): Long = runCatching {
        val docRef = db.collection("users").document(userId)
        docRef.update(mapOf("updatedAt" to FieldValue.serverTimestamp())).await()
        val snap = docRef.get().await()
        val serverNow = snap.getTimestamp("updatedAt")?.toDate()?.time
        if (serverNow != null) serverNow - System.currentTimeMillis() else 0L
    }.getOrDefault(0L)

    suspend fun updateUserPreferences(
        userId: String,
        activeOrgId: String? = null,
        preferredLanguage: String? = null,
        darkMode: Boolean? = null,
        displayName: String? = null
    ): Result<Unit> = updateExtendedUserPreferences(
        userId = userId,
        activeOrgId = activeOrgId,
        preferredLanguage = preferredLanguage,
        darkMode = darkMode,
        themeMode = darkMode?.let { if (it) "dark" else "light" },
        displayName = displayName
    )

    suspend fun updateExtendedUserPreferences(
        userId: String,
        activeOrgId: String? = null,
        preferredLanguage: String? = null,
        darkMode: Boolean? = null,
        themeMode: String? = null,
        fontScale: Float? = null,
        displayName: String? = null,
        timezone: String? = null,
        dateFormat: String? = null,
        numberFormat: String? = null,
        defaultCurrency: String? = null,
        avatarLocalRef: String? = null,
        contactPhoneOptional: String? = null,
        tourCompleted: Boolean? = null,
        notificationsEnabled: Boolean? = null
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
        if (activeOrgId != null) updates["activeOrgId"] = activeOrgId
        if (preferredLanguage != null && preferredLanguage in setOf("ar", "en")) {
            updates["preferredLanguage"] = preferredLanguage
        }
        if (darkMode != null) updates["darkMode"] = darkMode
        if (themeMode != null && themeMode in setOf("light", "dark", "system")) {
            updates["themeMode"] = themeMode
        }
        if (fontScale != null) updates["fontScale"] = fontScale.coerceIn(0.85f, 1.30f).toDouble()
        if (displayName != null && displayName.isNotBlank()) updates["displayName"] = displayName.trim().take(80)
        if (timezone != null) updates["timezone"] = timezone.trim().take(60)
        if (dateFormat != null) updates["dateFormat"] = dateFormat.trim().take(24)
        if (numberFormat != null && numberFormat in setOf("latin", "arabic_indic")) updates["numberFormat"] = numberFormat
        if (defaultCurrency != null) updates["defaultCurrency"] = defaultCurrency.uppercase().take(6)
        if (avatarLocalRef != null) updates["avatarLocalRef"] = avatarLocalRef.take(120000)
        if (contactPhoneOptional != null) updates["contactPhoneOptional"] = contactPhoneOptional.trim().take(32)
        if (tourCompleted != null) updates["tourCompleted"] = tourCompleted
        if (notificationsEnabled != null) updates["notificationsEnabled"] = notificationsEnabled
        db.collection("users").document(userId).update(updates).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "users/$userId") }

    // --- User Sessions ---

    fun observeSessions(userId: String): Flow<List<UserSessionRecord>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.collection("users").document(userId).collection("sessions")
            .snapshots()
            .map { snap -> snap.toObjects(UserSessionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (isSignedInAs(userId)) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "users/$userId/sessions")
                }
                throw error
            }
    }

    suspend fun upsertSession(
        userId: String,
        sessionId: String,
        deviceLabel: String,
        platform: String = "Android"
    ): Result<Unit> = runCatching {
        db.collection("users").document(userId).collection("sessions").document(sessionId)
            .set(
                mapOf(
                    "userId" to userId,
                    "deviceLabel" to deviceLabel.take(80),
                    "platform" to platform,
                    "active" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastActiveAt" to FieldValue.serverTimestamp()
                )
            ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "users/$userId/sessions/$sessionId") }

    suspend fun revokeSession(userId: String, sessionId: String): Result<Unit> = runCatching {
        db.collection("users").document(userId).collection("sessions").document(sessionId)
            .update(mapOf("active" to false, "lastActiveAt" to FieldValue.serverTimestamp()))
            .await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "users/$userId/sessions/$sessionId") }

    fun observeOrganizations(userId: String): Flow<List<Organization>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.collection("organizations")
            .whereArrayContains("memberUids", userId)
            .snapshots()
            .map { snap -> snap.toObjects(Organization::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                if (isSignedInAs(userId)) {
                    handleFirestoreError(fsErr ?: error, OperationType.LIST, "organizations")
                }
                throw error
            }
    }

    suspend fun createOrganization(
        userId: String,
        userEmail: String,
        name: String,
        baseCurrency: String,
        sectorType: String = "manufacturing"
    ): Result<String> = runCatching {
        require(name.isNotBlank()) { "Organization name is required" }
        val userDocRef = db.collection("users").document(userId)
        var userSnap = userDocRef.get().await()
        if (!userSnap.exists()) {
            userDocRef.set(
                mapOf(
                    "userId" to userId,
                    "email" to userEmail.ifBlank { "$userId@fvg.app" },
                    "displayName" to "FVG User",
                    "preferredLanguage" to "ar",
                    "darkMode" to false,
                    "themeMode" to "light",
                    "fontScale" to 1.0,
                    "defaultCurrency" to baseCurrency.uppercase(),
                    "trialDurationHours" to FvgFinancialEngine.TRIAL_DURATION_HOURS,
                    "trialStartedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            userSnap = userDocRef.get().await()
        }

        val nowApprox = System.currentTimeMillis()
        val userDurationHours = userSnap.getLong("trialDurationHours") ?: FvgFinancialEngine.TRIAL_DURATION_HOURS
        val userStartMillis = userSnap.getTimestamp("trialStartedAt")?.toDate()?.time ?: nowApprox
        val userEndMillis = userStartMillis + userDurationHours * 3600_000L
        val isUserTrialAlreadyExpired = userEndMillis <= nowApprox

        val orgId = newId("org")
        val orgRef = db.collection("organizations").document(orgId)
        val orgPayload = mapOf(
            "name" to name.trim(),
            "baseCurrency" to baseCurrency.uppercase(),
            "sectorType" to sectorType,
            "brandTagline" to "",
            "countryState" to "",
            "fiscalYearStart" to "01-01",
            "taxEnabled" to false,
            "defaultTaxRatePct" to "0.00",
            "ownerId" to userId,
            "memberUids" to listOf(userId),
            "roles" to mapOf(userId to "owner"),
            "planTier" to "trial",
            "subscriptionStatus" to if (isUserTrialAlreadyExpired) "expired" else "trial",
            "trialDurationHours" to userDurationHours,
            "trialStartedAt" to FieldValue.serverTimestamp(),
            "trialEndsAt" to if (isUserTrialAlreadyExpired) {
                FieldValue.serverTimestamp()
            } else {
                Timestamp(java.util.Date(userEndMillis))
            },
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        orgRef.set(orgPayload).await()

        userDocRef.update(
            mapOf("activeOrgId" to orgId, "updatedAt" to FieldValue.serverTimestamp())
        ).await()

        // Bootstrap standard empty Chart of Accounts structure (zero fake transactions)
        val orgObj = Organization(
            id = orgId,
            name = name.trim(),
            baseCurrency = baseCurrency.uppercase(),
            ownerId = userId,
            memberUids = listOf(userId),
            roles = mapOf(userId to "owner")
        )
        val defaultAccounts = listOf(
            Triple("1010", "Cash & Bank", "النقدية والبنوك") to ("asset" to "cash_bank" to "debit"),
            Triple("1110", "Accounts Receivable", "الذمم المدينة") to ("asset" to "receivable" to "debit"),
            Triple("1210", "Inventory", "المخزون") to ("asset" to "inventory" to "debit"),
            Triple("1510", "Property & Equipment", "الأصول الثابتة والمعدات") to ("asset" to "non_current_asset" to "debit"),
            Triple("2010", "Accounts Payable", "الذمم الدائنة") to ("liability" to "payable" to "credit"),
            Triple("2510", "Long-Term Financing", "تمويل طويل الأجل") to ("liability" to "non_current_liability" to "credit"),
            Triple("3010", "Owner Equity", "رأس المال وحقوق الملكية") to ("equity" to "equity" to "credit"),
            Triple("4010", "Operating Revenue", "إيرادات المبيعات") to ("revenue" to "revenue" to "credit"),
            Triple("5010", "Cost of Goods Sold", "تكلفة البضاعة المباعة") to ("cogs" to "cogs" to "debit"),
            Triple("6010", "Operating Expenses", "المصروفات التشغيلية") to ("expense" to "operating_expense" to "debit"),
            Triple("6210", "Inventory Loss & Scrap", "خسائر وتوالف المخزون") to ("expense" to "operating_expense" to "debit"),
            Triple("7010", "Interest Expense", "مصروفات التمويل والفوائد") to ("expense" to "interest_expense" to "debit")
        )
        val batch = db.batch()
        for ((meta, typing) in defaultAccounts) {
            val (code, enName, arName) = meta
            val (typePair, normalBal) = typing
            val (accType, subType) = typePair
            val accId = "acc_$code"
            val docRef = orgRef.collection("accounts").document(accId)
            val data = tenantEnvelope(orgObj, userId).apply {
                put("code", code)
                put("name", enName)
                put("nameAr", arName)
                put("accountType", accType)
                put("subType", subType)
                put("normalBalance", normalBal)
                put("openingBalance", "0.00")
            }
            batch.set(docRef, data)
        }
        val logRef = orgRef.collection("auditLogs").document(newId("log"))
        batch.set(logRef, mapOf(
            "orgId" to orgId,
            "ownerId" to userId,
            "memberUids" to listOf(userId),
            "createdBy" to userId,
            "action" to "CREATE_ORGANIZATION",
            "entityType" to "organization",
            "entityId" to orgId,
            "summary" to "Created organization '$name' ($baseCurrency) with standard COA",
            "actorEmail" to userEmail.ifBlank { userId },
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().await()
        orgId
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations") }

    /**
     * Organization metadata settings (explicitly excludes planTier and subscriptionStatus which are controlled only by the Platform Owner / payment verification).
     */
    suspend fun updateOrganizationSettings(
        org: Organization,
        newName: String,
        newCurrency: String,
        sectorType: String = org.sectorType,
        brandTagline: String = org.brandTagline,
        countryState: String = org.countryState,
        fiscalYearStart: String = org.fiscalYearStart,
        taxEnabled: Boolean = org.taxEnabled,
        defaultTaxRatePct: String = org.defaultTaxRatePct,
        actorUid: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        val updates = mapOf(
            "name" to newName.trim(),
            "baseCurrency" to newCurrency.uppercase(),
            "sectorType" to sectorType,
            "brandTagline" to brandTagline.trim().take(120),
            "countryState" to countryState.trim().take(80),
            "fiscalYearStart" to fiscalYearStart.trim().take(10),
            "taxEnabled" to taxEnabled,
            "defaultTaxRatePct" to defaultTaxRatePct.trim().take(12),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("organizations").document(org.id).update(updates).await()
        logAudit(org, actorUid, actorEmail, "UPDATE_ORGANIZATION", "organization", org.id, "Updated organization settings ($newName, $newCurrency, $sectorType)")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}") }

    suspend fun applySectorStarterTemplate(
        org: Organization,
        sectorKey: String,
        actorUid: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        val sectorDef = FvgFinancialEngine.FVG_SECTORS.firstOrNull { it.key == sectorKey }
            ?: throw IllegalArgumentException("Unsupported sector")
        require(sectorDef.isActive) { "Sector is currently marked Coming Soon" }

        val sectorAccounts = when (sectorKey) {
            "food_beverage" -> listOf(
                Triple("1215", "Perishable Raw Ingredients", "المواد الغذائية الأولية") to ("asset" to "inventory" to "debit"),
                Triple("1225", "Finished Food & Beverage Stock", "مخزون المنتجات الغذائية التامة") to ("asset" to "inventory" to "debit"),
                Triple("5025", "Packaging & Cold-Chain COGS", "تكلفة التعبئة والتبريد المباشرة") to ("cogs" to "cogs" to "debit"),
                Triple("6215", "Spoilage & Expiry Write-Off", "خسائر الصلاحية والتلف الغذائي") to ("expense" to "operating_expense" to "debit")
            )
            else -> listOf(
                Triple("1212", "Raw Materials & WIP", "المواد الخام وتحت التشغيل") to ("asset" to "inventory" to "debit"),
                Triple("1222", "Finished Manufactured Goods", "المنتجات الصناعية التامة") to ("asset" to "inventory" to "debit"),
                Triple("5020", "Direct Manufacturing Overhead", "التكاليف الصناعية غير المباشرة") to ("cogs" to "cogs" to "debit"),
                Triple("6212", "Production Scrap & Rework", "خسائر الهدر وإعادة التشغيل") to ("expense" to "operating_expense" to "debit")
            )
        }

        val orgRef = db.collection("organizations").document(org.id)
        val batch = db.batch()
        batch.update(orgRef, mapOf("sectorType" to sectorKey, "updatedAt" to FieldValue.serverTimestamp()))
        for ((meta, typing) in sectorAccounts) {
            val (code, enName, arName) = meta
            val (typePair, normalBal) = typing
            val (accType, subType) = typePair
            val accRef = orgRef.collection("accounts").document("acc_$code")
            batch.set(
                accRef,
                tenantEnvelope(org, actorUid).apply {
                    put("code", code)
                    put("name", enName)
                    put("nameAr", arName)
                    put("accountType", accType)
                    put("subType", subType)
                    put("normalBalance", normalBal)
                    put("openingBalance", "0.00")
                }
            )
        }
        batch.commit().await()
        logAudit(org, actorUid, actorEmail, "APPLY_SECTOR_TEMPLATE", "organization", org.id, "Applied $sectorKey chart of accounts starter structure (zero synthetic balances)")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}") }

    suspend fun updateMemberRole(
        org: Organization,
        targetUid: String,
        newRole: String,
        actorUid: String,
        actorEmail: String
    ): Result<Unit> = runCatching {
        require(targetUid.isNotBlank()) { "Member UID required" }
        require(targetUid != org.ownerId) { "Owner role cannot be downgraded" }
        require(newRole in listOf("admin", "accountant", "viewer")) { "Invalid role" }
        val updatedMembers = (org.memberUids + targetUid).distinct()
        val updatedRoles = org.roles.toMutableMap().apply { put(targetUid, newRole) }
        db.collection("organizations").document(org.id).update(
            mapOf(
                "memberUids" to updatedMembers,
                "roles" to updatedRoles,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "UPDATE_MEMBER_ROLE", "organization", org.id, "Set member $targetUid role to $newRole")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}") }

    private suspend fun logAudit(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        action: String,
        entityType: String,
        entityId: String,
        summary: String
    ) {
        val logId = newId("log")
        val payload = mapOf(
            "orgId" to org.id,
            "ownerId" to org.ownerId,
            "memberUids" to org.memberUids,
            "createdBy" to actorUid,
            "action" to action,
            "entityType" to entityType,
            "entityId" to entityId,
            "summary" to summary.take(500),
            "actorEmail" to actorEmail.ifBlank { actorUid },
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("organizations").document(org.id).collection("auditLogs").document(logId).set(payload).await()
    }

    // --- Observers ---

    fun observeProducts(orgId: String, uid: String): Flow<List<Product>> = observeOrgSubcollection(orgId, "products", uid)
    fun observePriceObservations(orgId: String, uid: String): Flow<List<PriceObservation>> = observeOrgSubcollection(orgId, "priceObservations", uid)
    fun observeInflationRecords(orgId: String, uid: String): Flow<List<InflationRecord>> = observeOrgSubcollection(orgId, "inflationRecords", uid)
    fun observePriceBaskets(orgId: String, uid: String): Flow<List<PriceBasket>> = observeOrgSubcollection(orgId, "priceBaskets", uid)
    fun observeFxRates(orgId: String, uid: String): Flow<List<FxRate>> = observeOrgSubcollection(orgId, "fxRates", uid)
    fun observeCostSnapshots(orgId: String, uid: String): Flow<List<CostSnapshot>> = observeOrgSubcollection(orgId, "costSnapshots", uid)
    fun observeScenarios(orgId: String, uid: String): Flow<List<ScenarioRecord>> = observeOrgSubcollection(orgId, "scenarios", uid)
    fun observeAccounts(orgId: String, uid: String): Flow<List<AccountRecord>> = observeOrgSubcollection(orgId, "accounts", uid)
    fun observeJournalEntries(orgId: String, uid: String): Flow<List<JournalEntryRecord>> = observeOrgSubcollection(orgId, "journalEntries", uid)
    fun observeCounterpartyItems(orgId: String, uid: String): Flow<List<CounterpartyRecord>> = observeOrgSubcollection(orgId, "counterpartyItems", uid)
    fun observeBudgets(orgId: String, uid: String): Flow<List<BudgetRecord>> = observeOrgSubcollection(orgId, "budgets", uid)
    fun observeInventoryItems(orgId: String, uid: String): Flow<List<InventoryItemRecord>> = observeOrgSubcollection(orgId, "inventoryItems", uid)
    fun observeInventoryMovements(orgId: String, uid: String): Flow<List<InventoryMovementRecord>> = observeOrgSubcollection(orgId, "inventoryMovements", uid)
    fun observeStocktakes(orgId: String, uid: String): Flow<List<StocktakeSessionRecord>> = observeOrgSubcollection(orgId, "stocktakes", uid)
    fun observeOcrDrafts(orgId: String, uid: String): Flow<List<OcrDraftRecord>> = observeOrgSubcollection(orgId, "ocrDrafts", uid)
    fun observeAuditLogs(orgId: String, uid: String): Flow<List<AuditLogRecord>> = observeOrgSubcollection(orgId, "auditLogs", uid)
    fun observeStandardCosts(orgId: String, uid: String): Flow<List<StandardCostSheet>> = observeOrgSubcollection(orgId, "standardCosts", uid)
    fun observeProductionOrders(orgId: String, uid: String): Flow<List<ProductionOrderRecord>> = observeOrgSubcollection(orgId, "productionOrders", uid)
    fun observeScrapLosses(orgId: String, uid: String): Flow<List<ScrapLossRecord>> = observeOrgSubcollection(orgId, "scrapLosses", uid)
    fun observePaymentRequests(orgId: String, uid: String): Flow<List<PaymentRequestRecord>> = observeOrgSubcollection(orgId, "paymentRequests", uid)
    fun observeBomFormulas(orgId: String, uid: String): Flow<List<BomFormulaRecord>> = observeOrgSubcollection(orgId, "bomFormulas", uid)
    fun observeAiConversations(orgId: String, uid: String): Flow<List<AiConversationRecord>> = observeOrgSubcollection(orgId, "aiConversations", uid)

    fun observePlatformPlans(): Flow<List<PlatformPlanRecord>> =
        db.collection("platformPlans").snapshots()
            .map { snap -> snap.toObjects(PlatformPlanRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun observeAnnouncements(): Flow<List<AppAnnouncementRecord>> =
        db.collection("announcements").snapshots()
            .map { snap -> snap.toObjects(AppAnnouncementRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun observeOwnSupportTickets(userId: String): Flow<List<SupportTicketRecord>> {
        if (userId.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.collection("supportTickets").whereEqualTo("createdBy", userId).snapshots()
            .map { snap -> snap.toObjects(SupportTicketRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }
    }

    // --- 1. Prices & Products ---

    suspend fun recordProductAndPriceObservation(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        existingProduct: Product?,
        name: String,
        sku: String,
        category: String,
        description: String,
        unit: String,
        currency: String,
        location: String,
        source: String,
        newPriceStr: String,
        observationDate: String,
        notes: String,
        attachmentRef: String = ""
    ): Result<String> = runCatching {
        val newPrice = FvgFinancialEngine.money(FvgFinancialEngine.d(newPriceStr))
        val oldPrice = existingProduct?.let { FvgFinancialEngine.dOrZero(it.currentPrice) } ?: newPrice
        val comparison = FvgFinancialEngine.calculatePriceChange(newPrice, oldPrice)

        val prodId = existingProduct?.id?.takeIf { it.isNotBlank() } ?: newId("prod")
        val prodRef = db.collection("organizations").document(org.id).collection("products").document(prodId)
        val obsId = newId("obs")
        val obsRef = db.collection("organizations").document(org.id).collection("priceObservations").document(obsId)

        val batch = db.batch()
        if (existingProduct == null) {
            val prodData = tenantEnvelope(org, actorUid).apply {
                put("name", name.trim())
                put("sku", sku.trim())
                put("category", category.trim())
                put("description", description.trim())
                put("unit", unit.trim())
                put("currency", currency.uppercase())
                put("location", location.trim().ifBlank { "HQ" })
                put("source", source.trim().ifBlank { "Manual" })
                put("currentPrice", comparison.newPrice.toPlainString())
                put("previousPrice", comparison.oldPrice.toPlainString())
            }
            batch.set(prodRef, prodData)
        } else {
            batch.update(
                prodRef,
                mapOf(
                    "name" to name.trim(),
                    "category" to category.trim(),
                    "unit" to unit.trim(),
                    "currency" to currency.uppercase(),
                    "location" to location.trim().ifBlank { "HQ" },
                    "source" to source.trim().ifBlank { "Manual" },
                    "previousPrice" to existingProduct.currentPrice,
                    "currentPrice" to comparison.newPrice.toPlainString(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
        }

        val obsData = tenantEnvelope(org, actorUid).apply {
            put("productId", prodId)
            put("productName", name.trim())
            put("category", category.trim())
            put("unit", unit.trim())
            put("currency", currency.uppercase())
            put("price", comparison.newPrice.toPlainString())
            put("previousPrice", comparison.oldPrice.toPlainString())
            put("absChange", comparison.absChange.toPlainString())
            put("pctChange", comparison.pctChange?.toPlainString() ?: "N/A")
            put("direction", comparison.direction)
            put("source", source.trim().ifBlank { "Manual" })
            put("location", location.trim().ifBlank { "HQ" })
            put("observationDate", observationDate)
            put("notes", notes.trim())
            if (attachmentRef.isNotBlank()) put("attachmentRef", attachmentRef.trim())
        }
        batch.set(obsRef, obsData)
        batch.commit().await()

        logAudit(
            org, actorUid, actorEmail, "RECORD_PRICE_OBSERVATION", "priceObservation", obsId,
            "Recorded ${name.trim()} price ${comparison.newPrice} $currency (prev ${comparison.oldPrice}, ${comparison.direction})"
        )
        obsId
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "organizations/${org.id}/priceObservations") }

    suspend fun importValidatedCsvProducts(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        rows: List<FvgFinancialEngine.CsvProductRow>,
        observationDate: String
    ): Result<Int> = runCatching {
        val validRows = rows.filter { it.error == null }
        require(validRows.isNotEmpty()) { "No valid rows to import" }
        val batch = db.batch()
        for (row in validRows) {
            val prodId = newId("prod")
            val obsId = newId("obs")
            val prodRef = db.collection("organizations").document(org.id).collection("products").document(prodId)
            val obsRef = db.collection("organizations").document(org.id).collection("priceObservations").document(obsId)
            val pStr = row.currentPrice.toPlainString()
            batch.set(prodRef, tenantEnvelope(org, actorUid).apply {
                put("name", row.name)
                put("sku", row.sku)
                put("category", row.category)
                put("description", "Imported via CSV/XLSX")
                put("unit", row.unit)
                put("currency", row.currency)
                put("location", row.location)
                put("source", "Bulk Import")
                put("currentPrice", pStr)
                put("previousPrice", pStr)
            })
            batch.set(obsRef, tenantEnvelope(org, actorUid).apply {
                put("productId", prodId)
                put("productName", row.name)
                put("category", row.category)
                put("unit", row.unit)
                put("currency", row.currency)
                put("price", pStr)
                put("previousPrice", pStr)
                put("absChange", "0.00")
                put("pctChange", "0.00")
                put("direction", "unchanged")
                put("source", "Bulk Import")
                put("location", row.location)
                put("observationDate", observationDate)
                put("notes", "Row ${row.rowNumber}")
            })
        }
        batch.commit().await()
        logAudit(org, actorUid, actorEmail, "IMPORT_PRODUCTS", "product", org.id, "Imported ${validRows.size} validated product records")
        validRows.size
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "organizations/${org.id}/products") }

    // --- 2. Inflation & Price Baskets ---

    suspend fun saveInflationRecord(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        period: String,
        category: String,
        monthlyRatePct: String,
        annualRatePct: String,
        indexValue: String,
        basePeriod: String,
        sourceType: String,
        sourceName: String,
        formulaNote: String
    ): Result<String> = runCatching {
        val mRate = FvgFinancialEngine.pct4(FvgFinancialEngine.d(monthlyRatePct)).toPlainString()
        val idxVal = FvgFinancialEngine.pct4(FvgFinancialEngine.d(indexValue)).toPlainString()
        val id = newId("inf")
        val data = tenantEnvelope(org, actorUid).apply {
            put("period", period.trim())
            put("category", category.trim().ifBlank { "All Items" })
            put("monthlyRatePct", mRate)
            put("annualRatePct", annualRatePct.trim())
            put("indexValue", idxVal)
            put("basePeriod", basePeriod.trim())
            put("sourceType", sourceType)
            put("sourceName", sourceName.trim())
            put("formulaNote", formulaNote.trim().ifBlank { "((Current Index - Previous Index) / Previous Index) × 100" })
        }
        db.collection("organizations").document(org.id).collection("inflationRecords").document(id).set(data).await()
        logAudit(org, actorUid, actorEmail, "CREATE_INFLATION_RECORD", "inflationRecord", id, "Saved $sourceType inflation for $period ($mRate%)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/inflationRecords") }

    suspend fun savePriceBasket(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        existingBasket: PriceBasket?,
        name: String,
        basePeriod: String,
        currentPeriod: String,
        currency: String,
        items: List<BasketItemMap>
    ): Result<String> = runCatching {
        val calcInputs = items.map {
            FvgFinancialEngine.BasketLineInput(
                name = it.name,
                quantity = FvgFinancialEngine.d(it.quantity),
                weight = FvgFinancialEngine.d(it.weight),
                basePrice = FvgFinancialEngine.d(it.basePrice),
                currentPrice = FvgFinancialEngine.d(it.currentPrice)
            )
        }
        val res = FvgFinancialEngine.calculateWeightedBasket(calcInputs)
        val newVersion = (existingBasket?.version ?: 0L) + 1L
        val itemMaps = items.map {
            mapOf(
                "name" to it.name,
                "unit" to it.unit,
                "quantity" to it.quantity,
                "weight" to it.weight,
                "basePrice" to it.basePrice,
                "currentPrice" to it.currentPrice
            )
        }
        val snapshotEntry = mapOf(
            "period" to currentPeriod,
            "version" to newVersion,
            "cost" to res.currentCost.toPlainString(),
            "indexValue" to (res.indexValue?.toPlainString() ?: "100.00")
        )
        val snapshots = (existingBasket?.periodSnapshots ?: emptyList()) + snapshotEntry
        val id = existingBasket?.id?.takeIf { it.isNotBlank() } ?: newId("bsk")
        val ref = db.collection("organizations").document(org.id).collection("priceBaskets").document(id)
        if (existingBasket == null) {
            ref.set(tenantEnvelope(org, actorUid).apply {
                put("name", name.trim())
                put("basePeriod", basePeriod.trim())
                put("currentPeriod", currentPeriod.trim())
                put("currency", currency.uppercase())
                put("version", newVersion)
                put("items", itemMaps)
                put("baseCost", res.baseCost.toPlainString())
                put("currentCost", res.currentCost.toPlainString())
                put("indexValue", res.indexValue?.toPlainString() ?: "100.00")
                put("periodSnapshots", snapshots)
            }).await()
        } else {
            ref.update(
                mapOf(
                    "name" to name.trim(),
                    "currentPeriod" to currentPeriod.trim(),
                    "version" to newVersion,
                    "items" to itemMaps,
                    "baseCost" to res.baseCost.toPlainString(),
                    "currentCost" to res.currentCost.toPlainString(),
                    "indexValue" to (res.indexValue?.toPlainString() ?: "100.00"),
                    "periodSnapshots" to snapshots,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        logAudit(org, actorUid, actorEmail, "SAVE_PRICE_BASKET", "priceBasket", id, "Basket '$name' v$newVersion index ${res.indexValue ?: "N/A"}")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "organizations/${org.id}/priceBaskets") }

    // --- 3. Manual & Approved Historical FX ---

    suspend fun saveFxRate(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        baseCurrency: String,
        quoteCurrency: String,
        rateStr: String,
        effectiveDate: String,
        rateType: String,
        sourceName: String,
        notes: String,
        effectiveTime: String = "",
        sourceKind: String = "manual",
        status: String = "approved"
    ): Result<String> = runCatching {
        val rate = FvgFinancialEngine.rate4(FvgFinancialEngine.d(rateStr))
        require(rate > BigDecimal.ZERO) { "Exchange rate must be positive" }
        require(status in setOf("draft", "approved", "cancelled")) { "Invalid FX status" }
        val id = newId("fx")
        val data = tenantEnvelope(org, actorUid).apply {
            put("baseCurrency", baseCurrency.uppercase())
            put("quoteCurrency", quoteCurrency.uppercase())
            put("rate", rate.toPlainString())
            put("effectiveDate", effectiveDate.trim())
            put("effectiveTime", effectiveTime.trim())
            put("rateType", rateType)
            put("sourceKind", sourceKind)
            put("sourceName", sourceName.trim())
            put("status", status)
            put("notes", notes.trim())
        }
        db.collection("organizations").document(org.id).collection("fxRates").document(id).set(data).await()
        logAudit(org, actorUid, actorEmail, "RECORD_FX_RATE", "fxRate", id, "$baseCurrency/$quoteCurrency = $rate ($rateType, $effectiveDate, $status)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/fxRates") }

    suspend fun updateFxRateStatus(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        fxId: String,
        newStatus: String
    ): Result<Unit> = runCatching {
        require(newStatus in setOf("draft", "approved", "cancelled")) { "Invalid FX status" }
        db.collection("organizations").document(org.id).collection("fxRates").document(fxId)
            .update(mapOf("status" to newStatus, "updatedAt" to FieldValue.serverTimestamp()))
            .await()
        logAudit(org, actorUid, actorEmail, "UPDATE_FX_STATUS", "fxRate", fxId, "Set FX rate $fxId status to $newStatus")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/fxRates/$fxId") }

    // --- 4. Costing (All 8 Methods + Risk Level) & Spec #45 Scenarios ---

    suspend fun saveCostSnapshot(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        productId: String,
        productName: String,
        period: String,
        currency: String,
        costingMethod: String,
        input: FvgFinancialEngine.CostingInput,
        latestInflationPct: BigDecimal? = null,
        completedEquivUnits: BigDecimal? = null,
        targetMarginPct: BigDecimal = BigDecimal("20")
    ): Result<String> = runCatching {
        val methodOutcome = FvgFinancialEngine.calculateCostingByMethod(
            method = costingMethod,
            input = input,
            targetMarginPct = targetMarginPct,
            completedEquivUnits = completedEquivUnits
        )
        val res = methodOutcome.coreResult
        val risk = FvgFinancialEngine.evaluateFinancialRisk(
            profitMarginPct = res.profitMarginPct,
            inflationRatePct = latestInflationPct
        )
        val id = newId("cst")
        val data = tenantEnvelope(org, actorUid).apply {
            put("productId", productId.ifBlank { "custom" })
            put("productName", productName.trim())
            put("period", period.trim())
            put("currency", currency.uppercase())
            put("costingMethod", costingMethod)
            put("directMaterial", FvgFinancialEngine.money(input.directMaterial).toPlainString())
            put("indirectMaterial", FvgFinancialEngine.money(input.indirectMaterial).toPlainString())
            put("directLabor", FvgFinancialEngine.money(input.directLabor).toPlainString())
            put("indirectLabor", FvgFinancialEngine.money(input.indirectLabor).toPlainString())
            put("overhead", FvgFinancialEngine.money(input.overhead).toPlainString())
            put("energyFuel", FvgFinancialEngine.money(input.energyFuel).toPlainString())
            put("packaging", FvgFinancialEngine.money(input.packaging).toPlainString())
            put("transport", FvgFinancialEngine.money(input.transport).toPlainString())
            put("wasteScrap", FvgFinancialEngine.money(input.wasteScrap).toPlainString())
            put("fixedCosts", FvgFinancialEngine.money(input.fixedCosts).toPlainString())
            put("goodUnits", methodOutcome.effectiveUnitsUsed.toPlainString())
            put("totalCost", res.totalCost.toPlainString())
            put("unitCost", res.unitCost.toPlainString())
            put("sellingPrice", FvgFinancialEngine.money(input.sellingPrice).toPlainString())
            put("revenue", res.revenue.toPlainString())
            put("grossProfit", res.grossProfit.toPlainString())
            put("netProfit", res.netProfit.toPlainString())
            put("profitMarginPct", res.profitMarginPct?.toPlainString() ?: "N/A")
            put("markupPct", res.markupPct?.toPlainString() ?: "N/A")
            put("breakEvenUnits", res.breakEvenUnits?.toPlainString() ?: "N/A")
            put("riskLevel", risk.level)
            put("formulaVersion", FvgFinancialEngine.MODEL_VERSION)
        }
        db.collection("organizations").document(org.id).collection("costSnapshots").document(id).set(data).await()
        logAudit(org, actorUid, actorEmail, "SAVE_COST_SNAPSHOT", "costSnapshot", id, "Saved $costingMethod costing for $productName (Unit: ${res.unitCost}, Risk: ${risk.level})")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/costSnapshots") }

    suspend fun saveScenario(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        snapshot: CostSnapshot,
        name: String,
        matPct: BigDecimal,
        labPct: BigDecimal,
        ovhPct: BigDecimal,
        pricePct: BigDecimal,
        scenarioType: String = "Custom",
        fxPct: BigDecimal = BigDecimal.ZERO,
        volumePct: BigDecimal = BigDecimal.ZERO
    ): Result<String> = runCatching {
        val baseMat = FvgFinancialEngine.dOrZero(snapshot.directMaterial).add(FvgFinancialEngine.dOrZero(snapshot.indirectMaterial))
        val baseLab = FvgFinancialEngine.dOrZero(snapshot.directLabor).add(FvgFinancialEngine.dOrZero(snapshot.indirectLabor))
        val baseOther = FvgFinancialEngine.dOrZero(snapshot.totalCost).subtract(baseMat).subtract(baseLab).max(BigDecimal.ZERO)
        val baseRev = FvgFinancialEngine.dOrZero(snapshot.revenue)
        val res = FvgFinancialEngine.runExtendedScenario(
            baseMaterial = baseMat,
            baseLabor = baseLab,
            baseOverheadAndOther = baseOther,
            baseRevenue = baseRev,
            materialChangePct = matPct,
            laborChangePct = labPct,
            overheadChangePct = ovhPct,
            priceChangePct = pricePct,
            fxChangePct = fxPct,
            volumeChangePct = volumePct
        )
        val id = newId("scn")
        val data = tenantEnvelope(org, actorUid).apply {
            put("snapshotId", snapshot.id)
            put("name", name.trim())
            put("scenarioType", scenarioType.take(60))
            put("materialChangePct", matPct.toPlainString())
            put("laborChangePct", labPct.toPlainString())
            put("overheadChangePct", ovhPct.toPlainString())
            put("priceChangePct", pricePct.toPlainString())
            put("fxChangePct", fxPct.toPlainString())
            put("volumeChangePct", volumePct.toPlainString())
            put("baseTotalCost", res.baseTotalCost.toPlainString())
            put("scenarioTotalCost", res.scenarioTotalCost.toPlainString())
            put("baseProfit", res.baseProfit.toPlainString())
            put("scenarioProfit", res.scenarioProfit.toPlainString())
        }
        db.collection("organizations").document(org.id).collection("scenarios").document(id).set(data).await()
        logAudit(org, actorUid, actorEmail, "CREATE_SCENARIO", "scenario", id, "What-if scenario '$name' [$scenarioType]: profit ${res.scenarioProfit}")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/scenarios") }

    // --- 5. Double-Entry Accounting & AR/AP ---

    suspend fun createAccount(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        code: String,
        name: String,
        nameAr: String,
        accountType: String,
        subType: String,
        normalBalance: String,
        openingBalance: String
    ): Result<String> = runCatching {
        val id = "acc_${code.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")}"
        val bal = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(openingBalance)).toPlainString()
        val data = tenantEnvelope(org, actorUid).apply {
            put("code", code.trim())
            put("name", name.trim())
            put("nameAr", nameAr.trim())
            put("accountType", accountType)
            put("subType", subType)
            put("normalBalance", normalBalance)
            put("openingBalance", bal)
        }
        db.collection("organizations").document(org.id).collection("accounts").document(id).set(data).await()
        logAudit(org, actorUid, actorEmail, "CREATE_ACCOUNT", "account", id, "Added account $code $name ($accountType)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/accounts") }

    suspend fun postJournalEntry(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        referenceNo: String,
        entryDate: String,
        period: String,
        description: String,
        currency: String,
        exchangeRateStr: String,
        originalCurrency: String,
        originalAmountStr: String,
        lines: List<FvgFinancialEngine.JournalLineInput>,
        sourceModule: String = "manual",
        status: String = "posted"
    ): Result<String> = runCatching {
        require(status in setOf("draft", "posted")) { "Invalid initial journal status" }
        val (totalDr, totalCr) = FvgFinancialEngine.validateJournalBalance(lines)
        val id = newId("je")
        val lineMaps = lines.map {
            mapOf(
                "accountCode" to it.accountCode,
                "accountName" to it.accountName,
                "debit" to FvgFinancialEngine.money(it.debit).toPlainString(),
                "credit" to FvgFinancialEngine.money(it.credit).toPlainString()
            )
        }
        val data = tenantEnvelope(org, actorUid).apply {
            put("referenceNo", referenceNo.trim())
            put("entryDate", entryDate.trim())
            put("period", period.trim())
            put("description", description.trim())
            put("currency", currency.uppercase())
            put("exchangeRate", FvgFinancialEngine.rate4(FvgFinancialEngine.dOrZero(exchangeRateStr).takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ONE).toPlainString())
            put("originalCurrency", originalCurrency.uppercase())
            put("originalAmount", FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(originalAmountStr)).toPlainString())
            put("status", status)
            put("lines", lineMaps)
            put("totalDebit", totalDr.toPlainString())
            put("totalCredit", totalCr.toPlainString())
            put("sourceModule", sourceModule)
            put("reversedEntryId", "")
        }
        db.collection("organizations").document(org.id).collection("journalEntries").document(id).set(data).await()
        val act = if (status == "draft") "SAVE_DRAFT_JOURNAL" else "POST_JOURNAL_ENTRY"
        logAudit(org, actorUid, actorEmail, act, "journalEntry", id, "$status $referenceNo ($totalDr $currency): $description")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/journalEntries") }

    suspend fun approveAndPostDraftJournalEntry(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        draftEntry: JournalEntryRecord
    ): Result<Unit> = runCatching {
        require(draftEntry.status == "draft") { "Only draft entries can be approved and posted" }
        db.collection("organizations").document(org.id).collection("journalEntries").document(draftEntry.id).update(
            mapOf(
                "status" to "posted",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "POST_DRAFT_JOURNAL", "journalEntry", draftEntry.id, "Posted approved draft ${draftEntry.referenceNo}")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/journalEntries/${draftEntry.id}") }

    suspend fun commitAccountingCsvImport(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        rows: List<FvgFinancialEngine.AccountingCsvRow>,
        accountNames: Map<String, String>
    ): Result<Int> = runCatching {
        val valid = rows.filter { it.error == null }
        require(valid.isNotEmpty()) { "No valid accounting rows to import" }
        val batch = db.batch()
        for (r in valid) {
            val id = newId("je")
            val amtStr = FvgFinancialEngine.money(r.amount).toPlainString()
            val lines = listOf(
                mapOf("accountCode" to r.debitAccountCode, "accountName" to (accountNames[r.debitAccountCode] ?: r.debitAccountCode), "debit" to amtStr, "credit" to "0.00"),
                mapOf("accountCode" to r.creditAccountCode, "accountName" to (accountNames[r.creditAccountCode] ?: r.creditAccountCode), "debit" to "0.00", "credit" to amtStr)
            )
            val jeRef = db.collection("organizations").document(org.id).collection("journalEntries").document(id)
            batch.set(jeRef, tenantEnvelope(org, actorUid).apply {
                put("referenceNo", r.referenceNo)
                put("entryDate", r.entryDate)
                put("period", r.entryDate.take(7))
                put("description", r.description)
                put("currency", org.baseCurrency)
                put("exchangeRate", "1.0000")
                put("originalCurrency", org.baseCurrency)
                put("originalAmount", amtStr)
                put("status", "posted")
                put("lines", lines)
                put("totalDebit", amtStr)
                put("totalCredit", amtStr)
                put("sourceModule", "csv_import")
                put("reversedEntryId", "")
            })
        }
        batch.commit().await()
        logAudit(org, actorUid, actorEmail, "IMPORT_ACCOUNTING_CSV", "journalEntry", org.id, "Atomically imported ${valid.size} balanced journal entries")
        valid.size
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "organizations/${org.id}/journalEntries") }

    suspend fun reverseJournalEntry(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        original: JournalEntryRecord,
        reversalDate: String
    ): Result<String> = runCatching {
        require(original.status == "posted") { "Only posted entries can be reversed" }
        val revLines = original.lines.map { line ->
            val code = line["accountCode"]?.toString().orEmpty()
            val name = line["accountName"]?.toString().orEmpty()
            val dr = FvgFinancialEngine.dOrZero(line["debit"]?.toString())
            val cr = FvgFinancialEngine.dOrZero(line["credit"]?.toString())
            FvgFinancialEngine.JournalLineInput(code, name, debit = cr, credit = dr)
        }
        val revId = postJournalEntry(
            org = org,
            actorUid = actorUid,
            actorEmail = actorEmail,
            referenceNo = "REV-${original.referenceNo}",
            entryDate = reversalDate,
            period = original.period,
            description = "Reversal of ${original.referenceNo}: ${original.description}",
            currency = original.currency,
            exchangeRateStr = original.exchangeRate,
            originalCurrency = original.originalCurrency,
            originalAmountStr = original.originalAmount,
            lines = revLines,
            sourceModule = "reversal"
        ).getOrThrow()
        db.collection("organizations").document(org.id).collection("journalEntries").document(original.id).update(
            mapOf(
                "status" to "reversed",
                "reversedEntryId" to revId,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "REVERSE_JOURNAL_ENTRY", "journalEntry", original.id, "Reversed entry ${original.referenceNo} via $revId")
        revId
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/journalEntries/${original.id}") }

    suspend fun saveCounterpartyItem(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        type: String,
        partyName: String,
        invoiceNo: String,
        currency: String,
        amountStr: String,
        dueDate: String
    ): Result<String> = runCatching {
        val amt = FvgFinancialEngine.money(FvgFinancialEngine.d(amountStr))
        require(amt > BigDecimal.ZERO) { "Invoice amount must be positive" }
        val id = newId("cp")
        val data = tenantEnvelope(org, actorUid).apply {
            put("type", type)
            put("partyName", partyName.trim())
            put("invoiceNo", invoiceNo.trim())
            put("currency", currency.uppercase())
            put("amount", amt.toPlainString())
            put("paidAmount", "0.00")
            put("dueDate", dueDate.trim())
            put("status", "open")
        }
        db.collection("organizations").document(org.id).collection("counterpartyItems").document(id).set(data).await()
        logAudit(org, actorUid, actorEmail, "CREATE_COUNTERPARTY", "counterpartyItem", id, "Recorded $type $invoiceNo ($partyName: $amt $currency)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/counterpartyItems") }

    suspend fun recordCounterpartyPayment(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        item: CounterpartyRecord,
        paymentStr: String
    ): Result<Unit> = runCatching {
        val payment = FvgFinancialEngine.money(FvgFinancialEngine.d(paymentStr))
        require(payment > BigDecimal.ZERO) { "Payment must be positive" }
        val total = FvgFinancialEngine.d(item.amount)
        val prevPaid = FvgFinancialEngine.dOrZero(item.paidAmount)
        val newPaid = FvgFinancialEngine.money(prevPaid.add(payment).min(total))
        val newStatus = if (newPaid.compareTo(total) >= 0) "paid" else "partial"
        db.collection("organizations").document(org.id).collection("counterpartyItems").document(item.id).update(
            mapOf(
                "paidAmount" to newPaid.toPlainString(),
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "RECORD_PAYMENT", "counterpartyItem", item.id, "Paid $payment on ${item.invoiceNo} (status: $newStatus)")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/counterpartyItems/${item.id}") }

    // --- 6. Budgets ---

    suspend fun saveBudget(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        name: String,
        budgetType: String,
        period: String,
        currency: String,
        plannedStr: String,
        actualStr: String,
        explanation: String,
        approveNow: Boolean
    ): Result<String> = runCatching {
        val planned = FvgFinancialEngine.money(FvgFinancialEngine.d(plannedStr))
        val actual = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(actualStr))
        val (variance, varPct) = FvgFinancialEngine.calculateBudgetVariance(planned, actual)
        val id = newId("bdg")
        val data = tenantEnvelope(org, actorUid).apply {
            put("name", name.trim())
            put("budgetType", budgetType)
            put("period", period.trim())
            put("version", 1L)
            put("currency", currency.uppercase())
            put("plannedAmount", planned.toPlainString())
            put("actualAmount", actual.toPlainString())
            put("variance", variance.toPlainString())
            put("variancePct", varPct?.toPlainString() ?: "N/A")
            put("explanation", explanation.trim())
            put("status", if (approveNow) "approved" else "draft")
        }
        db.collection("organizations").document(org.id).collection("budgets").document(id).set(data).await()
        logAudit(org, actorUid, actorEmail, "SAVE_BUDGET", "budget", id, "Budget '$name' ($period): Planned $planned vs Actual $actual")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/budgets") }

    suspend fun updateBudgetActuals(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        budget: BudgetRecord,
        actualStr: String,
        explanation: String,
        newStatus: String
    ): Result<Unit> = runCatching {
        val planned = FvgFinancialEngine.d(budget.plannedAmount)
        val actual = FvgFinancialEngine.money(FvgFinancialEngine.d(actualStr))
        val (variance, varPct) = FvgFinancialEngine.calculateBudgetVariance(planned, actual)
        db.collection("organizations").document(org.id).collection("budgets").document(budget.id).update(
            mapOf(
                "actualAmount" to actual.toPlainString(),
                "variance" to variance.toPlainString(),
                "variancePct" to (varPct?.toPlainString() ?: "N/A"),
                "explanation" to explanation.trim(),
                "status" to newStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "UPDATE_BUDGET_ACTUALS", "budget", budget.id, "Updated budget ${budget.name} actuals to $actual (variance: $variance)")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/budgets/${budget.id}") }

    // --- 7. Inventory, Valuation, Scrap & Stocktakes ---

    suspend fun createInventoryItem(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        sku: String,
        name: String,
        itemType: String,
        unit: String,
        warehouse: String,
        valuationMethod: String,
        openingQtyStr: String,
        openingUnitCostStr: String,
        marketPriceStr: String,
        batchNumber: String = "",
        expiryDate: String = ""
    ): Result<String> = runCatching {
        val qty = FvgFinancialEngine.dOrZero(openingQtyStr)
        val unitCost = FvgFinancialEngine.dOrZero(openingUnitCostStr)
        val market = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(marketPriceStr).takeIf { it > BigDecimal.ZERO } ?: unitCost)
        val itemId = newId("inv")
        val lots = if (qty > BigDecimal.ZERO) {
            listOf(mapOf("lotDate" to "Opening", "quantity" to qty.toPlainString(), "unitCost" to unitCost.toPlainString()))
        } else emptyList()
        val bookVal = FvgFinancialEngine.money(qty.multiply(unitCost))
        val data = tenantEnvelope(org, actorUid).apply {
            put("sku", sku.trim())
            put("name", name.trim())
            put("itemType", itemType)
            put("unit", unit.trim())
            put("warehouse", warehouse.trim().ifBlank { "Main Warehouse" })
            put("currency", org.baseCurrency)
            put("valuationMethod", valuationMethod)
            put("quantityOnHand", qty.setScale(4).toPlainString())
            put("totalBookValue", bookVal.toPlainString())
            put("movingAvgCost", unitCost.setScale(4).toPlainString())
            put("currentMarketPrice", market.toPlainString())
            put("batchNumber", batchNumber.trim().take(60))
            put("expiryDate", expiryDate.trim().take(20))
            put("fifoLots", lots)
        }
        db.collection("organizations").document(org.id).collection("inventoryItems").document(itemId).set(data).await()
        logAudit(org, actorUid, actorEmail, "CREATE_INVENTORY_ITEM", "inventoryItem", itemId, "Created $name ($valuationMethod, qty=$qty)")
        itemId
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/inventoryItems") }

    suspend fun recordInventoryMovement(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        item: InventoryItemRecord,
        movementType: String,
        qtyStr: String,
        unitCostStr: String,
        warehouse: String,
        reason: String,
        referenceNo: String,
        movementDate: String,
        postAccountingEntry: Boolean
    ): Result<String> = runCatching {
        val qty = FvgFinancialEngine.d(qtyStr)
        val currQty = FvgFinancialEngine.dOrZero(item.quantityOnHand)
        val currBook = FvgFinancialEngine.dOrZero(item.totalBookValue)
        val currAvg = FvgFinancialEngine.dOrZero(item.movingAvgCost)
        val currLots = item.fifoLots.map {
            FvgFinancialEngine.FifoLot(
                lotDate = it["lotDate"]?.toString().orEmpty(),
                quantity = FvgFinancialEngine.dOrZero(it["quantity"]?.toString()),
                unitCost = FvgFinancialEngine.dOrZero(it["unitCost"]?.toString())
            )
        }

        val outcome = when (movementType) {
            "receipt", "return" -> {
                val uCost = FvgFinancialEngine.d(unitCostStr)
                FvgFinancialEngine.applyInventoryReceipt(currQty, currBook, currLots, qty, uCost, movementDate)
            }
            "issue", "scrap", "damaged", "expired" -> {
                FvgFinancialEngine.applyInventoryIssue(item.valuationMethod, currQty, currBook, currAvg, currLots, qty)
            }
            "transfer" -> {
                require(qty <= currQty) { "Transfer quantity exceeds quantity on hand" }
                FvgFinancialEngine.InventoryValuationOutcome(currQty, currBook, currAvg, BigDecimal.ZERO.setScale(2), currAvg, currLots)
            }
            else -> {
                val uCost = FvgFinancialEngine.dOrZero(unitCostStr).takeIf { it > BigDecimal.ZERO } ?: currAvg
                FvgFinancialEngine.applyInventoryReceipt(currQty, currBook, currLots, qty, uCost, movementDate)
            }
        }

        val moveId = newId("mov")
        val lotsMap = outcome.remainingLots.map {
            mapOf("lotDate" to it.lotDate, "quantity" to it.quantity.toPlainString(), "unitCost" to it.unitCost.toPlainString())
        }
        val batch = db.batch()
        val itemRef = db.collection("organizations").document(org.id).collection("inventoryItems").document(item.id)
        batch.update(itemRef, mapOf(
            "quantityOnHand" to outcome.newQuantityOnHand.toPlainString(),
            "totalBookValue" to outcome.newTotalBookValue.toPlainString(),
            "movingAvgCost" to outcome.newMovingAvgCost.toPlainString(),
            "fifoLots" to lotsMap,
            "warehouse" to if (movementType == "transfer") warehouse.trim() else item.warehouse,
            "updatedAt" to FieldValue.serverTimestamp()
        ))

        val moveRef = db.collection("organizations").document(org.id).collection("inventoryMovements").document(moveId)
        batch.set(moveRef, tenantEnvelope(org, actorUid).apply {
            put("itemId", item.id)
            put("itemName", item.name)
            put("movementType", movementType)
            put("quantity", qty.toPlainString())
            put("unitCost", outcome.movementUnitCost.toPlainString())
            put("totalCost", outcome.movementTotalCost.toPlainString())
            put("warehouse", warehouse.trim().ifBlank { item.warehouse })
            put("reason", reason.trim().ifBlank { movementType })
            put("referenceNo", referenceNo.trim().ifBlank { moveId })
            put("movementDate", movementDate)
        })
        batch.commit().await()

        if (postAccountingEntry && outcome.movementTotalCost > BigDecimal.ZERO) {
            val lines = when (movementType) {
                "receipt" -> listOf(
                    FvgFinancialEngine.JournalLineInput("1210", "Inventory", outcome.movementTotalCost, BigDecimal.ZERO),
                    FvgFinancialEngine.JournalLineInput("2010", "Accounts Payable", BigDecimal.ZERO, outcome.movementTotalCost)
                )
                "scrap", "damaged", "expired" -> listOf(
                    FvgFinancialEngine.JournalLineInput("6210", "Inventory Loss & Scrap", outcome.movementTotalCost, BigDecimal.ZERO),
                    FvgFinancialEngine.JournalLineInput("1210", "Inventory", BigDecimal.ZERO, outcome.movementTotalCost)
                )
                "issue" -> listOf(
                    FvgFinancialEngine.JournalLineInput("5010", "Cost of Goods Sold", outcome.movementTotalCost, BigDecimal.ZERO),
                    FvgFinancialEngine.JournalLineInput("1210", "Inventory", BigDecimal.ZERO, outcome.movementTotalCost)
                )
                else -> emptyList()
            }
            if (lines.size >= 2) {
                postJournalEntry(
                    org, actorUid, actorEmail, "INV-$referenceNo", movementDate, movementDate.take(7),
                    "Auto-posted inventory $movementType for ${item.name}", org.baseCurrency, "1.0000", org.baseCurrency,
                    outcome.movementTotalCost.toPlainString(), lines, sourceModule = "inventory"
                )
            }
        }
        logAudit(org, actorUid, actorEmail, "INVENTORY_${movementType.uppercase()}", "inventoryMovement", moveId, "$movementType ${qty} ${item.unit} of ${item.name} (${outcome.movementTotalCost} ${org.baseCurrency})")
        moveId
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "organizations/${org.id}/inventoryMovements") }

    suspend fun createStocktakeSession(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        sessionName: String,
        warehouse: String,
        sessionDate: String,
        itemsInScope: List<InventoryItemRecord>,
        physicalCounts: Map<String, String>
    ): Result<String> = runCatching {
        require(itemsInScope.isNotEmpty()) { "Select at least one inventory item for stocktaking" }
        var totalDiscVal = BigDecimal.ZERO
        val lines = itemsInScope.map { item ->
            val sysQty = FvgFinancialEngine.dOrZero(item.quantityOnHand)
            val physQty = FvgFinancialEngine.dOrZero(physicalCounts[item.id] ?: item.quantityOnHand)
            val diffQty = physQty.subtract(sysQty)
            val unitCost = FvgFinancialEngine.dOrZero(item.movingAvgCost)
            val diffVal = FvgFinancialEngine.money(diffQty.multiply(unitCost))
            totalDiscVal = totalDiscVal.add(diffVal)
            mapOf(
                "itemId" to item.id,
                "itemName" to item.name,
                "systemQty" to sysQty.toPlainString(),
                "physicalQty" to physQty.toPlainString(),
                "diffQty" to diffQty.toPlainString(),
                "unitCost" to unitCost.toPlainString(),
                "diffValue" to diffVal.toPlainString()
            )
        }
        val stId = newId("stk")
        db.collection("organizations").document(org.id).collection("stocktakes").document(stId).set(
            tenantEnvelope(org, actorUid).apply {
                put("sessionName", sessionName.trim())
                put("warehouse", warehouse.trim())
                put("sessionDate", sessionDate)
                put("status", "in_review")
                put("lines", lines)
                put("totalDiscrepancyValue", FvgFinancialEngine.money(totalDiscVal).toPlainString())
                put("reviewedBy", "")
            }
        ).await()
        logAudit(org, actorUid, actorEmail, "CREATE_STOCKTAKE", "stocktake", stId, "Submitted stocktake '$sessionName' for review (discrepancy ${FvgFinancialEngine.money(totalDiscVal)})")
        stId
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/stocktakes") }

    suspend fun processStocktakeDecision(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        session: StocktakeSessionRecord,
        allItems: List<InventoryItemRecord>,
        decision: String
    ): Result<Unit> = runCatching {
        require(session.status != "posted") { "Stocktake is already posted" }
        if (decision == "posted") {
            val discVal = FvgFinancialEngine.dOrZero(session.totalDiscrepancyValue)
            for (line in session.lines) {
                val itemId = line["itemId"]?.toString().orEmpty()
                val diffQty = FvgFinancialEngine.dOrZero(line["diffQty"]?.toString())
                val item = allItems.firstOrNull { it.id == itemId } ?: continue
                if (diffQty.compareTo(BigDecimal.ZERO) != 0) {
                    val absQty = diffQty.abs().toPlainString()
                    val moveType = if (diffQty > BigDecimal.ZERO) "adjustment" else "scrap"
                    recordInventoryMovement(
                        org, actorUid, actorEmail, item, moveType, absQty,
                        item.movingAvgCost, session.warehouse,
                        "Stocktake adjustment (${session.sessionName})", session.id, session.sessionDate,
                        postAccountingEntry = false
                    )
                }
            }
            if (discVal.compareTo(BigDecimal.ZERO) != 0) {
                val absVal = FvgFinancialEngine.money(discVal.abs())
                val jeLines = if (discVal < BigDecimal.ZERO) {
                    listOf(
                        FvgFinancialEngine.JournalLineInput("6210", "Inventory Loss & Scrap", absVal, BigDecimal.ZERO),
                        FvgFinancialEngine.JournalLineInput("1210", "Inventory", BigDecimal.ZERO, absVal)
                    )
                } else {
                    listOf(
                        FvgFinancialEngine.JournalLineInput("1210", "Inventory", absVal, BigDecimal.ZERO),
                        FvgFinancialEngine.JournalLineInput("5010", "Cost of Goods Sold", BigDecimal.ZERO, absVal)
                    )
                }
                postJournalEntry(
                    org, actorUid, actorEmail, "STK-${session.id.takeLast(6)}", session.sessionDate,
                    session.sessionDate.take(7), "Posted stocktake variance: ${session.sessionName}",
                    org.baseCurrency, "1.0000", org.baseCurrency, absVal.toPlainString(), jeLines, "stocktake"
                )
            }
        }
        db.collection("organizations").document(org.id).collection("stocktakes").document(session.id).update(
            mapOf(
                "status" to decision,
                "reviewedBy" to actorUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "STOCKTAKE_$decision".uppercase(), "stocktake", session.id, "Stocktake '${session.sessionName}' marked $decision")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/stocktakes/${session.id}") }

    // --- 8. OCR Drafts (Human Review Required, Math + Duplicate Guard) ---

    suspend fun saveOcrDraft(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        fileName: String,
        ocrResult: OcrExtractionResult,
        duplicateWarning: String = ""
    ): Result<String> = runCatching {
        val id = newId("ocr")
        db.collection("organizations").document(org.id).collection("ocrDrafts").document(id).set(
            tenantEnvelope(org, actorUid).apply {
                put("fileName", fileName)
                put("invoiceNumber", ocrResult.invoiceNumber.take(64))
                put("extractedText", ocrResult.extractedText.take(9000))
                put("vendorName", ocrResult.vendorName.take(150))
                put("documentDate", ocrResult.documentDate.take(32))
                put("currency", ocrResult.currency.take(8))
                put("subtotalAmount", ocrResult.subtotalAmount.take(32))
                put("discountAmount", ocrResult.discountAmount.take(32))
                put("taxAmount", ocrResult.taxAmount.take(32))
                put("totalAmount", ocrResult.totalAmount.take(32))
                put("confidenceNote", ocrResult.confidenceNote.take(190))
                put("lineSummary", ocrResult.lineSummary.take(1800))
                put("duplicateWarning", duplicateWarning.take(240))
                put("reviewStatus", "pending_review")
            }
        ).await()
        logAudit(org, actorUid, actorEmail, "CREATE_OCR_DRAFT", "ocrDraft", id, "Extracted OCR draft from $fileName (pending review)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/ocrDrafts") }

    suspend fun reviewOcrDraft(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        draft: OcrDraftRecord,
        vendorName: String,
        invoiceNumber: String,
        documentDate: String,
        currency: String,
        subtotalAmount: String,
        discountAmount: String,
        taxAmount: String,
        totalAmount: String,
        lineSummary: String,
        reviewStatus: String
    ): Result<Unit> = runCatching {
        db.collection("organizations").document(org.id).collection("ocrDrafts").document(draft.id).update(
            mapOf(
                "vendorName" to vendorName.trim(),
                "invoiceNumber" to invoiceNumber.trim(),
                "documentDate" to documentDate.trim(),
                "currency" to currency.trim().uppercase(),
                "subtotalAmount" to subtotalAmount.trim(),
                "discountAmount" to discountAmount.trim(),
                "taxAmount" to taxAmount.trim(),
                "totalAmount" to totalAmount.trim(),
                "lineSummary" to lineSummary.trim(),
                "reviewStatus" to reviewStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "REVIEW_OCR_DRAFT", "ocrDraft", draft.id, "OCR draft ${draft.fileName} marked $reviewStatus")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/ocrDrafts/${draft.id}") }

    // --- 9. Standard Cost Sheets, Production Orders & Scrap Loss Approval ---

    suspend fun createStandardCostSheet(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        productId: String,
        productName: String,
        existingVersions: Long,
        stdMaterialCostStr: String,
        stdLaborHoursStr: String,
        stdLaborRateStr: String,
        stdOverheadPerUnitStr: String,
        notes: String
    ): Result<String> = runCatching {
        val mat = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(stdMaterialCostStr))
        val hrs = FvgFinancialEngine.dOrZero(stdLaborHoursStr)
        val rate = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(stdLaborRateStr))
        val ovh = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(stdOverheadPerUnitStr))
        val unitStd = FvgFinancialEngine.money(mat.add(hrs.multiply(rate)).add(ovh))
        val version = existingVersions + 1L
        val id = newId("std")
        db.collection("organizations").document(org.id).collection("standardCosts").document(id).set(
            tenantEnvelope(org, actorUid).apply {
                put("productId", productId.ifBlank { "prod_custom" })
                put("productName", productName.trim())
                put("version", version)
                put("currency", org.baseCurrency)
                put("standardMaterialCost", mat.toPlainString())
                put("standardLaborHours", hrs.setScale(2, com.example.domain.math.RoundingMode.HALF_UP).toPlainString())
                put("standardLaborRate", rate.toPlainString())
                put("standardOverheadPerUnit", ovh.toPlainString())
                put("standardUnitCost", unitStd.toPlainString())
                put("notes", notes.trim())
            }
        ).await()
        logAudit(org, actorUid, actorEmail, "CREATE_STANDARD_COST", "standardCost", id, "Created Standard Cost v$version for $productName ($unitStd ${org.baseCurrency}/unit)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/standardCosts") }

    suspend fun createProductionOrder(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        productId: String,
        productName: String,
        period: String,
        plannedUnitsStr: String,
        goodUnitsStr: String,
        scrapUnitsStr: String,
        actualMatStr: String,
        actualLabStr: String,
        actualOvhStr: String,
        stageNames: List<String>
    ): Result<String> = runCatching {
        val planned = FvgFinancialEngine.d(plannedUnitsStr)
        val good = FvgFinancialEngine.d(goodUnitsStr)
        val scrap = FvgFinancialEngine.dOrZero(scrapUnitsStr)
        require(planned > BigDecimal.ZERO && good > BigDecimal.ZERO && good <= planned) {
            "Planned and good units must be positive (good <= planned)"
        }
        val actMat = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(actualMatStr))
        val actLab = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(actualLabStr))
        val actOvh = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(actualOvhStr))
        val actTotal = FvgFinancialEngine.money(actMat.add(actLab).add(actOvh))
        val stageList = stageNames.mapIndexed { i, st ->
            mapOf("seq" to (i + 1), "name" to st, "status" to "in_progress")
        }
        val id = newId("po")
        db.collection("organizations").document(org.id).collection("productionOrders").document(id).set(
            tenantEnvelope(org, actorUid).apply {
                put("productId", productId.ifBlank { "prod_custom" })
                put("productName", productName.trim())
                put("period", period.trim())
                put("currency", org.baseCurrency)
                put("plannedUnits", planned.toPlainString())
                put("goodUnits", good.toPlainString())
                put("scrapUnits", scrap.toPlainString())
                put("actualMaterialCost", actMat.toPlainString())
                put("actualLaborCost", actLab.toPlainString())
                put("actualOverheadCost", actOvh.toPlainString())
                put("actualTotalCost", actTotal.toPlainString())
                put("status", "in_progress")
                put("stages", stageList)
            }
        ).await()
        logAudit(org, actorUid, actorEmail, "CREATE_PRODUCTION_ORDER", "productionOrder", id, "Production order $productName ($good/$planned good units, total $actTotal)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/productionOrders") }

    suspend fun completeProductionOrder(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        order: ProductionOrderRecord,
        scrapUnitsStr: String,
        actMatStr: String,
        actLabStr: String,
        actOvhStr: String
    ): Result<Unit> = runCatching {
        val scrap = FvgFinancialEngine.dOrZero(scrapUnitsStr)
        val actMat = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(actMatStr))
        val actLab = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(actLabStr))
        val actOvh = FvgFinancialEngine.money(FvgFinancialEngine.dOrZero(actOvhStr))
        val actTotal = FvgFinancialEngine.money(actMat.add(actLab).add(actOvh))
        val doneStages = order.stages.map { it.toMutableMap().apply { put("status", "completed") } }
        db.collection("organizations").document(org.id).collection("productionOrders").document(order.id).update(
            mapOf(
                "scrapUnits" to scrap.toPlainString(),
                "actualMaterialCost" to actMat.toPlainString(),
                "actualLaborCost" to actLab.toPlainString(),
                "actualOverheadCost" to actOvh.toPlainString(),
                "actualTotalCost" to actTotal.toPlainString(),
                "stages" to doneStages,
                "status" to "completed",
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "COMPLETE_PRODUCTION_ORDER", "productionOrder", order.id, "Completed order ${order.productName} (scrap $scrap, cost $actTotal)")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/productionOrders/${order.id}") }

    suspend fun submitScrapLossRequest(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        item: InventoryItemRecord,
        lossType: String,
        qtyStr: String,
        reason: String,
        lossDate: String
    ): Result<String> = runCatching {
        val qty = FvgFinancialEngine.d(qtyStr)
        require(qty > BigDecimal.ZERO) { "Scrap/loss quantity must be positive" }
        val unitCost = FvgFinancialEngine.dOrZero(item.movingAvgCost)
        val totalLoss = FvgFinancialEngine.money(qty.multiply(unitCost))
        val id = newId("scr")
        db.collection("organizations").document(org.id).collection("scrapLosses").document(id).set(
            tenantEnvelope(org, actorUid).apply {
                put("itemId", item.id)
                put("itemName", item.name)
                put("lossType", lossType)
                put("quantity", qty.toPlainString())
                put("unitCost", unitCost.toPlainString())
                put("totalLossCost", totalLoss.toPlainString())
                put("reason", reason.trim())
                put("lossDate", lossDate)
                put("status", "pending_approval")
                put("reviewedBy", "")
            }
        ).await()
        logAudit(org, actorUid, actorEmail, "SUBMIT_SCRAP_LOSS", "scrapLoss", id, "Submitted $lossType for ${item.name} ($qty ${item.unit}, est $totalLoss) awaiting admin approval")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/scrapLosses") }

    suspend fun reviewScrapLossRequest(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        scrap: ScrapLossRecord,
        item: InventoryItemRecord?,
        decision: String
    ): Result<Unit> = runCatching {
        require(scrap.status == "pending_approval") { "Scrap loss request is already resolved" }
        require(decision in setOf("approved", "rejected")) { "Invalid decision" }
        if (decision == "approved" && item != null) {
            recordInventoryMovement(
                org = org,
                actorUid = actorUid,
                actorEmail = actorEmail,
                item = item,
                movementType = scrap.lossType,
                qtyStr = scrap.quantity,
                unitCostStr = scrap.unitCost,
                warehouse = item.warehouse,
                reason = "Approved ${scrap.lossType}: ${scrap.reason}",
                referenceNo = "SCR-${scrap.id.takeLast(6)}",
                movementDate = scrap.lossDate,
                postAccountingEntry = true
            ).getOrThrow()
        }
        db.collection("organizations").document(org.id).collection("scrapLosses").document(scrap.id).update(
            mapOf(
                "status" to decision,
                "reviewedBy" to actorUid,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, actorUid, actorEmail, "REVIEW_SCRAP_$decision".uppercase(), "scrapLoss", scrap.id, "Scrap loss ${scrap.itemName} (${scrap.quantity}) marked $decision")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}/scrapLosses/${scrap.id}") }

    // --- 10. Payment Requests (Bankak / O-Cash / Bank Transfer Manual Verification) ---

    suspend fun submitPaymentRequest(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        planTier: String,
        paymentMethod: String,
        amountStr: String,
        currency: String,
        senderName: String,
        referenceNo: String,
        transferDate: String,
        notes: String,
        last4Digits: String = "",
        attachmentNote: String = "",
        receiptMatchStatus: String = "",
        receiptMatchNote: String = "",
        expectedPlanAmount: String = ""
    ): Result<String> = runCatching {
        val cleanPlan = planTier.trim().lowercase()
        require(cleanPlan.matches(Regex("^[a-zA-Z0-9_\\-]+$")) && cleanPlan != "trial") { "Invalid plan tier" }
        require(paymentMethod in setOf("bankak", "ocash", "fawry", "bank_transfer")) { "Invalid payment method" }
        val cleanLast4 = last4Digits.filter { it.isDigit() }.takeLast(4)
        require(senderName.isNotBlank() && (referenceNo.trim().length >= 2 || cleanLast4.length == 4)) {
            "Sender name and reference / last 4 digits are required"
        }
        val amt = FvgFinancialEngine.money(FvgFinancialEngine.d(amountStr))
        require(amt > BigDecimal.ZERO) { "Transfer amount must be positive" }
        val id = newId("pay")
        db.collection("organizations").document(org.id).collection("paymentRequests").document(id).set(
            tenantEnvelope(org, actorUid).apply {
                put("planTier", planTier)
                put("paymentMethod", paymentMethod)
                put("amount", amt.toPlainString())
                put("currency", currency.uppercase())
                put("senderName", senderName.trim())
                put("referenceNo", referenceNo.trim().ifBlank { "L4-$cleanLast4" })
                put("last4Digits", cleanLast4)
                put("transferDate", transferDate.trim())
                put("attachmentNote", attachmentNote.trim().take(240))
                put("notes", notes.trim().take(500))
                put("receiptMatchStatus", receiptMatchStatus.trim().take(40))
                put("receiptMatchNote", receiptMatchNote.trim().take(280))
                put("expectedPlanAmount", expectedPlanAmount.trim().take(40))
                put("status", "pending_verification")
                put("reviewedBy", "")
                put("decisionActorEmail", "")
                put("reviewNotes", "")
            }
        ).await()
        logAudit(org, actorUid, actorEmail, "SUBMIT_PAYMENT_REQUEST", "paymentRequest", id, "Submitted $paymentMethod proof ($referenceNo / *$cleanLast4, $amt $currency) for $planTier [${receiptMatchStatus.ifBlank { "manual" }}]")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "organizations/${org.id}/paymentRequests") }

    // --- 11. BOM Formulas & Recipes ---

    suspend fun saveBomFormula(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        existingId: String?,
        productName: String,
        outputUnit: String,
        batchSize: String,
        ingredients: List<Map<String, Any>>,
        unitMaterialCost: String,
        scrapAllowancePct: String,
        notes: String
    ): Result<String> = runCatching {
        require(productName.isNotBlank() && ingredients.isNotEmpty()) { "Product name and ingredients required" }
        val id = existingId?.takeIf { it.isNotBlank() } ?: newId("bom")
        val ref = db.collection("organizations").document(org.id).collection("bomFormulas").document(id)
        if (existingId.isNullOrBlank()) {
            ref.set(
                tenantEnvelope(org, actorUid).apply {
                    put("productName", productName.trim().take(140))
                    put("outputUnit", outputUnit.trim().ifBlank { "kg" })
                    put("batchSize", batchSize.trim().ifBlank { "1" })
                    put("ingredients", ingredients)
                    put("unitMaterialCost", unitMaterialCost.trim())
                    put("scrapAllowancePct", scrapAllowancePct.trim().ifBlank { "0.00" })
                    put("notes", notes.trim().take(400))
                }
            ).await()
        } else {
            ref.update(
                mapOf(
                    "productName" to productName.trim().take(140),
                    "outputUnit" to outputUnit.trim().ifBlank { "kg" },
                    "batchSize" to batchSize.trim().ifBlank { "1" },
                    "ingredients" to ingredients,
                    "unitMaterialCost" to unitMaterialCost.trim(),
                    "scrapAllowancePct" to scrapAllowancePct.trim().ifBlank { "0.00" },
                    "notes" to notes.trim().take(400),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        logAudit(org, actorUid, actorEmail, "SAVE_BOM_FORMULA", "bomFormula", id, "Saved BOM '$productName' ($unitMaterialCost ${org.baseCurrency}/$outputUnit)")
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "organizations/${org.id}/bomFormulas") }

    suspend fun deleteBomFormula(org: Organization, actorUid: String, actorEmail: String, bomId: String): Result<Unit> = runCatching {
        db.collection("organizations").document(org.id).collection("bomFormulas").document(bomId).delete().await()
        logAudit(org, actorUid, actorEmail, "DELETE_BOM_FORMULA", "bomFormula", bomId, "Deleted BOM formula $bomId")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "organizations/${org.id}/bomFormulas/$bomId") }

    // --- 12. Financial Assistant Persistent Conversations ---

    suspend fun saveAiConversation(
        org: Organization,
        actorUid: String,
        convId: String?,
        title: String,
        mode: String,
        messages: List<Map<String, Any>>
    ): Result<String> = runCatching {
        val id = convId?.takeIf { it.isNotBlank() } ?: newId("chat")
        val ref = db.collection("organizations").document(org.id).collection("aiConversations").document(id)
        val snap = ref.get().await()
        if (!snap.exists()) {
            ref.set(
                tenantEnvelope(org, actorUid).apply {
                    put("title", title.trim().take(120).ifBlank { "Financial Analysis" })
                    put("mode", mode)
                    put("messages", messages.takeLast(60))
                }
            ).await()
        } else {
            ref.update(
                mapOf(
                    "title" to title.trim().take(120).ifBlank { "Financial Analysis" },
                    "mode" to mode,
                    "messages" to messages.takeLast(60),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "organizations/${org.id}/aiConversations") }

    suspend fun deleteAiConversation(org: Organization, convId: String): Result<Unit> = runCatching {
        db.collection("organizations").document(org.id).collection("aiConversations").document(convId).delete().await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "organizations/${org.id}/aiConversations/$convId") }

    // --- 13. Support Tickets ---

    suspend fun submitSupportTicket(
        actorUid: String,
        senderName: String,
        senderEmail: String,
        orgId: String,
        orgName: String,
        message: String
    ): Result<String> {
        val id = newId("sup")
        return runCatching {
            require(message.isNotBlank()) { "Support message is required" }
            db.collection("supportTickets").document(id).set(
                mapOf(
                    "createdBy" to actorUid,
                    "senderName" to senderName.trim().take(100),
                    "senderEmail" to senderEmail.trim().take(120),
                    "orgId" to orgId,
                    "orgName" to orgName.trim().take(120),
                    "message" to message.trim().take(2000),
                    "status" to "open",
                    "ownerReply" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            id
        }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "supportTickets/$id") }
    }

    // --- 14. Platform Owner Admin Console (Server-Enforced to Verified Financialvisionguide@gmail.com) ---

    fun observeAllPaymentRequestsOwner(): Flow<List<PaymentRequestRecord>> =
        db.collectionGroup("paymentRequests").snapshots()
            .map { snap -> snap.toObjects(PaymentRequestRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun observeAllOrganizationsOwner(): Flow<List<Organization>> =
        db.collection("organizations").snapshots()
            .map { snap -> snap.toObjects(Organization::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun observeAllUsersOwner(): Flow<List<UserProfile>> =
        db.collection("users").snapshots()
            .map { snap -> snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun observeSupportTicketsOwner(): Flow<List<SupportTicketRecord>> =
        db.collection("supportTickets").snapshots()
            .map { snap -> snap.toObjects(SupportTicketRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    /**
     * Transactional idempotent verification: only approves/rejects a payment request when its
     * server-side status is still pending_verification, preventing double-processing.
     */
    suspend fun reviewPaymentRequestAsOwner(
        request: PaymentRequestRecord,
        decision: String, // "approved" or "rejected"
        reviewNotes: String,
        ownerUid: String,
        ownerEmail: String
    ): Result<Unit> = runCatching {
        require(decision in setOf("approved", "rejected")) { "Invalid review decision" }
        val reqRef = db.collection("organizations").document(request.orgId)
            .collection("paymentRequests").document(request.id)
        val orgRef = db.collection("organizations").document(request.orgId)
        val logRef = orgRef.collection("auditLogs").document(newId("log"))

        db.runTransaction { tx ->
            val reqSnap = tx.get(reqRef)
            val currentStatus = reqSnap.getString("status") ?: ""
            require(currentStatus == "pending_verification") {
                "Payment request ${request.referenceNo} was already processed ($currentStatus)"
            }
            val orgSnap = tx.get(orgRef)
            val orgOwnerId = orgSnap.getString("ownerId") ?: request.ownerId
            @Suppress("UNCHECKED_CAST")
            val orgMembers = (orgSnap.get("memberUids") as? List<String>) ?: request.memberUids

            tx.update(
                reqRef,
                mapOf(
                    "status" to decision,
                    "reviewedBy" to ownerUid,
                    "decisionActorEmail" to ownerEmail,
                    "reviewNotes" to reviewNotes.trim().take(500),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            if (decision == "approved") {
                tx.update(
                    orgRef,
                    mapOf(
                        "planTier" to request.planTier,
                        "subscriptionStatus" to "active",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            tx.set(
                logRef,
                mapOf(
                    "orgId" to request.orgId,
                    "ownerId" to orgOwnerId,
                    "memberUids" to orgMembers,
                    "createdBy" to ownerUid,
                    "action" to "OWNER_PAYMENT_$decision".uppercase(),
                    "entityType" to "paymentRequest",
                    "entityId" to request.id,
                    "summary" to "Platform owner verified ${request.referenceNo}: $decision (${reviewNotes.trim()})",
                    "actorEmail" to ownerEmail,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${request.orgId}/paymentRequests/${request.id}") }

    suspend fun ownerUpdateOrgSubscription(
        org: Organization,
        planTier: String,
        subscriptionStatus: String,
        newTrialEndsAtMillis: Long?,
        reason: String,
        ownerUid: String,
        ownerEmail: String
    ): Result<Unit> = runCatching {
        val updates = mutableMapOf<String, Any>(
            "planTier" to planTier,
            "subscriptionStatus" to subscriptionStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (newTrialEndsAtMillis != null) {
            updates["trialEndsAt"] = Timestamp(java.util.Date(newTrialEndsAtMillis))
        }
        db.collection("organizations").document(org.id).update(updates).await()
        logAudit(org, ownerUid, ownerEmail, "OWNER_SUBSCRIPTION_OVERRIDE", "organization", org.id, "Owner set $planTier / $subscriptionStatus ($reason)")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}") }

    fun observePlatformVisitsOwner(): Flow<List<PlatformVisitRecord>> =
        db.collection("platformVisits").snapshots()
            .map { snap -> snap.toObjects(PlatformVisitRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun observeMailOutboxOwner(): Flow<List<MailOutboxRecord>> =
        db.collection("mail").snapshots()
            .map { snap -> snap.toObjects(MailOutboxRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    fun observeAdminNotificationsOwner(): Flow<List<AdminNotificationRecord>> =
        db.collection("adminNotifications").snapshots()
            .map { snap ->
                snap.toObjects(AdminNotificationRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
            }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                emit(emptyList())
            }

    suspend fun markAdminNotificationAcknowledged(notifId: String, acknowledged: Boolean): Result<Unit> = runCatching {
        require(notifId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "Invalid notification ID" }
        db.collection("adminNotifications").document(notifId).update(
            mapOf(
                "acknowledged" to acknowledged,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "adminNotifications/$notifId") }

    suspend fun markAllAdminNotificationsAcknowledged(notifIds: List<String>): Result<Unit> = runCatching {
        val validIds = notifIds.filter { it.matches(Regex("^[a-zA-Z0-9_\\-]+$")) }.take(100)
        if (validIds.isEmpty()) return@runCatching
        val batch = db.batch()
        validIds.forEach { id ->
            val ref = db.collection("adminNotifications").document(id)
            batch.update(ref, mapOf("acknowledged" to true, "updatedAt" to FieldValue.serverTimestamp()))
        }
        batch.commit().await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "adminNotifications") }

    suspend fun deleteAdminNotification(notifId: String): Result<Unit> = runCatching {
        require(notifId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "Invalid notification ID" }
        db.collection("adminNotifications").document(notifId).delete().await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "adminNotifications/$notifId") }

    suspend fun reportSecurityAlert(
        signalType: String,
        severity: String,
        titleEn: String,
        titleAr: String,
        bodyEn: String,
        bodyAr: String,
        actorUid: String = "",
        actorEmail: String = "",
        orgId: String = ""
    ): Result<Unit> = runCatching {
        val id = newId("sec")
        db.collection("adminNotifications").document(id).set(
            mapOf(
                "category" to "security_alert",
                "severity" to severity.take(20),
                "signalType" to signalType.take(80),
                "titleEn" to titleEn.take(180),
                "titleAr" to titleAr.take(180),
                "bodyEn" to bodyEn.take(1000),
                "bodyAr" to bodyAr.take(1000),
                "actorUid" to actorUid.take(128),
                "actorEmail" to actorEmail.take(200),
                "orgId" to orgId.take(128),
                "sourcePlatform" to "android",
                "acknowledged" to false,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) Log.w("FvgRepo", "Skipped remote security alert", it) }

    suspend fun queueOwnerNotificationEmail(
        toEmails: List<String>,
        subject: String,
        bodyText: String,
        actorUid: String
    ): Result<String> = runCatching {
        val cleanRecipients = toEmails.map { it.trim() }.filter { it.contains("@") }.distinct().take(50)
        require(cleanRecipients.isNotEmpty()) { "At least one valid recipient email is required" }
        val id = newId("mail")
        val signedBody = "$bodyText\n\n--\nالمالكة زهراء يوسف | Zahra Yousif\nFinancialvisionguide@gmail.com | 00249911569966"
        db.collection("mail").document(id).set(
            mapOf(
                "from" to "Financialvisionguide@gmail.com",
                "to" to cleanRecipients,
                "createdBy" to actorUid,
                "mailType" to "notification",
                "message" to mapOf(
                    "subject" to subject.trim().take(180),
                    "text" to signedBody
                ),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "mail") }

    suspend fun savePlatformPlan(plan: PlatformPlanRecord): Result<String> = runCatching {
        val id = plan.id.ifBlank { plan.key.ifBlank { newId("plan") } }
        db.collection("platformPlans").document(id).set(
            mapOf(
                "key" to plan.key,
                "tierLevel" to plan.tierLevel.coerceIn(0L, 10L),
                "nameEn" to plan.nameEn.trim(),
                "nameAr" to plan.nameAr.trim(),
                "priceUsd" to plan.priceUsd.trim(),
                "priceSdg" to plan.priceSdg.trim(),
                "billingCycle" to plan.billingCycle,
                "maxOrgs" to plan.maxOrgs,
                "maxUsers" to plan.maxUsers,
                "maxProducts" to plan.maxProducts,
                "allowAllCostingMethods" to plan.allowAllCostingMethods,
                "allowBomAndProduction" to plan.allowBomAndProduction,
                "allowAiAssistant" to plan.allowAiAssistant,
                "allowOcr" to plan.allowOcr,
                "allowPdfXlsxExport" to plan.allowPdfXlsxExport,
                "allowAuditAndBackup" to plan.allowAuditAndBackup,
                "allowSectorAdvancedTools" to plan.allowSectorAdvancedTools,
                "active" to plan.active,
                "notesEn" to plan.notesEn.trim(),
                "notesAr" to plan.notesAr.trim(),
                "featuresEn" to plan.featuresEn.trim(),
                "featuresAr" to plan.featuresAr.trim(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "platformPlans") }

    suspend fun saveAnnouncement(ann: AppAnnouncementRecord, ownerEmail: String): Result<String> = runCatching {
        val id = ann.id.ifBlank { newId("ann") }
        db.collection("announcements").document(id).set(
            mapOf(
                "titleEn" to ann.titleEn.trim(),
                "titleAr" to ann.titleAr.trim(),
                "bodyEn" to ann.bodyEn.trim(),
                "bodyAr" to ann.bodyAr.trim(),
                "targetAudience" to ann.targetAudience,
                "targetEmail" to ann.targetEmail.trim(),
                "active" to ann.active,
                "createdByEmail" to ownerEmail,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.WRITE, "announcements") }

    suspend fun deletePlatformPlan(planId: String): Result<Unit> = runCatching {
        require(planId.matches(Regex("^[a-zA-Z0-9_\\-]+$"))) { "Invalid plan ID" }
        db.collection("platformPlans").document(planId).delete().await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.DELETE, "platformPlans/$planId") }

    suspend fun ownerUpdateOrgMemberRole(
        org: Organization,
        memberUid: String,
        newRole: String,
        ownerUid: String,
        ownerEmail: String
    ): Result<Unit> = runCatching {
        val cleanUid = memberUid.trim()
        require(cleanUid.isNotBlank()) { "Member UID required" }
        require(cleanUid != org.ownerId) { "Organization owner role is immutable" }
        require(newRole in setOf("admin", "accountant", "viewer")) { "Role must be admin, accountant, or viewer" }
        val updatedRoles = org.roles.toMutableMap().apply { put(cleanUid, newRole) }
        val updatedMembers = (org.memberUids + cleanUid).distinct()
        db.collection("organizations").document(org.id).update(
            mapOf(
                "memberUids" to updatedMembers,
                "roles" to updatedRoles,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, ownerUid, ownerEmail, "OWNER_RBAC_UPDATE", "organization", org.id, "Platform owner assigned $newRole to $cleanUid")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}") }

    suspend fun ownerRemoveOrgMember(
        org: Organization,
        memberUid: String,
        ownerUid: String,
        ownerEmail: String
    ): Result<Unit> = runCatching {
        val cleanUid = memberUid.trim()
        require(cleanUid.isNotBlank()) { "Member UID required" }
        require(cleanUid != org.ownerId) { "Organization owner cannot be removed" }
        val updatedRoles = org.roles.toMutableMap().apply { remove(cleanUid) }
        val updatedMembers = org.memberUids.filterNot { it == cleanUid }
        db.collection("organizations").document(org.id).update(
            mapOf(
                "memberUids" to updatedMembers,
                "roles" to updatedRoles,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        logAudit(org, ownerUid, ownerEmail, "OWNER_RBAC_REMOVE", "organization", org.id, "Platform owner removed member $cleanUid")
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "organizations/${org.id}") }

    suspend fun resendWelcomeEmailAsOwner(
        user: UserProfile,
        ownerUid: String
    ): Result<String> = runCatching {
        val targetEmail = user.email.trim()
        require(targetEmail.contains("@")) { "User has no valid email address" }
        val display = user.displayName.ifBlank { targetEmail.substringBefore("@") }
        val id = newId("mail_welcome_resend")
        val isAr = user.preferredLanguage == "ar"
        val subject = if (isAr) {
            "أهلاً بك في دليل رؤيتك المالي (FVG) — رسالتك الترحيبية من المالكة زهراء يوسف"
        } else {
            "Welcome to Financial Vision Guide (FVG) — From Owner Zahra Yousif"
        }
        val body = if (isAr) {
            """
            مرحباً $display،

            يسعدني جداً انضمامك إلى منصة دليل رؤيتك المالي (FVG — Financial Vision Guide)، شريكك المحاسبي والمالي الموثوق لإدارة الأسعار، التضخم، طرق التكاليف الـ 8، القوائم المالية، والمخزون بدقة واحترافية.

            • تم تفعيل فترة تجربتك المجانية الكاملة لمدة 7 أيام لتجربة جميع الأدوات.
            • لأي استفسار أو ترقية باقة أو دعم فني يسعدنا تواصلك معنا في أي وقت عبر البريد أو الواتساب: 00249911569966.

            مع خالص التقدير والنجاح لمشروعك،
            المالكة زهراء يوسف
            Financialvisionguide@gmail.com | 00249911569966
            """.trimIndent()
        } else {
            """
            Welcome $display,

            We are delighted to welcome you to Financial Vision Guide (FVG) — your precision companion for Price & Inflation Monitoring, 8 Costing Methods, Double-Entry Statements, Inventory FIFO/MWA, and AI Financial Analysis.

            • Your full 7-day free trial is active so you can explore the complete platform.
            • For any question, subscription activation, or guidance, reach us anytime at 00249911569966.

            Warm regards,
            المالكة زهراء يوسف (Owner Zahra Yousif)
            Financialvisionguide@gmail.com | 00249911569966
            """.trimIndent()
        }
        db.collection("mail").document(id).set(
            mapOf(
                "from" to "Financialvisionguide@gmail.com",
                "to" to listOf(targetEmail),
                "createdBy" to ownerUid,
                "mailType" to "welcome",
                "message" to mapOf(
                    "subject" to subject,
                    "text" to body
                ),
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).await()
        id
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.CREATE, "mail") }

    suspend fun replySupportTicket(ticketId: String, status: String, ownerReply: String): Result<Unit> = runCatching {
        db.collection("supportTickets").document(ticketId).update(
            mapOf(
                "status" to status,
                "ownerReply" to ownerReply.trim(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
        Unit
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.UPDATE, "supportTickets/$ticketId") }

    suspend fun fetchOrgAuditLogsOwner(orgId: String): Result<List<AuditLogRecord>> = runCatching {
        require(orgId.isNotBlank()) { "Organization ID is required" }
        val snap = db.collection("organizations")
            .document(orgId)
            .collection("auditLogs")
            .limit(80)
            .get()
            .await()
        snap.toObjects(AuditLogRecord::class.java)
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }.onFailure { if (it !is CancellationException) handleFirestoreError(it, OperationType.LIST, "organizations/$orgId/auditLogs") }

    suspend fun fetchOrganizationBranchFinancials(org: Organization, uid: String): FvgFinancialEngine.BranchFinancialInput {
        val accSnap = db.collection("organizations").document(org.id).collection("accounts")
            .whereArrayContains("memberUids", uid).get().await()
        val jeSnap = db.collection("organizations").document(org.id).collection("journalEntries")
            .whereArrayContains("memberUids", uid).get().await()
        val scrapSnap = db.collection("organizations").document(org.id).collection("scrapLosses")
            .whereArrayContains("memberUids", uid).get().await()

        val accs = accSnap.toObjects(AccountRecord::class.java)
        val jes = jeSnap.toObjects(JournalEntryRecord::class.java)
        val scraps = scrapSnap.toObjects(ScrapLossRecord::class.java)
        val totalScrap = scraps.filter { it.status == "approved" }
            .fold(BigDecimal.ZERO) { acc, item -> acc.add(FvgFinancialEngine.dOrZero(item.totalLossCost)) }

        val report = if (accs.isNotEmpty()) {
            val accInputs = accs.map {
                FvgFinancialEngine.AccountInput(
                    code = it.code,
                    name = it.name,
                    accountType = it.accountType,
                    subType = it.subType,
                    normalBalance = it.normalBalance,
                    openingBalance = FvgFinancialEngine.dOrZero(it.openingBalance)
                )
            }
            val postedLines = jes.filter { it.status == "posted" || it.status == "reversed" }.map { je ->
                je.lines.map { l ->
                    FvgFinancialEngine.JournalLineInput(
                        accountCode = l["accountCode"]?.toString().orEmpty(),
                        accountName = l["accountName"]?.toString().orEmpty(),
                        debit = FvgFinancialEngine.dOrZero(l["debit"]?.toString()),
                        credit = FvgFinancialEngine.dOrZero(l["credit"]?.toString())
                    )
                }
            }
            FvgFinancialEngine.generateFinancialStatements(accInputs, postedLines)
        } else null

        return FvgFinancialEngine.BranchFinancialInput(
            orgId = org.id,
            branchName = org.name,
            currency = org.baseCurrency,
            revenue = report?.revenue ?: BigDecimal.ZERO,
            cogs = report?.cogs ?: BigDecimal.ZERO,
            opEx = report?.operatingExpenses ?: BigDecimal.ZERO,
            scrapLoss = FvgFinancialEngine.money(totalScrap),
            assets = report?.totalAssets ?: BigDecimal.ZERO,
            liabilities = report?.totalLiabilities ?: BigDecimal.ZERO
        )
    }

    suspend fun readDocumentUpdatedAtMillis(orgId: String, subcollection: String, docId: String): Pair<Long, String>? {
        if (orgId.isBlank() || subcollection.isBlank() || docId.isBlank()) return null
        return runCatching {
            val snap = db.collection("organizations").document(orgId)
                .collection(subcollection).document(docId).get().await()
            if (!snap.exists()) return null
            val ts = snap.getTimestamp("updatedAt")?.toDate()?.time ?: 0L
            val summary = when (subcollection) {
                "products" -> "${snap.getString("name").orEmpty()}: ${snap.getString("currentPrice").orEmpty()} ${snap.getString("currency").orEmpty()}"
                "inventoryItems" -> "${snap.getString("name").orEmpty()}: qty=${snap.getString("quantityOnHand").orEmpty()}"
                else -> docId
            }
            ts to summary
        }.getOrNull()
    }

    suspend fun recordOfflineSyncAudit(
        org: Organization,
        actorUid: String,
        actorEmail: String,
        action: String,
        entityType: String,
        entityId: String,
        summary: String
    ) {
        logAudit(org, actorUid, actorEmail, action, entityType, entityId, summary)
    }
}

