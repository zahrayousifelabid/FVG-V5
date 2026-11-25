package com.example.domain.math

actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY
}

actual class MathContext actual constructor(
    val precision: Int,
    val roundingMode: RoundingMode
)

private fun jsBigInt(s: String): dynamic = js("BigInt(s)")
private fun jsBiAdd(a: dynamic, b: dynamic): dynamic = js("a + b")
private fun jsBiSub(a: dynamic, b: dynamic): dynamic = js("a - b")
private fun jsBiMul(a: dynamic, b: dynamic): dynamic = js("a * b")
private fun jsBiDiv(a: dynamic, b: dynamic): dynamic = js("a / b")
private fun jsBiRem(a: dynamic, b: dynamic): dynamic = js("a % b")
private fun jsBiNeg(a: dynamic): dynamic = js("-a")
private fun jsBiAbs(a: dynamic): dynamic = js("a < BigInt(0) ? -a : a")
private fun jsBiSign(a: dynamic): Int = js("a === BigInt(0) ? 0 : (a < BigInt(0) ? -1 : 1)")
private fun jsBiCmp(a: dynamic, b: dynamic): Int = js("a < b ? -1 : (a > b ? 1 : 0)")
private fun jsBiStr(a: dynamic): String = js("a.toString()")
private fun jsBiToDouble(a: dynamic): Double = js("Number(a)")

private val BI_ZERO: dynamic = jsBigInt("0")
private val BI_ONE: dynamic = jsBigInt("1")
private val BI_TWO: dynamic = jsBigInt("2")
private val BI_TEN: dynamic = jsBigInt("10")

private fun pow10(n: Int): dynamic {
    if (n <= 0) return BI_ONE
    return jsBigInt("1" + "0".repeat(n))
}

