package com.example.clarity.domain.model

import java.util.UUID

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountMinor: Long,
    val category: String = "General",
    val note: String = "",
    val dateEpochMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)
