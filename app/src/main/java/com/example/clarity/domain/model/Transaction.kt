package com.example.clarity.domain.model

import java.util.UUID

enum class TransactionError { SIGN_IN_REQUIRED, ACCOUNT_REQUIRED, ACCOUNT_NOT_FOUND }

class TransactionException(val error: TransactionError, message: String? = null) : Exception(message)

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountMinor: Long,
    val category: String = "General",
    val note: String = "",
    val dateEpochMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val accountId: String? = null,
    // Local funding metadata derived from the wallet link, never serialized to Firestore.
    val isCash: Boolean = false,
    val currency: String = "LKR",
    val targetCurrency: String = "LKR",
    val conversion: ConversionSnapshot? = if (currency == targetCurrency) ConversionSnapshot.identity(amountMinor) else null,
    val pendingConversion: Boolean = conversion == null
)
