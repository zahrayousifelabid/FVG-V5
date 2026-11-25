package com.example.domain.export

/**
 * Pure-Kotlin ZIP (STORED) reader/writer and lightweight SpreadsheetML (.xlsx) parser
 * so XLSX export and import work uniformly across Android, Desktop, iOS, and Web (Kotlin/JS).
 */
object FvgZipUtils {

    private val crcTable: IntArray by lazy {
        IntArray(256) { n ->
            var c = n
            repeat(8) {
                c = if ((c and 1) != 0) {
                    (c ushr 1) xor 0xEDB88320.toInt()
                } else {
                    c ushr 1
                }
            }
            c
        }
    }

    fun crc32(bytes: ByteArray): Int {
        var crc = -1
        for (b in bytes) {
            crc = (crc ushr 8) xor crcTable[(crc xor b.toInt()) and 0xFF]
        }
        return crc.inv()
    }

    private fun MutableList<Byte>.putShortLE(v: Int) {
        add((v and 0xFF).toByte())
        add(((v ushr 8) and 0xFF).toByte())
    }

    private fun MutableList<Byte>.putIntLE(v: Int) {
        add((v and 0xFF).toByte())
        add(((v ushr 8) and 0xFF).toByte())
        add(((v ushr 16) and 0xFF).toByte())
        add(((v ushr 24) and 0xFF).toByte())
    }

    private fun readShortLE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readIntLE(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            ((data[offset + 2].toInt() and 0xFF) shl 16) or
            ((data[offset + 3].toInt() and 0xFF) shl 24)

    fun writeStoredZip(entries: Map<String, ByteArray>): ByteArray {
        val out = ArrayList<Byte>(4096)
        val centralDir = ArrayList<Byte>(1024)

        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val localOffset = out.size

            // Local file header (PK\x03\x04)
            out.putIntLE(0x04034B50)
            out.putShortLE(20) // version needed
            out.putShortLE(0x0800) // UTF-8 filename flag
            out.putShortLE(0) // method: STORED
            out.putShortLE(0) // mod time
            out.putShortLE(0x5421) // mod date
            out.putIntLE(crc)
            out.putIntLE(data.size)
            out.putIntLE(data.size)
            out.putShortLE(nameBytes.size)
            out.putShortLE(0) // extra length
            for (b in nameBytes) out.add(b)
            for (b in data) out.add(b)

            // Central directory header (PK\x01\x02)
            centralDir.putIntLE(0x02014B50)
            centralDir.putShortLE(20) // version made by
            centralDir.putShortLE(20) // version needed
            centralDir.putShortLE(0x0800) // UTF-8 flag
            centralDir.putShortLE(0) // STORED
            centralDir.putShortLE(0)
            centralDir.putShortLE(0x5421)
            centralDir.putIntLE(crc)
            centralDir.putIntLE(data.size)
            centralDir.putIntLE(data.size)
            centralDir.putShortLE(nameBytes.size)
            centralDir.putShortLE(0)
            centralDir.putShortLE(0)
            centralDir.putShortLE(0)
            centralDir.putShortLE(0)
            centralDir.putIntLE(0)
            centralDir.putIntLE(localOffset)
            for (b in nameBytes) centralDir.add(b)
        }

        val cdOffset = out.size
        out.addAll(centralDir)
        val cdSize = centralDir.size

        // End of central directory (PK\x05\x06)
        out.putIntLE(0x06054B50)
        out.putShortLE(0)
        out.putShortLE(0)
        out.putShortLE(entries.size)
        out.putShortLE(entries.size)
        out.putIntLE(cdSize)
        out.putIntLE(cdOffset)
        out.putShortLE(0)

        return out.toByteArray()
    }

    fun buildXlsxBytes(payload: ReportExportPayload): ByteArray {
        val xmlEntries = FvgExportFormatter.buildXlsxXmlEntries(payload)
        val byteEntries = LinkedHashMap<String, ByteArray>(xmlEntries.size)
        for ((k, v) in xmlEntries) {
            byteEntries[k] = v.encodeToByteArray()
        }
        return writeStoredZip(byteEntries)
    }

    data class RawZipEntry(
        val name: String,
        val compressionMethod: Int,
        val compressedBytes: ByteArray
    )

