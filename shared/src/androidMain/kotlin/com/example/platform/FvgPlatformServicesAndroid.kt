package com.example.platform

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Base64
import android.util.Xml
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.domain.export.FvgExportFormatter
import com.example.domain.export.ReportExportPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

actual fun FvgImagePayload.toComposeImageBitmap(): ImageBitmap? = runCatching {
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

actual fun FvgImagePayload.rotate90(): FvgImagePayload {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return this
    val matrix = Matrix().apply { postRotate(90f) }
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    val out = ByteArrayOutputStream()
    rotated.compress(Bitmap.CompressFormat.JPEG, 90, out)
    return FvgImagePayload(out.toByteArray(), mimeType = "image/jpeg", fileName = fileName)
}

actual fun FvgImagePayload.toAvatarBase64(maxDimPx: Int, quality: Int): String {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return ""
    val maxSide = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
    val scale = if (maxSide > maxDimPx) maxDimPx.toFloat() / maxSide else 1f
    val resized = Bitmap.createScaledBitmap(
        bmp,
        (bmp.width * scale).toInt().coerceAtLeast(1),
        (bmp.height * scale).toInt().coerceAtLeast(1),
        true
    )
    val baos = ByteArrayOutputStream()
    resized.compress(Bitmap.CompressFormat.JPEG, quality, baos)
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}

actual fun decodeBase64ToComposeImageBitmap(base64OrDataUrl: String): ImageBitmap? = runCatching {
    val clean = base64OrDataUrl.substringAfter("base64,", base64OrDataUrl).trim()
    if (clean.isBlank()) return null
    val bytes = Base64.decode(clean, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
}.getOrNull()

fun Bitmap.toFvgImagePayload(fileName: String = "image.jpg"): FvgImagePayload {
    val out = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 90, out)
    return FvgImagePayload(bytes = out.toByteArray(), mimeType = "image/jpeg", fileName = fileName)
}

fun FvgImagePayload.toAndroidBitmap(): Bitmap? =
    runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull()

class AndroidKeyValueStore(
    context: Context,
    name: String
) : FvgKeyValueStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun getStringOrNull(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long = prefs.getLong(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getStringSet(key: String): Set<String> =
        prefs.getStringSet(key, emptySet()) ?: emptySet()

    override fun putStringSet(key: String, value: Set<String>) {
        prefs.edit().putStringSet(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }
}

class AndroidConnectivityMonitor(context: Context) : FvgConnectivityMonitor {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentlyOnline())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = checkCurrentlyOnline()
        }

        override fun onLost(network: Network) {
            _isOnline.value = checkCurrentlyOnline()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            _isOnline.value = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    init {
        runCatching {
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(req, networkCallback)
        }
    }

    private fun checkCurrentlyOnline(): Boolean = runCatching {
        val cm = connectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    override fun refreshConnectivity(): Boolean {
        val now = checkCurrentlyOnline()
        _isOnline.value = now
        return now
    }

    override fun dispose() {
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
    }
}

object FvgAndroidExportWriter {
    private fun buildLayout(
        text: String,
        paint: TextPaint,
        widthPx: Int,
        alignRight: Boolean
    ): StaticLayout {
        val alignment = if (alignRight) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(24))
            .setAlignment(alignment)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .build()
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        widthPx: Int,
        paint: TextPaint,
        alignRight: Boolean
    ): Int {
        val layout = buildLayout(text, paint, widthPx, alignRight)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height
    }

    fun writePdf(output: OutputStream, payload: ReportExportPayload) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = doc.startPage(pageInfo)
        var canvas = page.canvas

        val headerPaint = Paint().apply { color = Color.parseColor("#022A27") }
        val goldPaint = Paint().apply { color = Color.parseColor("#DDB065"); strokeWidth = 3f }
        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#FAF2E8")
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = TextPaint().apply {
            color = Color.parseColor("#DDB065")
            textSize = 10f
            isAntiAlias = true
        }
        val metaPaint = TextPaint().apply {
            color = Color.parseColor("#15423A")
            textSize = 9.5f
            isAntiAlias = true
        }
        val thPaint = TextPaint().apply {
            color = Color.parseColor("#022A27")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val tdPaint = TextPaint().apply {
            color = Color.parseColor("#1D2321")
            textSize = 8.5f
            isAntiAlias = true
        }
        val rowBgPaint = Paint().apply { color = Color.parseColor("#FAF2E8") }
        val linePaint = Paint().apply { color = Color.parseColor("#DCD7C8"); strokeWidth = 1f }

        val marginX = 28f
        val contentWidth = pageWidth - 56
        val hasArabic = payload.isArabic ||
            payload.title.any { it in '\u0600'..'\u06FF' } ||
            payload.headers.any { h -> h.any { it in '\u0600'..'\u06FF' } }

        fun drawHeader(c: Canvas) {
            c.drawRect(0f, 0f, pageWidth.toFloat(), 76f, headerPaint)
            c.drawLine(0f, 76f, pageWidth.toFloat(), 76f, goldPaint)
            drawWrapped(c, "FVG — ${payload.title}", marginX, 16f, contentWidth, titlePaint, hasArabic)
            drawWrapped(
                c,
                "${payload.organizationName}  |  ${payload.currency}  |  ${payload.generatedDate}",
                marginX,
                46f,
                contentWidth,
                subtitlePaint,
                hasArabic
            )
        }

        drawHeader(canvas)
        var y = 88f
        val methHeight = drawWrapped(canvas, payload.methodNotes, marginX, y, contentWidth, metaPaint, hasArabic)
        y += methHeight + 8f
        if (payload.missingDataNotes.isNotEmpty()) {
            val notesHeight = drawWrapped(
                canvas,
                payload.missingDataNotes.joinToString(" • "),
                marginX,
                y,
                contentWidth,
                metaPaint,
                hasArabic
            )
            y += notesHeight + 8f
        }
        y += 6f

        val colCount = payload.headers.size.coerceAtLeast(1)
        val colWidth = contentWidth / colCount
        val orderedIndices = if (hasArabic) (0 until colCount).reversed().toList() else (0 until colCount).toList()

        fun drawTableHeader(yPos: Float): Float {
            var maxH = 18
            val layouts = orderedIndices.mapIndexed { displayIdx, srcIdx ->
                val text = payload.headers.getOrElse(srcIdx) { "" }
                val l = buildLayout(text, thPaint, (colWidth - 6).coerceAtLeast(24), hasArabic)
                maxH = maxOf(maxH, l.height)
                displayIdx to l
            }
            canvas.drawRect(24f, yPos - 4f, pageWidth - 24f, yPos + maxH + 6f, rowBgPaint)
            for ((displayIdx, layout) in layouts) {
                canvas.save()
                canvas.translate(marginX + displayIdx * colWidth + 2f, yPos)
                layout.draw(canvas)
                canvas.restore()
            }
            return yPos + maxH + 12f
        }

        y = drawTableHeader(y)

        for (row in payload.rows) {
            var maxRowH = 16
            val layouts = orderedIndices.mapIndexed { displayIdx, srcIdx ->
                val text = row.getOrElse(srcIdx) { "" }
                val l = buildLayout(text, tdPaint, (colWidth - 6).coerceAtLeast(24), hasArabic)
                maxRowH = maxOf(maxRowH, l.height)
                displayIdx to l
            }
            if (y + maxRowH > pageHeight - 40f) {
                doc.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = doc.startPage(pageInfo)
                canvas = page.canvas
                drawHeader(canvas)
                y = 90f
                y = drawTableHeader(y)
            }
            for ((displayIdx, layout) in layouts) {
                canvas.save()
                canvas.translate(marginX + displayIdx * colWidth + 2f, y)
                layout.draw(canvas)
                canvas.restore()
            }
            y += maxRowH + 8f
            canvas.drawLine(24f, y - 3f, pageWidth - 24f, y - 3f, linePaint)
        }

        doc.finishPage(page)
        doc.writeTo(output)
        doc.close()
    }

    fun writeXlsx(output: OutputStream, payload: ReportExportPayload) {
        val entries = FvgExportFormatter.buildXlsxXmlEntries(payload)
        ZipOutputStream(output).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    fun extractXlsxToCsvLines(input: InputStream): String {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val buf = ByteArrayOutputStream()
                    zis.copyTo(buf)
                    entries[entry.name] = buf.toByteArray()
                }
                entry = zis.nextEntry
            }
        }
        val sharedStrings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val parser = Xml.newPullParser()
            parser.setInput(bytes.inputStream(), "UTF-8")
            var inT = false
            val current = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") current.clear()
                        if (parser.name == "t") inT = true
                    }
                    XmlPullParser.TEXT -> if (inT) current.append(parser.text)
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "t") inT = false
                        if (parser.name == "si") sharedStrings.add(current.toString())
                    }
                }
                event = parser.next()
            }
        }

        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value
            ?: return ""

        val rowsOut = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(sheetBytes.inputStream(), "UTF-8")
        var currentRow = mutableListOf<String>()
        var cellType = ""
        var inVal = false
        val valBuf = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> cellType = parser.getAttributeValue(null, "t") ?: ""
                    "v", "t" -> {
                        inVal = true
                        valBuf.clear()
                    }
                }
                XmlPullParser.TEXT -> if (inVal) valBuf.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> {
                        inVal = false
                        val raw = valBuf.toString()
                        val resolved = if (cellType == "s") {
                            raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                        } else raw
                        currentRow.add(resolved)
                    }
                    "row" -> if (currentRow.isNotEmpty()) rowsOut.add(currentRow)
                }
            }
            event = parser.next()
        }
        return rowsOut.joinToString("\n") { r -> r.joinToString(",") { it.replace(",", " ") } }
    }
}

