package com.example.clarity.data.sync

import android.util.Log
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.ProofFileStore
import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.data.remote.ProofRemoteDataSource
import com.example.clarity.domain.model.ProofStoragePlanException
import com.example.clarity.domain.repository.AuthRepository
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Shared serialization for foreground refresh and durable background uploads. */
@Singleton
class ProofSyncProcessor @Inject constructor(
    private val database: ClarityDatabase,
    private val files: ProofFileStore,
    private val remote: ProofRemoteDataSource,
    private val auth: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher
) {
    private val mutex = Mutex()
    suspend fun <T> withSync(block: suspend () -> T): T = mutex.withLock { block() }

    suspend fun syncPending(uid: String): Boolean = withContext(ioDispatcher) {
        var complete = true
        for (row in database.transactionProofDao.pending(uid)) {
            try { withSync { upload(row) } }
            catch (error: ProofStoragePlanException) {
                Log.w("ProofSync", ProofStoragePlanException.MESSAGE)
                complete = false
                break
            }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w("ProofSync", "Proof remains queued for a network-constrained retry", error)
                complete = false
            }
        }
        complete && database.transactionProofDao.pending(uid).isEmpty()
    }

    // Caller holds withSync. Re-read to avoid sending an old scheduled revision.
    suspend fun upload(pending: TransactionProofEntity) {
        val dao = database.transactionProofDao
        val row = dao.get(pending.userId, pending.transactionId, pending.id) ?: return
        if (!row.pendingUpload || row.unavailable) return
        requireOwner(row.userId)
        val transaction = database.transactionDao.getById(row.transactionId)
        if (transaction?.userId != row.userId || transaction.isDeleted) return
        val file = files.file(row.userId, row.id)
        val updatedMetadata = if (file.isFile && row.revision == row.createdAt) {
            false
        } else {
            try {
                remote.updateMetadata(row)
            } catch (error: StorageException) {
                if (file.isFile && error.errorCode == StorageException.ERROR_NOT_AUTHORIZED) false else throw error
            }
        }
        if (!updatedMetadata) {
            requireOwner(row.userId)
            if (!file.isFile) {
                // Only a confirmed remote 404 plus a missing local original is terminal.
                dao.markUnavailable(row.userId, row.transactionId, row.id, row.revision)
                return
            }
            remote.upload(row, file)
        }
        requireOwner(row.userId)
        dao.acknowledge(row.userId, row.transactionId, row.id, row.revision)
    }

    private suspend fun requireOwner(uid: String) = check(auth.currentUser.first()?.uid == uid) { "Your session changed" }
}
