package com.example.clarity.data.repository

import com.example.clarity.data.local.dao.AccountDao
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.AccountRemoteDataSource
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Room supplies accounts and a durable outbox; batches serialize all default changes per user. */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountRepositoryImpl @Inject constructor(
    private val dao: AccountDao,
    private val remote: AccountRemoteDataSource,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher
) : AccountRepository {
    private val mutationMutex = Mutex()
    private val syncScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val users = authRepository.currentUser.distinctUntilChangedBy { it?.uid }
        .shareIn(syncScope, SharingStarted.Eagerly, replay = 1)
    private data class SyncState(val uid: String?, val warning: String? = null)
    private val syncState = MutableStateFlow(SyncState(null))
    // Survives auth collector restarts so an old batch cannot overtake a newer one after sign-in.
    private val uploadMutexes = mutableMapOf<String, Mutex>()

    init {
        syncScope.launch {
            users.collectLatest { user ->
                syncState.value = SyncState(user?.uid)
                if (user != null) coroutineScope {
                    val triggers = Channel<Unit>(Channel.CONFLATED)
                    launch {
                        dao.observeAll(user.uid).collect { triggers.trySend(Unit) }
                    }
                    launch {
                        while (isActive) {
                            triggers.trySend(Unit)
                            delay(RETRY_INTERVAL_MILLIS)
                        }
                    }
                    launch {
                        remote.observeAccounts(user.uid).map { snapshot ->
                            mutationMutex.withLock {
                                dao.reconcileRemote(user.uid, snapshot.accounts.map { it.toEntity() }, snapshot.authoritative)
                            }
                            triggers.trySend(Unit)
                        }.retryWhen { error, _ ->
                            if (error is CancellationException) throw error
                            syncState.value = SyncState(user.uid, "Unable to sync accounts")
                            delay(RETRY_INTERVAL_MILLIS)
                            true
                        }.collect()
                    }
                    for (trigger in triggers) uploadPending(user.uid)
                }
            }
        }
    }

    private suspend fun uploadPending(uid: String) {
        val uploadMutex = mutationMutex.withLock { uploadMutexes.getOrPut(uid) { Mutex() } }
        // The upload outlives cancellation of the auth listener. Re-signing in cannot
        // send a newer batch while an older Firestore task for this user is unresolved.
        syncScope.async {
            uploadMutex.withLock {
                try {
                    if (authRepository.currentUser.first()?.uid != uid) return@withLock
                    val pending = mutationMutex.withLock { dao.getAllForUser(uid).filter { it.pendingSync } }
                    if (pending.isNotEmpty()) {
                        remote.writeAccounts(uid, pending.map { it.toDomain() })
                        mutationMutex.withLock { dao.acknowledgeBatch(pending) }
                        if (users.replayCache.firstOrNull()?.uid == uid) syncState.value = SyncState(uid)
                    }
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    if (users.replayCache.firstOrNull()?.uid == uid) {
                        syncState.value = SyncState(uid, "Unable to sync accounts")
                    }
                }
            }
        }.await()
    }

    override fun observeAccounts(): Flow<Resource<List<Account>>> = users.flatMapLatest { user ->
        if (user == null) flowOf(Resource.Error("Please sign in"))
        else combine(dao.observeAll(user.uid), syncState.filter { it.uid == user.uid }) { local, status ->
            val accounts = local.map { it.toDomain() }
            if (status.warning == null) Resource.Success(accounts)
            else Resource.Error(status.warning, accounts)
        }.onStart { emit(Resource.Loading()) }
    }.onStart { emit(Resource.Loading()) }.catch { error ->
        if (error is CancellationException) throw error
        emit(Resource.Error("Unable to load accounts"))
    }.flowOn(ioDispatcher)

    override suspend fun createAccount(account: Account): Result<Unit> = localOperation { uid ->
        dao.save(validated(account.copy(userId = uid)).toEntity(), create = true)
    }

    override suspend fun updateAccount(account: Account): Result<Unit> = localOperation { uid ->
        require(account.userId == uid) { "Cannot change account owner" }
        dao.save(validated(account).toEntity(), create = false)
    }

    override suspend fun setDefaultAccount(id: String): Result<Unit> = localOperation { uid ->
        dao.setDefault(id, uid)
    }

    override suspend fun getAccount(id: String): Result<Account?> = localOperation { uid ->
        dao.getById(id)?.takeIf { it.userId == uid }?.toDomain()
    }

    override suspend fun getDefaultAccount(): Result<Account?> = localOperation { uid ->
        dao.getDefault(uid)?.toDomain()
    }

    private fun validated(account: Account): Account {
        val normalized = account.copy(name = account.name.trim(), bankName = account.bankName.trim(),
            accountType = account.accountType?.trim())
        require(normalized.id.isNotBlank() && normalized.id.length <= 128 && '/' !in normalized.id) { "Invalid account id" }
        require(normalized.name.isNotBlank()) { "Account name must not be blank" }
        require(normalized.name.length <= 100 && normalized.bankName.length <= 100 &&
            (normalized.accountType?.length ?: 0) <= 100) { "Account field is too long" }
        require(normalized.currency.matches(Regex("[A-Z]{3}"))) { "Currency must be a three-letter uppercase code" }
        return normalized
    }

    private suspend fun <T> localOperation(block: suspend (String) -> T): Result<T> = withContext(ioDispatcher) {
        try {
            val uid = authRepository.currentUser.first()?.uid
                ?: return@withContext Result.failure(IllegalStateException("Please sign in"))
            mutationMutex.withLock { Result.success(block(uid)) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    internal fun stopSync() { syncScope.cancel() }

    private companion object {
        const val RETRY_INTERVAL_MILLIS = 30_000L
    }
}
