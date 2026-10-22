package com.example.clarity.data.sync

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.GoalDetailWriteResult
import com.example.clarity.data.remote.GoalOperationWriteResult
import com.example.clarity.data.remote.GoalRemoteDataSource
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.GoalPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalSyncProcessor @Inject constructor(
    private val db: ClarityDatabase,
    private val remote: GoalRemoteDataSource,
    private val auth: AuthRepository
) {
    private val mutex = Mutex()

    suspend fun reconcile(uid: String, remoteGoals: List<FinancialGoal>) = mutex.withLock {
        db.withTransaction {
            checkOwner(uid)
            remoteGoals.filter { it.userId == uid }.forEach { remoteGoal ->
                val local = db.goalDao.get(uid, remoteGoal.id)?.toDomain()
                val pendingOps = db.goalProgressOperationDao.pendingForGoal(uid, remoteGoal.id)
                    .map { it.toDomain() }
                val keepLocalDetails = local != null &&
                    (local.pendingSync || local.updatedAt > remoteGoal.updatedAt) &&
                    local.targetMinor >= remoteGoal.savedMinor
                val detailSource = if (keepLocalDetails && local != null) local else remoteGoal
                val replayedSaved = GoalPolicy.replayPendingOperations(
                    baseSavedMinor = remoteGoal.savedMinor,
                    targetMinor = detailSource.targetMinor,
                    pendingOperations = pendingOps
                )
                val derivedStatus = if (remoteGoal.status == GoalStatus.ARCHIVED) {
                    GoalStatus.ARCHIVED
                } else {
                    GoalPolicy.deriveWriteStatus(replayedSaved, detailSource.targetMinor)
                }
                val merged = detailSource.copy(
                    id = remoteGoal.id,
                    userId = uid,
                    currency = remoteGoal.currency,
                    createdAt = local?.createdAt ?: remoteGoal.createdAt,
                    savedMinor = replayedSaved,
                    status = derivedStatus,
                    updatedAt = maxOf(local?.updatedAt ?: 0L, remoteGoal.updatedAt),
                    pendingSync = keepLocalDetails && local?.pendingSync == true
                )
                db.goalDao.upsert(merged.toEntity())
            }
        }
    }

    suspend fun upload(uid: String): Boolean = mutex.withLock {
        checkOwner(uid)
        var complete = true

        for (row in db.goalDao.pending(uid)) {
            try {
                checkOwner(uid)
                val pendingOps = db.goalProgressOperationDao.pendingForGoal(uid, row.id)
                    .map { it.toDomain() }
                val initialSavedMinor = when {
                    pendingOps.isEmpty() -> row.savedMinor
                    pendingOps.any { it.type == GoalProgressOperationType.SET } -> 0L
                    else -> (row.savedMinor - pendingOps.sumOf { it.amountMinor }).coerceAtLeast(0L)
                }
                val detailResult = remote.writeDetails(row.toDomain(), initialSavedMinor)
                db.withTransaction {
                    checkOwner(uid)
                    val current = db.goalDao.get(uid, row.id) ?: return@withTransaction
                    val currentDomain = current.toDomain()
                    val currentPendingOps = db.goalProgressOperationDao.pendingForGoal(uid, row.id)
                        .map { it.toDomain() }
                    val detailsUnchangedSinceRead = current.name == row.name &&
                        current.description == row.description &&
                        current.targetMinor == row.targetMinor &&
                        current.targetDateEpochDay == row.targetDateEpochDay

                    when (detailResult) {
                        is GoalDetailWriteResult.Saved -> {
                            val savedRemote = detailResult.goal
                            if (detailsUnchangedSinceRead) {
                                val replayedSaved = GoalPolicy.replayPendingOperations(
                                    baseSavedMinor = savedRemote.savedMinor,
                                    targetMinor = savedRemote.targetMinor,
                                    pendingOperations = currentPendingOps
                                )
                                val status = if (savedRemote.status == GoalStatus.ARCHIVED) {
                                    GoalStatus.ARCHIVED
                                } else {
                                    GoalPolicy.deriveWriteStatus(replayedSaved, savedRemote.targetMinor)
                                }
                                db.goalDao.upsert(
                                    savedRemote.copy(
                                        savedMinor = replayedSaved,
                                        status = status,
                                        updatedAt = maxOf(currentDomain.updatedAt, savedRemote.updatedAt),
                                        pendingSync = false
                                    ).toEntity()
                                )
                            }
                        }
                        is GoalDetailWriteResult.RejectedTargetBelowSaved -> {
                            val remoteGoal = detailResult.remoteGoal
                            val replayedSaved = GoalPolicy.replayPendingOperations(
                                baseSavedMinor = remoteGoal.savedMinor,
                                targetMinor = remoteGoal.targetMinor,
                                pendingOperations = currentPendingOps
                            )
                            val status = if (remoteGoal.status == GoalStatus.ARCHIVED) {
                                GoalStatus.ARCHIVED
                            } else {
                                GoalPolicy.deriveWriteStatus(replayedSaved, remoteGoal.targetMinor)
                            }
                            db.goalDao.upsert(
                                remoteGoal.copy(
                                    savedMinor = replayedSaved,
                                    status = status,
                                    updatedAt = maxOf(currentDomain.updatedAt, remoteGoal.updatedAt),
                                    pendingSync = false
                                ).toEntity()
                            )
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                complete = false
            }
        }

        for (opRow in db.goalProgressOperationDao.pending(uid)) {
            try {
                checkOwner(uid)
                val parentGoal = db.goalDao.get(uid, opRow.goalId)
                if (parentGoal == null || parentGoal.pendingSync) {
                    complete = false
                    continue
                }
                val opResult = remote.writeOperation(uid, opRow.toDomain())
                db.withTransaction {
                    checkOwner(uid)
                    when (opResult) {
                        is GoalOperationWriteResult.Applied -> {
                            db.goalProgressOperationDao.markApplied(uid, opRow.id)
                            opResult.remoteGoal?.let { remoteGoal ->
                                reconcileSingleGoalWithPendingOps(uid, remoteGoal)
                            }
                        }
                        is GoalOperationWriteResult.Rejected -> {
                            db.goalProgressOperationDao.markRejected(uid, opRow.id)
                            opResult.remoteGoal?.let { remoteGoal ->
                                reconcileSingleGoalWithPendingOps(uid, remoteGoal)
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                complete = false
            }
        }

        complete &&
            db.goalDao.pending(uid).isEmpty() &&
            db.goalProgressOperationDao.pending(uid).isEmpty()
    }

    private suspend fun reconcileSingleGoalWithPendingOps(uid: String, remoteGoal: FinancialGoal) {
        val currentGoal = db.goalDao.get(uid, remoteGoal.id)?.toDomain() ?: return
        val remainingPending = db.goalProgressOperationDao.pendingForGoal(uid, remoteGoal.id)
            .map { it.toDomain() }
        val keepLocalDetails = currentGoal.pendingSync && currentGoal.targetMinor >= remoteGoal.savedMinor
        val detailSource = if (keepLocalDetails) currentGoal else remoteGoal
        val replayedSaved = GoalPolicy.replayPendingOperations(
            baseSavedMinor = remoteGoal.savedMinor,
            targetMinor = detailSource.targetMinor,
            pendingOperations = remainingPending
        )
        val status = if (remoteGoal.status == GoalStatus.ARCHIVED) {
            GoalStatus.ARCHIVED
        } else {
            GoalPolicy.deriveWriteStatus(replayedSaved, detailSource.targetMinor)
        }
        db.goalDao.upsert(
            detailSource.copy(
                id = remoteGoal.id,
                userId = uid,
                currency = remoteGoal.currency,
                createdAt = currentGoal.createdAt,
                savedMinor = replayedSaved,
                status = status,
                updatedAt = maxOf(currentGoal.updatedAt, remoteGoal.updatedAt),
                pendingSync = keepLocalDetails
            ).toEntity()
        )
    }

    private suspend fun checkOwner(uid: String) =
        check(auth.currentUser.first()?.uid == uid) { "Session changed" }
}

