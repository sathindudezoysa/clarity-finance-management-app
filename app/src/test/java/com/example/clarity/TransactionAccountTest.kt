package com.example.clarity

import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.mapper.toTransaction
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.isSignInRequired
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class TransactionAccountTest {
    private val transaction = Transaction(id = "transaction", userId = "user-a", amountMinor = 1250,
        accountId = "savings", createdAt = 1, updatedAt = 10)

    @Test
    fun selectedAccountRoundTripsThroughLocalAndRemoteMappings() {
        assertEquals(transaction, transaction.toEntity().toDomain())
        assertEquals(transaction, transaction.toFirestoreMap().toTransaction(transaction.id))
        assertEquals("savings", transaction.toFirestoreMap()["accountId"])
        assertFalse(transaction.toFirestoreMap().containsKey("pendingSync"))
    }

    @Test
    fun legacyTransactionsStillLoadWithoutAnAccount() {
        val legacy = transaction.copy(accountId = null)
        assertEquals(legacy, legacy.toEntity().toDomain())
        assertEquals(legacy, legacy.toFirestoreMap().minus("accountId").toTransaction(legacy.id))
        assertEquals(legacy, legacy.toFirestoreMap().toTransaction(legacy.id))
    }

    @Test
    fun malformedRemoteAccountReferencesAreRejected() {
        for (invalid in listOf("", " ", "other/account", "a".repeat(129), 42L, true)) {
            assertNull((transaction.toFirestoreMap() + ("accountId" to invalid)).toTransaction(transaction.id))
        }
    }

    @Test
    fun addAndEditUseCasesPreserveAccountSelectionWhenNormalizingFields() = runBlocking {
        val repository = RecordingRepository()
        val accounts = TransactionTestAccounts()
        AddTransactionUseCase(repository, accounts)(transaction.copy(category = " Food ", note = " Lunch ")).getOrThrow()
        assertEquals("savings", repository.saved!!.accountId)
        assertEquals("Food", repository.saved!!.category)
        UpdateTransactionUseCase(repository, accounts)(repository.saved!!.copy(accountId = "daily", category = " Travel ")).getOrThrow()
        assertEquals("daily", repository.saved!!.accountId)
        assertEquals("Travel", repository.saved!!.category)
        assertEquals(listOf("savings", "daily"), accounts.lookups)
    }

    @Test
    fun addAndUpdateRejectBlankAccountsBeforeLookupOrWriting() = runBlocking {
        for (update in listOf(false, true)) {
            for (accountId in listOf(null, "", " \t ")) {
                val repository = RecordingRepository()
                val accounts = TransactionTestAccounts()
                val error = save(update, repository, accounts, transaction.copy(accountId = accountId)).exceptionOrNull()
                assertEquals(TransactionError.ACCOUNT_REQUIRED, (error as TransactionException).error)
                assertTrue(accounts.lookups.isEmpty())
                assertNull(repository.saved)
            }
        }
    }

    @Test
    fun addAndUpdateRejectUnknownAndForeignAccountsBeforeWriting() = runBlocking {
        for (update in listOf(false, true)) {
            for (accountId in listOf("unknown", "foreign")) {
                val repository = RecordingRepository()
                val accounts = TransactionTestAccounts()
                val error = save(update, repository, accounts, transaction.copy(accountId = accountId)).exceptionOrNull()
                assertEquals(TransactionError.ACCOUNT_NOT_FOUND, (error as TransactionException).error)
                assertEquals(listOf(accountId), accounts.lookups)
                assertNull(repository.saved)
            }
        }
    }

    @Test
    fun addAndUpdateWriteTransactionsWithAnAccountReturnedForTheCurrentUser() = runBlocking {
        for (update in listOf(false, true)) {
            val repository = RecordingRepository()
            val accounts = TransactionTestAccounts()
            assertTrue(save(update, repository, accounts, transaction).isSuccess)
            assertEquals(transaction, repository.saved)
            assertEquals(if (update) "update" else "add", repository.operation)
            assertEquals(listOf("savings"), accounts.lookups)
        }
    }

    @Test
    fun addAndUpdatePropagateAccountLookupFailuresWithoutWriting() = runBlocking {
        for (update in listOf(false, true)) {
            val repository = RecordingRepository()
            val accounts = TransactionTestAccounts()
            val failure = AccountException(AccountError.SIGN_IN_REQUIRED, "Please sign in")
            accounts.failure = failure
            assertSame(failure, save(update, repository, accounts, transaction).exceptionOrNull())
            assertNull(repository.saved)
        }
    }

    @Test
    fun resourceErrorsRetainTypedCausesDiagnosticsAndCachedData() {
        val cached = listOf(transaction)
        val causes = listOf(AccountException(AccountError.SIGN_IN_REQUIRED, "Account auth detail"),
            TransactionException(TransactionError.SIGN_IN_REQUIRED, "Transaction auth detail"))
        for (cause in causes) {
            val error = Resource.Error("Different diagnostic text", cached, cause)
            assertTrue(error.cause.isSignInRequired())
            assertSame(cached, error.data)
            assertSame(cause, error.cause)
            assertEquals("Different diagnostic text", error.message)
        }
        assertFalse(Resource.Error("Please sign in", cached).cause.isSignInRequired())
        assertFalse(IllegalStateException("Please sign in").isSignInRequired())
        assertFalse(AccountException(AccountError.NOT_FOUND).isSignInRequired())
        assertFalse(TransactionException(TransactionError.ACCOUNT_NOT_FOUND).isSignInRequired())
    }

    @Test
    fun cancellationDuringAccountLookupIsNotConvertedToASaveFailure() = runBlocking {
        for (update in listOf(false, true)) {
            val repository = RecordingRepository()
            val accounts = TransactionTestAccounts()
            val cancellation = CancellationException("Cancelled")
            accounts.failure = cancellation
            try {
                save(update, repository, accounts, transaction)
                fail("Cancellation must propagate")
            } catch (error: CancellationException) {
                assertSame(cancellation, error)
            }
            assertNull(repository.saved)
        }
    }

    private suspend fun save(update: Boolean, repository: RecordingRepository,
        accounts: AccountRepository, transaction: Transaction): Result<Unit> =
        if (update) UpdateTransactionUseCase(repository, accounts)(transaction)
        else AddTransactionUseCase(repository, accounts)(transaction)

    private class RecordingRepository : TransactionRepository {
        var saved: Transaction? = null
        var operation: String? = null
        override fun observeTransactions() = flowOf<Resource<List<Transaction>>>(Resource.Success(emptyList()))
        override fun observeTransaction(id: String) = flowOf<Resource<Transaction?>>(Resource.Success(saved))
        override suspend fun addTransaction(t: Transaction): Result<Unit> { saved = t; operation = "add"; return Result.success(Unit) }
        override suspend fun updateTransaction(t: Transaction): Result<Unit> { saved = t; operation = "update"; return Result.success(Unit) }
        override suspend fun deleteTransaction(id: String) = Result.success(Unit)
    }
}

// Shared by transaction use-case tests so valid tag tests also supply a real account selection.
internal class TransactionTestAccounts : AccountRepository {
    val lookups = mutableListOf<String>()
    var failure: Throwable? = null
    private val accounts = listOf(
        Account("daily", "user-a", "Daily", "Bank", isDefault = true),
        Account("savings", "user-a", "Savings", "Bank"),
        Account("foreign", "user-b", "Private", "Bank")
    )
    override suspend fun getAccount(id: String): Result<Account?> {
        lookups.add(id)
        failure?.let { return Result.failure(it) }
        return Result.success(accounts.firstOrNull { it.id == id && it.userId == "user-a" })
    }
    override fun observeAccounts() = flowOf(Resource.Success(accounts.filter { it.userId == "user-a" }))
    override suspend fun getDefaultAccount() = Result.success(accounts.first { it.id == "daily" })
    override suspend fun createAccount(account: Account): Result<Unit> = error("Unused")
    override suspend fun updateAccount(account: Account): Result<Unit> = error("Unused")
    override suspend fun setDefaultAccount(id: String): Result<Unit> = error("Unused")
}
