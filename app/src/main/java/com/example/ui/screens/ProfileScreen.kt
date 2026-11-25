package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.export.FvgExportService
import com.example.ui.auth.AuthHelper
import com.example.ui.components.*
import com.example.ui.i18n.fvgStrings
import com.example.ui.theme.*
import com.example.ui.viewmodel.FvgUiState
import com.example.ui.viewmodel.FvgViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun cropCenterSquareAndCompress(source: Bitmap): Pair<Bitmap, String> {
    val size = minOf(source.width, source.height).coerceAtLeast(1)
    val x = ((source.width - size) / 2).coerceAtLeast(0)
    val y = ((source.height - size) / 2).coerceAtLeast(0)
    val square = Bitmap.createBitmap(source, x, y, size, size)
    val scaled = Bitmap.createScaledBitmap(square, 160, 160, true)
    // Re-encoding strips all EXIF metadata safely
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 76, out)
    val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    return scaled to b64
}

@Composable
fun ProfileScreen(
    state: FvgUiState,
    viewModel: FvgViewModel,
    isArabic: Boolean,
    onBack: () -> Unit,
    onSignOutClick: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = remember { FirebaseAuth.getInstance() }
    val s = fvgStrings(isArabic)
    val profile = state.userProfile
    val firebaseUser = auth.currentUser

    var displayName by remember(profile?.displayName) {
        mutableStateOf(profile?.displayName ?: viewModel.userDisplayName)
    }
    var newEmailInput by remember(profile?.email) {
        mutableStateOf(firebaseUser?.email ?: profile?.email ?: viewModel.userEmail)
    }
    var preferredLanguage by remember(profile?.preferredLanguage) {
        mutableStateOf(profile?.preferredLanguage ?: if (isArabic) "ar" else "en")
    }
    var themeMode by remember(profile?.themeMode) {
        mutableStateOf(profile?.themeMode ?: "light")
    }
    var fontScale by remember(profile?.fontScale) {
        mutableFloatStateOf(profile?.fontScale ?: 1.0f)
    }
    var timezone by remember(profile?.timezone) {
        mutableStateOf(profile?.timezone ?: "Africa/Khartoum")
    }
    var dateFormat by remember(profile?.dateFormat) {
        mutableStateOf(profile?.dateFormat ?: "yyyy-MM-dd")
    }
    var numberFormat by remember(profile?.numberFormat) {
        mutableStateOf(profile?.numberFormat ?: "en-US")
    }
    var defaultCurrency by remember(profile?.defaultCurrency) {
        mutableStateOf(profile?.defaultCurrency ?: "SDG")
    }
    var contactPhoneOptional by remember(profile?.contactPhoneOptional) {
        mutableStateOf(profile?.contactPhoneOptional ?: "")
    }
    var notificationsEnabled by remember(profile?.notificationsEnabled) {
        mutableStateOf(profile?.notificationsEnabled ?: true)
    }
    var avatarBase64 by remember(profile?.avatarLocalRef) {
        mutableStateOf(profile?.avatarLocalRef)
    }
    var previewBitmap by remember(avatarBase64) {
        mutableStateOf(
            avatarBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
                runCatching {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }.getOrNull()
            }
        )
    }
    var accountNotice by remember { mutableStateOf<String?>(null) }

    val cameraAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) {
            val (scaled, b64) = cropCenterSquareAndCompress(bmp)
            previewBitmap = scaled
            avatarBase64 = b64
            accountNotice = if (isArabic) "تم التقاط وضغط الصورة الشخصية. اضغط حفظ التعديلات." else "Avatar captured & compressed (EXIF stripped). Tap Save Preferences."
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraAvatarLauncher.launch(null)
        } else {
            accountNotice = if (isArabic) "تم رفض إذن الكاميرا؛ يمكنك اختيار صورة من المعرض." else "Camera permission denied; use Photo Picker instead."
        }
    }

    val photoPickerAvatarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            runCatching {
                @Suppress("DEPRECATION")
                val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                val (scaled, b64) = cropCenterSquareAndCompress(bmp)
                previewBitmap = scaled
                avatarBase64 = b64
                accountNotice = if (isArabic) "تم تجهيز الصورة. اضغط حفظ لتثبيتها." else "Photo ready & compressed. Tap Save Preferences."
            }.onFailure {
                accountNotice = it.localizedMessage
            }
        }
    }

    val exportAccountLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                val obj = JSONObject().apply {
                    put("userId", viewModel.currentUserId)
                    put("displayName", displayName)
                    put("email", viewModel.userEmail)
                    put("preferredLanguage", preferredLanguage)
                    put("themeMode", themeMode)
                    put("fontScale", fontScale)
                    put("defaultCurrency", defaultCurrency)
                    put("timezone", timezone)
                    put("exportedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
                    put("organizationsCount", state.organizations.size)
                }
                FvgExportService.writeTextToUri(context, uri, obj.toString(2))
                accountNotice = if (isArabic) "تم تصدير ملف بيانات الحساب JSON بنجاح." else "Account profile exported cleanly (JSON)."
            }.onFailure { accountNotice = it.localizedMessage }
        }
    }

    Column(modifier = Modifier.fillMaxSize().testTag("profile_screen")) {
        Surface(
            color = FvgDeepGreen,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("profile_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = s.backHome, tint = FvgWarmGold)
                    }
                    Text(
                        text = s.profileTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = FvgWarmIvory,
                        fontWeight = FontWeight.Bold
                    )
                }
                FvgRoleBadge(state.currentUserRole)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar & Identity Card
            item {
                FvgCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            border = BorderStroke(2.dp, FvgWarmGold),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(78.dp)
                        ) {
                            if (previewBitmap != null) {
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = if (isArabic) "صورة الملف الشخصي" else "Profile Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = displayName.trim().take(2).uppercase().ifBlank { "FV" },
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(viewModel.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val verified = firebaseUser?.isEmailVerified == true
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (verified) FvgPositive.copy(alpha = 0.14f) else FvgWarning.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = if (verified) {
                                        if (isArabic) "بريد إلكتروني موثق ✓" else "Email Verified ✓"
                                    } else {
                                        if (isArabic) "بانتظار توثيق البريد" else "Email Unverified"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (verified) FvgPositive else FvgWarning,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    cameraAvatarLauncher.launch(null)
                                } else {
                                    cameraPermLauncher.launch(Manifest.permission.CAMERA)
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isArabic) "كاميرا" else "Camera")
                        }
                        OutlinedButton(
                            onClick = {
                                photoPickerAvatarLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (isArabic) "المعرض" else "Gallery")
                        }
                        if (previewBitmap != null) {
                            IconButton(
                                onClick = {
                                    previewBitmap?.let { bmp ->
                                        val m = Matrix().apply { postRotate(90f) }
                                        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                                        val (scaled, b64) = cropCenterSquareAndCompress(rotated)
                                        previewBitmap = scaled
                                        avatarBase64 = b64
                                    }
                                },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = if (isArabic) "تدوير 90 درجة" else "Rotate 90°")
                            }
                            IconButton(
                                onClick = { previewBitmap = null; avatarBase64 = null },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = if (isArabic) "إزالة الصورة" else "Remove Avatar", tint = FvgNegative)
                            }
                        }
                    }
                    Text(
                        text = if (isArabic) "تُضغط الصورة اختيارياً وتُحذف بيانات الموقع EXIF ولا تظهر لأي منظمة أخرى." else "Optional private avatar: compressed JPEG, EXIF stripped, never shared across workspaces.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Preferences & Display Card
            item {
                FvgCard {
                    Text(
                        text = if (isArabic) "البيانات والتفضيلات والخط والمظهر" else "Identity, Theme, Language & Font Size",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FvgField(displayName, { displayName = it }, if (isArabic) "الاسم المعروض" else "Display Name", testTag = "profile_name_input")
                    FvgField(contactPhoneOptional, { contactPhoneOptional = it }, if (isArabic) "رقم تواصل اختياري" else "Optional Contact Phone")

                    Text(if (isArabic) "لغة الواجهة:" else "Interface Language:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = listOf("ar", "en"),
                        selected = preferredLanguage,
                        onSelect = { preferredLanguage = it },
                        labelResolver = { if (it == "ar") "العربية (RTL)" else "English (LTR)" }
                    )

                    Text(if (isArabic) "المظهر (Theme):" else "Appearance Theme:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(
                        options = listOf("system", "light", "dark"),
                        selected = themeMode,
                        onSelect = { themeMode = it },
                        labelResolver = {
                            when (it) {
                                "light" -> if (isArabic) "نهاري" else "Light"
                                "dark" -> if (isArabic) "ليلي" else "Dark"
                                else -> if (isArabic) "تلقائي (النظام)" else "System"
                            }
                        }
                    )

                    Text(
                        text = if (isArabic) "حجم الخط (${(fontScale * 100).toInt()}%)" else "Font Size Scale (${(fontScale * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Slider(
                        value = fontScale,
                        onValueChange = { fontScale = it },
                        valueRange = 0.85f..1.30f,
                        steps = 8,
                        modifier = Modifier.testTag("profile_font_slider")
                    )

                    Text(if (isArabic) "العملة الافتراضية:" else "Default Currency:", style = MaterialTheme.typography.labelLarge)
                    FvgChipSelector(listOf("SDG", "USD", "SAR", "AED", "EGP", "EUR"), defaultCurrency, { defaultCurrency = it })

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FvgField(timezone, { timezone = it }, if (isArabic) "المنطقة الزمنية" else "Timezone", modifier = Modifier.weight(1f))
                        FvgField(dateFormat, { dateFormat = it }, if (isArabic) "تنسيق التاريخ" else "Date Format", modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isArabic) "تفعيل الإشعارات والتنبيهات" else "Enable Financial & Trial Notifications", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                    }

                    FvgPrimaryButton(
                        text = if (isArabic) "حفظ بيانات الملف الشخصي" else "Save Profile & Preferences",
                        icon = Icons.Filled.Save,
                        enabled = !state.isBusy && displayName.isNotBlank(),
                        onClick = {
                            viewModel.updateExtendedProfile(
                                displayName = displayName,
                                preferredLanguage = preferredLanguage,
                                themeMode = themeMode,
                                fontScale = fontScale,
                                timezone = timezone,
                                dateFormat = dateFormat,
                                numberFormat = numberFormat,
                                defaultCurrency = defaultCurrency,
                                avatarLocalRef = avatarBase64,
                                contactPhoneOptional = contactPhoneOptional,
                                notificationsEnabled = notificationsEnabled
                            )
                        },
                        testTag = "profile_save_button"
                    )
                }
            }

            // Security, Email Change & Password Reset
            item {
                FvgCard {
                    Text(
                        text = if (isArabic) "الأمان وتغيير البريد وكلمة المرور" else "Account Security, Email & Password",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (isArabic)
                            "لا يتيح الحساب تغيير صلاحية الاشتراك أو تمديد التجربة من الملف الشخصي؛ يُدار الاشتراك عبر مالكة المنصة فقط."
                        else
                            "Subscription plans, roles, and trial windows are protected and cannot be modified from user profile.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FvgField(newEmailInput, { newEmailInput = it }, if (isArabic) "البريد الإلكتروني الجديد" else "Verified / New Email")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    AuthHelper.verifyBeforeUpdateEmail(auth, newEmailInput)
                                        .onSuccess {
                                            accountNotice = if (isArabic) "أُرسل رابط التأكيد إلى $newEmailInput." else "Verification link sent to $newEmailInput."
                                        }
                                        .onFailure { accountNotice = it.localizedMessage }
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "تحديث البريد" else "Update Email")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    AuthHelper.sendPasswordReset(auth, viewModel.userEmail)
                                        .onSuccess {
                                            accountNotice = if (isArabic) "أُرسل رابط إعادة تعيين كلمة المرور إلى بريدك." else "Password reset email sent to ${viewModel.userEmail}."
                                        }
                                        .onFailure { accountNotice = it.localizedMessage }
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(if (isArabic) "إعادة تعيين كلمة المرور" else "Reset Password")
                        }
                    }
                    if (accountNotice != null) {
                        Text(accountNotice!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Export / Deletion Request / Sign Out
            item {
                FvgCard {
                    OutlinedButton(
                        onClick = { exportAccountLauncher.launch("fvg_profile_export.json") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isArabic) "تصدير بيانات حسابي (JSON)" else "Export Account Data (JSON)")
                    }
                    OutlinedButton(
                        onClick = {
                            viewModel.submitSupportTicket(
                                senderName = displayName,
                                senderEmail = viewModel.userEmail,
                                message = "[ACCOUNT_DELETION_REQUEST] Request permanent account deletion for ${viewModel.userEmail} (${viewModel.currentUserId})."
                            )
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(if (isArabic) "طلب حذف الحساب نهائياً" else "Request Account Deletion", color = FvgNegative)
                    }
                    Button(
                        onClick = onSignOutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = FvgNegative, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("profile_sign_out_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(s.signOut)
                    }
                }
            }
        }
    }
}
