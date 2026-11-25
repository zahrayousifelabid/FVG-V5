package com.example.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import com.example.domain.export.ReportExportPayload
import kotlinx.coroutines.flow.StateFlow

@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)

class FvgImagePayload(
    val bytes: ByteArray,
    val mimeType: String = "image/jpeg",
    val fileName: String = "image.jpg"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FvgImagePayload) return false
        return mimeType == other.mimeType && fileName == other.fileName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        return result
    }
}

expect fun FvgImagePayload.toComposeImageBitmap(): ImageBitmap?
expect fun FvgImagePayload.rotate90(): FvgImagePayload
expect fun FvgImagePayload.toAvatarBase64(maxDimPx: Int = 256, quality: Int = 76): String
expect fun decodeBase64ToComposeImageBitmap(base64OrDataUrl: String): ImageBitmap?

interface FvgKeyValueStore {
    fun getString(key: String, defaultValue: String = ""): String
    fun getStringOrNull(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getInt(key: String, defaultValue: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, defaultValue: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getStringSet(key: String): Set<String>
    fun putStringSet(key: String, value: Set<String>)
    fun remove(key: String)
    fun clear()
}

interface FvgConnectivityMonitor {
    val isOnline: StateFlow<Boolean>
    fun refreshConnectivity(): Boolean
    fun dispose()
}

interface FvgPlatformBridge {
    val platformTag: String
    val deviceLabel: String
    val installVisitScreen: String
    val installPrefKey: String

    fun openKvStore(name: String): FvgKeyValueStore
    fun createConnectivityMonitor(): FvgConnectivityMonitor

    fun showToast(message: String)
    fun copyToClipboard(label: String, text: String)
    fun openExternalUrl(url: String)

    fun launchExportText(
        suggestedFileName: String,
        mimeType: String,
        content: String,
        onResult: (Result<Unit>) -> Unit
    )

    fun launchExportPdf(
        payload: ReportExportPayload,
        suggestedFileName: String,
        onResult: (Result<Unit>) -> Unit
    )

    fun launchExportXlsx(
        payload: ReportExportPayload,
        suggestedFileName: String,
        onResult: (Result<Unit>) -> Unit
    )

    fun launchImportSpreadsheetOrCsv(
        onLoaded: (fileName: String, csvText: String) -> Unit,
        onError: (String) -> Unit
    )

    fun launchPickImage(
        onPicked: (FvgImagePayload) -> Unit,
        onError: (String) -> Unit
    )

    fun launchCaptureCameraImage(
        onCaptured: (FvgImagePayload) -> Unit,
        onError: (String) -> Unit
    )
}

@Composable
expect fun rememberFvgPlatformBridge(): FvgPlatformBridge

val LocalFvgPlatformBridge = staticCompositionLocalOf<FvgPlatformBridge?> { null }
