package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "transactions", indices = [Index(value = ["userId"]), Index(value = ["accountId"])])
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
    val isDeleted: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val accountId: String? = null,
    val currency: String = "LKR",
    val targetCurrency: String = "LKR",
    @androidx.room.Embedded(prefix = "conversion_")
    val conversion: com.example.clarity.domain.model.ConversionSnapshot? =
        if (currency == targetCurrency) com.example.clarity.domain.model.ConversionSnapshot.identity(amountMinor) else null,
    val pendingConversion: Boolean = conversion == null
)
