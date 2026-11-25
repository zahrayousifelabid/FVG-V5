package com.example.domain.json

class FvgJsonObject(initial: Map<String, Any?> = emptyMap()) {
    private val map = linkedMapOf<String, Any?>()

    init {
        initial.forEach { (k, v) -> map[k] = v }
    }

    constructor(json: String) : this() {
        val parsed = FvgJsonParser(json).parseObject()
        map.putAll(parsed.map)
    }

    fun put(key: String, value: Any?): FvgJsonObject {
        map[key] = value
        return this
    }

    fun optString(key: String, fallback: String = ""): String {
        val v = map[key] ?: return fallback
        return v.toString()
    }

    fun optLong(key: String, fallback: Long = 0L): Long {
        val v = map[key] ?: return fallback
        return when (v) {
            is Number -> v.toLong()
            is String -> v.toLongOrNull() ?: fallback
            else -> fallback
        }
    }

    fun optDouble(key: String, fallback: Double = 0.0): Double {
        val v = map[key] ?: return fallback
        return when (v) {
            is Number -> v.toDouble()
            is String -> v.toDoubleOrNull() ?: fallback
            else -> fallback
        }
    }

    fun optBoolean(key: String, fallback: Boolean = false): Boolean {
        val v = map[key] ?: return fallback
        return when (v) {
            is Boolean -> v
            is String -> v.toBooleanStrictOrNull() ?: fallback
            else -> fallback
        }
    }

    fun optJSONArray(key: String): FvgJsonArray? = map[key] as? FvgJsonArray
    fun optJSONObject(key: String): FvgJsonObject? = map[key] as? FvgJsonObject
    fun keys(): Set<String> = map.keys
    fun toMap(): Map<String, Any?> = map.toMap()

    override fun toString(): String = toString(0)

    fun toString(indentSpaces: Int): String = buildString {
        appendObject(this@FvgJsonObject, this, indentSpaces, 0)
    }
}

class FvgJsonArray(initial: List<Any?> = emptyList()) {
    private val list = mutableListOf<Any?>()

    init {
        list.addAll(initial)
    }

    constructor(json: String) : this() {
        val parsed = FvgJsonParser(json).parseArray()
        list.addAll(parsed.list)
    }

    fun put(value: Any?): FvgJsonArray {
        list.add(value)
        return this
    }

    fun length(): Int = list.size
    fun getJSONObject(index: Int): FvgJsonObject = list[index] as FvgJsonObject
    fun optJSONObject(index: Int): FvgJsonObject? = list.getOrNull(index) as? FvgJsonObject
    fun optString(index: Int, fallback: String = ""): String = list.getOrNull(index)?.toString() ?: fallback
    fun toList(): List<Any?> = list.toList()

    override fun toString(): String = toString(0)

    fun toString(indentSpaces: Int): String = buildString {
        appendArray(this@FvgJsonArray, this, indentSpaces, 0)
    }
}

private fun escapeJsonString(s: String, out: StringBuilder) {
    out.append('"')
    for (ch in s) {
        when (ch) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\b' -> out.append("\\b")
            '\u000C' -> out.append("\\f")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> {
                if (ch.code < 0x20) {
                    val hex = ch.code.toString(16).padStart(4, '0')
                    out.append("\\u").append(hex)
                } else {
                    out.append(ch)
                }
            }
        }
    }
    out.append('"')
}

private fun appendValue(value: Any?, out: StringBuilder, indent: Int, level: Int) {
    when (value) {
        null -> out.append("null")
        is FvgJsonObject -> appendObject(value, out, indent, level)
        is FvgJsonArray -> appendArray(value, out, indent, level)
        is Map<*, *> -> {
            val obj = FvgJsonObject()
            value.forEach { (k, v) -> if (k != null) obj.put(k.toString(), v) }
            appendObject(obj, out, indent, level)
        }
        is Iterable<*> -> {
            val arr = FvgJsonArray()
            value.forEach { arr.put(it) }
            appendArray(arr, out, indent, level)
        }
        is Boolean -> out.append(value.toString())
        is Number -> out.append(value.toString())
        else -> escapeJsonString(value.toString(), out)
    }
}

