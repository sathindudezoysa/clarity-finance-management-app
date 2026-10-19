package com.example.clarity.data.sync

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.BudgetRemoteDataSource
import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BudgetSyncProcessor @Inject constructor(
    private val db: ClarityDatabase, private val remote: BudgetRemoteDataSource,
    private val auth: AuthRepository, private val signals: BudgetSignals
) {
    suspend fun reconcile(uid: String, budgets: List<Budget>) {
        db.withTransaction {
            checkOwner(uid)
            budgets.filter { it.userId == uid }.forEach { incoming ->
                val old = db.budgetDao.get(uid, incoming.id)
                if (old == null || (!old.pendingSync && (incoming.updatedAt > old.updatedAt ||
                        (incoming.updatedAt == old.updatedAt && !incoming.active)))) {
                    db.budgetDao.upsert(incoming.copy(pendingSync = false).toEntity())
                } else if (!old.pendingSync && old.updatedAt > incoming.updatedAt) {
                    db.budgetDao.upsert(old.copy(pendingSync = true))
                }
            }
        }
        signals.failed(uid, false)
    }
    // Network waits are outside Room transactions and mutation locks. Acknowledge by full snapshot comparison.
    suspend fun upload(uid: String): Boolean {
        checkOwner(uid)
        var complete = true
        for (row in db.budgetDao.pending(uid)) {
            try {
                checkOwner(uid)
                val saved = remote.write(row.toDomain())
                db.withTransaction {
                    checkOwner(uid)
                    if (db.budgetDao.get(uid, row.id) == row) db.budgetDao.upsert(saved.copy(pendingSync = false).toEntity())
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                complete = false
            }
        }
        signals.failed(uid, !complete)
        return complete && db.budgetDao.pending(uid).isEmpty()
    }
    private suspend fun checkOwner(uid: String) { check(auth.currentUser.first()?.uid == uid) { "Session changed" } }
}
