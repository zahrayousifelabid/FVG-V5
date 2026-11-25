package com.example.domain.math

expect enum class RoundingMode {
    UP,
    DOWN,
    CEILING,
    FLOOR,
    HALF_UP,
    HALF_DOWN,
    HALF_EVEN,
    UNNECESSARY
}

expect class MathContext(precision: Int, roundingMode: RoundingMode)

expect class BigDecimal : Comparable<BigDecimal> {
    constructor(value: String)
    constructor(value: Int)
    constructor(value: Long)
    constructor(value: Double)

    fun add(augend: BigDecimal): BigDecimal
    fun add(augend: BigDecimal, mc: MathContext): BigDecimal
    fun subtract(subtrahend: BigDecimal): BigDecimal
    fun subtract(subtrahend: BigDecimal, mc: MathContext): BigDecimal
    fun multiply(multiplicand: BigDecimal): BigDecimal
    fun multiply(multiplicand: BigDecimal, mc: MathContext): BigDecimal
    fun divide(divisor: BigDecimal): BigDecimal
    fun divide(divisor: BigDecimal, mc: MathContext): BigDecimal
    fun divide(divisor: BigDecimal, scale: Int, roundingMode: RoundingMode): BigDecimal
    fun setScale(newScale: Int): BigDecimal
    fun setScale(newScale: Int, roundingMode: RoundingMode): BigDecimal
    fun abs(): BigDecimal
    fun negate(): BigDecimal
    fun signum(): Int
    fun max(other: BigDecimal): BigDecimal
    fun min(other: BigDecimal): BigDecimal
    fun scale(): Int
    fun stripTrailingZeros(): BigDecimal
    override fun compareTo(other: BigDecimal): Int
    fun toDouble(): Double
    fun toFloat(): Float
    fun toInt(): Int
    fun toLong(): Long
    fun toPlainString(): String

    companion object {
        val ZERO: BigDecimal
        val ONE: BigDecimal
        val TEN: BigDecimal
        fun valueOf(value: Double): BigDecimal
        fun valueOf(value: Long): BigDecimal
    }
}
