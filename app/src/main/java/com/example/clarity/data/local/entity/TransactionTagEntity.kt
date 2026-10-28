package com.example.clarity.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.example.clarity.domain.model.TransactionType

/**
 * Category tags scoped to their owner and transaction type (EXPENSE / INCOME),
 * seeded with defaults and synced with Firestore for custom user preferences.
 */
@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["userId", "type", "name"]
)
data class TransactionTagEntity(
    val userId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val type: String = TransactionType.EXPENSE.name,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val pendingSync: Boolean = false
)
