package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.IncomeSourceEntity
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.util.IncomePolicy

fun IncomeSourceEntity.toDomain() = IncomeSource(id, userId, name, amountMinor, accountId, recurring,
    active, createdAt, updatedAt, lastConfirmedMonth, pendingSync)
fun IncomeSource.toEntity() = IncomeSourceEntity(userId, id, name, amountMinor, accountId, recurring,
    active, createdAt, updatedAt, lastConfirmedMonth, pendingSync)
fun IncomeSource.toRemoteMap(): Map<String, Any?> = mapOf("id" to id, "userId" to userId,
    "name" to name, "amountMinor" to amountMinor, "accountId" to accountId, "recurring" to recurring,
    "active" to active, "createdAt" to createdAt, "updatedAt" to updatedAt, "lastConfirmedMonth" to lastConfirmedMonth)
fun Map<String, Any?>.toIncomeSource(id: String, uid: String): IncomeSource? {
    if (this["id"] != id || this["userId"] != uid || !IncomePolicy.validId(uid)) return null
    if (!containsKey("lastConfirmedMonth")) return null
    val month = this["lastConfirmedMonth"]
    if (month != null && month !is String) return null
    val source = IncomeSource(id, uid, this["name"] as? String ?: return null,
        this["amountMinor"].incomeLong() ?: return null, this["accountId"] as? String ?: return null,
        this["recurring"] as? Boolean ?: return null, this["active"] as? Boolean ?: return null,
        this["createdAt"].incomeLong() ?: return null, this["updatedAt"].incomeLong() ?: return null, month as? String)
    return source.takeIf { runCatching { IncomePolicy.validate(it) }.isSuccess }
}

// Firestore integer fields arrive as Long. Never truncate double-valued money or timestamps.
private fun Any?.incomeLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is Short -> toLong()
    is Byte -> toLong()
    else -> null
}