class AndroidFvgPlatformBridge(
    private val context: Context,
    private val onExportTextRequest: (String, String, (Result<Unit>) -> Unit) -> Unit,
    private val onExportPdfRequest: (ReportExportPayload, String, (Result<Unit>) -> Unit) -> Unit,
    private val onExportXlsxRequest: (ReportExportPayload, String, (Result<Unit>) -> Unit) -> Unit,
    private val onImportFileRequest: ((String, String) -> Unit, (String) -> Unit) -> Unit,
    private val onPickImageRequest: ((FvgImagePayload) -> Unit, (String) -> Unit) -> Unit,
    private val onCameraCaptureRequest: ((FvgImagePayload) -> Unit, (String) -> Unit) -> Unit
) : FvgPlatformBridge {
    private val appContext = context.applicationContext

    override val platformTag: String = "android"

    override val deviceLabel: String
        get() = listOf(Build.MANUFACTURER?.trim().orEmpty(), Build.MODEL?.trim().orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android Device" }

    override val installVisitScreen: String = "android_install"
    override val installPrefKey: String = "android_install_logged"

    override fun openKvStore(name: String): FvgKeyValueStore = AndroidKeyValueStore(appContext, name)

    override fun createConnectivityMonitor(): FvgConnectivityMonitor = AndroidConnectivityMonitor(appContext)

    override fun showToast(message: String) {
        Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun copyToClipboard(label: String, text: String) {
        runCatching {
            val cb = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cb?.setPrimaryClip(ClipData.newPlainText(label, text))
        }
    }

    override fun openExternalUrl(url: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            appContext.startActivity(intent)
        }
    }

    override fun launchExportText(
        suggestedFileName: String,
        mimeType: String,
        content: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        onExportTextRequest(suggestedFileName, content, onResult)
    }

    override fun launchExportPdf(
        payload: ReportExportPayload,
        suggestedFileName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        onExportPdfRequest(payload, suggestedFileName, onResult)
    }

    override fun launchExportXlsx(
        payload: ReportExportPayload,
        suggestedFileName: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        onExportXlsxRequest(payload, suggestedFileName, onResult)
    }

    override fun launchImportSpreadsheetOrCsv(
        onLoaded: (fileName: String, csvText: String) -> Unit,
        onError: (String) -> Unit
    ) {
        onImportFileRequest(onLoaded, onError)
    }

    override fun launchPickImage(
        onPicked: (FvgImagePayload) -> Unit,
        onError: (String) -> Unit
    ) {
        onPickImageRequest(onPicked, onError)
    }

    override fun launchCaptureCameraImage(
        onCaptured: (FvgImagePayload) -> Unit,
        onError: (String) -> Unit
    ) {
        onCameraCaptureRequest(onCaptured, onError)
    }
}

@Composable
actual fun rememberFvgPlatformBridge(): FvgPlatformBridge {
    val context = LocalContext.current
    var pendingTextContent by remember { mutableStateOf("") }
    var pendingTextCallback by remember { mutableStateOf<((Result<Unit>) -> Unit)?>(null) }

    var pendingPdfPayload by remember { mutableStateOf<ReportExportPayload?>(null) }
    var pendingPdfCallback by remember { mutableStateOf<((Result<Unit>) -> Unit)?>(null) }

    var pendingXlsxPayload by remember { mutableStateOf<ReportExportPayload?>(null) }
    var pendingXlsxCallback by remember { mutableStateOf<((Result<Unit>) -> Unit)?>(null) }

    var pendingImportSuccess by remember { mutableStateOf<((String, String) -> Unit)?>(null) }
    var pendingImportError by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var pendingImageSuccess by remember { mutableStateOf<((FvgImagePayload) -> Unit)?>(null) }
    var pendingImageError by remember { mutableStateOf<((String) -> Unit)?>(null) }

    var pendingCameraSuccess by remember { mutableStateOf<((FvgImagePayload) -> Unit)?>(null) }
    var pendingCameraError by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val textCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val cb = pendingTextCallback
        pendingTextCallback = null
        if (uri == null) return@rememberLauncherForActivityResult
        val res = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(pendingTextContent.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to open output stream for export")
        }
        cb?.invoke(res)
    }

    val pdfCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val payload = pendingPdfPayload
        val cb = pendingPdfCallback
        pendingPdfPayload = null
        pendingPdfCallback = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        val res = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FvgAndroidExportWriter.writePdf(out, payload)
            } ?: error("Unable to open output stream for PDF")
        }
        cb?.invoke(res)
    }

    val xlsxCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val payload = pendingXlsxPayload
        val cb = pendingXlsxCallback
        pendingXlsxPayload = null
        pendingXlsxCallback = null
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        val res = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FvgAndroidExportWriter.writeXlsx(out, payload)
            } ?: error("Unable to open output stream for XLSX")
        }
        cb?.invoke(res)
    }

    val importOpenLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val ok = pendingImportSuccess
        val err = pendingImportError
        pendingImportSuccess = null
        pendingImportError = null
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val name = uri.lastPathSegment.orEmpty()
            val isXlsx = mime.contains("spreadsheetml") || name.endsWith(".xlsx", ignoreCase = true)
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                if (isXlsx) FvgAndroidExportWriter.extractXlsxToCsvLines(input)
                else input.bufferedReader().use { it.readText() }
            }.orEmpty()
            if (text.isBlank()) {
                err?.invoke("Empty or unreadable file")
            } else {
                ok?.invoke(name.ifBlank { "import.csv" }, text)
            }
        }.onFailure { e ->
            err?.invoke(e.message ?: "File import failed")
        }
    }

    // Google Play Policy compliant zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val ok = pendingImageSuccess
        val err = pendingImageError
        pendingImageSuccess = null
        pendingImageError = null
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read image")
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val name = uri.lastPathSegment?.substringAfterLast("/")?.ifBlank { "image.jpg" } ?: "image.jpg"
            ok?.invoke(FvgImagePayload(bytes = bytes, mimeType = mime, fileName = name))
        }.onFailure { e ->
            err?.invoke(e.message ?: "Image load failed")
        }
    }

    val cameraPreviewLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        val ok = pendingCameraSuccess
        pendingCameraSuccess = null
        pendingCameraError = null
        if (bitmap != null) {
            ok?.invoke(bitmap.toFvgImagePayload("camera_${currentTimeMillis()}.jpg"))
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPreviewLauncher.launch(null)
        } else {
            val err = pendingCameraError
            pendingCameraSuccess = null
            pendingCameraError = null
            err?.invoke("Camera permission denied")
        }
    }

    val currentContext by rememberUpdatedState(context)

    return remember(currentContext) {
        AndroidFvgPlatformBridge(
            context = currentContext,
            onExportTextRequest = { fileName, content, onResult ->
                pendingTextContent = content
                pendingTextCallback = onResult
                textCreateLauncher.launch(fileName)
            },
            onExportPdfRequest = { payload, fileName, onResult ->
                pendingPdfPayload = payload
                pendingPdfCallback = onResult
                pdfCreateLauncher.launch(fileName)
            },
            onExportXlsxRequest = { payload, fileName, onResult ->
                pendingXlsxPayload = payload
                pendingXlsxCallback = onResult
                xlsxCreateLauncher.launch(fileName)
            },
            onImportFileRequest = { onLoaded, onError ->
                pendingImportSuccess = onLoaded
                pendingImportError = onError
                importOpenLauncher.launch(
                    arrayOf(
                        "text/*",
                        "text/csv",
                        "application/vnd.ms-excel",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                    )
                )
            },
            onPickImageRequest = { onPicked, onError ->
                pendingImageSuccess = onPicked
                pendingImageError = onError
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onCameraCaptureRequest = { onCaptured, onError ->
                pendingCameraSuccess = onCaptured
                pendingCameraError = onError
                val granted = ContextCompat.checkSelfPermission(
                    currentContext,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    cameraPreviewLauncher.launch(null)
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        )
    }
}
