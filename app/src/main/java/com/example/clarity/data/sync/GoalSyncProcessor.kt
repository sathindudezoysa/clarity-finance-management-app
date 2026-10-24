package com.example.clarity.data.sync

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.GoalRemoteDataSource
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.repository.AuthRepository
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
                val local = db.goalDao.get(uid, remoteGoal.id)
                if (local == null || (!local.pendingSync && remoteGoal.updatedAt >= local.updatedAt)) {
                    db.goalDao.upsert(
                        remoteGoal.copy(
                            createdAt = local?.createdAt ?: remoteGoal.createdAt,
                            pendingSync = false
                        ).toEntity()
                    )
                }
            }
        }
    }

    suspend fun upload(uid: String): Boolean = mutex.withLock {
        checkOwner(uid)
        var complete = true
        for (row in db.goalDao.pending(uid)) {
            try {
                checkOwner(uid)
                val saved = remote.write(row.toDomain())
                db.withTransaction {
                    checkOwner(uid)
                    if (db.goalDao.get(uid, row.id) == row) {
                        db.goalDao.upsert(saved.copy(pendingSync = false).toEntity())
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                complete = false
            }
        }
        complete && db.goalDao.pending(uid).isEmpty()
    }

    private suspend fun checkOwner(uid: String) =
        check(auth.currentUser.first()?.uid == uid) { "Session changed" }
}
