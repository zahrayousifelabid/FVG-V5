package com.example.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.example.domain.export.FvgZipUtils
import com.example.domain.export.ReportExportPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.util.prefs.Preferences
import java.util.zip.ZipInputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop escape/navigation is handled explicitly via UI back affordances
}

actual fun FvgImagePayload.toComposeImageBitmap(): ImageBitmap? = runCatching {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

actual fun FvgImagePayload.rotate90(): FvgImagePayload {
    val img = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return this
    val newW = img.height
    val newH = img.width
    val surface = Surface.makeRasterN32Premul(newW, newH)
    val canvas = surface.canvas
    canvas.save()
    canvas.translate(newW.toFloat(), 0f)
    canvas.rotate(90f)
    canvas.drawImage(img, 0f, 0f)
    canvas.restore()
    val encoded = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 90)?.bytes ?: return this
    return FvgImagePayload(encoded, "image/jpeg", fileName)
}

@OptIn(ExperimentalEncodingApi::class)
actual fun FvgImagePayload.toAvatarBase64(maxDimPx: Int, quality: Int): String {
    val img = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return ""
    val maxSide = maxOf(img.width, img.height).coerceAtLeast(1)
    val scale = if (maxSide > maxDimPx) maxDimPx.toFloat() / maxSide else 1f
    val w = (img.width * scale).toInt().coerceAtLeast(1)
    val h = (img.height * scale).toInt().coerceAtLeast(1)
    val surface = Surface.makeRasterN32Premul(w, h)
    surface.canvas.drawImageRect(img, Rect.makeWH(w.toFloat(), h.toFloat()), Paint())
    val encoded = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, quality)?.bytes ?: return ""
    return Base64.encode(encoded)
}

@OptIn(ExperimentalEncodingApi::class)
actual fun decodeBase64ToComposeImageBitmap(base64OrDataUrl: String): ImageBitmap? = runCatching {
    val clean = base64OrDataUrl.substringAfter("base64,", base64OrDataUrl).trim()
    if (clean.isBlank()) return null
    val bytes = Base64.decode(clean)
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

class DesktopKeyValueStore(name: String) : FvgKeyValueStore {
    private val prefs = Preferences.userRoot().node("com/fvg/$name")

    override fun getString(key: String, defaultValue: String): String = prefs.get(key, defaultValue)
    override fun getStringOrNull(key: String): String? = prefs.get(key, null)
    override fun putString(key: String, value: String) = prefs.put(key, value)
    override fun getBoolean(key: String, defaultValue: Boolean): Boolean = prefs.getBoolean(key, defaultValue)
    override fun putBoolean(key: String, value: Boolean) = prefs.putBoolean(key, value)
    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)
    override fun putInt(key: String, value: Int) = prefs.putInt(key, value)
    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)
    override fun putLong(key: String, value: Long) = prefs.putLong(key, value)

    override fun getStringSet(key: String): Set<String> {
        val raw = prefs.get(key, "")
        return if (raw.isBlank()) emptySet() else raw.split("\u001F").filter { it.isNotEmpty() }.toSet()
    }

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.put(key, value.joinToString("\u001F"))
    }

    override fun remove(key: String) = prefs.remove(key)
    override fun clear() = runCatching { prefs.clear() }.let { Unit }
}

class DesktopConnectivityMonitor : FvgConnectivityMonitor {
    private val _isOnline = MutableStateFlow(true)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()
    override fun refreshConnectivity(): Boolean = true
    override fun dispose() {}
}

class DesktopFvgPlatformBridge : FvgPlatformBridge {
    override val platformTag: String = "desktop"
    override val deviceLabel: String = "${System.getProperty("os.name") ?: "Desktop"} JVM"
    override val installVisitScreen: String = "desktop_install"
    override val installPrefKey: String = "desktop_install_logged"

    override fun openKvStore(name: String): FvgKeyValueStore = DesktopKeyValueStore(name)
    override fun createConnectivityMonitor(): FvgConnectivityMonitor = DesktopConnectivityMonitor()

    override fun showToast(message: String) {
        println("[FVG] $message")
    }

    override fun copyToClipboard(label: String, text: String) {
        runCatching {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        }
    }

    override fun openExternalUrl(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }

