package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.TransactionTagEntity
import com.example.clarity.domain.model.CategoryTag
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType

fun TransactionTagEntity.toDomain(): CategoryTag {
    val txType = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE)
    return CategoryTag(
        id = TransactionTags.tagId(txType, name),
        userId = userId,
        name = name,
        type = txType,
        isDefault = isDefault,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun TransactionTagEntity.toFirestoreMap(): Map<String, Any> {
    val txType = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE)
    return mapOf(
        "id" to TransactionTags.tagId(txType, name),
        "userId" to userId,
        "name" to name.trim(),
        "type" to txType.name,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
}

fun Map<String, Any?>?.toTransactionTagEntity(expectedId: String, expectedUid: String): TransactionTagEntity? {
    if (this == null) return null
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    val name = (this["name"] as? String)?.trim() ?: return null
    val typeStr = this["type"] as? String ?: return null
    val createdAt = (this["createdAt"] as? Number)?.toLong() ?: return null
    val updatedAt = (this["updatedAt"] as? Number)?.toLong() ?: createdAt
    if (id != expectedId || userId != expectedUid || name.isEmpty() || name.length > 100) return null
    val txType = runCatching { TransactionType.valueOf(typeStr) }.getOrNull() ?: return null
    if (TransactionTags.tagId(txType, name) != id) return null
    return TransactionTagEntity(
        userId = userId,
        name = name,
        type = txType.name,
        isDefault = TransactionTags.isDefault(txType, name),
        createdAt = createdAt,
        updatedAt = maxOf(createdAt, updatedAt),
        pendingSync = false
    )
}
