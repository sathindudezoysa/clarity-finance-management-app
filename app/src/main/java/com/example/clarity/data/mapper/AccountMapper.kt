package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.domain.model.Account

fun AccountEntity.toDomain(): Account = Account(
    id, userId, name, bankName, accountType, currency, isDefault, createdAt, updatedAt
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id, userId, name, bankName, accountType, currency, isDefault, createdAt, updatedAt
)

fun Account.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id, "userId" to userId, "name" to name, "bankName" to bankName,
    "accountType" to accountType, "currency" to currency, "isDefault" to isDefault,
    "createdAt" to createdAt, "updatedAt" to updatedAt
)

fun Map<String, Any?>.toAccount(documentId: String): Account? {
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    val name = this["name"] as? String ?: return null
    val bankName = this["bankName"] as? String ?: return null
    val accountType = this["accountType"]
    val currency = this["currency"] as? String ?: return null
    val isDefault = this["isDefault"] as? Boolean ?: return null
    val created = this["createdAt"] as? Long ?: return null
    val updated = this["updatedAt"] as? Long ?: return null
    if (id != documentId || id.isBlank() || id.length > 128 || '/' in id ||
        userId.isBlank() || userId.length > 128 || name.isBlank() || name.length > 100 ||
        bankName.length > 100 || (accountType != null && (accountType !is String || accountType.length > 100)) ||
        !currency.matches(Regex("[A-Z]{3}")) || created < 0 || updated < created) return null
    return Account(id, userId, name, bankName, accountType as String?, currency, isDefault, created, updated)
}
