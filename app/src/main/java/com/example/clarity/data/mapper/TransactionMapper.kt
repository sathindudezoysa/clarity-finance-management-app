package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.google.firebase.firestore.DocumentSnapshot

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id, userId, TransactionType.valueOf(type), amountMinor, category, note,
    dateEpochMillis, createdAt, updatedAt
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id, userId, type.name, amountMinor, category, note, dateEpochMillis, createdAt, updatedAt
)

fun Transaction.toFirestoreMap(): Map<String, Any> = mapOf(
    "id" to id, "userId" to userId, "type" to type.name, "amountMinor" to amountMinor,
    "category" to category, "note" to note, "dateEpochMillis" to dateEpochMillis,
    "createdAt" to createdAt, "updatedAt" to updatedAt
)

fun DocumentSnapshot.toDomain(): Transaction? = data?.toTransaction(id)

// The snapshot adapter delegates to this pure parser, which can be tested without Firebase.
fun Map<String, Any?>.toTransaction(documentId: String): Transaction? {
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    val typeName = this["type"] as? String ?: return null
    val type = TransactionType.values().firstOrNull { it.name == typeName } ?: return null
    val amount = this["amountMinor"] as? Long ?: return null
    val category = this["category"] as? String ?: return null
    val note = this["note"] as? String ?: return null
    val date = this["dateEpochMillis"] as? Long ?: return null
    val created = this["createdAt"] as? Long ?: return null
    val updated = this["updatedAt"] as? Long ?: return null
    if (id != documentId || id.isBlank() || id.length > 128 || '/' in id ||
        userId.isBlank() || userId.length > 128 || amount <= 0 ||
        category.isBlank() || category.length > 100 || note.length > 2000 ||
        created < 0 || updated < created) return null
    return Transaction(id, userId, type, amount, category, note, date, created, updated)
}