private fun appendObject(obj: FvgJsonObject, out: StringBuilder, indent: Int, level: Int) {
    val entries = obj.toMap().entries.toList()
    if (entries.isEmpty()) {
        out.append("{}")
        return
    }
    out.append('{')
    val childPad = if (indent > 0) " ".repeat((level + 1) * indent) else ""
    val closePad = if (indent > 0) " ".repeat(level * indent) else ""
    entries.forEachIndexed { idx, (k, v) ->
        if (idx > 0) out.append(',')
        if (indent > 0) out.append('\n').append(childPad)
        escapeJsonString(k, out)
        out.append(if (indent > 0) ": " else ":")
        appendValue(v, out, indent, level + 1)
    }
    if (indent > 0) out.append('\n').append(closePad)
    out.append('}')
}

private fun appendArray(arr: FvgJsonArray, out: StringBuilder, indent: Int, level: Int) {
    val items = arr.toList()
    if (items.isEmpty()) {
        out.append("[]")
        return
    }
    out.append('[')
    val childPad = if (indent > 0) " ".repeat((level + 1) * indent) else ""
    val closePad = if (indent > 0) " ".repeat(level * indent) else ""
    items.forEachIndexed { idx, item ->
        if (idx > 0) out.append(',')
        if (indent > 0) out.append('\n').append(childPad)
        appendValue(item, out, indent, level + 1)
    }
    if (indent > 0) out.append('\n').append(closePad)
    out.append(']')
}

private class FvgJsonParser(private val src: String) {
    private var pos = 0

    private fun skipWs() {
        while (pos < src.length && src[pos].isWhitespace()) pos++
    }

    fun parseObject(): FvgJsonObject {
        skipWs()
        require(pos < src.length && src[pos] == '{') { "Expected '{' at $pos" }
        pos++
        val obj = FvgJsonObject()
        skipWs()
        if (pos < src.length && src[pos] == '}') {
            pos++
            return obj
        }
        while (true) {
            skipWs()
            val key = parseString()
            skipWs()
            require(pos < src.length && src[pos] == ':') { "Expected ':' at $pos" }
            pos++
            val value = parseValue()
            obj.put(key, value)
            skipWs()
            if (pos >= src.length) error("Unterminated JSON object")
            when (src[pos]) {
                ',' -> pos++
                '}' -> {
                    pos++
                    return obj
                }
                else -> error("Expected ',' or '}' at $pos")
            }
        }
    }

    fun parseArray(): FvgJsonArray {
        skipWs()
        require(pos < src.length && src[pos] == '[') { "Expected '[' at $pos" }
        pos++
        val arr = FvgJsonArray()
        skipWs()
        if (pos < src.length && src[pos] == ']') {
            pos++
            return arr
        }
        while (true) {
            arr.put(parseValue())
            skipWs()
            if (pos >= src.length) error("Unterminated JSON array")
            when (src[pos]) {
                ',' -> pos++
                ']' -> {
                    pos++
                    return arr
                }
                else -> error("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun parseValue(): Any? {
        skipWs()
        if (pos >= src.length) error("Unexpected end of JSON")
        return when (src[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> {
                require(src.startsWith("true", pos)) { "Invalid token at $pos" }
                pos += 4
                true
            }
            'f' -> {
                require(src.startsWith("false", pos)) { "Invalid token at $pos" }
                pos += 5
                false
            }
            'n' -> {
                require(src.startsWith("null", pos)) { "Invalid token at $pos" }
                pos += 4
                null
            }
            else -> parseNumber()
        }
    }

    private fun parseString(): String {
        require(pos < src.length && src[pos] == '"') { "Expected '\"' at $pos" }
        pos++
        val sb = StringBuilder()
        while (pos < src.length) {
            val ch = src[pos++]
            when (ch) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= src.length) error("Unterminated escape")
                    when (val esc = src[pos++]) {
                        '"', '\\', '/' -> sb.append(esc)
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            require(pos + 4 <= src.length) { "Invalid unicode escape" }
                            val code = src.substring(pos, pos + 4).toInt(16)
                            pos += 4
                            sb.append(code.toChar())
                        }
                        else -> error("Unknown escape '\\$esc'")
                    }
                }
                else -> sb.append(ch)
            }
        }
        error("Unterminated string")
    }

    private fun parseNumber(): Number {
        val start = pos
        while (pos < src.length && (src[pos].isDigit() || src[pos] in "-+.eE")) {
            pos++
        }
        val token = src.substring(start, pos)
        return if (token.contains('.') || token.contains('e') || token.contains('E')) {
            token.toDouble()
        } else {
            val l = token.toLong()
            if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
        }
    }
}
