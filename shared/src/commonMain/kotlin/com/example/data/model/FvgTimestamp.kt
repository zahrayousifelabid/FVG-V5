package com.example.data.model

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class FvgIgnoreExtraProperties()

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class FvgDocumentId()

@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
)
@Retention(AnnotationRetention.RUNTIME)
expect annotation class FvgServerTimestamp()

expect class FvgTimestamp

expect fun FvgTimestamp.toEpochMillis(): Long
expect fun fvgTimestampFromMillis(millis: Long): FvgTimestamp
expect fun fvgServerTimestampSentinel(): Any
