package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.GoalEntity
import com.example.clarity.data.local.entity.GoalProgressOperationEntity
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalProgressOperation
import com.example.clarity.domain.model.GoalProgressOperationState
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.util.GoalPolicy

fun GoalEntity.toDomain(): FinancialGoal = FinancialGoal(
    id = id,
    userId = userId,
    name = name,
    description = description,
    targetMinor = targetMinor,
    savedMinor = savedMinor,
    currency = currency,
    targetDateEpochDay = targetDateEpochDay,
    status = runCatching { GoalStatus.valueOf(status) }.getOrElse {
        GoalPolicy.deriveWriteStatus(savedMinor, targetMinor)
    },
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
    savedMinor = savedMinor,
    currency = currency,
    targetDateEpochDay = targetDateEpochDay,
    status = status.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    pendingSync = pendingSync
)

fun GoalProgressOperationEntity.toDomain(): GoalProgressOperation = GoalProgressOperation(
    id = id,
    userId = userId,
    goalId = goalId,
    type = GoalProgressOperationType.valueOf(type),
    amountMinor = amountMinor,
    createdAt = createdAt,
    state = GoalProgressOperationState.valueOf(state),
    sourceAccountId = sourceAccountId
)

fun GoalProgressOperation.toEntity(): GoalProgressOperationEntity = GoalProgressOperationEntity(
    userId = userId,
    id = id,
    goalId = goalId,
    type = type.name,
    amountMinor = amountMinor,
    createdAt = createdAt,
    state = state.name,
    sourceAccountId = sourceAccountId
)

fun FinancialGoal.toRemoteMap(lastOperationId: String? = null): Map<String, Any?> {
    val base = mutableMapOf<String, Any?>(
        "id" to id,
        "userId" to userId,
        "name" to name,
        "description" to description,
        "targetMinor" to targetMinor,
        "savedMinor" to savedMinor,
        "currency" to currency,
        "targetDateEpochDay" to targetDateEpochDay,
        "status" to status.name,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )
    if (lastOperationId != null) {
        base["lastOperationId"] = lastOperationId
    }
    return base
}

fun GoalProgressOperation.toRemoteMap(): Map<String, Any?> {
    val base = mutableMapOf<String, Any?>(
        "id" to id,
        "userId" to userId,
        "goalId" to goalId,
        "type" to type.name,
        "amountMinor" to amountMinor,
        "createdAt" to createdAt
    )
    if (type == GoalProgressOperationType.ADD && !sourceAccountId.isNullOrBlank()) {
        base["sourceAccountId"] = sourceAccountId
    }
    return base
}

private val REQUIRED_GOAL_KEYS = setOf(
    "id",
    "userId",
    "name",
    "description",
    "targetMinor",
    "savedMinor",
    "currency",
    "targetDateEpochDay",
    "status",
    "createdAt",
    "updatedAt"
)

private val ALLOWED_GOAL_KEYS = REQUIRED_GOAL_KEYS + "lastOperationId"

fun Map<String, Any?>.toFinancialGoal(id: String, uid: String): FinancialGoal? {
    if (!keys.containsAll(REQUIRED_GOAL_KEYS) || !ALLOWED_GOAL_KEYS.containsAll(keys)) return null
    if (this["id"] != id || this["userId"] != uid || !GoalPolicy.validId(uid) || !GoalPolicy.validId(id)) {
        return null
    }
    val lastOp = this["lastOperationId"]
    if (lastOp != null && (lastOp !is String || !GoalPolicy.validId(lastOp))) return null
    val name = this["name"] as? String ?: return null
    val description = this["description"] as? String ?: return null
    val targetMinor = this["targetMinor"].goalLong() ?: return null
    val savedMinor = this["savedMinor"].goalLong() ?: return null
    val currency = this["currency"] as? String ?: return null
    val targetDateEpochDay = this["targetDateEpochDay"].goalLong() ?: return null
    val statusStr = this["status"] as? String ?: return null
    val status = runCatching { GoalStatus.valueOf(statusStr) }.getOrNull() ?: return null
    val createdAt = this["createdAt"].goalLong() ?: return null
    val updatedAt = this["updatedAt"].goalLong() ?: return null

    val goal = FinancialGoal(
        id = id,
        userId = uid,
        name = name,
        description = description,
        targetMinor = targetMinor,
        savedMinor = savedMinor,
        currency = currency,
        targetDateEpochDay = targetDateEpochDay,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pendingSync = false
    )
    return goal.takeIf { runCatching { GoalPolicy.validateStoredOrRemote(it) }.isSuccess }
}

private val REQUIRED_OPERATION_KEYS = setOf(
    "id",
    "userId",
    "goalId",
    "type",
    "amountMinor",
    "createdAt"
)

private val ALLOWED_OPERATION_KEYS = REQUIRED_OPERATION_KEYS + "sourceAccountId"

fun Map<String, Any?>.toGoalProgressOperation(id: String, goalId: String, uid: String): GoalProgressOperation? {
    if (!keys.containsAll(REQUIRED_OPERATION_KEYS) || !ALLOWED_OPERATION_KEYS.containsAll(keys)) return null
    if (this["id"] != id || this["goalId"] != goalId || this["userId"] != uid) return null
    if (!GoalPolicy.validId(id) || !GoalPolicy.validId(goalId) || !GoalPolicy.validId(uid)) return null
    val typeStr = this["type"] as? String ?: return null
    val type = runCatching { GoalProgressOperationType.valueOf(typeStr) }.getOrNull() ?: return null
    val amountMinor = this["amountMinor"].goalLong() ?: return null
    val createdAt = this["createdAt"].goalLong() ?: return null
    val rawSourceAccount = if (containsKey("sourceAccountId")) {
        this["sourceAccountId"] as? String ?: return null
    } else {
        null
    }
    if (type == GoalProgressOperationType.SET && rawSourceAccount != null) return null
    val op = GoalProgressOperation(
        id = id,
        userId = uid,
        goalId = goalId,
        type = type,
        amountMinor = amountMinor,
        createdAt = createdAt,
        state = GoalProgressOperationState.APPLIED,
        sourceAccountId = rawSourceAccount
    )
    return op.takeIf { runCatching { GoalPolicy.validateOperation(it) }.isSuccess }
}

// Firestore integer fields arrive as Long. Never truncate double-valued money or timestamps.
private fun Any?.goalLong(): Long? = when (this) {
    is Long -> this
    is Int -> toLong()
    is Short -> toLong()
    is Byte -> toLong()
    else -> null
}
