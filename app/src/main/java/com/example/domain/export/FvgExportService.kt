package com.example.domain.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FvgExportService {

    fun toCsvString(payload: ReportExportPayload): String {
        val sb = StringBuilder()
        sb.appendLine("# FVG Report: ${payload.title}")
        sb.appendLine("# Organization: ${payload.organizationName} | Currency: ${payload.currency} | Date: ${payload.generatedDate}")
        sb.appendLine("# Methodology: ${payload.methodNotes}")
        if (payload.missingDataNotes.isNotEmpty()) {
            sb.appendLine("# Missing-data notes: ${payload.missingDataNotes.joinToString("; ")}")
        }
        fun esc(cell: String): String {
            val safe = cell.replace("\"", "\"\"")
            return "\"$safe\""
        }
        sb.appendLine(payload.headers.joinToString(",") { esc(it) })
        for (row in payload.rows) {
            sb.appendLine(row.joinToString(",") { esc(it) })
        }
        return sb.toString()
    }

    fun toJsonString(payload: ReportExportPayload): String {
        val root = JSONObject().apply {
            put("title", payload.title)
            put("organization", payload.organizationName)
            put("currency", payload.currency)
            put("generatedDate", payload.generatedDate)
            put("methodology", payload.methodNotes)
            put("missingDataNotes", JSONArray(payload.missingDataNotes))
            val rowsArray = JSONArray()
            for (row in payload.rows) {
                val obj = JSONObject()
                payload.headers.forEachIndexed { idx, h ->
                    obj.put(h, row.getOrElse(idx) { "" })
                }
                rowsArray.put(obj)
            }
            put("rows", rowsArray)
        }
        return root.toString(2)
    }

    fun toWorkspaceBackupJson(
        organizationName: String,
        baseCurrency: String,
        generatedAtIso: String,
        sections: Map<String, Int>,
        payloads: List<ReportExportPayload>
    ): String {
        val root = JSONObject().apply {
            put("schema", "FVG_WORKSPACE_BACKUP_V1")
            put("organization", organizationName)
            put("baseCurrency", baseCurrency)
            put("exportedAt", generatedAtIso)
            put("recordCounts", JSONObject(sections))
            val reportsArr = JSONArray()
            for (p in payloads) {
                reportsArr.put(JSONObject(toJsonString(p)))
            }
            put("datasets", reportsArr)
        }
        return root.toString(2)
    }

    fun toChatTranscriptText(
        organizationName: String,
        aiMode: String,
        generatedDate: String,
        messages: List<Pair<String, String>>
    ): String = buildString {
        appendLine("FVG — Financial Vision Guide | Financial Assistant Transcript")
        appendLine("Organization: $organizationName")
        appendLine("Mode: $aiMode")
        appendLine("Exported: $generatedDate")
        appendLine("--------------------------------------------------")
        messages.forEachIndexed { index, (role, text) ->
            appendLine("[${index + 1}] $role:")
            appendLine(text)
            appendLine()
        }
    }

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
        val contentWidth = (pageWidth - 56)
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
        fun escXml(s: String): String = s
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        fun colLetter(index: Int): String {
            var n = index + 1
            val sb = StringBuilder()
            while (n > 0) {
                val rem = (n - 1) % 26
                sb.append(('A'.code + rem).toChar())
                n = (n - 1) / 26
            }
            return sb.reverse().toString()
        }

        val allRows = mutableListOf<List<String>>()
        allRows.add(listOf("FVG — ${payload.title}", "Organization: ${payload.organizationName}", "Currency: ${payload.currency}", "Date: ${payload.generatedDate}"))
        allRows.add(listOf("Methodology: ${payload.methodNotes}"))
        if (payload.missingDataNotes.isNotEmpty()) {
            allRows.add(listOf("Missing-Data Notes: ${payload.missingDataNotes.joinToString("; ")}"))
        }
        allRows.add(payload.headers)
        allRows.addAll(payload.rows)

        val rightToLeftAttr = if (payload.isArabic) """ rightToLeft="1"""" else ""
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0"$rightToLeftAttr/></sheetViews><sheetData>""")
            allRows.forEachIndexed { rIdx, row ->
                val rNum = rIdx + 1
                append("""<row r="$rNum">""")
                row.forEachIndexed { cIdx, cell ->
                    val ref = "${colLetter(cIdx)}$rNum"
                    append("""<c r="$ref" t="inlineStr"><is><t>${escXml(cell)}</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbook = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/package/2006/workbook" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="FVG Report" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val wbRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>"""

        ZipOutputStream(output).use { zos ->
            fun put(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("xl/workbook.xml", workbook)
            put("xl/_rels/workbook.xml.rels", wbRels)
            put("xl/worksheets/sheet1.xml", sheetXml)
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

    fun writeTextToUri(context: Context, uri: Uri, text: String) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: error("Unable to open output stream for export")
    }
}
