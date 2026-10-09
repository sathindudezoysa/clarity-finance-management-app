package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index(value = ["userId"])])
data class TransactionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val type: String,
    val amountMinor: Long,
    val category: String,
    val note: String,
    val dateEpochMillis: Long,
    val createdAt: Long,
    val updatedAt: Long,
    // Local-only outbox metadata. Never serialized to Firestore.
    val pendingSync: Boolean = false,
    val isDeleted: Boolean = false
)
