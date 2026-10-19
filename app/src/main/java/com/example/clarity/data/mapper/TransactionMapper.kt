package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.CurrencyConversion
import com.google.firebase.firestore.DocumentSnapshot

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id, userId, TransactionType.valueOf(type), amountMinor, category, note,
    dateEpochMillis, createdAt, updatedAt, accountId,
    currency = currency, targetCurrency = targetCurrency, conversion = conversion, pendingConversion = pendingConversion
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id, userId, type.name, amountMinor, category, note, dateEpochMillis, createdAt, updatedAt,
    accountId = accountId, currency = currency, targetCurrency = targetCurrency,
    conversion = conversion, pendingConversion = pendingConversion
)

fun Transaction.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id, "userId" to userId, "type" to type.name, "amountMinor" to amountMinor,
    "category" to category, "note" to note, "dateEpochMillis" to dateEpochMillis,
    "createdAt" to createdAt, "updatedAt" to updatedAt, "accountId" to accountId,
    "currency" to currency, "targetCurrency" to targetCurrency, "pendingConversion" to pendingConversion,
    "conversion" to conversion?.let { mapOf("amountMinor" to it.amountMinor, "rate" to it.rate,
        "provider" to it.provider, "rateAt" to it.rateAt, "fetchedAt" to it.fetchedAt, "cached" to it.cached) }
)

fun DocumentSnapshot.toDomain(): Transaction? = data?.toTransaction(id)

// The snapshot adapter delegates to this pure parser, which can be tested without Firebase.
fun Map<String, Any?>.toTransaction(documentId: String): Transaction? {
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    val typeName = this["type"] as? String ?: return null
    val type = TransactionType.values().firstOrNull { it.name == typeName } ?: return null
    val amount = this["amountMinor"].toFirestoreLong() ?: return null
    val category = this["category"] as? String ?: return null
    val note = this["note"] as? String ?: return null
    val date = this["dateEpochMillis"].toFirestoreLong() ?: return null
    val created = this["createdAt"].toFirestoreLong() ?: return null
    val updated = this["updatedAt"].toFirestoreLong() ?: return null
    val accountId = this["accountId"]
    if (accountId != null && (accountId !is String || accountId.isBlank() || accountId.length > 128 || '/' in accountId)) return null
    if (id != documentId || id.isBlank() || id.length > 128 || '/' in id ||
        userId.isBlank() || userId.length > 128 || amount <= 0 ||
        category.isBlank() || category.length > 100 || note.length > 2000 ||
        created < 0 || updated < created) return null
    // The sole legacy interpretation is the former LKR-only contract. Partial new metadata is invalid.
    val keys = setOf("currency", "targetCurrency", "conversion", "pendingConversion")
    if (keys.none { containsKey(it) }) return Transaction(id, userId, type, amount, category, note, date, created, updated, accountId as? String)
    if (!keys.all { containsKey(it) }) return null
    return runCatching {
        val currency = this["currency"] as String
        val target = this["targetCurrency"] as String
        val pending = this["pendingConversion"] as Boolean
        val snapshot = this["conversion"]?.let { raw ->
            val map = raw as Map<*, *>
            require(map.keys == setOf("amountMinor", "rate", "provider", "rateAt", "fetchedAt", "cached"))
            ConversionSnapshot(requireNotNull(map["amountMinor"].toFirestoreLong()), map["rate"] as String,
                map["provider"] as String, requireNotNull(map["rateAt"].toFirestoreLong()),
                requireNotNull(map["fetchedAt"].toFirestoreLong()), map["cached"] as Boolean)
        }
        Transaction(id, userId, type, amount, category, note, date, created, updated, accountId as? String,
            currency = currency, targetCurrency = target, conversion = snapshot, pendingConversion = pending)
            .also { CurrencyConversion.validate(it) }
    }.getOrNull()
}