    private fun chooseSaveFile(suggestedFileName: String): File? {
        val dlg = FileDialog(null as Frame?, "Save $suggestedFileName", FileDialog.SAVE)
        dlg.file = suggestedFileName
        dlg.isVisible = true
        val dir = dlg.directory ?: return null
        val file = dlg.file ?: return null
        return File(dir, file)
    }

    private fun chooseOpenFile(title: String): File? {
        val dlg = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dlg.isVisible = true
        val dir = dlg.directory ?: return null
        val file = dlg.file ?: return null
        return File(dir, file)
    }

    override fun launchExportText(
        suggestedFileName: String,
        mimeType: String,
        content: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        onResult(
            runCatching {
                val target = chooseSaveFile(suggestedFileName) ?: return
                target.writeText(content, Charsets.UTF_8)
            }
        )
    }

    override fun launchExportPdf(
        payload: ReportExportPayload,
        suggestedFileName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        val htmlName = suggestedFileName.substringBeforeLast(".") + ".html"
        val dir = if (payload.isArabic) "rtl" else "ltr"
        val html = buildString {
            append("""<!doctype html><html dir="$dir"><head><meta charset="utf-8"><title>${payload.title}</title>""")
            append("<style>body{font-family:sans-serif;padding:24px;color:#1D2321}h1{color:#022A27}table{width:100%;border-collapse:collapse}th,td{border:1px solid #DCD7C8;padding:8px}th{background:#022A27;color:#FAF2E8}</style></head><body>")
            append("<h1>FVG — ${payload.title}</h1>")
            append("<p>${payload.organizationName} | ${payload.currency} | ${payload.generatedDate}</p>")
            append("<p>${payload.methodNotes}</p><table><thead><tr>")
            payload.headers.forEach { append("<th>$it</th>") }
            append("</tr></thead><tbody>")
            payload.rows.forEach { r ->
                append("<tr>")
                r.forEach { append("<td>$it</td>") }
                append("</tr>")
            }
            append("</tbody></table><script>window.print()</script></body></html>")
        }
        onResult(
            runCatching {
                val target = chooseSaveFile(htmlName) ?: File.createTempFile("fvg_report_", ".html")
                target.writeText(html, Charsets.UTF_8)
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(target.toURI())
            }
        )
    }

    override fun launchExportXlsx(
        payload: ReportExportPayload,
        suggestedFileName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        onResult(
            runCatching {
                val target = chooseSaveFile(suggestedFileName) ?: return
                target.writeBytes(FvgZipUtils.buildXlsxBytes(payload))
            }
        )
    }

    override fun launchImportSpreadsheetOrCsv(
        onLoaded: (fileName: String, csvText: String) -> Unit,
        onError: (String) -> Unit
    ) {
        runCatching {
            val file = chooseOpenFile("Import CSV / XLSX") ?: return
            val bytes = file.readBytes()
            val text = if (file.name.endsWith(".xlsx", ignoreCase = true)) {
                val unpacked = linkedMapOf<String, ByteArray>()
                ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) unpacked[entry.name] = zis.readBytes()
                        entry = zis.nextEntry
                    }
                }
                FvgZipUtils.xlsxEntriesToCsvLines(unpacked)
            } else {
                bytes.decodeToString()
            }
            if (text.isBlank()) onError("Empty or unreadable file") else onLoaded(file.name, text)
        }.onFailure { onError(it.message ?: "Import failed") }
    }

    override fun launchPickImage(onPicked: (FvgImagePayload) -> Unit, onError: (String) -> Unit) {
        runCatching {
            val file = chooseOpenFile("Select Image") ?: return
            val ext = file.extension.lowercase()
            val mime = if (ext == "png") "image/png" else "image/jpeg"
            onPicked(FvgImagePayload(file.readBytes(), mime, file.name))
        }.onFailure { onError(it.message ?: "Image load failed") }
    }

    override fun launchCaptureCameraImage(onCaptured: (FvgImagePayload) -> Unit, onError: (String) -> Unit) {
        launchPickImage(onCaptured, onError)
    }
}

@Composable
actual fun rememberFvgPlatformBridge(): FvgPlatformBridge = remember { DesktopFvgPlatformBridge() }
