package com.example.data.model

actual typealias FvgIgnoreExtraProperties = com.google.firebase.firestore.IgnoreExtraProperties
actual typealias FvgDocumentId = com.google.firebase.firestore.DocumentId
actual typealias FvgServerTimestamp = com.google.firebase.firestore.ServerTimestamp
actual typealias FvgTimestamp = com.google.firebase.Timestamp

actual fun FvgTimestamp.toEpochMillis(): Long = this.toDate().time

actual fun fvgTimestampFromMillis(millis: Long): FvgTimestamp {
    val sec = Math.floorDiv(millis, 1000L)
    val nano = (Math.floorMod(millis, 1000L) * 1_000_000L).toInt()
    return com.google.firebase.Timestamp(sec, nano)
}

actual fun fvgServerTimestampSentinel(): Any = com.google.firebase.firestore.FieldValue.serverTimestamp()
