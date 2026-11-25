package com.example.platform

import android.os.SystemClock
import java.util.Locale
import java.util.TimeZone

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

actual fun localTimezoneOffsetMillis(utcMillis: Long): Long =
    TimeZone.getDefault().getOffset(utcMillis).toLong()

actual fun formatFixedDecimal(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals.coerceAtLeast(0)}f", value)
