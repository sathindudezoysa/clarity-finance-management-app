package com.example.clarity

import com.example.clarity.data.local.dao.AccountDao
import com.example.clarity.data.local.entity.AccountEntity
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.mapper.toAccount
import com.example.clarity.data.remote.AccountRemoteDataSource
import com.example.clarity.data.remote.AccountSnapshot
import com.example.clarity.data.repository.AccountRepositoryImpl
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class AccountRepositoryTest {
    private val dao = MemoryDao()
    private val auth = FakeAuth()
    private val remote = FakeRemote()
    private val repository = AccountRepositoryImpl(dao, remote, auth, Dispatchers.Unconfined,
        runTransaction = { block -> block() }, warnRetainedAccount = { dao.retainedWarnings.add(it) })

    @After
    fun tearDown() { repository.stopSync() }

    private fun account(id: String, name: String = id, default: Boolean = false) =
        Account(id = id, name = name, bankName = "Bank", isDefault = default)

    private suspend fun saved(id: String) = repository.getAccount(id).getOrThrow()!!

    private suspend fun assertDefault(id: String, userId: String = "user-a") {
        assertEquals(listOf(id), dao.getAllForUser(userId).filter { it.isDefault }.map { it.id })
        assertEquals(id, repository.getDefaultAccount().getOrThrow()!!.id)
    }

    @Test
    fun firstAccountIsDefaultAndAdditionalAccountsArePreserved() = runBlocking {
        assertNull(repository.getDefaultAccount().getOrThrow())
        assertTrue(repository.createAccount(account("daily")).isSuccess)
        assertTrue(repository.createAccount(account("savings")).isSuccess)
        assertTrue(repository.createAccount(account("travel")).isSuccess)
        assertDefault("daily")
        assertEquals(3, repository.observeAccounts().filterIsInstance<Resource.Success<List<Account>>>().first().data!!.size)
        assertEquals("user-a", saved("daily").userId)
    }

    @Test
    fun settingAndUpdatingDefaultAlwaysKeepExactlyOne() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        repository.createAccount(account("savings")).getOrThrow()
        val createdAt = saved("savings").createdAt
        repository.setDefaultAccount("savings").getOrThrow()
        assertDefault("savings")
        repository.setDefaultAccount("savings").getOrThrow()
        assertDefault("savings")
        repository.updateAccount(saved("daily").copy(isDefault = true)).getOrThrow()
        assertDefault("daily")
        assertEquals(createdAt, saved("savings").createdAt)
        assertTrue(repository.updateAccount(saved("daily").copy(isDefault = false)).isFailure)
        assertTrue(repository.setDefaultAccount("missing").isFailure)
        assertDefault("daily")
        repository.createAccount(account("new-default", default = true)).getOrThrow()
        assertDefault("new-default")
        assertEquals(3, dao.getAllForUser("user-a").size)
    }

    @Test
    fun concurrentCreatesAndSwitchesKeepOneDefaultAtEveryWrite() = runBlocking {
        (1..10).map { async(Dispatchers.Default) { repository.createAccount(account("account-$it")) } }.awaitAll()
            .forEach { it.getOrThrow() }
        (1..30).map { async(Dispatchers.Default) { repository.setDefaultAccount("account-${it % 10 + 1}") } }.awaitAll()
            .forEach { it.getOrThrow() }
        assertEquals(10, dao.getAllForUser("user-a").size)
        assertEquals(1, dao.getAllForUser("user-a").count { it.isDefault })
        assertTrue(dao.defaultCounts.all { it == 1 })
    }

    @Test
    fun blankAndDuplicateNamesAreRejectedOnCreateAndUpdate() = runBlocking {
        assertTrue(repository.createAccount(account("blank", " \t ")).isFailure)
        repository.createAccount(account("daily", " Daily ")).getOrThrow()
        assertEquals("Daily", saved("daily").name)
        assertTrue(repository.createAccount(account("duplicate", " daily ")).isFailure)
        repository.createAccount(account("savings", "Savings")).getOrThrow()
        assertTrue(repository.updateAccount(saved("savings").copy(name = "DAILY", isDefault = true)).isFailure)
        assertTrue(repository.updateAccount(saved("daily").copy(name = "")).isFailure)
        assertDefault("daily")
        assertEquals("Savings", saved("savings").name)
        repository.updateAccount(saved("daily").copy(bankName = "New bank")).getOrThrow()
        assertDefault("daily")
    }

    @Test
    fun repositoryValidationKeepsUiErrorTypesAndDoesNotAlterTheDefaultOnFailure() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        repository.createAccount(account("savings")).getOrThrow()
        val before = dao.getAllForUser("user-a")
        val duplicate = repository.updateAccount(saved("savings").copy(name = " DAILY ", isDefault = true)).exceptionOrNull()
        assertTrue(duplicate is IllegalArgumentException)
        assertEquals("An account with this name already exists", duplicate!!.message)
        val blank = repository.createAccount(account("blank", " \t ")).exceptionOrNull()
        assertTrue(blank is IllegalArgumentException)
        assertEquals("Account name must not be blank", blank!!.message)
        val clearDefault = repository.updateAccount(saved("daily").copy(isDefault = false)).exceptionOrNull()
        assertTrue(clearDefault is IllegalArgumentException)
        assertEquals("Select another default account first", clearDefault!!.message)
        assertEquals(before, dao.getAllForUser("user-a"))
        assertDefault("daily")
    }

    @Test
    fun ownershipAndDefaultSelectionAreScopedToSignedInUser() = runBlocking {
        repository.createAccount(account("a", "Daily")).getOrThrow()
        val userA = saved("a")
        auth.currentUser.value = User("user-b", null, null)
        repository.createAccount(account("b", "Daily")).getOrThrow()
        assertDefault("b", "user-b")
        assertNull(repository.getAccount("a").getOrThrow())
        assertTrue(repository.setDefaultAccount("a").isFailure)
        assertTrue(repository.updateAccount(userA.copy(userId = "user-b")).isFailure)
        assertTrue(repository.updateAccount(saved("b").copy(userId = "user-a")).isFailure)
        assertEquals("a", dao.getDefault("user-a")!!.id)
        assertEquals(listOf("b"), repository.observeAccounts().filterIsInstance<Resource.Success<List<Account>>>().first().data!!.map { it.id })
        auth.currentUser.value = null
        assertTrue(repository.createAccount(account("signed-out")).isFailure)
        assertTrue(repository.getDefaultAccount().isFailure)
        assertTrue(repository.observeAccounts().first { it !is Resource.Loading } is Resource.Error)
    }

    @Test
    fun defaultSwitchUploadsBothSidesInOneBatchAndRetainsNewerEdits() = runBlocking {
        repository.createAccount(account("a")).getOrThrow()
        repository.createAccount(account("b")).getOrThrow()
        remote.batches.clear()
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate
        repository.setDefaultAccount("b").getOrThrow()
        val batch = remote.batches.single()
        assertEquals(setOf("a", "b"), batch.map { it.id }.toSet())
        assertEquals(listOf("b"), batch.filter { it.isDefault }.map { it.id })
        repository.setDefaultAccount("a").getOrThrow()
        assertDefault("a")
        assertTrue(dao.getAllForUser("user-a").all { it.pendingSync })
        remote.gate = null
        gate.complete(Unit)
        assertEquals(listOf("a"), remote.batches.last().filter { it.isDefault }.map { it.id })
        assertTrue(dao.getAllForUser("user-a").none { it.pendingSync })
        assertDefault("a")
    }

    @Test
    fun failedUploadRetainsWholeDefaultSwitchForRetry() = runBlocking {
        repository.createAccount(account("a")).getOrThrow()
        repository.createAccount(account("b")).getOrThrow()
        remote.fail = true
        repository.setDefaultAccount("b").getOrThrow()
        assertTrue(dao.getAllForUser("user-a").all { it.pendingSync })
        assertDefault("b")
        remote.fail = false
        repository.updateAccount(saved("b").copy(bankName = "Retry bank")).getOrThrow()
        assertEquals(setOf("a", "b"), remote.batches.last().map { it.id }.toSet())
        assertEquals(listOf("b"), remote.batches.last().filter { it.isDefault }.map { it.id })
        assertTrue(dao.getAllForUser("user-a").none { it.pendingSync })
    }

    @Test
    fun offlineAccountMustBeAcknowledgedBeforeTransactionUpload() = runBlocking {
        remote.fail = true
        repository.createAccount(account("daily")).getOrThrow()
        val pending = dao.getById("daily")!!
        assertTrue(pending.pendingSync)
        val events = mutableListOf<String>()
        remote.onCommitted = { events.add("account") }
        val gate = CompletableDeferred<Unit>()
        remote.fail = false
        remote.gate = gate
        val sync = async(start = CoroutineStart.UNDISPATCHED) {
            repository.uploadPending("user-a") {
                assertFalse(dao.getById("daily")!!.pendingSync)
                events.add("transaction")
            }
        }
        assertFalse(sync.isCompleted)
        assertTrue(events.isEmpty())
        assertTrue(dao.getById("daily")!!.pendingSync)
        gate.complete(Unit)
        assertTrue(sync.await())
        assertEquals(listOf("account", "transaction"), events)
    }

    @Test
    fun failedAccountUploadDefersTransactionsUntilNextSync() = runBlocking {
        remote.fail = true
        repository.createAccount(account("daily")).getOrThrow()
        var transactionWrites = 0
        assertFalse(repository.uploadPending("user-a") { transactionWrites++ })
        assertEquals(0, transactionWrites)
        assertTrue(dao.getById("daily")!!.pendingSync)
        remote.fail = false
        assertTrue(repository.uploadPending("user-a") { transactionWrites++ })
        assertEquals(1, transactionWrites)
        assertFalse(dao.getById("daily")!!.pendingSync)
    }

    @Test
    fun slowUploadAllowsLocalSavesAndCannotAcknowledgeAnEditMadeDuringUpload() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        remote.gate = firstGate
        repository.updateAccount(saved("daily").copy(bankName = "Uploading version")).getOrThrow()
        val uploading = remote.batches.last().single()

        withTimeout(2_000) {
            repository.updateAccount(saved("daily").copy(bankName = "Newer local edit")).getOrThrow()
            repository.createAccount(account("savings")).getOrThrow()
            assertEquals("Newer local edit", saved("daily").bankName)
        }
        val newer = dao.getById("daily")!!
        assertTrue(newer.updatedAt > uploading.updatedAt)
        assertTrue(newer.pendingSync)
        val nextUploadStarted = CompletableDeferred<Unit>()
        remote.onStarted = { nextUploadStarted.complete(Unit) }
        remote.gate = secondGate
        firstGate.complete(Unit)
        withTimeout(2_000) { nextUploadStarted.await() }

        // The older upload was acknowledged, but the newer row must still be pending.
        assertEquals(newer, dao.getById("daily"))
        assertEquals("Newer local edit", remote.batches.last().first { it.id == "daily" }.bankName)
        assertTrue(dao.getById("savings")!!.pendingSync)
        remote.gate = null
        secondGate.complete(Unit)
        withTimeout(2_000) { repository.uploadPending("user-a") }
        assertEquals(newer.copy(pendingSync = false), dao.getById("daily"))
        assertTrue(dao.getAllForUser("user-a").none { it.pendingSync })
    }

    @Test
    fun skippedMalformedAccountDoesNotDeleteLocalCopyOrBlockOtherRemoteUpdates() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        repository.createAccount(account("malformed")).getOrThrow()
        repository.createAccount(account("deleted")).getOrThrow()
        val malformed = dao.getById("malformed")!!
        val daily = saved("daily").copy(bankName = "Remote bank", updatedAt = saved("daily").updatedAt + 10)
        remote.snapshots.emit(AccountSnapshot(listOf(daily), true, setOf("daily", "malformed")))
        assertEquals(malformed, dao.getById("malformed"))
        assertEquals("Remote bank", saved("daily").bankName)
        assertNull(dao.getById("deleted"))
    }

    @Test
    fun reconciliationKeepsReferencedAccountsAndDeletesUnreferencedAccounts() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        repository.createAccount(account("linked")).getOrThrow()
        repository.createAccount(account("unused")).getOrThrow()
        val linked = dao.getById("linked")!!
        dao.transactions.add(TransactionEntity("tx", "user-a", "EXPENSE", 100, "Food", "", 1, 1, 1,
            accountId = "linked"))

        repository.reconcileRemote("user-a", listOf(dao.getById("daily")!!), authoritative = true)

        assertEquals(linked, dao.getById("linked"))
        assertNull(dao.getById("unused"))
        assertEquals(listOf("linked"), dao.retainedWarnings)
    }

    @Test
    fun reconciliationAlsoProtectsAccountsReferencedByPendingTombstones() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        repository.createAccount(account("linked")).getOrThrow()
        dao.transactions.add(TransactionEntity("tx", "user-a", "EXPENSE", 100, "Food", "", 1, 1, 1,
            pendingSync = true, isDeleted = true, accountId = "linked"))
        val remoteAccounts = listOf(dao.getById("daily")!!)
        repository.reconcileRemote("user-a", remoteAccounts, authoritative = true)
        assertNotNull(dao.getById("linked"))
        dao.transactions.clear()
        repository.reconcileRemote("user-a", remoteAccounts, authoritative = true)
        assertNull(dao.getById("linked"))
    }

    @Test
    fun signingOutAndBackInCannotLetAnOlderBatchOvertakeTheNewDefault() = runBlocking {
        repository.createAccount(account("a")).getOrThrow()
        repository.createAccount(account("b")).getOrThrow()
        remote.batches.clear()
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate
        repository.setDefaultAccount("b").getOrThrow()
        auth.currentUser.value = null
        auth.currentUser.value = User("user-a", null, null)
        repository.setDefaultAccount("a").getOrThrow()
        assertEquals(1, remote.batches.size)
        remote.gate = null
        gate.complete(Unit)
        assertEquals(listOf("a"), remote.batches.last().filter { it.isDefault }.map { it.id })
        assertTrue(dao.getAllForUser("user-a").none { it.pendingSync })
        assertDefault("a")
    }

    @Test
    fun cachedRemoteDataCannotOverwritePendingDefaultAndRemoteDefaultsAreNormalized() = runBlocking {
        repository.createAccount(account("a")).getOrThrow()
        repository.createAccount(account("b")).getOrThrow()
        val old = dao.getAllForUser("user-a").map { it.copy(pendingSync = false) }
        remote.fail = true
        repository.setDefaultAccount("b").getOrThrow()
        repository.reconcileRemote("user-a", old, authoritative = false)
        assertDefault("b")
        repository.reconcileRemote("user-a", old, authoritative = true)
        assertDefault("b")
        val pending = dao.getAllForUser("user-a")
        repository.acknowledgeBatch(pending)
        val newer = pending.maxOf { it.updatedAt } + 10
        repository.reconcileRemote("user-a", pending.map { it.copy(isDefault = true, pendingSync = false, updatedAt = newer) }, true)
        assertEquals(1, dao.getAllForUser("user-a").count { it.isDefault })
        val single = dao.getAllForUser("user-a").first().copy(isDefault = false, pendingSync = false, updatedAt = newer + 100)
        repository.acknowledgeBatch(dao.getAllForUser("user-a"))
        repository.reconcileRemote("user-a", listOf(single), true)
        assertDefault(single.id)
    }

    @Test
    fun accountFieldsRoundTripWithoutSendingLocalOutboxMetadata() {
        val original = account("a").copy(userId = "user-a", accountType = "Savings", isDefault = true)
        assertEquals(original, original.toEntity().toDomain())
        assertEquals(original, original.toFirestoreMap().toAccount(original.id))
        assertFalse(original.toFirestoreMap().containsKey("pendingSync"))
        val optional = original.copy(accountType = null)
        assertEquals(optional, optional.toFirestoreMap().minus("accountType").toAccount(optional.id))
        assertNull(original.toFirestoreMap().toAccount("different-id"))
    }

    // Primitive storage is faked; repository validation and reconciliation run unchanged.
    private class MemoryDao : AccountDao() {
        private val rows = MutableStateFlow<Map<String, AccountEntity>>(emptyMap())
        val defaultCounts = mutableListOf<Int>()
        val transactions = mutableListOf<TransactionEntity>()
        val retainedWarnings = mutableListOf<String>()
        override suspend fun hasTransactionReferences(accountId: String) = transactions.any { it.accountId == accountId }
        override fun observeAll(userId: String) = rows.map { it.values.filter { row -> row.userId == userId } }.distinctUntilChanged()
        override suspend fun getAllForUser(userId: String) = rows.value.values.filter { it.userId == userId }
        override suspend fun getById(id: String) = rows.value[id]
        override suspend fun getDefault(userId: String) = getAllForUser(userId).firstOrNull { it.isDefault }
        override suspend fun upsertAll(entities: List<AccountEntity>) {
            val updated = rows.value + entities.associateBy { it.id }
            updated.values.groupBy { it.userId }.values.forEach { accounts ->
                defaultCounts.add(accounts.count { it.isDefault })
                assertEquals("Every committed account set must have one default", 1, accounts.count { it.isDefault })
            }
            rows.value = updated
        }
        override suspend fun deleteById(id: String, userId: String) {
            if (rows.value[id]?.userId == userId) rows.value = rows.value - id
        }
    }

    private class FakeRemote : AccountRemoteDataSource {
        val batches = mutableListOf<List<Account>>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        var onCommitted: () -> Unit = {}
        var onStarted: () -> Unit = {}
        val snapshots = MutableSharedFlow<AccountSnapshot>()
        override fun observeAccounts(userId: String): Flow<AccountSnapshot> = snapshots
        override suspend fun writeAccounts(userId: String, accounts: List<Account>) {
            assertTrue(accounts.all { it.userId == userId })
            batches.add(accounts)
            onStarted()
            gate?.await()
            check(!fail) { "Offline" }
            onCommitted()
        }
    }

    private class FakeAuth : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(User("user-a", null, null))
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
        override suspend fun signOut() { currentUser.value = null }
    }
}
