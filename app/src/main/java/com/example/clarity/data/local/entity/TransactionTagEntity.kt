package com.example.clarity.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/** Names are scoped to their owner and retained when a transaction is edited or deleted. */
@Entity(tableName = "transaction_tags", primaryKeys = ["userId", "name"])
data class TransactionTagEntity(
    val userId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String
)
