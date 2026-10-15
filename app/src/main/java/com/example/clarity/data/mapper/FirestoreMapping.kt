package com.example.clarity.data.mapper

// Firestore and imported data may represent the same numeric field with different Number types.
internal fun Any?.toFirestoreLong(): Long? {
    val number = this as? Number ?: return null
    if (number is Double || number is Float) {
        val value = number.toDouble()
        // Avoid silently turning NaN, infinity or an out-of-range value into a valid timestamp.
        if (!value.isFinite() || value < Long.MIN_VALUE.toDouble() || value >= -Long.MIN_VALUE.toDouble()) return null
    }
    return number.toLong()
}

internal fun <T : Any> Iterable<Pair<String, Map<String, Any?>?>>.mapFirestoreDocuments(
    parse: (Map<String, Any?>, String) -> T?,
    warn: (String) -> Unit
): List<T> = mapNotNull { (id, data) ->
    val parsed = data?.let { parse(it, id) }
    if (parsed == null) warn(id)
    parsed
}
