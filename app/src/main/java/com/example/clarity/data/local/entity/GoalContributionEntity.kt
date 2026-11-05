package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "goal_contributions",
    primaryKeys = ["userId", "id"],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["goalId"])
    ]
)
data class GoalContributionEntity(
    val userId: String,
    val id: String,
    val goalId: String,
    val amountMinor: Long,
    val currency: String,
    val dateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val pendingSync: Boolean = false
)

