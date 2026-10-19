package com.example.clarity

import com.example.clarity.data.mapper.toDomain
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.mapper.toTransaction
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.repository.TransactionTagRepository
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.domain.usecase.transaction.UpdateTransactionUseCase
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TransactionTagsTest {
    private val accounts = TransactionTestAccounts()
    @Test
    fun suggestionsIncludeRequirementExamplesAndDeduplicateCustomNames() = runBlocking {
        val repository = object : TransactionTagRepository {
            override fun observeTags() = flowOf(listOf(" food ", "Gym", "gym", " Books ", "", "x".repeat(101)))
        }
        val tags = GetTransactionTagsUseCase(repository)().first()
        assertTrue(tags.containsAll(listOf("Food", "Travel", "Groceries", "Savings")))
        assertEquals(TransactionTags.predefined + listOf("Books", "Gym"), tags)
    }

    @Test
    fun addAcceptsEachPredefinedTagWithoutDescription() = runBlocking {
        val repository = RecordingRepository()
        val add = AddTransactionUseCase(repository, accounts)
        for (tag in TransactionTags.predefined) {
            assertTrue(add(Transaction(amountMinor = 1250, category = tag, accountId = "savings")).isSuccess)
            assertEquals(tag, repository.saved!!.category)
            assertEquals("", repository.saved!!.note)
        }
    }

    @Test
    fun customTagAndDescriptionSurviveLocalAndRemoteRoundTrips() = runBlocking {
        val repository = RecordingRepository()
        val original = Transaction(userId = "user-a", amountMinor = 2500,
            category = "  Weekend hobby  ", note = "  Supplies\nfor painting  ", accountId = "savings")
        assertTrue(AddTransactionUseCase(repository, accounts)(original).isSuccess)
        val saved = repository.saved!!
        assertEquals("Weekend hobby", saved.category)
        assertEquals("Supplies\nfor painting", saved.note)
        assertEquals(saved, saved.toEntity().toDomain())
        assertEquals(saved, saved.toFirestoreMap().toTransaction(saved.id))
    }

    @Test
    fun editingCanChangeTagAndClearDescriptionWithoutChangingIdentity() = runBlocking {
        val repository = RecordingRepository()
        val original = Transaction(userId = "user-a", amountMinor = 2500, category = "Food", note = "Lunch", accountId = "savings")
        assertTrue(UpdateTransactionUseCase(repository, accounts)(original.copy(category = " Savings ", note = "  ")).isSuccess)
        assertEquals(original.copy(category = "Savings", note = ""), repository.saved)
    }

    @Test
    fun blankTagKeepsTheExistingGeneralDefault() = runBlocking {
        val repository = RecordingRepository()
        assertTrue(AddTransactionUseCase(repository, accounts)(Transaction(amountMinor = 100, category = "  ", accountId = "savings")).isSuccess)
        assertEquals("General", repository.saved!!.category)
    }

    @Test
    fun addAndEditRejectOversizedTagsAndDescriptionsBeforeWriting() = runBlocking {
        val repository = RecordingRepository()
        val add = AddTransactionUseCase(repository, accounts)
        val edit = UpdateTransactionUseCase(repository, accounts)
        val longTag = Transaction(amountMinor = 100, category = "t".repeat(101))
        val longDescription = Transaction(amountMinor = 100, note = "d".repeat(2001))
        assertTrue(add(longTag).isFailure)
        assertTrue(add(longDescription).isFailure)
        assertTrue(edit(longTag).isFailure)
        assertTrue(edit(longDescription).isFailure)
        assertNull(repository.saved)
    }

    private class RecordingRepository : TransactionRepository {
        override suspend fun retryConversion(id: String) = Result.success(Unit)
        var saved: Transaction? = null
        override fun observeTransactions(): Flow<Resource<List<Transaction>>> = flowOf(Resource.Success(emptyList()))
        override fun observeTransaction(id: String): Flow<Resource<Transaction?>> = flowOf(Resource.Success(saved))
        override suspend fun addTransaction(t: Transaction): Result<Unit> {
            saved = t
            return Result.success(Unit)
        }
        override suspend fun updateTransaction(t: Transaction): Result<Unit> {
            saved = t
            return Result.success(Unit)
        }
        override suspend fun deleteTransaction(id: String) = Result.success(Unit)
    }
}