actual class BigDecimal private constructor(
    private val unscaled: dynamic,
    private val scaleVal: Int
) : Comparable<BigDecimal> {

    actual constructor(value: String) : this(
        parsed = parseDecimalString(value)
    )

    private constructor(parsed: Pair<dynamic, Int>) : this(parsed.first, parsed.second)

    actual constructor(value: Int) : this(jsBigInt(value.toString()), 0)
    actual constructor(value: Long) : this(jsBigInt(value.toString()), 0)
    actual constructor(value: Double) : this(parseDecimalString(formatDoubleExact(value)))

    actual fun add(augend: BigDecimal): BigDecimal {
        val targetScale = maxOf(scaleVal, augend.scaleVal)
        val a = if (scaleVal == targetScale) unscaled else jsBiMul(unscaled, pow10(targetScale - scaleVal))
        val b = if (augend.scaleVal == targetScale) augend.unscaled else jsBiMul(augend.unscaled, pow10(targetScale - augend.scaleVal))
        return BigDecimal(jsBiAdd(a, b), targetScale)
    }

    actual fun add(augend: BigDecimal, mc: MathContext): BigDecimal =
        add(augend).roundToContext(mc)

    actual fun subtract(subtrahend: BigDecimal): BigDecimal {
        val targetScale = maxOf(scaleVal, subtrahend.scaleVal)
        val a = if (scaleVal == targetScale) unscaled else jsBiMul(unscaled, pow10(targetScale - scaleVal))
        val b = if (subtrahend.scaleVal == targetScale) subtrahend.unscaled else jsBiMul(subtrahend.unscaled, pow10(targetScale - subtrahend.scaleVal))
        return BigDecimal(jsBiSub(a, b), targetScale)
    }

    actual fun subtract(subtrahend: BigDecimal, mc: MathContext): BigDecimal =
        subtract(subtrahend).roundToContext(mc)

    actual fun multiply(multiplicand: BigDecimal): BigDecimal {
        return BigDecimal(jsBiMul(unscaled, multiplicand.unscaled), scaleVal + multiplicand.scaleVal)
    }

    actual fun multiply(multiplicand: BigDecimal, mc: MathContext): BigDecimal {
        return multiply(multiplicand).roundToContext(mc)
    }

    actual fun divide(divisor: BigDecimal): BigDecimal =
        divide(divisor, MathContext(0, RoundingMode.UNNECESSARY))

    actual fun divide(divisor: BigDecimal, mc: MathContext): BigDecimal {
        val divSign = jsBiSign(divisor.unscaled)
        if (divSign == 0) throw ArithmeticException("Division by zero")
        val preferredScale = scaleVal - divisor.scaleVal
        val numSign = jsBiSign(unscaled)
        if (numSign == 0) {
            return BigDecimal(BI_ZERO, preferredScale)
        }
        val prec = mc.precision
        if (prec <= 0) {
            // Exact division needed
            val numAbs = jsBiAbs(unscaled)
            val denAbs = jsBiAbs(divisor.unscaled)
            var extraScale = 0
            var scaledNum = numAbs
            var rem = jsBiRem(scaledNum, denAbs)
            while (jsBiSign(rem) != 0 && extraScale < 64) {
                scaledNum = jsBiMul(scaledNum, BI_TEN)
                extraScale++
                rem = jsBiRem(scaledNum, denAbs)
            }
            if (jsBiSign(rem) != 0) throw ArithmeticException("Non-terminating decimal expansion")
            var q = jsBiDiv(scaledNum, denAbs)
            if ((numSign < 0) != (divSign < 0)) q = jsBiNeg(q)
            return BigDecimal(q, preferredScale + extraScale)
        }

        val numAbs = jsBiAbs(unscaled)
        val denAbs = jsBiAbs(divisor.unscaled)
        val numDigits = jsBiStr(numAbs).length
        val denDigits = jsBiStr(denAbs).length

        var shift = (prec + denDigits - numDigits)
        if (shift < 0) shift = 0
        var scaledNum = if (shift > 0) jsBiMul(numAbs, pow10(shift)) else numAbs
        // Ensure quotient has at least `prec` digits
        var q = jsBiDiv(scaledNum, denAbs)
        while (jsBiStr(q).length < prec) {
            shift++
            scaledNum = jsBiMul(scaledNum, BI_TEN)
            q = jsBiDiv(scaledNum, denAbs)
        }
        var qDigits = jsBiStr(q).length
        while (qDigits > prec) {
            // Too many digits in quotient (if shift was positive we can reduce shift, else round away extra)
            if (shift > 0) {
                shift--
                scaledNum = jsBiDiv(scaledNum, BI_TEN)
                q = jsBiDiv(scaledNum, denAbs)
                qDigits = jsBiStr(q).length
            } else {
                break
            }
        }
        val rem = jsBiRem(scaledNum, denAbs)
        val isNeg = (numSign < 0) != (divSign < 0)
        val inc = shouldIncrement(q, rem, denAbs, isNeg, mc.roundingMode)
        var finalQ = if (inc) jsBiAdd(q, BI_ONE) else q
        var finalScale = preferredScale + shift

        // If rounding caused overflow to prec+1 digits, divide by 10 and decrement scale
        if (jsBiStr(finalQ).length > prec) {
            val ovRem = jsBiRem(finalQ, BI_TEN)
            val ovQ = jsBiDiv(finalQ, BI_TEN)
            val ovInc = shouldIncrement(ovQ, ovRem, BI_TEN, isNeg, mc.roundingMode)
            finalQ = if (ovInc) jsBiAdd(ovQ, BI_ONE) else ovQ
            finalScale--
        }

        // If exact (remainder was 0), strip trailing zeros down to preferredScale (matching JDK behavior)
        if (jsBiSign(rem) == 0) {
            while (finalScale > preferredScale && jsBiSign(jsBiRem(finalQ, BI_TEN)) == 0) {
                finalQ = jsBiDiv(finalQ, BI_TEN)
                finalScale--
            }
        }

        val signedQ = if (isNeg && jsBiSign(finalQ) != 0) jsBiNeg(finalQ) else finalQ
        return BigDecimal(signedQ, finalScale)
    }

    actual fun divide(divisor: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal {
        val divSign = jsBiSign(divisor.unscaled)
        if (divSign == 0) throw ArithmeticException("Division by zero")
        val numSign = jsBiSign(unscaled)
        if (numSign == 0) return BigDecimal(BI_ZERO, scale)

        val scaleDiff = scale - (scaleVal - divisor.scaleVal)
        val numAbs = jsBiAbs(unscaled)
        val denAbs = jsBiAbs(divisor.unscaled)

        val scaledNum: dynamic
        val scaledDen: dynamic
        if (scaleDiff >= 0) {
            scaledNum = jsBiMul(numAbs, pow10(scaleDiff))
            scaledDen = denAbs
        } else {
            scaledNum = numAbs
            scaledDen = jsBiMul(denAbs, pow10(-scaleDiff))
        }
        val q = jsBiDiv(scaledNum, scaledDen)
        val rem = jsBiRem(scaledNum, scaledDen)
        val isNeg = (numSign < 0) != (divSign < 0)
        val inc = shouldIncrement(q, rem, scaledDen, isNeg, roundingMode)
        val absFinal = if (inc) jsBiAdd(q, BI_ONE) else q
        val signedFinal = if (isNeg && jsBiSign(absFinal) != 0) jsBiNeg(absFinal) else absFinal
        return BigDecimal(signedFinal, scale)
    }

    actual fun setScale(newScale: Int): BigDecimal = setScale(newScale, RoundingMode.UNNECESSARY)

    actual fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal {
        if (newScale == scaleVal) return this
        val diff = newScale - scaleVal
        if (diff > 0) {
            return BigDecimal(jsBiMul(unscaled, pow10(diff)), newScale)
        }
        val factor = pow10(-diff)
        val isNeg = jsBiSign(unscaled) < 0
        val absU = jsBiAbs(unscaled)
        val q = jsBiDiv(absU, factor)
        val rem = jsBiRem(absU, factor)
        val inc = shouldIncrement(q, rem, factor, isNeg, roundingMode)
        val absFinal = if (inc) jsBiAdd(q, BI_ONE) else q
        val signedFinal = if (isNeg && jsBiSign(absFinal) != 0) jsBiNeg(absFinal) else absFinal
        return BigDecimal(signedFinal, newScale)
    }

    private fun roundToContext(mc: MathContext): BigDecimal {
        val prec = mc.precision
        if (prec <= 0) return this
        val s = jsBiSign(unscaled)
        if (s == 0) return this
        val absU = jsBiAbs(unscaled)
        val digits = jsBiStr(absU).length
        if (digits <= prec) return this
        val drop = digits - prec
        val factor = pow10(drop)
        val isNeg = s < 0
        val q = jsBiDiv(absU, factor)
        val rem = jsBiRem(absU, factor)
        val inc = shouldIncrement(q, rem, factor, isNeg, mc.roundingMode)
        var absFinal = if (inc) jsBiAdd(q, BI_ONE) else q
        var finalScale = scaleVal - drop
        if (jsBiStr(absFinal).length > prec) {
            val ovRem = jsBiRem(absFinal, BI_TEN)
            val ovQ = jsBiDiv(absFinal, BI_TEN)
            val ovInc = shouldIncrement(ovQ, ovRem, BI_TEN, isNeg, mc.roundingMode)
            absFinal = if (ovInc) jsBiAdd(ovQ, BI_ONE) else ovQ
            finalScale--
        }
        val signedFinal = if (isNeg && jsBiSign(absFinal) != 0) jsBiNeg(absFinal) else absFinal
        return BigDecimal(signedFinal, finalScale)
    }

    actual fun abs(): BigDecimal = if (jsBiSign(unscaled) < 0) BigDecimal(jsBiAbs(unscaled), scaleVal) else this

    actual fun negate(): BigDecimal = if (jsBiSign(unscaled) == 0) this else BigDecimal(jsBiNeg(unscaled), scaleVal)

    actual fun signum(): Int = jsBiSign(unscaled)

    actual fun max(other: BigDecimal): BigDecimal = if (compareTo(other) >= 0) this else other

    actual fun min(other: BigDecimal): BigDecimal = if (compareTo(other) <= 0) this else other

    actual fun scale(): Int = scaleVal

    actual fun stripTrailingZeros(): BigDecimal {
        if (jsBiSign(unscaled) == 0) return ZERO
        var u = unscaled
        var sc = scaleVal
        while (jsBiSign(jsBiRem(u, BI_TEN)) == 0) {
            u = jsBiDiv(u, BI_TEN)
            sc--
        }
        return BigDecimal(u, sc)
    }

    actual override fun compareTo(other: BigDecimal): Int {
        val targetScale = maxOf(scaleVal, other.scaleVal)
        val a = if (scaleVal == targetScale) unscaled else jsBiMul(unscaled, pow10(targetScale - scaleVal))
        val b = if (other.scaleVal == targetScale) other.unscaled else jsBiMul(other.unscaled, pow10(targetScale - other.scaleVal))
        return jsBiCmp(a, b)
    }

    actual fun toDouble(): Double {
        if (jsBiSign(unscaled) == 0) return 0.0
        val plain = toPlainString()
        return plain.toDoubleOrNull() ?: (jsBiToDouble(unscaled) * (js("Math.pow(10, -scaleVal)") as Number).toDouble())
    }

    actual fun toFloat(): Float = toDouble().toFloat()

    actual fun toInt(): Int = toLong().toInt()

    actual fun toLong(): Long {
        val truncated = if (scaleVal > 0) {
            jsBiDiv(unscaled, pow10(scaleVal))
        } else if (scaleVal < 0) {
            jsBiMul(unscaled, pow10(-scaleVal))
        } else {
            unscaled
        }
        return jsBiStr(truncated).toLongOrNull() ?: 0L
    }

    actual fun toPlainString(): String {
        val sign = jsBiSign(unscaled)
        if (sign == 0) {
            return if (scaleVal <= 0) "0" else "0." + "0".repeat(scaleVal)
        }
        val prefix = if (sign < 0) "-" else ""
        val digits = jsBiStr(jsBiAbs(unscaled))
        return when {
            scaleVal == 0 -> prefix + digits
            scaleVal < 0 -> prefix + digits + "0".repeat(-scaleVal)
            digits.length <= scaleVal -> prefix + "0." + "0".repeat(scaleVal - digits.length) + digits
            else -> {
                val split = digits.length - scaleVal
                prefix + digits.substring(0, split) + "." + digits.substring(split)
            }
        }
    }

    override fun toString(): String = toPlainString()

    override fun equals(other: Any?): Boolean {
        if (other !is BigDecimal) return false
        return scaleVal == other.scaleVal && jsBiCmp(unscaled, other.unscaled) == 0
    }

    override fun hashCode(): Int {
        var result = jsBiStr(unscaled).hashCode()
        result = 31 * result + scaleVal
        return result
    }

    actual companion object {
        actual val ZERO: BigDecimal = BigDecimal(BI_ZERO, 0)
        actual val ONE: BigDecimal = BigDecimal(BI_ONE, 0)
        actual val TEN: BigDecimal = BigDecimal(BI_TEN, 0)
        actual fun valueOf(value: Double): BigDecimal = BigDecimal(value)
        actual fun valueOf(value: Long): BigDecimal = BigDecimal(value)
    }
}

private fun shouldIncrement(
    qAbs: dynamic,
    remAbs: dynamic,
    divisorAbs: dynamic,
    isNeg: Boolean,
    mode: RoundingMode
): Boolean {
    if (jsBiSign(remAbs) == 0) return false
    return when (mode) {
        RoundingMode.UNNECESSARY -> throw ArithmeticException("Rounding necessary")
        RoundingMode.UP -> true
        RoundingMode.DOWN -> false
        RoundingMode.CEILING -> !isNeg
        RoundingMode.FLOOR -> isNeg
        RoundingMode.HALF_UP -> jsBiCmp(jsBiMul(remAbs, BI_TWO), divisorAbs) >= 0
        RoundingMode.HALF_DOWN -> jsBiCmp(jsBiMul(remAbs, BI_TWO), divisorAbs) > 0
        RoundingMode.HALF_EVEN -> {
            val cmp = jsBiCmp(jsBiMul(remAbs, BI_TWO), divisorAbs)
            if (cmp > 0) true
            else if (cmp < 0) false
            else jsBiSign(jsBiRem(qAbs, BI_TWO)) != 0
        }
    }
}

private fun formatDoubleExact(value: Double): String {
    require(value.isFinite()) { "Infinite or NaN" }
    val s = value.toString()
    return if (s.endsWith(".0")) s else s
}

private fun parseDecimalString(raw: String): Pair<dynamic, Int> {
    val s = raw.trim()
    if (s.isEmpty()) throw NumberFormatException("Empty BigDecimal")
    val expSplit = s.split('e', 'E')
    require(expSplit.size <= 2) { "Invalid BigDecimal: $raw" }
    val mantissaRaw = expSplit[0]
    val exp = if (expSplit.size == 2) expSplit[1].toInt() else 0

    var idx = 0
    var negative = false
    if (mantissaRaw[0] == '-') {
        negative = true
        idx++
    } else if (mantissaRaw[0] == '+') {
        idx++
    }
    val body = mantissaRaw.substring(idx)
    val dotIdx = body.indexOf('.')
    val intPart: String
    val fracPart: String
    if (dotIdx >= 0) {
        intPart = body.substring(0, dotIdx)
        fracPart = body.substring(dotIdx + 1)
        require(!fracPart.contains('.')) { "Invalid BigDecimal: $raw" }
    } else {
        intPart = body
        fracPart = ""
    }
    val combined = intPart + fracPart
    require(combined.isNotEmpty() && combined.all { it in '0'..'9' }) { "Invalid BigDecimal: $raw" }
    val trimmedDigits = combined.trimStart('0')
    val bi = if (trimmedDigits.isEmpty()) {
        BI_ZERO
    } else {
        jsBigInt((if (negative) "-" else "") + trimmedDigits)
    }
    val scale = fracPart.length - exp
    return bi to scale
}
