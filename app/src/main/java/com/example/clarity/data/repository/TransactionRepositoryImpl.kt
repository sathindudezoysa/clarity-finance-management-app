package com.example.clarity.data.repository

import android.util.Log
import com.example.clarity.data.local.dao.TransactionDao
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.mapper.mapFirestoreDocuments
import com.example.clarity.data.mapper.toTransaction
import com.example.clarity.data.remote.FirestoreDataSource
import com.example.clarity.data.sync.AccountUploader
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.util.Resource
import com.example.clarity.di.ApplicationScope
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room alone supplies UI records; its pending flags and tombstones form a durable outbox.
 * Application injection eagerly starts this singleton's auth-scoped sync collector.
 * One listener serves all screens; UID changes cancel it and isolate retries by owner.
 * Greater updatedAt wins; ties prefer the server, while cached snapshots cannot acknowledge edits.
 * Server transactions prevent an older queued upload from overwriting a newer server version.
 * Pending edits win remote deletions by recreating the document; pending tombstones stay hidden.
 * Deletes clear only after acknowledgement plus absence in a fresh authoritative snapshot.
 * Retry on every committed outbox change, startup/sign-in, snapshots, and every 30 seconds.
 * Serialize writes per document; acknowledged versions suppress resubmission until reconciliation.
 * Never await Firestore tasks in UI writes; failures are logged and Room retains pending records.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TransactionRepositoryImpl @Inject internal constructor(
    private val dao: TransactionDao,
    private val firestore: FirestoreDataSource,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val accountUploader: AccountUploader,
    @ApplicationScope applicationScope: CoroutineScope,
    private val rates: com.example.clarity.domain.repository.ExchangeRateRepository,
    private val preferences: com.example.clarity.domain.repository.CurrencyPreferenceRepository,
    private val currencyWork: com.example.clarity.data.sync.CurrencyWorkScheduler,
    private val clock: java.time.Clock = java.time.Clock.systemUTC()
) : TransactionRepository {
    private val mutationMutex = Mutex()
    // Application ownership keeps synchronization independent of screen subscriptions.
    private val syncScope = CoroutineScope(applicationScope.coroutineContext + SupervisorJob(applicationScope.coroutineContext[Job]))
    private val users = authRepository.currentUser.distinctUntilChangedBy { it?.uid }
        .shareIn(syncScope, SharingStarted.Eagerly, replay = 1)
    private data class SyncState(val uid: String?, val warning: String? = null)
    private val syncState = MutableStateFlow(SyncState(null))
    private val listenerGeneration = MutableStateFlow(0L)
    private var activeUid: String? = null
    private data class WriteKey(val uid: String, val id: String)
    private class WriteAttempt(val entity: TransactionEntity)
    // All outbox bookkeeping is guarded by mutationMutex; durable state remains in Room.
    private val inFlight = mutableMapOf<WriteKey, WriteAttempt>()
    private val acknowledged = mutableMapOf<WriteKey, TransactionEntity>()

    init {
        syncScope.launch {
            users.collectLatest { user ->
                mutationMutex.withLock {
                    activeUid = user?.uid
                    // Keep running tasks tracked across sign-out; a later sign-in must not
                    // start a newer write to the same document before the old one finishes.
                    acknowledged.clear()
                    listenerGeneration.update { it + 1 }
                    syncState.value = SyncState(user?.uid)
                }
                if (user != null) coroutineScope {
                    currencyWork.schedule(user.uid)
                    val outbox = launch {
                        dao.observePendingForUser(user.uid).distinctUntilChanged().conflate()
                            .retryWhen { error, _ ->
                                if (error is CancellationException) throw error
                                Log.w(TAG, "Outbox observation failed; retrying", error)
                                delay(5_000)
                                true
                            }.collect { pending ->
                                if (pending.isNotEmpty()) currencyWork.schedule(user.uid)
                                try {
                                    mutationMutex.withLock { retryPending(user.uid) }
                                } catch (error: Exception) {
                                    if (error is CancellationException) throw error
                                    Log.w(TAG, "Outbox sync failed; retaining pending records for retry", error)
                                }
                            }
                    }
                    val retries = launch {
                        while (isActive) {
                            delay(RETRY_INTERVAL_MILLIS)
                            try {
                                mutationMutex.withLock { retryPending(user.uid) }
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                Log.w(TAG, "Pending transaction retry failed; retaining Room outbox", error)
                            }
                        }
                    }
                    try {
                        observeRemote(user.uid).collect { warning ->
                            syncState.value = SyncState(user.uid, warning)
                        }
                    } finally {
                        outbox.cancel()
                        retries.cancel()
                    }
                }
            }
        }
    }

    override fun observeTransactions(): Flow<Resource<List<Transaction>>> =
        users.flatMapLatest { user ->
            if (user == null) flowOf(Resource.Error("Please sign in",
                cause = TransactionException(TransactionError.SIGN_IN_REQUIRED, "Please sign in")))
            else combine(
                dao.observeAll(user.uid), syncState.filter { it.uid == user.uid }
            ) { local, status ->
                val transactions = local.map { it.toDomain().copy(isCash = dao.isWalletCash(user.uid, it.id)) }
                if (status.warning == null) Resource.Success(transactions)
                else Resource.Error(status.warning, transactions)
            }.onStart { emit(Resource.Loading()) }
        }.onStart { emit(Resource.Loading()) }.catch { error ->
            if (error is CancellationException) throw error
            emit(Resource.Error("Unable to load transactions", cause = error))
        }.flowOn(ioDispatcher)

    override fun observeTransaction(id: String): Flow<Resource<Transaction?>> =
        users.flatMapLatest { user ->
            if (user == null) flowOf<Resource<Transaction?>>(Resource.Error("Please sign in",
                cause = TransactionException(TransactionError.SIGN_IN_REQUIRED, "Please sign in")))
            else combine(
                dao.observeById(id, user.uid), syncState.filter { it.uid == user.uid }
            ) { local, status ->
                val transaction = local?.toDomain()?.copy(isCash = dao.isWalletCash(user.uid, local.id))
                if (status.warning == null) Resource.Success<Transaction?>(transaction)
                else Resource.Error<Transaction?>(status.warning, transaction)
            }.onStart { emit(Resource.Loading()) }
        }.onStart { emit(Resource.Loading()) }.catch { error ->
            if (error is CancellationException) throw error
            emit(Resource.Error("Unable to load transaction", cause = error))
        }.flowOn(ioDispatcher)

    private fun observeRemote(uid: String): Flow<String?> =
        listenerGeneration.flatMapLatest { generation ->
            callbackFlow<QuerySnapshot> {
                val registration = firestore.getCollection("users/$uid/transactions")
                    .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                        if (error != null) close(error)
                        else if (snapshot != null) trySend(snapshot)
                    }
                awaitClose { registration.remove() }
            }.buffer(Channel.CONFLATED).map<QuerySnapshot, String?> { snapshot ->
                mutationMutex.withLock {
                    // Ignore queued events from a listener replaced after a delete acknowledgement.
                    if (activeUid == uid && generation == listenerGeneration.value) {
                        val authoritative = !snapshot.metadata.isFromCache && !snapshot.metadata.hasPendingWrites()
                        val remoteIds = snapshot.documents.map { it.id }.toSet()
                        val remote = snapshot.documents.map { it.id to it.data }.mapFirestoreDocuments(
                            { data, id -> data.toTransaction(id)?.takeIf { it.userId == uid }?.toEntity() },
                            { id -> Log.w(TAG, "Skipping malformed transaction document $id") }
                        )
                        val confirmedDeletes = dao.getPendingForUser(uid).filter { entity ->
                            entity.isDeleted && acknowledged[WriteKey(uid, entity.id)] == entity
                        }.map { it.id }.toSet()
                        dao.reconcileRemote(uid, remote, remoteIds, authoritative, confirmedDeletes)
                        retryPending(uid, remoteIds, authoritative)
                    }
                }
                null
            }.onStart {
                emit(null)
                mutationMutex.withLock { retryPending(uid) }
            }.retryWhen { error, attempt ->
                if (error is CancellationException) throw error
                Log.w(TAG, "Transaction listener failed; retaining Room data and reconnecting", error)
                emit("Unable to sync transactions")
                delay(minOf(RETRY_INTERVAL_MILLIS, (attempt.coerceAtMost(5) + 1) * 5_000L))
                true
            }
        }

    private suspend fun retryPending(
        uid: String,
        remoteIds: Set<String> = emptySet(),
        authoritative: Boolean = false
    ) {
        if (activeUid != uid) return
        val pending = dao.getPendingForUser(uid)
        val pendingByKey = pending.associateBy { WriteKey(uid, it.id) }
        acknowledged.keys.filter { key ->
            key.uid == uid && acknowledged[key] != pendingByKey[key]
        }.forEach { acknowledged.remove(it) }
        pending.forEach { entity ->
            val key = WriteKey(uid, entity.id)
            // A pending edit recreates a remotely deleted row; a delete wins a remote recreation.
            if (authoritative && acknowledged[key] == entity &&
                ((entity.isDeleted && entity.id in remoteIds) || (!entity.isDeleted && entity.id !in remoteIds))) {
                acknowledged.remove(key)
            }
            enqueueRemote(entity)
        }
    }

    override suspend fun isCashTransaction(id: String): Boolean = withContext(ioDispatcher) {
        val uid = authRepository.currentUser.first()?.uid ?: return@withContext false
        dao.isWalletCash(uid, id)
    }

    override suspend fun addTransaction(t: Transaction): Result<Unit> = localWrite { uid ->
        require(t.amountMinor > 0) { "Amount must be greater than zero" }
        require(dao.getById(t.id) == null) { "Transaction already exists" }
        val now = clock.millis()
        val money = prepareMoney(t.copy(targetCurrency = preferences.current()), null)
        val entity = money.copy(userId = uid, createdAt = now, updatedAt = maxOf(now, money.conversion?.fetchedAt ?: 0))
            .also { com.example.clarity.domain.model.CurrencyConversion.validate(it) }.toEntity()
            .copy(pendingSync = true)
        entity
    }

    override suspend fun updateTransaction(t: Transaction): Result<Unit> = localWrite { uid ->
        val existing = dao.getById(t.id)
        require(existing != null && existing.userId == uid && !existing.isDeleted) { "Transaction not found" }
        require(t.userId == existing.userId) { "Cannot change transaction owner" }
        require(t.amountMinor > 0) { "Amount must be greater than zero" }
        require(existing.updatedAt < Long.MAX_VALUE) { "Transaction timestamp is out of range" }
        require(!dao.isWalletCash(uid, t.id) || t.accountId == null) { "Cash transactions cannot be assigned to a bank account" }
        require(!dao.isWalletCash(uid, t.id) || t.currency == "LKR") { "Cash wallet entries must remain LKR" }
        val money = prepareMoney(t.copy(targetCurrency = existing.targetCurrency), existing.toDomain())
        val entity = money.copy(
            userId = uid, createdAt = existing.createdAt,
            updatedAt = maxOf(clock.millis(), existing.updatedAt + 1, money.conversion?.fetchedAt ?: 0)
        ).also { com.example.clarity.domain.model.CurrencyConversion.validate(it) }.toEntity().copy(pendingSync = true)
        entity
    }

    override suspend fun deleteTransaction(id: String): Result<Unit> = localWrite { uid ->
        val existing = dao.getById(id)
        require(existing != null && existing.userId == uid && !existing.isDeleted) { "Transaction not found" }
        val deleted = existing.copy(isDeleted = true, pendingSync = true)
        deleted
    }

    override suspend fun retryConversion(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            val uid = authRepository.currentUser.first()?.uid ?: throw TransactionException(TransactionError.SIGN_IN_REQUIRED)
            val row = dao.getById(id)
            require(row != null && row.userId == uid && !row.isDeleted) { "Transaction not found" }
            if (row.pendingConversion) currencyWork.schedule(uid, replace = true)
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    private suspend fun localWrite(block: suspend (String) -> TransactionEntity): Result<Unit> = withContext(ioDispatcher) {
        try {
            val uid = authRepository.currentUser.first()?.uid
                ?: return@withContext Result.failure(TransactionException(TransactionError.SIGN_IN_REQUIRED, "Please sign in"))
            mutationMutex.withLock {
                if (activeUid != uid) throw TransactionException(TransactionError.SIGN_IN_REQUIRED, "Please sign in")
                val entity = block(uid)
                require(authRepository.currentUser.first()?.uid == uid) { "Your session changed. Please try again." }
                dao.persistLocal(entity)
                enqueueRemote(entity)
                currencyWork.schedule(uid)
            }
            Result.success(Unit)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    private fun enqueueRemote(entity: TransactionEntity) {
        if (entity.userId != activeUid) return
        val key = WriteKey(entity.userId, entity.id)
        // Wait for the previous version, including an upload preceding a local deletion.
        // Overlapping tasks could otherwise recreate a document after its delete succeeds.
        if (inFlight.containsKey(key) || acknowledged[key] == entity) return
        acknowledged.remove(key)
        val attempt = WriteAttempt(entity)
        inFlight[key] = attempt
        // Await account acknowledgement outside mutationMutex so local saves stay responsive.
        syncScope.launch {
            try {
                val uploaded = accountUploader.uploadPending(entity.userId) {
                    mutationMutex.withLock {
                        if (activeUid != entity.userId) {
                            finishWrite(key, attempt, IllegalStateException("User changed before upload"))
                        } else {
                            startRemoteWrite(key, attempt)
                        }
                    }
                }
                if (!uploaded) finishWrite(key, attempt, IllegalStateException("Accounts remain pending"))
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Could not queue transaction write; local change retained for retry", error)
                finishWrite(key, attempt, error)
            }
        }
    }

    private fun startRemoteWrite(key: WriteKey, attempt: WriteAttempt) {
        val entity = attempt.entity
        val document = firestore.getCollection("users/" + entity.userId + "/transactions").document(entity.id)
        // Room is the offline queue. Server transactions fail offline and are retried above;
        // their read/compare/write prevents timestamp-older edits from blindly overwriting.
        // Neither transaction tasks nor delete tasks are awaited by the local write path.
        val task = if (entity.isDeleted) document.delete() else document.firestore.runTransaction { transaction ->
            val snapshot = transaction.get(document)
            val remote = snapshot.toDomain()
            require(!snapshot.exists() || (remote != null && remote.userId == entity.userId)) {
                "Remote transaction is malformed or belongs to another user"
            }
            if (remote == null || remote.updatedAt < entity.updatedAt) {
                transaction.set(document, entity.toDomain().copy(createdAt = remote?.createdAt ?: entity.createdAt).toFirestoreMap())
            }
            Unit
        }
        task.addOnSuccessListener { finishWrite(key, attempt, null) }
        task.addOnFailureListener { error ->
            Log.w(TAG, "Remote transaction write failed; local change retained for retry", error)
            finishWrite(key, attempt, error)
        }
    }

    private fun finishWrite(key: WriteKey, attempt: WriteAttempt, error: Exception?) {
        syncScope.launch {
            try {
                mutationMutex.withLock {
                    // Identity checks prevent an old task callback from acknowledging a newer attempt.
                    if (inFlight[key] !== attempt) return@withLock
                    inFlight.remove(key)
                    val current = dao.getById(key.id)
                    if (error != null) {
                        if (activeUid == key.uid) syncState.value = SyncState(key.uid, "Unable to sync transactions")
                    } else if (current == attempt.entity) {
                        acknowledged[key] = attempt.entity
                        if (attempt.entity.isDeleted && activeUid == key.uid) {
                            // Restart the sole listener to obtain fresh server absence before removing
                            // a tombstone. Older queued snapshots cannot resurrect the deleted row.
                            listenerGeneration.update { it + 1 }
                        }
                    }
                    // Send a newer edit/tombstone only after the previous task is terminal.
                    // Failed unchanged versions remain pending for the normal retry points.
                    if (current != null && current.userId == key.uid && current.pendingSync &&
                        current != attempt.entity && activeUid == key.uid) {
                        enqueueRemote(current)
                    }
                }
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                Log.w(TAG, "Could not acknowledge remote write; retaining Room outbox for retry", failure)
            }
        }
    }

    internal fun stopSync(): Job = checkNotNull(syncScope.coroutineContext[Job]).also { it.cancel() }

    // Local save never waits for exchange-rate network access.
    private suspend fun prepareMoney(t: Transaction, old: Transaction?): Transaction {
        com.example.clarity.domain.model.Currencies.digits(t.currency)
        com.example.clarity.domain.model.Currencies.digits(t.targetCurrency)
        val snapshot = when {
            old?.conversion != null && old.amountMinor == t.amountMinor && old.currency == t.currency -> old.conversion
            t.currency == t.targetCurrency -> com.example.clarity.domain.model.ConversionSnapshot.identity(t.amountMinor)
            else -> rates.cached(t.currency, t.targetCurrency)?.let {
                com.example.clarity.domain.model.CurrencyConversion.snapshot(t.amountMinor, it)
            }
        }
        return t.copy(conversion = snapshot, pendingConversion = snapshot == null)
    }

    suspend fun backgroundSync(uid: String): Boolean {
        if (authRepository.currentUser.first()?.uid != uid) return true
        // Fetching runs outside mutationMutex. Every late result is checked again in Room.
        for (request in dao.pendingConversions(uid)) {
            try {
                val quote = rates.latest(request.currency, request.targetCurrency)
                val snapshot = com.example.clarity.domain.model.CurrencyConversion.snapshot(request.amountMinor, quote)
                mutationMutex.withLock {
                    if (activeUid == uid && authRepository.currentUser.first()?.uid == uid) {
                        dao.resolveConversion(request, snapshot, clock.millis())
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Conversion remains pending", error)
            }
        }
        if (authRepository.currentUser.first()?.uid != uid) return true
        mutationMutex.withLock { retryPending(uid) }
        val synced = withTimeoutOrNull(20_000) {
            dao.observePendingForUser(uid).first { it.isEmpty() }
            true
        } ?: false
        return synced && dao.pendingConversions(uid).isEmpty()
    }

    private companion object {
        const val TAG = "TransactionRepository"
        const val RETRY_INTERVAL_MILLIS = 30_000L
    }
}
