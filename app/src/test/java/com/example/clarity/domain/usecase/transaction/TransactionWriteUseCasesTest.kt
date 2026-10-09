package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.model.Transaction
import com.example.clarity.transactions.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class TransactionWriteUseCasesTest {
    @Test fun addAndUpdateRejectZeroAndNegativeAmounts() = runTest {
        val repository = FakeTransactionRepository()
        listOf(0L, -1L, Long.MIN_VALUE).forEach { amount ->
            val t = Transaction(amountMinor = amount)
            val add = AddTransactionUseCase(repository)(t)
            val update = UpdateTransactionUseCase(repository)(t)
            assertTrue(add.exceptionOrNull() is IllegalArgumentException)
            assertEquals("Amount must be greater than zero", add.exceptionOrNull()?.message)
            assertEquals("Amount must be greater than zero", update.exceptionOrNull()?.message)
        }
        assertNull(repository.lastAdded)
        assertNull(repository.lastUpdated)
    }
    @Test fun addDefaultsBlankCategoryAndTrimsNote() = runTest {
        val repository = FakeTransactionRepository()
        assertTrue(AddTransactionUseCase(repository)(Transaction(amountMinor = 123L, category = "  ", note = " note ")).isSuccess)
        assertEquals("General", repository.lastAdded?.category)
        assertEquals("note", repository.lastAdded?.note)
    }
    @Test fun updateNormalizesWithoutChangingIdentity() = runTest {
        val repository = FakeTransactionRepository()
        val original = Transaction(id = "id", userId = "owner", amountMinor = 100L, category = " Food ", note = " note ")
        assertTrue(UpdateTransactionUseCase(repository)(original).isSuccess)
        assertEquals(original.copy(category = "Food", note = "note"), repository.lastUpdated)
        UpdateTransactionUseCase(repository)(original.copy(category = " "))
        assertEquals("General", repository.lastUpdated?.category)
    }
    @Test fun enforcesRemoteSchemaLengthsAndPropagatesFailures() = runTest {
        val repository = FakeTransactionRepository()
        assertTrue(AddTransactionUseCase(repository)(Transaction(amountMinor = 1L, category = "x".repeat(101))).isFailure)
        assertTrue(UpdateTransactionUseCase(repository)(Transaction(amountMinor = 1L, note = "x".repeat(2001))).isFailure)
        repository.writeResult = Result.failure(IllegalStateException("Please sign in"))
        assertEquals("Please sign in", AddTransactionUseCase(repository)(Transaction(amountMinor = 1L)).exceptionOrNull()?.message)
    }
}
