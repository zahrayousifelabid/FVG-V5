package com.example.data.model

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
actual annotation class FvgIgnoreExtraProperties

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
actual annotation class FvgDocumentId

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
actual annotation class FvgServerTimestamp

actual class FvgTimestamp(val epochMillis: Long = 0L) : Comparable<FvgTimestamp> {
    override fun compareTo(other: FvgTimestamp): Int = epochMillis.compareTo(other.epochMillis)
    override fun equals(other: Any?): Boolean = other is FvgTimestamp && epochMillis == other.epochMillis
    override fun hashCode(): Int = epochMillis.hashCode()
    override fun toString(): String = "FvgTimestamp($epochMillis)"
}

actual fun FvgTimestamp.toEpochMillis(): Long = epochMillis

actual fun fvgTimestampFromMillis(millis: Long): FvgTimestamp = FvgTimestamp(millis)

const val FVG_SERVER_TIMESTAMP_SENTINEL = "__FVG_SERVER_TIMESTAMP__"

actual fun fvgServerTimestampSentinel(): Any = FVG_SERVER_TIMESTAMP_SENTINEL
