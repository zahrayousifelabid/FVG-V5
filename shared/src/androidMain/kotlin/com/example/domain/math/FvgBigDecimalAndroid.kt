package com.example.domain.math

actual enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY;

    fun toJava(): java.math.RoundingMode = when (this) {
        UP -> java.math.RoundingMode.UP
        DOWN -> java.math.RoundingMode.DOWN
        CEILING -> java.math.RoundingMode.CEILING
        FLOOR -> java.math.RoundingMode.FLOOR
        HALF_UP -> java.math.RoundingMode.HALF_UP
        HALF_DOWN -> java.math.RoundingMode.HALF_DOWN
        HALF_EVEN -> java.math.RoundingMode.HALF_EVEN
        UNNECESSARY -> java.math.RoundingMode.UNNECESSARY
    }
}

actual class MathContext actual constructor(
    precision: Int,
    roundingMode: RoundingMode
) {
    val raw: java.math.MathContext = java.math.MathContext(precision, roundingMode.toJava())
}

actual class BigDecimal private constructor(val raw: java.math.BigDecimal) : Comparable<BigDecimal> {
    actual constructor(value: String) : this(java.math.BigDecimal(value))
    actual constructor(value: Int) : this(java.math.BigDecimal(value))
    actual constructor(value: Long) : this(java.math.BigDecimal(value))
    actual constructor(value: Double) : this(java.math.BigDecimal(value))

    actual fun add(augend: BigDecimal): BigDecimal = BigDecimal(raw.add(augend.raw))
    actual fun add(augend: BigDecimal, mc: MathContext): BigDecimal = BigDecimal(raw.add(augend.raw, mc.raw))
    actual fun subtract(subtrahend: BigDecimal): BigDecimal = BigDecimal(raw.subtract(subtrahend.raw))
    actual fun subtract(subtrahend: BigDecimal, mc: MathContext): BigDecimal = BigDecimal(raw.subtract(subtrahend.raw, mc.raw))
    actual fun multiply(multiplicand: BigDecimal): BigDecimal = BigDecimal(raw.multiply(multiplicand.raw))
    actual fun multiply(multiplicand: BigDecimal, mc: MathContext): BigDecimal = BigDecimal(raw.multiply(multiplicand.raw, mc.raw))
    actual fun divide(divisor: BigDecimal): BigDecimal = BigDecimal(raw.divide(divisor.raw))
    actual fun divide(divisor: BigDecimal, mc: MathContext): BigDecimal = BigDecimal(raw.divide(divisor.raw, mc.raw))
    actual fun divide(divisor: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal =
        BigDecimal(raw.divide(divisor.raw, scale, roundingMode.toJava()))
    actual fun setScale(newScale: Int): BigDecimal = BigDecimal(raw.setScale(newScale))
    actual fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal =
        BigDecimal(raw.setScale(newScale, roundingMode.toJava()))
    actual fun abs(): BigDecimal = BigDecimal(raw.abs())
    actual fun negate(): BigDecimal = BigDecimal(raw.negate())
    actual fun signum(): Int = raw.signum()
    actual fun max(other: BigDecimal): BigDecimal = BigDecimal(raw.max(other.raw))
    actual fun min(other: BigDecimal): BigDecimal = BigDecimal(raw.min(other.raw))
    actual fun scale(): Int = raw.scale()
    actual fun stripTrailingZeros(): BigDecimal = BigDecimal(raw.stripTrailingZeros())
    actual override fun compareTo(other: BigDecimal): Int = raw.compareTo(other.raw)
    actual fun toDouble(): Double = raw.toDouble()
    actual fun toFloat(): Float = raw.toFloat()
    actual fun toInt(): Int = raw.toInt()
    actual fun toLong(): Long = raw.toLong()
    actual fun toPlainString(): String = raw.toPlainString()

    override fun equals(other: Any?): Boolean = other is BigDecimal && raw == other.raw
    override fun hashCode(): Int = raw.hashCode()
    override fun toString(): String = raw.toString()

    actual companion object {
        actual val ZERO: BigDecimal = BigDecimal(java.math.BigDecimal.ZERO)
        actual val ONE: BigDecimal = BigDecimal(java.math.BigDecimal.ONE)
        actual val TEN: BigDecimal = BigDecimal(java.math.BigDecimal.TEN)
        actual fun valueOf(value: Double): BigDecimal = BigDecimal(java.math.BigDecimal.valueOf(value))
        actual fun valueOf(value: Long): BigDecimal = BigDecimal(java.math.BigDecimal.valueOf(value))
    }
}
