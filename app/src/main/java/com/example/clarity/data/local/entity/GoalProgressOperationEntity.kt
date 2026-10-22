package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "goal_progress_operations",
    primaryKeys = ["userId", "id"],
    indices = [Index(value = ["userId", "goalId", "state"])]
)
data class GoalProgressOperationEntity(
    val userId: String,
    val id: String,
    val goalId: String,
    val type: String,
    val amountMinor: Long,
    val createdAt: Long,
    val state: String,
    val sourceAccountId: String? = null
)
