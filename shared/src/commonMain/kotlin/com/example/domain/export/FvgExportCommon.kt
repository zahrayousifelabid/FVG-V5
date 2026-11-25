package com.example.domain.export

import com.example.domain.json.FvgJsonArray
import com.example.domain.json.FvgJsonObject

data class ReportExportPayload(
    val title: String,
    val organizationName: String,
    val currency: String,
    val generatedDate: String,
    val methodNotes: String,
    val headers: List<String>,
    val rows: List<List<String>>,
    val missingDataNotes: List<String> = emptyList(),
    val isArabic: Boolean = false
)

object FvgExportFormatter {

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

    fun toJsonObject(payload: ReportExportPayload): FvgJsonObject {
        val rowsArray = FvgJsonArray()
        for (row in payload.rows) {
            val obj = FvgJsonObject()
            payload.headers.forEachIndexed { idx, h ->
                obj.put(h, row.getOrElse(idx) { "" })
            }
            rowsArray.put(obj)
        }
        return FvgJsonObject().apply {
            put("title", payload.title)
            put("organization", payload.organizationName)
            put("currency", payload.currency)
            put("generatedDate", payload.generatedDate)
            put("methodology", payload.methodNotes)
            put("missingDataNotes", FvgJsonArray(payload.missingDataNotes))
            put("rows", rowsArray)
        }
    }

    fun toJsonString(payload: ReportExportPayload): String = toJsonObject(payload).toString(2)

    fun toWorkspaceBackupJson(
        organizationName: String,
        baseCurrency: String,
        generatedAtIso: String,
        sections: Map<String, Int>,
        payloads: List<ReportExportPayload>
    ): String {
        val reportsArr = FvgJsonArray()
        for (p in payloads) {
            reportsArr.put(toJsonObject(p))
        }
        val root = FvgJsonObject().apply {
            put("schema", "FVG_WORKSPACE_BACKUP_V1")
            put("organization", organizationName)
            put("baseCurrency", baseCurrency)
            put("exportedAt", generatedAtIso)
            put("recordCounts", FvgJsonObject(sections))
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

    fun buildXlsxXmlEntries(payload: ReportExportPayload): Map<String, String> {
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

        return linkedMapOf(
            "[Content_Types].xml" to contentTypes,
            "_rels/.rels" to rels,
            "xl/workbook.xml" to workbook,
            "xl/_rels/workbook.xml.rels" to wbRels,
            "xl/worksheets/sheet1.xml" to sheetXml
        )
    }
}
