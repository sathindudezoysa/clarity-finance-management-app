package com.example.clarity

import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.mapper.toTransaction
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
        AddTransactionUseCase(repository)(transaction.copy(category = " Food ", note = " Lunch ")).getOrThrow()
        assertEquals("savings", repository.saved!!.accountId)
        assertEquals("Food", repository.saved!!.category)
        UpdateTransactionUseCase(repository)(repository.saved!!.copy(accountId = "daily", category = " Travel ")).getOrThrow()
        assertEquals("daily", repository.saved!!.accountId)
        assertEquals("Travel", repository.saved!!.category)
    }

    private class RecordingRepository : TransactionRepository {
        var saved: Transaction? = null
        override fun observeTransactions() = flowOf<Resource<List<Transaction>>>(Resource.Success(emptyList()))
        override fun observeTransaction(id: String) = flowOf<Resource<Transaction?>>(Resource.Success(saved))
        override suspend fun addTransaction(t: Transaction): Result<Unit> { saved = t; return Result.success(Unit) }
        override suspend fun updateTransaction(t: Transaction): Result<Unit> { saved = t; return Result.success(Unit) }
        override suspend fun deleteTransaction(id: String) = Result.success(Unit)
    }
}
