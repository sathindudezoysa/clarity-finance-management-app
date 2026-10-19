package com.example.clarity.data.local.entity

import androidx.room.Entity

@Entity(tableName = "income_sources", primaryKeys = ["userId", "id"])
data class IncomeSourceEntity(
    val userId: String, val id: String, val name: String, val amountMinor: Long,
    val accountId: String, val recurring: Boolean, val active: Boolean,
    val createdAt: Long, val updatedAt: Long, val lastConfirmedMonth: String?, val pendingSync: Boolean
)