    /**
     * Parses local headers of a ZIP archive and returns raw entry slices (supporting method=0 and method=8).
     */
    fun parseRawZipEntries(zipBytes: ByteArray): List<RawZipEntry> {
        val result = mutableListOf<RawZipEntry>()
        // Prefer Central Directory at end for accurate sizes (handles data-descriptor entries from Excel/Android)
        var eocdOffset = -1
        val minOffset = (zipBytes.size - 65_560).coerceAtLeast(0)
        for (i in zipBytes.size - 22 downTo minOffset) {
            if (readIntLE(zipBytes, i) == 0x06054B50) {
                eocdOffset = i
                break
            }
        }
        if (eocdOffset >= 0) {
            val totalEntries = readShortLE(zipBytes, eocdOffset + 10)
            var cdPos = readIntLE(zipBytes, eocdOffset + 16)
            var count = 0
            while (count < totalEntries && cdPos + 46 <= zipBytes.size && readIntLE(zipBytes, cdPos) == 0x02014B50) {
                val method = readShortLE(zipBytes, cdPos + 10)
                val compSize = readIntLE(zipBytes, cdPos + 20)
                val nameLen = readShortLE(zipBytes, cdPos + 28)
                val extraLen = readShortLE(zipBytes, cdPos + 30)
                val commentLen = readShortLE(zipBytes, cdPos + 32)
                val localOffset = readIntLE(zipBytes, cdPos + 42)
                val nameStart = cdPos + 46
                val name = if (nameStart + nameLen <= zipBytes.size) {
                    zipBytes.decodeToString(nameStart, nameStart + nameLen)
                } else ""
                if (name.isNotEmpty() && !name.endsWith("/") && localOffset + 30 <= zipBytes.size && readIntLE(zipBytes, localOffset) == 0x04034B50) {
                    val lNameLen = readShortLE(zipBytes, localOffset + 26)
                    val lExtraLen = readShortLE(zipBytes, localOffset + 28)
                    val dataStart = localOffset + 30 + lNameLen + lExtraLen
                    val dataEnd = (dataStart + compSize).coerceAtMost(zipBytes.size)
                    if (dataStart in 0..dataEnd) {
                        result.add(RawZipEntry(name, method, zipBytes.copyOfRange(dataStart, dataEnd)))
                    }
                }
                cdPos += 46 + nameLen + extraLen + commentLen
                count++
            }
            if (result.isNotEmpty()) return result
        }

        // Fallback sequential scan for simple stored ZIPs
        var pos = 0
        while (pos + 30 <= zipBytes.size && readIntLE(zipBytes, pos) == 0x04034B50) {
            val method = readShortLE(zipBytes, pos + 8)
            val compSize = readIntLE(zipBytes, pos + 18)
            val nameLen = readShortLE(zipBytes, pos + 26)
            val extraLen = readShortLE(zipBytes, pos + 28)
            val nameStart = pos + 30
            val dataStart = nameStart + nameLen + extraLen
            val dataEnd = (dataStart + compSize).coerceAtMost(zipBytes.size)
            if (dataStart > zipBytes.size || compSize < 0) break
            val name = zipBytes.decodeToString(nameStart, nameStart + nameLen)
            if (!name.endsWith("/")) {
                result.add(RawZipEntry(name, method, zipBytes.copyOfRange(dataStart, dataEnd)))
            }
            pos = dataEnd
        }
        return result
    }

    private fun unescapeXml(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    /**
     * Converts unpacked XLSX XML entries (`xl/sharedStrings.xml`, `xl/worksheets/sheet*.xml`) into CSV lines.
     */
    fun xlsxEntriesToCsvLines(entries: Map<String, ByteArray>): String {
        val sharedStrings = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.decodeToString()?.let { xml ->
            val siRegex = Regex("<si\\b[^>]*>(.*?)</si>", RegexOption.DOT_MATCHES_ALL)
            val tRegex = Regex("<t\\b[^>]*>(.*?)</t>", RegexOption.DOT_MATCHES_ALL)
            for (si in siRegex.findAll(xml)) {
                val sb = StringBuilder()
                for (t in tRegex.findAll(si.groupValues[1])) {
                    sb.append(unescapeXml(t.groupValues[1]))
                }
                sharedStrings.add(sb.toString())
            }
        }

        val sheetXml = (entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/sheet") }?.value)
            ?.decodeToString()
            ?: return ""

        val rowRegex = Regex("<row\\b[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
        val cellRegex = Regex("<c\\b([^>]*)>(.*?)</c>", RegexOption.DOT_MATCHES_ALL)
        val typeAttrRegex = Regex("""\bt\s*=\s*"([^"]*)"""")
        val valRegex = Regex("<(?:v|t)\\b[^>]*>(.*?)</(?:v|t)>", RegexOption.DOT_MATCHES_ALL)

        val rowsOut = mutableListOf<List<String>>()
        for (rowMatch in rowRegex.findAll(sheetXml)) {
            val rowCells = mutableListOf<String>()
            for (cellMatch in cellRegex.findAll(rowMatch.groupValues[1])) {
                val attrs = cellMatch.groupValues[1]
                val body = cellMatch.groupValues[2]
                val cellType = typeAttrRegex.find(attrs)?.groupValues?.getOrNull(1).orEmpty()
                val textParts = valRegex.findAll(body).map { unescapeXml(it.groupValues[1]) }.toList()
                val raw = textParts.joinToString("")
                val resolved = if (cellType == "s") {
                    raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                } else {
                    raw
                }
                rowCells.add(resolved)
            }
            if (rowCells.isNotEmpty()) rowsOut.add(rowCells)
        }
        return rowsOut.joinToString("\n") { r -> r.joinToString(",") { it.replace(",", " ") } }
    }
}
