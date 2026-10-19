package com.example.clarity.data.sync

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.IncomeSourceRemoteDataSource
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.IncomePolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IncomeSyncProcessor @Inject internal constructor(
    private val db: ClarityDatabase, private val remote: IncomeSourceRemoteDataSource, private val auth: AuthRepository, private val accountUploader: AccountUploader
) {
    private val mutex = Mutex()
    suspend fun reconcile(uid: String, sources: List<IncomeSource>) = mutex.withLock {
        db.withTransaction {
            checkOwner(uid)
            sources.filter { it.userId == uid }.forEach { source ->
                val local = db.incomeSourceDao.get(uid, source.id)?.toDomain()
                val selected = if (local != null && (local.pendingSync || local.updatedAt > source.updatedAt)) local else source
                db.incomeSourceDao.upsert(selected.copy(lastConfirmedMonth = IncomePolicy.latestMonth(local?.lastConfirmedMonth,
                    source.lastConfirmedMonth)).toEntity())
            }
        }
    }
    suspend fun upload(uid: String): Boolean = mutex.withLock {
        checkOwner(uid)
        if (!accountUploader.uploadPending(uid)) return@withLock false
        var complete = true
        for (row in db.incomeSourceDao.pending(uid)) {
            try {
                checkOwner(uid)
                val saved = remote.write(row.toDomain())
                db.withTransaction {
                    checkOwner(uid)
                    val current = db.incomeSourceDao.get(uid, row.id)
                    if (current == row) db.incomeSourceDao.upsert(saved.copy(pendingSync = false).toEntity())
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                complete = false
            }
        }
        complete && db.incomeSourceDao.pending(uid).isEmpty()
    }
    private suspend fun checkOwner(uid: String) = check(auth.currentUser.first()?.uid == uid) { "Session changed" }
}
