package com.example.clarity.data.remote

import com.example.clarity.data.mapper.toFinancialGoal
import com.example.clarity.data.mapper.toGoalProgressOperation
import com.example.clarity.data.mapper.toRemoteMap
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalProgressOperation
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.util.GoalPolicy
import com.google.firebase.firestore.MetadataChanges
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.math.BigInteger
import javax.inject.Inject

sealed interface GoalDetailWriteResult {
    data class Saved(val goal: FinancialGoal) : GoalDetailWriteResult
    data class RejectedTargetBelowSaved(val remoteGoal: FinancialGoal) : GoalDetailWriteResult
}

sealed interface GoalOperationWriteResult {
    data class Applied(val remoteGoal: FinancialGoal?) : GoalOperationWriteResult
    data class Rejected(val remoteGoal: FinancialGoal?, val reason: String) : GoalOperationWriteResult
}

interface GoalRemoteDataSource {
    fun observe(uid: String): Flow<List<FinancialGoal>>
    suspend fun writeDetails(goal: FinancialGoal, initialSavedMinor: Long = goal.savedMinor): GoalDetailWriteResult
    suspend fun writeOperation(uid: String, operation: GoalProgressOperation): GoalOperationWriteResult
}

class FirestoreGoalDataSource @Inject constructor(
    private val firestore: FirestoreDataSource
) : GoalRemoteDataSource {

    override fun observe(uid: String): Flow<List<FinancialGoal>> = callbackFlow {
        val listener = firestore.getCollection("users/$uid/goals")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    close(error)
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.data?.toFinancialGoal(it.id, uid) })
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun writeDetails(
        goal: FinancialGoal,
        initialSavedMinor: Long
    ): GoalDetailWriteResult = withTimeoutOrNull(30_000) {
        val doc = firestore.getCollection("users/${goal.userId}/goals").document(goal.id)
        doc.firestore.runTransaction { tx ->
            val snapshot = tx.get(doc)
            val remote = snapshot.data?.toFinancialGoal(goal.id, goal.userId)
            require(!snapshot.exists() || remote != null) { "Malformed financial goal" }
            if (remote == null) {
                val createdSaved = initialSavedMinor.coerceIn(0L, goal.targetMinor)
                val created = goal.copy(
                    savedMinor = createdSaved,
                    status = GoalPolicy.deriveWriteStatus(createdSaved, goal.targetMinor),
                    pendingSync = false
                )
                tx.set(doc, created.toRemoteMap())
                GoalDetailWriteResult.Saved(created)
            } else if (remote.updatedAt > goal.updatedAt) {
                GoalDetailWriteResult.Saved(remote)
            } else if (goal.targetMinor < remote.savedMinor) {
                GoalDetailWriteResult.RejectedTargetBelowSaved(remote)
            } else {
                val newStatus = if (remote.status == GoalStatus.ARCHIVED) {
                    GoalStatus.ARCHIVED
                } else {
                    GoalPolicy.deriveWriteStatus(remote.savedMinor, goal.targetMinor)
                }
                val merged = goal.copy(
                    userId = remote.userId,
                    currency = remote.currency,
                    savedMinor = remote.savedMinor,
                    createdAt = remote.createdAt,
                    updatedAt = maxOf(goal.updatedAt, remote.updatedAt),
                    status = newStatus,
                    pendingSync = false
                )
                val existingLastOp = snapshot.data?.get("lastOperationId") as? String
                tx.set(doc, merged.toRemoteMap(lastOperationId = existingLastOp))
                GoalDetailWriteResult.Saved(merged)
            }
        }.await()
    } ?: throw IOException("Goal detail sync timed out")

    override suspend fun writeOperation(
        uid: String,
        operation: GoalProgressOperation
    ): GoalOperationWriteResult = withTimeoutOrNull(30_000) {
        val goalDoc = firestore.getCollection("users/$uid/goals").document(operation.goalId)
        val opDoc = firestore.getCollection("users/$uid/goals/${operation.goalId}/progress_operations")
            .document(operation.id)
        goalDoc.firestore.runTransaction { tx ->
            // All reads must execute before any writes in a Firestore transaction.
            val opSnapshot = tx.get(opDoc)
            val goalSnapshot = tx.get(goalDoc)

            if (opSnapshot.exists()) {
                val existingOp = opSnapshot.data?.toGoalProgressOperation(operation.id, operation.goalId, uid)
                require(existingOp != null) { "Malformed goal progress operation" }
                val currentGoal = goalSnapshot.data?.toFinancialGoal(operation.goalId, uid)
                return@runTransaction GoalOperationWriteResult.Applied(currentGoal)
            }

            if (!goalSnapshot.exists()) {
                return@runTransaction GoalOperationWriteResult.Rejected(
                    remoteGoal = null,
                    reason = "Goal does not exist on server."
                )
            }

            val remoteGoal = goalSnapshot.data?.toFinancialGoal(operation.goalId, uid)
                ?: throw IllegalStateException("Malformed remote goal")

            val candidate: BigInteger = when (operation.type) {
                GoalProgressOperationType.ADD -> {
                    if (operation.amountMinor <= 0L) {
                        return@runTransaction GoalOperationWriteResult.Rejected(
                            remoteGoal = remoteGoal,
                            reason = "Contribution amount must be positive."
                        )
                    }
                    BigInteger.valueOf(remoteGoal.savedMinor).add(BigInteger.valueOf(operation.amountMinor))
                }
                GoalProgressOperationType.SET -> {
                    if (operation.amountMinor < 0L) {
                        return@runTransaction GoalOperationWriteResult.Rejected(
                            remoteGoal = remoteGoal,
                            reason = "Saved balance cannot be negative."
                        )
                    }
                    BigInteger.valueOf(operation.amountMinor)
                }
            }

            if (candidate < BigInteger.ZERO || candidate > BigInteger.valueOf(remoteGoal.targetMinor)) {
                return@runTransaction GoalOperationWriteResult.Rejected(
                    remoteGoal = remoteGoal,
                    reason = "Operation would exceed the goal target or produce a negative balance."
                )
            }

            val newSaved = candidate.longValueExact()
            val newStatus = if (remoteGoal.status == GoalStatus.ARCHIVED) {
                GoalStatus.ARCHIVED
            } else {
                GoalPolicy.deriveWriteStatus(newSaved, remoteGoal.targetMinor)
            }
            val newUpdatedAt = maxOf(remoteGoal.updatedAt, operation.createdAt, remoteGoal.createdAt)
            val updatedGoal = remoteGoal.copy(
                savedMinor = newSaved,
                status = newStatus,
                updatedAt = newUpdatedAt,
                pendingSync = false
            )
            val remoteOp = operation.copy(userId = uid)
            tx.set(opDoc, remoteOp.toRemoteMap())
            tx.set(goalDoc, updatedGoal.toRemoteMap(lastOperationId = operation.id))
            GoalOperationWriteResult.Applied(updatedGoal)
        }.await()
    } ?: throw IOException("Goal operation sync timed out")
}

