package com.example.clarity.presentation.transactions.detail

import androidx.lifecycle.SavedStateHandle
import com.example.clarity.R
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.usecase.transaction.DeleteTransactionUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionUseCase
import com.example.clarity.transactions.FakeTransactionRepository
import com.example.clarity.transactions.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private fun vm(repository: FakeTransactionRepository) = TransactionDetailViewModel(
        SavedStateHandle(mapOf("id" to "id")), GetTransactionUseCase(repository), DeleteTransactionUseCase(repository)
    )
    @Test fun deleteNeedsConfirmationAndSignalsNavigationOnlyAfterSuccess() = runTest {
        val repository = FakeTransactionRepository()
        repository.publish(Transaction(id = "id", amountMinor = 100L))
        val vm = vm(repository)
        advanceUntilIdle()
        vm.showDeleteDialog()
        assertTrue(vm.uiState.value.isDeleteDialogVisible)
        assertNull(repository.lastDeleted)
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals("id", repository.lastDeleted)
        assertTrue(vm.uiState.value.isDeleted)
        assertFalse(vm.uiState.value.isDeleting)
    }
    @Test fun failedDeleteRetainsTransactionAndAllowsRetry() = runTest {
        val repository = FakeTransactionRepository()
        val original = Transaction(id = "id", amountMinor = 100L)
        repository.publish(original)
        repository.writeResult = Result.failure(IllegalStateException("failed"))
        val vm = vm(repository)
        advanceUntilIdle()
        vm.confirmDelete()
        advanceUntilIdle()
        assertEquals(original, vm.uiState.value.transaction)
        assertEquals(R.string.transactions_delete_error, vm.uiState.value.error)
        assertFalse(vm.uiState.value.isDeleted)
        repository.writeResult = Result.success(Unit)
        vm.confirmDelete()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isDeleted)
    }
}
