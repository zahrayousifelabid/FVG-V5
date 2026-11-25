package com.example.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.example.domain.export.FvgZipUtils
import com.example.domain.export.ReportExportPayload
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.await
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.Promise

val LocalFvgWebToastHandler = staticCompositionLocalOf<((String) -> Unit)?> { null }

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack = rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) {
            onDispose { }
        } else {
            val listener: (Event) -> Unit = { event ->
                val ke = event as? KeyboardEvent
                if (ke?.key == "Escape") {
                    currentOnBack.value()
                }
            }
            window.addEventListener("keydown", listener)
            onDispose {
                window.removeEventListener("keydown", listener)
            }
        }
    }
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

class JsLocalStorageKvStore(private val namespace: String) : FvgKeyValueStore {
    private fun k(key: String): String = "fvg::$namespace::$key"

    override fun getString(key: String, defaultValue: String): String =
        window.localStorage.getItem(k(key)) ?: defaultValue

    override fun getStringOrNull(key: String): String? = window.localStorage.getItem(k(key))

    override fun putString(key: String, value: String) {
        window.localStorage.setItem(k(key), value)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        when (window.localStorage.getItem(k(key))) {
            "true" -> true
            "false" -> false
            else -> defaultValue
        }

    override fun putBoolean(key: String, value: Boolean) {
        window.localStorage.setItem(k(key), if (value) "true" else "false")
    }

    override fun getInt(key: String, defaultValue: Int): Int =
        window.localStorage.getItem(k(key))?.toIntOrNull() ?: defaultValue

    override fun putInt(key: String, value: Int) {
        window.localStorage.setItem(k(key), value.toString())
    }

    override fun getLong(key: String, defaultValue: Long): Long =
        window.localStorage.getItem(k(key))?.toLongOrNull() ?: defaultValue

    override fun putLong(key: String, value: Long) {
        window.localStorage.setItem(k(key), value.toString())
    }

    override fun getStringSet(key: String): Set<String> {
        val raw = window.localStorage.getItem(k(key)).orEmpty()
        return if (raw.isBlank()) emptySet() else raw.split("\u001F").filter { it.isNotEmpty() }.toSet()
    }

    override fun putStringSet(key: String, value: Set<String>) {
        window.localStorage.setItem(k(key), value.joinToString("\u001F"))
    }

    override fun remove(key: String) {
        window.localStorage.removeItem(k(key))
    }

    override fun clear() {
        val prefix = "fvg::$namespace::"
        val keys = mutableListOf<String>()
        for (i in 0 until window.localStorage.length) {
            val keyName = window.localStorage.key(i) ?: continue
            if (keyName.startsWith(prefix)) keys.add(keyName)
        }
        keys.forEach { window.localStorage.removeItem(it) }
    }
}

class JsConnectivityMonitor : FvgConnectivityMonitor {
    private val _isOnline = MutableStateFlow(window.navigator.onLine)
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val onOnline: (Event) -> Unit = { _isOnline.value = true }
    private val onOffline: (Event) -> Unit = { _isOnline.value = false }

    init {
        window.addEventListener("online", onOnline)
        window.addEventListener("offline", onOffline)
    }

    override fun refreshConnectivity(): Boolean {
        val now = window.navigator.onLine
        _isOnline.value = now
        return now
    }

