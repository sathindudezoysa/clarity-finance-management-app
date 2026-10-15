package com.example.clarity.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.local.dao.AccountDao
import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.remote.AccountRemoteDataSource
import com.example.clarity.data.sync.AccountUploader
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
import javax.inject.Singleton

/** Room supplies accounts and a durable outbox; batches serialize all default changes per user. */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class AccountRepositoryImpl internal constructor(
    private val dao: AccountDao,
    private val remote: AccountRemoteDataSource,
    private val authRepository: AuthRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val runTransaction: suspend (suspend () -> Unit) -> Unit,
    private val warnRetainedAccount: (String) -> Unit = { id ->
        Log.w("AccountRepository", "Keeping account $id missing remotely because local transactions reference it")
    },
    private val warnSkippedAccounts: (List<String>) -> Unit = { ids ->
        Log.w("AccountRepository", "Skipping remote accounts with duplicate names: $ids")
    },
    private val warnInvalidTimestamps: (List<String>) -> Unit = { ids ->
        Log.w("AccountRepository", "Skipping remote accounts with out-of-range timestamps: $ids")
    }
) : AccountRepository, AccountUploader {
    @Inject
    constructor(
        database: ClarityDatabase,
        remote: AccountRemoteDataSource,
        authRepository: AuthRepository,
        ioDispatcher: CoroutineDispatcher
    ) : this(database.accountDao, remote, authRepository, ioDispatcher,
        { block -> database.withTransaction { block() } })

    private val mutationMutex = Mutex()
    private val syncScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val users = authRepository.currentUser.distinctUntilChangedBy { it?.uid }
        .shareIn(syncScope, SharingStarted.Eagerly, replay = 1)
    private data class SyncState(val uid: String?, val warning: String? = null)
    private val syncState = MutableStateFlow(SyncState(null))
    // Survives auth collector restarts so an old batch cannot overtake a newer one after sign-in.
    private val uploadMutex = Mutex()

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
                                reconcileRemote(user.uid, snapshot.accounts.map { it.toEntity() }, snapshot.authoritative, snapshot.remoteIds)
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

    override suspend fun uploadPending(uid: String, afterAccounts: suspend () -> Unit): Boolean {
        // The upload outlives cancellation of the auth listener. Re-signing in cannot
        // send a newer batch while an older Firestore task for this user is unresolved.
        val uploaded = syncScope.async {
            uploadMutex.withLock {
                try {
                    if (authRepository.currentUser.first()?.uid != uid) return@withLock false
                    val pending = mutationMutex.withLock { dao.getAllForUser(uid).filter { it.pendingSync } }
                    if (pending.isNotEmpty()) {
                        // Only uploadMutex serializes the network phase; local operations use mutationMutex.
                        remote.writeAccounts(uid, pending.map { it.toDomain() })
                        mutationMutex.withLock {
                            // Compare each current row with the uploaded version before clearing pendingSync.
                            acknowledgeBatch(pending)
                            if (users.replayCache.firstOrNull()?.uid == uid) syncState.value = SyncState(uid)
                        }
                    }
                    true
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    mutationMutex.withLock {
                        if (users.replayCache.firstOrNull()?.uid == uid) {
                            syncState.value = SyncState(uid, "Unable to sync accounts")
                        }
                    }
                    false
                }
            }
        }.await()
        if (uploaded) afterAccounts()
        return uploaded
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
        saveAccount(validated(account.copy(userId = uid)).toEntity(), create = true)
    }

    override suspend fun updateAccount(account: Account): Result<Unit> = localOperation { uid ->
        if (account.userId != uid) throw AccountException(AccountError.OWNER_CHANGE, "Cannot change account owner")
        saveAccount(validated(account).toEntity(), create = false)
    }

    override suspend fun setDefaultAccount(id: String): Result<Unit> = localOperation { uid ->
        runTransaction {
            val existing = dao.getById(id)
            if (existing == null || existing.userId != uid) throw AccountException(AccountError.NOT_FOUND, "Account not found")
            if (!existing.isDefault) saveAccountInTransaction(existing.copy(isDefault = true), create = false)
        }
    }

    override suspend fun getAccount(id: String): Result<Account?> = localOperation { uid ->
        dao.getById(id)?.takeIf { it.userId == uid }?.toDomain()
    }

    override suspend fun getDefaultAccount(): Result<Account?> = localOperation { uid ->
        dao.getDefault(uid)?.toDomain()
    }

    private suspend fun saveAccount(entity: AccountEntity, create: Boolean) = runTransaction {
        saveAccountInTransaction(entity, create)
    }

    // Reads, validation and both sides of a default switch share one Room transaction.
    private suspend fun saveAccountInTransaction(entity: AccountEntity, create: Boolean) = with(dao) {
        val existing = getById(entity.id)
        if (create) {
            if (existing != null) throw AccountException(AccountError.ALREADY_EXISTS, "Account already exists")
        } else if (existing == null || existing.userId != entity.userId) {
            throw AccountException(AccountError.NOT_FOUND, "Account not found")
        }
        val accounts = getAllForUser(entity.userId)
        if (entity.name.isBlank()) throw AccountException(AccountError.NAME_BLANK, "Account name must not be blank")
        if (accounts.any { it.id != entity.id && it.name.equals(entity.name, ignoreCase = true) }) {
            throw AccountException(AccountError.NAME_DUPLICATE, "An account with this name already exists")
        }
        if (existing?.isDefault == true && !entity.isDefault) {
            throw AccountException(AccountError.DEFAULT_REQUIRED, "Select another default account first")
        }
        val latest = accounts.maxOfOrNull { it.updatedAt } ?: 0L
        require(latest < Long.MAX_VALUE) { "Account timestamp is out of range" }
        val now = maxOf(System.currentTimeMillis(), latest + 1)
        val saved = entity.copy(
            isDefault = accounts.isEmpty() || entity.isDefault,
            createdAt = existing?.createdAt ?: now, updatedAt = now, pendingSync = true
        )
        val previousDefaults = if (saved.isDefault) accounts.filter { it.isDefault && it.id != saved.id }
            .map { it.copy(isDefault = false, updatedAt = now, pendingSync = true) } else emptyList()
        upsertAll(previousDefaults + saved)
    }

    internal suspend fun acknowledgeBatch(entities: List<AccountEntity>) = runTransaction {
        with(dao) {
            // Never clear a newer local change made while the batch was uploading.
            val unchanged = entities.filter { getById(it.id) == it }
            upsertAll(unchanged.map { it.copy(pendingSync = false) })
        }
    }

    internal suspend fun reconcileRemote(
        userId: String,
        remote: List<AccountEntity>,
        authoritative: Boolean,
        remoteIds: Set<String> = remote.map { it.id }.toSet()
    ) = runTransaction {
        with(dao) {
            val local = getAllForUser(userId)
            val pending = local.any { it.pendingSync }
            val invalidTimestampIds = remote.filter { it.userId == userId && it.updatedAt >= Long.MAX_VALUE }.map { it.id }
            if (invalidTimestampIds.isNotEmpty()) warnInvalidTimestamps(invalidTimestampIds)
            val merged = local.associateBy { it.id }.toMutableMap()
            remote.filter { it.userId == userId && it.updatedAt < Long.MAX_VALUE }.forEach { incoming ->
                val existing = getById(incoming.id)
                if (existing != null && existing.userId != userId) return@forEach
                // A pending default switch is an indivisible group. Cache cannot confirm it.
                if (existing?.pendingSync == true || (pending && !authoritative)) return@forEach
                if (existing != null && existing.updatedAt > incoming.updatedAt) {
                    if (authoritative) merged[incoming.id] = existing.copy(pendingSync = true)
                } else {
                    merged[incoming.id] = incoming.copy(
                        isDefault = if (pending) existing?.isDefault == true else incoming.isDefault,
                        pendingSync = pending && incoming.isDefault != (existing?.isDefault == true)
                    )
                }
            }
            if (authoritative) {
                local.filter { !it.pendingSync && it.id !in remoteIds }.forEach {
                    if (hasTransactionReferences(it.id)) {
                        warnRetainedAccount(it.id)
                    } else {
                        merged.remove(it.id)
                        deleteById(it.id, userId)
                    }
                }
            }
            if (merged.isEmpty()) return@with
            val localIds = local.map { it.id }.toSet()
            merged.values.groupBy { it.name.lowercase(java.util.Locale.ROOT) }.values
                .filter { it.size > 1 }.forEach { duplicates ->
                    val kept = duplicates.firstOrNull { it.id in localIds }
                        ?: duplicates.minWith(compareBy<AccountEntity> { it.createdAt }.thenBy { it.id })
                    val skippedIds = duplicates.filter { it.id != kept.id && it.id !in localIds }.map { it.id }
                    skippedIds.forEach { merged.remove(it) }
                    if (skippedIds.isNotEmpty()) warnSkippedAccounts(skippedIds)
                }
            // Normalize malformed or concurrent remote defaults before Room observers see them.
            val defaults = merged.values.filter { it.isDefault }
            val selected = defaults.maxWithOrNull(compareBy<AccountEntity> { it.updatedAt }.thenBy { it.id })
                ?: merged.values.minWith(compareBy<AccountEntity> { it.createdAt }.thenBy { it.id })
            val latest = merged.values.maxOf { it.updatedAt }
            val now = maxOf(System.currentTimeMillis(), if (latest < Long.MAX_VALUE) latest + 1 else latest)
            val normalized = merged.values.map { account ->
                val isDefault = account.id == selected.id
                if (account.isDefault == isDefault) account
                else account.copy(isDefault = isDefault, updatedAt = now, pendingSync = true)
            }
            upsertAll(normalized)
        }
    }

    private fun validated(account: Account): Account {
        val normalized = account.copy(name = account.name.trim(), bankName = account.bankName.trim(),
            accountType = account.accountType?.trim())
        require(normalized.id.isNotBlank() && normalized.id.length <= 128 && '/' !in normalized.id) { "Invalid account id" }
        if (normalized.name.isBlank()) throw AccountException(AccountError.NAME_BLANK, "Account name must not be blank")
        if (normalized.name.length > 100 || normalized.bankName.length > 100 ||
            (normalized.accountType?.length ?: 0) > 100) {
            throw AccountException(AccountError.FIELD_TOO_LONG, "Account field is too long")
        }
        if (!normalized.currency.matches(Regex("[A-Z]{3}"))) {
            throw AccountException(AccountError.CURRENCY_INVALID, "Currency must be a three-letter uppercase code")
        }
        return normalized
    }

    private suspend fun <T> localOperation(block: suspend (String) -> T): Result<T> = withContext(ioDispatcher) {
        try {
            val uid = authRepository.currentUser.first()?.uid
                ?: return@withContext Result.failure(AccountException(AccountError.SIGN_IN_REQUIRED, "Please sign in"))
            mutationMutex.withLock { Result.success(block(uid)) }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    internal fun stopSync(): Job = checkNotNull(syncScope.coroutineContext[Job]).also { it.cancel() }

    private companion object {
        const val RETRY_INTERVAL_MILLIS = 30_000L
    }
}
