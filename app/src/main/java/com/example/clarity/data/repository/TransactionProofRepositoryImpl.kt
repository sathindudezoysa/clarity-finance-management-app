package com.example.clarity.data.repository

import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.ProofFileStore
import com.example.clarity.data.sync.ProofSyncProcessor
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.remote.ProofRemoteDataSource
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.TransactionProofRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionProofRepositoryImpl @Inject constructor(
    private val database: ClarityDatabase,
    private val files: ProofFileStore,
    private val remote: ProofRemoteDataSource,
    private val auth: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val sync: ProofSyncProcessor
) : TransactionProofRepository {
    private val dao get() = database.transactionProofDao
    override fun observeProofs(transactionId: String): Flow<List<TransactionProof>> =
        auth.currentUser.distinctUntilChangedBy { it?.uid }.flatMapLatest { user ->
            if (user == null) flowOf(emptyList())
            else combine(dao.observe(user.uid, transactionId), database.transactionDao.observeById(transactionId, user.uid)) { rows, transaction ->
                if (transaction == null) emptyList() else rows.map { it.toDomain() }
            }
        }.flowOn(ioDispatcher)

    override suspend fun attach(transactionId: String, contentUri: String) = operation {
        val uid = owner()
        availableTransaction(uid, transactionId)
        val id = UUID.randomUUID().toString()
        val imported = files.import(uid, id, contentUri)
        try {
            database.withTransaction {
                requireOwner(uid)
                availableTransaction(uid, transactionId)
                require(dao.count(uid, transactionId) < ProofPolicy.MAX_ATTACHMENTS) { "A transaction can have up to ten proofs" }
                val now = System.currentTimeMillis()
                dao.upsert(TransactionProofEntity(uid, transactionId, id, imported.displayName, imported.mimeType,
                    imported.file.length(), now, null, true, now))
            }
        } catch (error: Exception) { imported.file.delete(); throw error }
    }

    override suspend fun setVerified(transactionId: String, proofId: String, amountMinor: Long, verified: Boolean) = operation {
        val uid = owner()
        ProofPolicy.requireId(proofId)
        database.withTransaction {
            val transaction = availableTransaction(uid, transactionId)
            require(transaction.amountMinor == amountMinor) { "The amount changed. Review it again" }
            val row = requireNotNull(dao.get(uid, transactionId, proofId)) { "Proof not found" }
            require(!row.unavailable) { "Attach the original proof again" }
            requireOwner(uid)
            dao.upsert(row.copy(verifiedAmountMinor = if (verified) amountMinor else null, pendingUpload = true,
                revision = maxOf(System.currentTimeMillis(), Math.addExact(row.revision, 1))))
        }
    }

    override suspend fun open(transactionId: String, proofId: String) = operation {
        val uid = owner()
        availableTransaction(uid, transactionId)
        ProofPolicy.requireId(proofId)
        val row = requireNotNull(dao.get(uid, transactionId, proofId)) { "Proof not found" }
        require(!row.unavailable) { "Attach the original proof again" }
        val file = files.file(uid, proofId)
        if (!file.isFile) {
            try { remote.download(row, file) } catch (error: Exception) { file.delete(); throw error }
        }
        requireOwner(uid)
        availableTransaction(uid, transactionId)
        ProofOpenFile(files.share(uid, proofId, row.mimeType, row.displayName), row.mimeType)
    }

    override suspend fun removeUnavailable(transactionId: String, proofId: String) = operation {
        val uid = owner()
        ProofPolicy.requireId(proofId)
        database.withTransaction {
            availableTransaction(uid, transactionId)
            val row = requireNotNull(dao.get(uid, transactionId, proofId)) { "Proof not found" }
            require(row.unavailable && !row.pendingUpload) { "Only missing proofs can be removed" }
            requireOwner(uid)
            dao.removeUnavailable(uid, transactionId, proofId)
        }
        Unit
    }

    override suspend fun refresh(transactionId: String) = operation {
        val uid = owner()
        availableTransaction(uid, transactionId)
        sync.withSync {
            dao.observePending(uid).first().filter { it.transactionId == transactionId }.forEach { sync.upload(it) }
            val rows = remote.list(uid, transactionId)
            database.withTransaction {
                requireOwner(uid)
                availableTransaction(uid, transactionId)
                for (row in rows) {
                    val local = dao.get(uid, transactionId, row.id)
                    // An offline verification always wins until acknowledged; remote refresh cannot erase it.
                    if (local == null || (!local.pendingUpload && row.revision >= local.revision)) dao.upsert(row)
                }
            }
        }
    }

    private suspend fun owner(): String = requireNotNull(auth.currentUser.first()?.uid) { "Please sign in" }
    private suspend fun requireOwner(uid: String) = check(auth.currentUser.first()?.uid == uid) { "Your session changed" }
    private suspend fun availableTransaction(uid: String, id: String): TransactionEntity {
        ProofPolicy.requireId(id)
        return requireNotNull(database.transactionDao.getById(id)?.takeIf { it.userId == uid && !it.isDeleted }) { "Transaction is unavailable" }
    }
    private suspend fun <T> operation(block: suspend () -> T): Result<T> = withContext(ioDispatcher) {
        try {
            withTimeoutOrNull(45_000) { Result.success(block()) }
                ?: Result.failure(java.io.IOException("Proof operation timed out; retry when connected"))
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }
}