    override fun dispose() {
        window.removeEventListener("online", onOnline)
        window.removeEventListener("offline", onOffline)
    }
}

private fun byteArrayToUint8Array(bytes: ByteArray): Uint8Array {
    val arr = Uint8Array(bytes.size)
    val dynamicArr: dynamic = arr
    for (i in bytes.indices) {
        dynamicArr[i] = bytes[i].toInt() and 0xFF
    }
    return arr
}

private fun arrayBufferToByteArray(buffer: ArrayBuffer): ByteArray {
    val i8 = Int8Array(buffer)
    return ByteArray(i8.length) { idx ->
        val dyn: dynamic = i8
        (dyn[idx] as Number).toByte()
    }
}

@JsName("DecompressionStream")
private external class JsDecompressionStream(format: String)

private fun inflateDeflateRawPromise(compressed: ByteArray): Promise<ByteArray> {
    val uint8 = byteArrayToUint8Array(compressed)
    val promiseDyn: dynamic = js(
        """(async function(data) {
            if (typeof DecompressionStream === 'undefined') {
                throw new Error('Browser DecompressionStream is unavailable');
            }
            var stream = new Blob([data]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
            return await new Response(stream).arrayBuffer();
        })"""
    )(uint8)
    @Suppress("UNCHECKED_CAST")
    val bufPromise = promiseDyn as Promise<ArrayBuffer>
    return bufPromise.then { buf -> arrayBufferToByteArray(buf) }
}

private fun unpackZipAsync(
    zipBytes: ByteArray,
    onDone: (Map<String, ByteArray>) -> Unit,
    onErr: (String) -> Unit
) {
    val rawEntries = FvgZipUtils.parseRawZipEntries(zipBytes)
    if (rawEntries.isEmpty()) {
        onErr("Invalid or empty XLSX archive")
        return
    }
    val resolved = linkedMapOf<String, ByteArray>()
    val pending = mutableListOf<Pair<String, Promise<ByteArray>>>()
    for (entry in rawEntries) {
        when (entry.compressionMethod) {
            0 -> resolved[entry.name] = entry.compressedBytes
            8 -> pending.add(entry.name to inflateDeflateRawPromise(entry.compressedBytes))
        }
    }
    if (pending.isEmpty()) {
        onDone(resolved)
        return
    }
    val promisesArr = pending.map { it.second }.toTypedArray()
    Promise.all(promisesArr).then(
        onFulfilled = { results ->
            for (i in pending.indices) {
                resolved[pending[i].first] = results[i]
            }
            onDone(resolved)
        },
        onRejected = { err ->
            onErr(err.message ?: "Failed to decompress XLSX entry")
        }
    )
}

class JsFvgPlatformBridge(
    private val toastSink: ((String) -> Unit)?
) : FvgPlatformBridge {
    override val platformTag: String = "web_pwa"
    override val deviceLabel: String
        get() = runCatching {
            val ua = window.navigator.userAgent
            when {
                ua.contains("Android", ignoreCase = true) -> "Web Browser (Android)"
                ua.contains("iPhone", ignoreCase = true) -> "Web Browser (iPhone)"
                ua.contains("iPad", ignoreCase = true) -> "Web Browser (iPad)"
                ua.contains("Mac", ignoreCase = true) -> "Web Browser (macOS)"
                ua.contains("Windows", ignoreCase = true) -> "Web Browser (Windows)"
                else -> "Web Browser"
            }
        }.getOrDefault("Web Browser")

    override val installVisitScreen: String = "web_compose_session"
    override val installPrefKey: String = "web_compose_install_logged"

    override fun openKvStore(name: String): FvgKeyValueStore = JsLocalStorageKvStore(name)
    override fun createConnectivityMonitor(): FvgConnectivityMonitor = JsConnectivityMonitor()

    override fun showToast(message: String) {
        toastSink?.invoke(message) ?: println("[FVG Web] $message")
    }

    override fun copyToClipboard(label: String, text: String) {
        runCatching {
            val nav: dynamic = window.navigator
            nav.clipboard?.writeText(text)
        }
    }

    override fun openExternalUrl(url: String) {
        window.open(url, "_blank", "noopener,noreferrer")
    }

    private fun triggerBrowserDownload(fileName: String, mimeType: String, bytes: ByteArray) {
        val blob = Blob(arrayOf(byteArrayToUint8Array(bytes)), BlobPropertyBag(type = mimeType))
        val url: String = js("URL.createObjectURL")(blob) as String
        val anchor = document.createElement("a") as HTMLAnchorElement
        anchor.href = url
        anchor.download = fileName
        anchor.style.display = "none"
        document.body?.appendChild(anchor)
        anchor.click()
        document.body?.removeChild(anchor)
        window.setTimeout({ js("URL.revokeObjectURL")(url); Unit }, 2000)
    }

    override fun launchExportText(
        suggestedFileName: String,
        mimeType: String,
        content: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        onResult(
            runCatching {
                triggerBrowserDownload(suggestedFileName, mimeType, content.encodeToByteArray())
            }
        )
    }

    override fun launchExportPdf(
        payload: ReportExportPayload,
        suggestedFileName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        fun escHtml(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val dir = if (payload.isArabic) "rtl" else "ltr"
        val html = buildString {
            append("""<!doctype html><html dir="$dir"><head><meta charset="utf-8">""")
            append("<title>${escHtml(payload.title)}</title>")
            append(
                "<style>" +
                    "body{font-family:Arial,sans-serif;margin:0;padding:24px;color:#1D2321;background:#FFFFFF}" +
                    ".hdr{background:#022A27;color:#FAF2E8;padding:18px 22px;border-bottom:4px solid #DDB065;border-radius:8px}" +
                    ".hdr h1{margin:0;font-size:20px}.hdr p{margin:6px 0 0;color:#DDB065;font-size:12px}" +
                    ".meta{margin:14px 0;font-size:12px;color:#15423A}" +
                    "table{width:100%;border-collapse:collapse;font-size:12px}" +
                    "th{background:#FAF2E8;color:#022A27;text-align:start;padding:8px;border:1px solid #DCD7C8}" +
                    "td{padding:7px 8px;border:1px solid #DCD7C8}" +
                    "@media print{body{padding:0}.no-print{display:none}}" +
                    "</style></head><body>"
            )
            append("<div class='hdr'><h1>FVG — ${escHtml(payload.title)}</h1>")
            append("<p>${escHtml(payload.organizationName)} | ${escHtml(payload.currency)} | ${escHtml(payload.generatedDate)}</p></div>")
            append("<div class='meta'>${escHtml(payload.methodNotes)}")
            if (payload.missingDataNotes.isNotEmpty()) {
                append("<br/>${escHtml(payload.missingDataNotes.joinToString(" • "))}")
            }
            append("</div><table><thead><tr>")
            payload.headers.forEach { append("<th>${escHtml(it)}</th>") }
            append("</tr></thead><tbody>")
            payload.rows.forEach { row ->
                append("<tr>")
                row.forEach { cell -> append("<td>${escHtml(cell)}</td>") }
                append("</tr>")
            }
            append("</tbody></table><script>setTimeout(function(){window.print();},250);</script></body></html>")
        }
        onResult(
            runCatching {
                val popup = window.open("", "_blank")
                if (popup != null) {
                    popup.document.open()
                    popup.document.write(html)
                    popup.document.close()
                } else {
                    val printName = suggestedFileName.substringBeforeLast(".") + "_print.html"
                    triggerBrowserDownload(printName, "text/html;charset=utf-8", html.encodeToByteArray())
                }
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
                triggerBrowserDownload(
                    fileName = suggestedFileName,
                    mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bytes = FvgZipUtils.buildXlsxBytes(payload)
                )
            }
        )
    }

    private fun openFilePicker(
        accept: String,
        captureAttr: String? = null,
        onBytes: (name: String, mime: String, bytes: ByteArray) -> Unit,
        onError: (String) -> Unit
    ) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = accept
        if (captureAttr != null) {
            input.setAttribute("capture", captureAttr)
        }
        input.style.display = "none"
        input.onchange = {
            val file = input.files?.get(0)
            document.body?.removeChild(input)
            if (file != null) {
                val reader = FileReader()
                reader.onload = {
                    val buf = reader.result as? ArrayBuffer
                    if (buf == null) {
                        onError("Unable to read selected file")
                    } else {
                        onBytes(file.name, file.type.ifBlank { "application/octet-stream" }, arrayBufferToByteArray(buf))
                    }
                }
                reader.onerror = { onError("File read error") }
                reader.readAsArrayBuffer(file)
            }
            Unit
        }
        document.body?.appendChild(input)
        input.click()
    }

    override fun launchImportSpreadsheetOrCsv(
        onLoaded: (fileName: String, csvText: String) -> Unit,
        onError: (String) -> Unit
    ) {
        openFilePicker(
            accept = ".csv,.txt,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            onBytes = { name, mime, bytes ->
                val isXlsx = mime.contains("spreadsheetml") || name.endsWith(".xlsx", ignoreCase = true)
                if (isXlsx) {
                    unpackZipAsync(
                        zipBytes = bytes,
                        onDone = { entries ->
                            val csv = FvgZipUtils.xlsxEntriesToCsvLines(entries)
                            if (csv.isBlank()) onError("Empty or unreadable XLSX sheet") else onLoaded(name, csv)
                        },
                        onErr = onError
                    )
                } else {
                    val text = bytes.decodeToString()
                    if (text.isBlank()) onError("Empty CSV file") else onLoaded(name, text)
                }
            },
            onError = onError
        )
    }

    override fun launchPickImage(onPicked: (FvgImagePayload) -> Unit, onError: (String) -> Unit) {
        openFilePicker(
            accept = "image/*",
            onBytes = { name, mime, bytes ->
                onPicked(FvgImagePayload(bytes = bytes, mimeType = mime.ifBlank { "image/jpeg" }, fileName = name))
            },
            onError = onError
        )
    }

    override fun launchCaptureCameraImage(onCaptured: (FvgImagePayload) -> Unit, onError: (String) -> Unit) {
        openFilePicker(
            accept = "image/*",
            captureAttr = "environment",
            onBytes = { name, mime, bytes ->
                onCaptured(FvgImagePayload(bytes = bytes, mimeType = mime.ifBlank { "image/jpeg" }, fileName = name))
            },
            onError = onError
        )
    }
}

@Composable
actual fun rememberFvgPlatformBridge(): FvgPlatformBridge {
    val toastHandler = LocalFvgWebToastHandler.current
    return remember(toastHandler) { JsFvgPlatformBridge(toastHandler) }
}
