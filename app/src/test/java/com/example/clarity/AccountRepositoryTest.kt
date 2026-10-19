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
import com.example.clarity.data.sync.AccountUploader
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.accounts.AccountMessages
import com.example.clarity.presentation.accounts.accountErrorMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
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
    private val applicationJob = SupervisorJob()
    private val applicationScope = CoroutineScope(applicationJob + Dispatchers.Unconfined)
    private val skippedAccountWarnings = mutableListOf<List<String>>()
    private val invalidTimestampWarnings = mutableListOf<List<String>>()
    private var transactionGate: CompletableDeferred<Unit>? = null
    private var onTransactionStarted: () -> Unit = {}
    private val repository = AccountRepositoryImpl(dao, remote, auth, Dispatchers.Unconfined, applicationScope,
        runTransaction = { block ->
            transactionGate?.let { gate -> onTransactionStarted(); gate.await() }
            block()
        }, warnRetainedAccount = { dao.retainedWarnings.add(it) },
        warnSkippedAccounts = { skippedAccountWarnings.add(it) },
        warnInvalidTimestamps = { invalidTimestampWarnings.add(it) })
    private val uploader: AccountUploader = repository

    @After
    fun tearDown() { applicationScope.cancel() }

    private fun account(id: String, name: String = id, default: Boolean = false) =
        Account(id = id, name = name, bankName = "Bank", isDefault = default)

    private suspend fun saved(id: String) = repository.getAccount(id).getOrThrow()!!

    private suspend fun assertDefault(id: String, userId: String = "user-a") {
        assertEquals(listOf(id), dao.getAllForUser(userId).filter { it.isDefault }.map { it.id })
        assertEquals(id, repository.getDefaultAccount().getOrThrow()!!.id)
    }

    @Test
    fun applicationOwnershipKeepsOneSyncCollectorAndRepositoryCancellationIsIsolated() = runBlocking {
        repository.observeAccounts().first { it !is Resource.Loading }
        repository.observeAccounts().first { it !is Resource.Loading }
        assertEquals(1, remote.subscriptions)
        assertEquals(1, remote.activeSubscriptions)
        auth.currentUser.value = null
        assertEquals(0, remote.activeSubscriptions)
        auth.currentUser.value = User("user-a", null, null)
        assertEquals(2, remote.subscriptions)
        assertEquals(1, remote.activeSubscriptions)
        repository.stopSync().join()
        assertEquals(0, remote.activeSubscriptions)
        assertTrue(applicationJob.isActive)
        assertEquals(42, applicationScope.async { 42 }.await())
    }

    @Test
    fun applicationCancellationPropagatesAndLeavesUnacknowledgedWritesInTheOutbox() = runBlocking {
        remote.fail = true
        repository.createAccount(account("pending")).getOrThrow()
        remote.fail = false
        remote.gate = CompletableDeferred()
        val upload = async(start = CoroutineStart.UNDISPATCHED) { uploader.uploadPending("user-a") }
        assertFalse(upload.isCompleted)
        applicationJob.cancel()
        applicationJob.join()
        try {
            upload.await()
            fail("Cancelled synchronization must propagate cancellation")
        } catch (_: CancellationException) { }
        assertTrue(dao.getById("pending")!!.pendingSync)
        assertEquals(0, remote.activeSubscriptions)
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
    fun remoteDataSourceConflatesSnapshotsWhileReconciliationIsBlockedAndTheLatestWins() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        val original = saved("daily")
        val gate = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        transactionGate = gate
        onTransactionStarted = { started.complete(Unit) }
        dao.committedRows.clear()
        val first = original.copy(bankName = "First snapshot", updatedAt = original.updatedAt + 10)
        val middle = original.copy(bankName = "Middle snapshot", updatedAt = original.updatedAt + 20)
        val latest = original.copy(bankName = "Latest snapshot", updatedAt = original.updatedAt + 30)
        remote.snapshots.emit(AccountSnapshot(listOf(first), true))
        withTimeout(2_000) { started.await() }
        withTimeout(2_000) {
            remote.snapshots.emit(AccountSnapshot(listOf(middle), true))
            remote.snapshots.emit(AccountSnapshot(listOf(latest), true))
        }
        transactionGate = null
        gate.complete(Unit)
        withTimeout(2_000) {
            repository.observeAccounts().first { it.data?.singleOrNull()?.bankName == "Latest snapshot" }
        }
        assertEquals(listOf("First snapshot", "Latest snapshot"),
            dao.committedRows.flatMap { it }.map { it.bankName })
        assertEquals(latest, saved("daily"))
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
        assertEquals(AccountError.NAME_DUPLICATE, (duplicate as AccountException).error)
        val blank = repository.createAccount(account("blank", " \t ")).exceptionOrNull()
        assertEquals(AccountError.NAME_BLANK, (blank as AccountException).error)
        val clearDefault = repository.updateAccount(saved("daily").copy(isDefault = false)).exceptionOrNull()
        assertEquals(AccountError.DEFAULT_REQUIRED, (clearDefault as AccountException).error)
        assertEquals(before, dao.getAllForUser("user-a"))
        assertDefault("daily")
    }

    @Test
    fun repositoryReturnsTypedErrorsForOwnershipAuthenticationAndInvalidFields() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        suspend fun assertError(expected: AccountError, result: Result<*>) {
            assertEquals(expected, (result.exceptionOrNull() as AccountException).error)
        }
        assertError(AccountError.ALREADY_EXISTS, repository.createAccount(account("daily")))
        assertError(AccountError.NOT_FOUND, repository.setDefaultAccount("missing"))
        assertError(AccountError.NOT_FOUND, repository.updateAccount(account("missing").copy(userId = "user-a")))
        assertError(AccountError.OWNER_CHANGE, repository.updateAccount(saved("daily").copy(userId = "other")))
        for (invalid in listOf(account("long", "a".repeat(101)), account("long").copy(bankName = "a".repeat(101)),
            account("long").copy(accountType = "a".repeat(101)))) {
            assertError(AccountError.FIELD_TOO_LONG, repository.createAccount(invalid))
        }
        assertError(AccountError.CURRENCY_INVALID, repository.createAccount(account("currency").copy(currency = "usd")))
        auth.currentUser.value = null
        assertError(AccountError.SIGN_IN_REQUIRED, repository.createAccount(account("signed-out")))
    }

    @Test
    fun accountErrorMessagesUseTypedErrorsRegardlessOfExceptionMessage() {
        val fallback = AccountMessages.SAVE_ERROR
        assertEquals(AccountMessages.NAME_DUPLICATE,
            accountErrorMessage(AccountException(AccountError.NAME_DUPLICATE, "Different log text"), fallback))
        assertEquals(fallback, accountErrorMessage(IllegalArgumentException("An account with this name already exists"), fallback))
        assertEquals(fallback, accountErrorMessage(null, fallback))
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
        val error = repository.observeAccounts().first { it !is Resource.Loading } as Resource.Error
        assertEquals(AccountError.SIGN_IN_REQUIRED, (error.cause as AccountException).error)
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
    fun offlineCreatesAndDefaultSwitchAreRetriedFromTheOutboxAfterRepositoryRestart() = runBlocking {
        remote.fail = true
        repository.createAccount(account("daily")).getOrThrow()
        repository.createAccount(account("savings")).getOrThrow()
        repository.setDefaultAccount("savings").getOrThrow()
        val pending = dao.getAllForUser("user-a")
        assertTrue(pending.all { it.pendingSync })
        assertDefault("savings")
        repository.stopSync().join()

        val restarted = AccountRepositoryImpl(dao, remote, auth, Dispatchers.Unconfined, applicationScope,
            runTransaction = { it() })
        try {
            assertEquals(pending, dao.getAllForUser("user-a"))
            remote.batches.clear()
            remote.fail = false
            remote.snapshots.emit(AccountSnapshot(emptyList(), authoritative = false))
            assertEquals(setOf("daily", "savings"), remote.batches.single().map { it.id }.toSet())
            assertEquals(listOf("savings"), remote.batches.single().filter { it.isDefault }.map { it.id })
            assertTrue(dao.getAllForUser("user-a").none { it.pendingSync })
            assertEquals("savings", restarted.getDefaultAccount().getOrThrow()!!.id)
        } finally {
            restarted.stopSync().join()
        }
    }

    @Test
    fun uploadsAcrossDifferentUsersCannotAcknowledgeANewerDefaultSwitch() = runBlocking {
        repository.createAccount(account("a")).getOrThrow()
        repository.createAccount(account("b")).getOrThrow()
        remote.batches.clear()
        val oldUpload = CompletableDeferred<Unit>()
        remote.gate = oldUpload
        repository.setDefaultAccount("b").getOrThrow()
        repository.setDefaultAccount("a").getOrThrow()
        val newer = dao.getAllForUser("user-a")
        auth.currentUser.value = User("user-b", null, null)
        repository.createAccount(account("c")).getOrThrow()
        val nextUpload = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        remote.gate = nextUpload
        remote.onStarted = { started.complete(Unit) }
        oldUpload.complete(Unit)
        withTimeout(2_000) { started.await() }
        assertEquals(newer, dao.getAllForUser("user-a"))
        assertTrue(newer.all { it.pendingSync })
        assertEquals(setOf("user-b"), remote.batches.last().map { it.userId }.toSet())
        remote.gate = null
        nextUpload.complete(Unit)
        assertTrue(uploader.uploadPending("user-b"))
        assertTrue(dao.getAllForUser("user-b").none { it.pendingSync })
        assertEquals(newer, dao.getAllForUser("user-a"))
        auth.currentUser.value = User("user-a", null, null)
        assertDefault("a")
        assertTrue(dao.getAllForUser("user-a").none { it.pendingSync })
        assertEquals(listOf("a"), remote.batches.last().filter { it.isDefault }.map { it.id })
        assertTrue(remote.batches.all { it.map { account -> account.userId }.distinct().size == 1 })
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
            uploader.uploadPending("user-a") {
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
        assertFalse(uploader.uploadPending("user-a") { transactionWrites++ })
        assertEquals(0, transactionWrites)
        assertTrue(dao.getById("daily")!!.pendingSync)
        remote.fail = false
        assertTrue(uploader.uploadPending("user-a") { transactionWrites++ })
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
        withTimeout(2_000) { uploader.uploadPending("user-a") }
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
    fun outOfRangeRemoteTimestampsAreSkippedWithoutDeletingTheLocalCopy() = runBlocking {
        repository.createAccount(account("daily")).getOrThrow()
        val local = dao.getById("daily")!!
        val invalid = local.copy(id = "invalid", name = "Invalid", updatedAt = Long.MAX_VALUE)
        val invalidReplacement = local.copy(bankName = "Malformed update", updatedAt = Long.MAX_VALUE)
        val valid = local.copy(id = "savings", name = "Savings", isDefault = false)
        remote.batches.clear()

        repository.reconcileRemote("user-a", listOf(invalid, invalidReplacement, valid), authoritative = true)

        assertEquals(local, dao.getById(local.id))
        assertEquals(valid, dao.getById(valid.id))
        assertNull(dao.getById(invalid.id))
        assertEquals(listOf(listOf(invalid.id, local.id)), invalidTimestampWarnings)
        assertDefault(local.id)
        assertTrue(remote.batches.isEmpty())
    }

    @Test
    fun reconciliationSaturatesTimestampWhenExistingLocalDataIsAtTheLimit() = runBlocking {
        val local = account("daily", default = true).copy(userId = "user-a", updatedAt = Long.MAX_VALUE).toEntity()
        dao.upsertAll(listOf(local))
        val incoming = local.copy(id = "savings", name = "Savings", updatedAt = 10)

        repository.reconcileRemote("user-a", listOf(local, incoming), authoritative = true)

        assertEquals(local, dao.getById(local.id))
        assertEquals(Long.MAX_VALUE, dao.getById(incoming.id)!!.updatedAt)
        assertDefault(local.id)
        assertEquals(listOf(listOf(local.id)), invalidTimestampWarnings)
    }

    @Test
    fun duplicateRemoteNamesKeepTheOldestAndDoNotInterruptTheSyncLoop() = runBlocking {
        val older = account("z-older", "Daily").copy(userId = "user-a", createdAt = 10, updatedAt = 10)
        val newer = account("a-newer", "DAILY", default = true)
            .copy(userId = "user-a", createdAt = 20, updatedAt = 20)

        remote.snapshots.emit(AccountSnapshot(listOf(newer, older), true))

        assertEquals(listOf(older.id), dao.getAllForUser("user-a").map { it.id })
        assertEquals(older.createdAt, saved(older.id).createdAt)
        assertDefault(older.id)
        assertEquals(listOf(listOf(newer.id)), skippedAccountWarnings)
        assertTrue(remote.batches.flatten().none { it.id == newer.id })

        val updated = saved(older.id).copy(bankName = "Next snapshot", updatedAt = saved(older.id).updatedAt + 1)
        remote.snapshots.emit(AccountSnapshot(listOf(updated, newer), true))

        assertEquals(updated, saved(older.id))
        assertNull(dao.getById(newer.id))
        assertDefault(older.id)
        assertTrue(repository.observeAccounts().first { it !is Resource.Loading } is Resource.Success)
    }

    @Test
    fun remoteRenameCannotReplaceAnotherLocalAccountWithTheSameName() = runBlocking {
        repository.createAccount(account("daily", "Daily")).getOrThrow()
        repository.createAccount(account("savings", "Savings")).getOrThrow()
        val local = dao.getAllForUser("user-a")
        val incoming = local.map { if (it.id == "savings") it.copy(name = "DAILY", updatedAt = it.updatedAt + 1) else it }
        repository.reconcileRemote("user-a", incoming, authoritative = true)
        assertEquals(local, dao.getAllForUser("user-a"))
        assertEquals(listOf(listOf("savings")), skippedAccountWarnings)
        assertDefault("daily")
    }

    @Test
    fun twoRemoteRenamesToTheSameNewNameKeepBothExistingLocalIds() = runBlocking {
        repository.createAccount(account("daily", "Daily")).getOrThrow()
        repository.createAccount(account("savings", "Savings")).getOrThrow()
        val local = dao.getAllForUser("user-a")
        repository.reconcileRemote("user-a", local.map { it.copy(name = "Shared", updatedAt = it.updatedAt + 1) }, true)
        assertEquals(setOf("daily", "savings"), dao.getAllForUser("user-a").map { it.id }.toSet())
        assertEquals("Shared", saved("daily").name)
        assertEquals("Savings", saved("savings").name)
        assertEquals(listOf(listOf("savings")), skippedAccountWarnings)
        assertDefault("daily")
    }

    @Test
    fun aRemoteRenameMayUseTheNameOfAnAccountAuthoritativelyDeletedInTheSameSnapshot() = runBlocking {
        repository.createAccount(account("daily", "Daily")).getOrThrow()
        repository.createAccount(account("savings", "Savings")).getOrThrow()
        val renamed = dao.getById("daily")!!.copy(name = "Savings")
        repository.reconcileRemote("user-a", listOf(renamed), authoritative = true)
        assertEquals(listOf(renamed), dao.getAllForUser("user-a"))
        assertTrue(skippedAccountWarnings.isEmpty())
    }

    @Test
    fun remoteNameCollisionKeepsTheExistingLocalAccountUnchanged() = runBlocking {
        repository.createAccount(account("local", "Daily")).getOrThrow()
        val local = dao.getById("local")!!
        val incoming = account("remote", "DAILY", default = true)
            .copy(userId = "user-a", createdAt = 1, updatedAt = local.updatedAt + 1).toEntity()
        remote.batches.clear()

        repository.reconcileRemote("user-a", listOf(incoming, local), authoritative = true)

        assertEquals(listOf(local), dao.getAllForUser("user-a"))
        assertNull(dao.getById(incoming.id))
        assertDefault(local.id)
        assertEquals(listOf(listOf(incoming.id)), skippedAccountWarnings)
        assertTrue(remote.batches.isEmpty())
    }

    @Test
    fun duplicateRemoteNameTiesKeepTheSmallestIdAndNormalizeSurvivingDefaults() = runBlocking {
        val kept = account("a", "Daily", default = true)
            .copy(userId = "user-a", createdAt = 10, updatedAt = 10).toEntity()
        val skipped = kept.copy(id = "z", name = "DAILY", updatedAt = 40)
        val other = kept.copy(id = "savings", name = "Savings", createdAt = 20, updatedAt = 30)

        repository.reconcileRemote("user-a", listOf(skipped, other, kept), authoritative = true)

        assertEquals(setOf(kept.id, other.id), dao.getAllForUser("user-a").map { it.id }.toSet())
        assertNull(dao.getById(skipped.id))
        assertDefault(other.id)
        assertEquals(listOf(listOf(skipped.id)), skippedAccountWarnings)
        assertTrue(dao.defaultCounts.all { it == 1 })
        assertTrue(remote.batches.flatten().none { it.id == skipped.id })
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
        val committedRows = mutableListOf<List<AccountEntity>>()
        override suspend fun hasTransactionReferences(accountId: String) = transactions.any { it.accountId == accountId }
        override fun observeAll(userId: String) = rows.map { it.values.filter { row -> row.userId == userId } }.distinctUntilChanged()
        override suspend fun getAllForUser(userId: String) = rows.value.values.filter { it.userId == userId }
        override suspend fun getById(id: String) = rows.value[id]
        override suspend fun getDefault(userId: String) = getAllForUser(userId).firstOrNull { it.isDefault }
        override suspend fun upsertAll(entities: List<AccountEntity>) {
            if (entities.isNotEmpty()) committedRows.add(entities)
            val updated = rows.value + entities.associateBy { it.id }
            updated.values.groupBy { it.userId }.values.forEach { accounts ->
                assertEquals("Account names must be unique per user", accounts.size,
                    accounts.map { it.name.lowercase(java.util.Locale.ROOT) }.distinct().size)
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
        var subscriptions = 0
        var activeSubscriptions = 0
        val batches = mutableListOf<List<Account>>()
        var gate: CompletableDeferred<Unit>? = null
        var fail = false
        var onCommitted: () -> Unit = {}
        var onStarted: () -> Unit = {}
        val snapshots = MutableSharedFlow<AccountSnapshot>()
        override fun observeAccounts(userId: String): Flow<AccountSnapshot> = snapshots.buffer(Channel.CONFLATED)
            .onStart { subscriptions++; activeSubscriptions++ }
            .onCompletion { activeSubscriptions-- }
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
