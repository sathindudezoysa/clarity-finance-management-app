package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "budgets", primaryKeys = ["userId", "id"], indices = [Index(value = ["userId", "month", "categoryKey"], unique = true)])
data class BudgetEntity(
    val id: String, val userId: String, val month: String, val category: String, val categoryKey: String,
    val limitMinor: Long, val currency: String, val active: Boolean,
    val createdAt: Long, val updatedAt: Long, val pendingSync: Boolean
)

// Local only: a durable high-water mark, independent of budget cloud synchronization.
@Entity(tableName = "budget_alerts", primaryKeys = ["userId", "budgetId"])
data class BudgetAlertEntity(val userId: String, val budgetId: String, val level: Int)

@Entity(tableName = "budget_resolutions", primaryKeys = ["userId", "transactionId", "currency"])
data class BudgetResolutionEntity(
    val userId: String, val transactionId: String, val transactionVersion: Long,
    val currency: String, val amountMinor: Long
)
