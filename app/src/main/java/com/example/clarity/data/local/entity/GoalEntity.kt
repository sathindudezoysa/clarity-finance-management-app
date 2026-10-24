package com.example.clarity.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "goals",
    primaryKeys = ["userId", "id"],
    indices = [Index(value = ["userId"])]
)
data class GoalEntity(
    val userId: String,
    val id: String,
    val name: String,
    val description: String,
    val targetMinor: Long,
    val currency: String,
    val targetDateEpochDay: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val pendingSync: Boolean = false
)
