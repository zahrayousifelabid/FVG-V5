package com.example.platform

actual fun currentTimeMillis(): Long = (js("Date.now()") as Number).toLong()

actual fun elapsedRealtimeMillis(): Long =
    (js("(typeof performance !== 'undefined' && performance.now) ? Math.floor(performance.now()) : Date.now()") as Number).toLong()

actual fun localTimezoneOffsetMillis(utcMillis: Long): Long {
    val msDouble = utcMillis.toDouble()
    val offsetMinutes = (js("-(new Date(msDouble).getTimezoneOffset())") as Number).toLong()
    return offsetMinutes * 60_000L
}

actual fun formatFixedDecimal(value: Double, decimals: Int): String {
    val d = decimals.coerceAtLeast(0)
    return (js("Number(value).toFixed(d)") as String)
}
