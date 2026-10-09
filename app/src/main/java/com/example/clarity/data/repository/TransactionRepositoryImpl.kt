package com.example.clarity.data.repository

import android.util.Log
import com.example.clarity.data.local.dao.TransactionDao
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.util.Resource
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRepositoryImpl @Inject constructor(
    private val dao: TransactionDao,
    private val firestore: FirestoreDataSource,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher
) : TransactionRepository {
    private val mutationMutex = Mutex()
    private val syncScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    override fun observeTransactions(): Flow<Resource<List<Transaction>>> =
        authRepository.currentUser.distinctUntilChangedBy { it?.uid }.flatMapLatest { user ->
            if (user == null) flowOf(Resource.Error("Please sign in"))
            else combine(dao.observeAll(user.uid), observeRemote(user.uid)) { local, warning ->
                val transactions = local.map { it.toDomain() }
                if (warning == null) Resource.Success(transactions)
                else Resource.Error(warning, transactions)
            }.onStart { emit(Resource.Loading()) }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(Resource.Error("Unable to load transactions"))
        }.flowOn(ioDispatcher)

    override fun observeTransaction(id: String): Flow<Resource<Transaction?>> =
        authRepository.currentUser.distinctUntilChangedBy { it?.uid }.flatMapLatest { user ->
            if (user == null) flowOf<Resource<Transaction?>>(Resource.Error("Please sign in"))
            else combine(dao.observeById(id, user.uid), observeRemote(user.uid)) { local, warning ->
                val transaction = local?.toDomain()
                if (warning == null) Resource.Success<Transaction?>(transaction)
                else Resource.Error<Transaction?>(warning, transaction)
            }.onStart { emit(Resource.Loading()) }
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(Resource.Error("Unable to load transaction"))
        }.flowOn(ioDispatcher)

    private data class RemoteEvent(val snapshot: QuerySnapshot? = null, val error: Exception? = null)

    private fun observeRemote(uid: String): Flow<String?> = callbackFlow {
        val registration = firestore.getCollection("users/$uid/transactions")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                trySend(RemoteEvent(snapshot, error))
            }
        awaitClose { registration.remove() }
    }.map { event ->
        if (event.error != null) {
            Log.w(TAG, "Transaction listener failed; keeping Room data", event.error)
            "Unable to sync transactions"
        } else {
            event.snapshot?.let { snapshot ->
                mutationMutex.withLock {
                    val remote = snapshot.documents.mapNotNull { doc ->
                        doc.toDomain()?.takeIf { it.userId == uid }?.toEntity()
                    }
                    dao.reconcileRemote(
                        uid, remote, snapshot.documents.map { it.id }.toSet(),
                        authoritative = !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()
                    )
                }
            }
            null
        }
    }.onStart {
        mutationMutex.withLock { dao.getPendingForUser(uid).forEach(::enqueueRemote) }
        emit(null)
    }.catch { error ->
        if (error is CancellationException) throw error
        Log.w(TAG, "Transaction sync unavailable; keeping Room data", error)
        emit("Unable to sync transactions")
    }

    override suspend fun addTransaction(t: Transaction): Result<Unit> = localWrite { uid ->
        require(t.amountMinor > 0) { "Amount must be greater than zero" }
        require(dao.getById(t.id) == null) { "Transaction already exists" }
        val now = System.currentTimeMillis()
        val entity = t.copy(userId = uid, createdAt = now, updatedAt = now).toEntity()
            .copy(pendingSync = true)
        dao.upsert(entity)
        enqueueRemote(entity)
    }

    override suspend fun updateTransaction(t: Transaction): Result<Unit> = localWrite { uid ->
        val existing = dao.getById(t.id)
        require(existing != null && existing.userId == uid && !existing.isDeleted) { "Transaction not found" }
        require(t.userId == existing.userId) { "Cannot change transaction owner" }
        require(t.amountMinor > 0) { "Amount must be greater than zero" }
        val entity = t.copy(
            userId = uid, createdAt = existing.createdAt,
            updatedAt = maxOf(System.currentTimeMillis(), existing.updatedAt + 1)
        ).toEntity().copy(pendingSync = true)
        dao.upsert(entity)
        enqueueRemote(entity)
    }

    override suspend fun deleteTransaction(id: String): Result<Unit> = localWrite { uid ->
        val existing = dao.getById(id)
        require(existing != null && existing.userId == uid && !existing.isDeleted) { "Transaction not found" }
        // Persist a hidden tombstone so a restart/offline cache cannot resurrect this deletion.
        val deleted = existing.copy(isDeleted = true, pendingSync = true)
        dao.upsert(deleted)
        enqueueRemote(deleted)
    }

    private suspend fun localWrite(block: suspend (String) -> Unit): Result<Unit> = withContext(ioDispatcher) {
        try {
            val uid = authRepository.currentUser.first()?.uid
                ?: return@withContext Result.failure(IllegalStateException("Please sign in"))
            mutationMutex.withLock { block(uid) }
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    private fun enqueueRemote(entity: TransactionEntity) {
        // Fire-and-forget on the IO dispatcher: never await server acknowledgement. Firestore
        // queues offline writes; Room pending flags survive failure/restart and are replayed on
        // the next subscription. Matching authoritative snapshots acknowledge set writes;
        // deletion success callbacks acknowledge tombstones without blocking the caller.
        try {
            val document = firestore.getCollection("users/${entity.userId}/transactions").document(entity.id)
            val task = if (entity.isDeleted) document.delete() else document.set(entity.toDomain().toFirestoreMap())
            if (entity.isDeleted) task.addOnSuccessListener {
                syncScope.launch {
                    try {
                        mutationMutex.withLock {
                            // A delete acknowledgement is authoritative, unlike an empty cache.
                            if (dao.getById(entity.id) == entity) dao.deleteById(entity.id, entity.userId)
                        }
                    } catch (error: Exception) {
                        if (error is CancellationException) throw error
                        Log.w(TAG, "Could not acknowledge local deletion; retained for retry", error)
                    }
                }
            }
            task.addOnFailureListener { error ->
                Log.w(TAG, "Remote transaction write failed; local change retained", error)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Could not queue remote transaction write; local change retained", error)
        }
    }

    private companion object { const val TAG = "TransactionRepository" }
}
