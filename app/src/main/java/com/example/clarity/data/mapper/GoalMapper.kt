package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.GoalEntity
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.util.GoalPolicy

private val REMOTE_GOAL_KEYS = setOf(
    "id",
    "userId",
    "name",
    "description",
    "targetMinor",
    "currency",
    "targetDateEpochDay",
    "createdAt",
    "updatedAt"
)

fun GoalEntity.toDomain(): FinancialGoal = FinancialGoal(
    id = id,
    userId = userId,
    name = name,
    description = description,
    targetMinor = targetMinor,
    currency = currency,
    targetDateEpochDay = targetDateEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pendingSync = pendingSync
)

fun FinancialGoal.toEntity(): GoalEntity = GoalEntity(
    userId = userId,
    id = id,
    name = name,
    description = description,
    targetMinor = targetMinor,
    currency = currency,
    targetDateEpochDay = targetDateEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pendingSync = pendingSync
)

fun FinancialGoal.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "name" to name,
    "description" to description,
    "targetMinor" to targetMinor,
    "currency" to currency,
    "targetDateEpochDay" to targetDateEpochDay,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
)

fun Map<String, Any?>.toFinancialGoal(docId: String, ownerId: String): FinancialGoal? = runCatching {
    if (!keys.containsAll(REMOTE_GOAL_KEYS)) return null
    if (!REMOTE_GOAL_KEYS.containsAll(keys)) return null
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    if (id != docId || userId != ownerId) return null
    val name = this["name"] as? String ?: return null
    val description = this["description"] as? String ?: return null
    val targetMinor = this["targetMinor"] as? Long ?: return null
    val currency = this["currency"] as? String ?: return null
    val targetDateEpochDay = this["targetDateEpochDay"] as? Long ?: return null
    val createdAt = this["createdAt"] as? Long ?: return null
    val updatedAt = this["updatedAt"] as? Long ?: return null

    FinancialGoal(
        id = id,
        userId = userId,
        name = name,
        description = description,
        targetMinor = targetMinor,
        currency = currency,
        targetDateEpochDay = targetDateEpochDay,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = false
    ).also(GoalPolicy::validateStoredOrRemote)
}.getOrNull()
