package com.example.clarity.data.sync

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.GoalContributionRemoteDataSource
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoalContributionSyncProcessor @Inject constructor(
    private val db: ClarityDatabase,
    private val remote: GoalContributionRemoteDataSource,
    private val auth: AuthRepository
) {
    private val mutex = Mutex()

    suspend fun reconcile(uid: String, remoteContributions: List<GoalContribution>) = mutex.withLock {
        db.withTransaction {
            checkOwner(uid)
            remoteContributions.filter { it.userId == uid }.forEach { remoteItem ->
                val local = db.goalContributionDao.get(uid, remoteItem.id)
                if (local == null || (!local.pendingSync && remoteItem.updatedAt >= local.updatedAt)) {
                    db.goalContributionDao.upsert(
                        remoteItem.copy(
                            createdAt = local?.createdAt ?: remoteItem.createdAt,
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
        for (row in db.goalContributionDao.pending(uid)) {
            try {
                checkOwner(uid)
                val saved = remote.write(row.toDomain())
                db.withTransaction {
                    checkOwner(uid)
                    if (db.goalContributionDao.get(uid, row.id) == row) {
                        db.goalContributionDao.upsert(saved.copy(pendingSync = false).toEntity())
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                complete = false
            }
        }
        complete && db.goalContributionDao.pending(uid).isEmpty()
    }

    private suspend fun checkOwner(uid: String) =
        check(auth.currentUser.first()?.uid == uid) { "Session changed" }
}

