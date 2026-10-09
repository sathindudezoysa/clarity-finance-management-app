package com.example.clarity.presentation.transactions.edit

import androidx.lifecycle.SavedStateHandle
import com.example.clarity.R
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.usecase.transaction.*
import com.example.clarity.transactions.FakeTransactionRepository
import com.example.clarity.transactions.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private fun vm(repository: FakeTransactionRepository, id: String? = null) = TransactionEditViewModel(
        SavedStateHandle(if (id == null) emptyMap() else mapOf("id" to id)),
        GetTransactionUseCase(repository), AddTransactionUseCase(repository), UpdateTransactionUseCase(repository)
    )

    @Test fun rejectsInvalidZeroAndNegativeAmountsInline() = runTest {
        val repository = FakeTransactionRepository()
        val vm = vm(repository)
        listOf("abc", "", "1.001").forEach {
            vm.save(TransactionType.EXPENSE, it, "", "", 1L)
            assertEquals(R.string.transactions_amount_invalid, vm.uiState.value.amountError)
        }
        listOf("0", "-1").forEach {
            vm.save(TransactionType.EXPENSE, it, "", "", 1L)
            assertEquals(R.string.transactions_amount_positive, vm.uiState.value.amountError)
        }
        assertNull(repository.lastAdded)
    }
    @Test fun savesMinimalExpenseAndExposesLoadingWithoutDuplicateSubmission() = runTest {
        val repository = FakeTransactionRepository()
        repository.writeGate = CompletableDeferred()
        val vm = vm(repository)
        vm.save(TransactionType.EXPENSE, "12.3", "", "", 123L)
        assertTrue(vm.uiState.value.isSaving)
        advanceUntilIdle()
        vm.save(TransactionType.EXPENSE, "99", "", "", 123L)
        repository.writeGate?.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSuccess)
        assertFalse(vm.uiState.value.isSaving)
        assertEquals(1230L, repository.lastAdded?.amountMinor)
        assertEquals("General", repository.lastAdded?.category)
        assertEquals("", repository.lastAdded?.userId)
        assertTrue(java.util.UUID.fromString(repository.lastAdded?.id).toString().isNotBlank())
    }
    @Test fun preloadsAndEditsIncomePreservingIdentity() = runTest {
        val repository = FakeTransactionRepository()
        val existing = Transaction(id = "id", userId = "owner", amountMinor = 100L, createdAt = 5L)
        repository.publish(existing)
        val vm = vm(repository, "id")
        advanceUntilIdle()
        assertEquals(existing, vm.uiState.value.transaction)
        vm.save(TransactionType.INCOME, "42.50", " Salary ", " note ", 999L)
        advanceUntilIdle()
        assertEquals(existing.copy(type = TransactionType.INCOME, amountMinor = 4250L, category = "Salary", note = "note", dateEpochMillis = 999L), repository.lastUpdated)
        assertTrue(vm.uiState.value.isSuccess)
        assertNull(repository.lastAdded)
    }
    @Test fun missingTransactionCannotBeSavedAndFailuresKeepFormOpen() = runTest {
        val repository = FakeTransactionRepository()
        val missing = vm(repository, "missing")
        advanceUntilIdle()
        assertEquals(R.string.transactions_not_found, missing.uiState.value.error)
        missing.save(TransactionType.EXPENSE, "1", "", "", 1L)
        assertNull(repository.lastUpdated)
        repository.writeResult = Result.failure(IllegalStateException("Please sign in"))
        val add = vm(repository)
        add.save(TransactionType.INCOME, "1", "", "", 1L)
        advanceUntilIdle()
        assertFalse(add.uiState.value.isSuccess)
        assertFalse(add.uiState.value.isSaving)
        assertEquals(R.string.transactions_sign_in, add.uiState.value.error)
    }
}
