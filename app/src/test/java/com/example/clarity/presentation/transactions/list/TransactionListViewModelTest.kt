package com.example.clarity.presentation.transactions.list

import com.example.clarity.R
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.Resource
import com.example.clarity.transactions.FakeTransactionRepository
import com.example.clarity.transactions.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.math.BigInteger

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test fun observesChangesOrderingAndTotalsWithoutRefresh() = runTest {
        val repository = FakeTransactionRepository()
        val vm = TransactionListViewModel(GetTransactionsUseCase(repository))
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.transactions.isEmpty())
        val expense = Transaction(id = "expense", amountMinor = 250L, dateEpochMillis = 100L)
        val income = Transaction(id = "income", type = TransactionType.INCOME, amountMinor = 1000L, dateEpochMillis = 200L)
        repository.publish(expense, income)
        advanceUntilIdle()
        assertEquals(listOf("income", "expense"), vm.uiState.value.transactions.map { it.id })
        assertEquals(BigInteger.valueOf(1000L), vm.uiState.value.incomeMinor)
        assertEquals(BigInteger.valueOf(250L), vm.uiState.value.expenseMinor)
        assertEquals(BigInteger.valueOf(750L), vm.uiState.value.netMinor)
        repository.publish(income)
        advanceUntilIdle()
        assertEquals(BigInteger.valueOf(1000L), vm.uiState.value.netMinor)
    }
    @Test fun listenerWarningKeepsDataButSignedOutStateClearsIt() = runTest {
        val repository = FakeTransactionRepository()
        val vm = TransactionListViewModel(GetTransactionsUseCase(repository))
        val cached = Transaction(amountMinor = 200L)
        repository.reads.value = Resource.Error("Unable to sync transactions", listOf(cached))
        advanceUntilIdle()
        assertEquals(listOf(cached), vm.uiState.value.transactions)
        assertEquals(R.string.transactions_load_error, vm.uiState.value.error)
        repository.reads.value = Resource.Error("Please sign in")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.transactions.isEmpty())
        assertEquals(R.string.transactions_sign_in, vm.uiState.value.error)
    }
    @Test fun loadingClearsPriorAccountDataAndTotalsDoNotOverflow() = runTest {
        val repository = FakeTransactionRepository()
        val vm = TransactionListViewModel(GetTransactionsUseCase(repository))
        repository.publish(
            Transaction(id = "1", amountMinor = Long.MAX_VALUE),
            Transaction(id = "2", amountMinor = Long.MAX_VALUE)
        )
        advanceUntilIdle()
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO, vm.uiState.value.expenseMinor)
        repository.reads.value = Resource.Loading()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.transactions.isEmpty())
    }
}
