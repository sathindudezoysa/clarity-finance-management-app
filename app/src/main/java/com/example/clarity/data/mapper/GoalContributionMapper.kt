package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.GoalContributionEntity
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.util.GoalProgressPolicy

private val REMOTE_GOAL_CONTRIBUTION_KEYS = setOf(
    "id",
    "userId",
    "goalId",
    "amountMinor",
    "currency",
    "dateEpochDay",
    "createdAt",
    "updatedAt"
)

fun GoalContributionEntity.toDomain(): GoalContribution = GoalContribution(
    id = id,
    userId = userId,
    goalId = goalId,
    amountMinor = amountMinor,
    currency = currency,
    dateEpochDay = dateEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pendingSync = pendingSync
)

fun GoalContribution.toEntity(): GoalContributionEntity = GoalContributionEntity(
    userId = userId,
    id = id,
    goalId = goalId,
    amountMinor = amountMinor,
    currency = currency,
    dateEpochDay = dateEpochDay,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pendingSync = pendingSync
)

fun GoalContribution.toRemoteMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "goalId" to goalId,
    "amountMinor" to amountMinor,
    "currency" to currency,
    "dateEpochDay" to dateEpochDay,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
)

fun Map<String, Any?>.toGoalContribution(docId: String, ownerId: String): GoalContribution? = runCatching {
    if (!keys.containsAll(REMOTE_GOAL_CONTRIBUTION_KEYS)) return null
    if (!REMOTE_GOAL_CONTRIBUTION_KEYS.containsAll(keys)) return null
    val id = this["id"] as? String ?: return null
    val userId = this["userId"] as? String ?: return null
    if (id != docId || userId != ownerId) return null
    val goalId = this["goalId"] as? String ?: return null
    val amountMinor = this["amountMinor"] as? Long ?: return null
    val currency = this["currency"] as? String ?: return null
    val dateEpochDay = this["dateEpochDay"] as? Long ?: return null
    val createdAt = this["createdAt"] as? Long ?: return null
    val updatedAt = this["updatedAt"] as? Long ?: return null

    GoalContribution(
        id = id,
        userId = userId,
        goalId = goalId,
        amountMinor = amountMinor,
        currency = currency,
        dateEpochDay = dateEpochDay,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = false
    ).also(GoalProgressPolicy::validateStoredOrRemote)
}.getOrNull()

