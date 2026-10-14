package com.example.clarity.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "accounts", indices = [
    Index(value = ["userId"]),
    Index(value = ["userId", "name"], unique = true)
])
data class AccountEntity(
    @PrimaryKey val id: String,
    val userId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val bankName: String,
    val accountType: String?,
    val currency: String,
    val isDefault: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    // Local-only outbox metadata, excluded from Firestore.
    val pendingSync: Boolean = false
)
