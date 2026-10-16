package com.example.clarity.data.local.entity

import androidx.room.Entity

// Separate from transactions: adding evidence never changes financial amounts or their outbox.
@Entity(tableName = "transaction_proofs", primaryKeys = ["userId", "transactionId", "id"])
data class TransactionProofEntity(
    val userId: String,
    val transactionId: String,
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val verifiedAmountMinor: Long?,
    val pendingUpload: Boolean,
    val revision: Long
)
