package com.example.clarity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.repository.TransactionTagRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.transaction.*
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.transactions.detail.TransactionDetailViewModel
import com.example.clarity.presentation.transactions.edit.TransactionEditViewModel
import com.example.clarity.presentation.transactions.list.TransactionListViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionAccountViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private var nextKey = 0
    private val transactions = MemoryTransactions()
    private val accounts = MemoryAccounts()
    private val getAccounts = GetAccountsUseCase(accounts)

    @After
    fun tearDown() { instrumentation.runOnMainSync { store.clear() } }

    private fun <T : ViewModel> model(type: Class<T>, create: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync {
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
            }
            result = ViewModelProvider(store, factory).get("transaction-${nextKey++}", type)
        }
        return checkNotNull(result)
    }

    private fun editor(handle: SavedStateHandle = SavedStateHandle()) = model(TransactionEditViewModel::class.java) {
        TransactionEditViewModel(handle, GetTransactionUseCase(transactions), AddTransactionUseCase(transactions),
            UpdateTransactionUseCase(transactions), GetTransactionTagsUseCase(object : TransactionTagRepository {
                override fun observeTags() = flowOf(emptyList<String>())
            }), getAccounts)
    }

    private suspend fun ready(editor: TransactionEditViewModel) = withTimeout(10_000) {
        editor.uiState.first { !it.isLoading && !it.isAccountsLoading }
    }

    private fun save(editor: TransactionEditViewModel) = instrumentation.runOnMainSync {
        editor.save(TransactionType.EXPENSE, "12.50", "Food", "Lunch", 100)
    }

    @Test
    fun newEntrySelectsDefaultButReopeningAndViewingUseTheSavedAccount() = runBlocking {
        val editor = editor()
        assertEquals("daily", ready(editor).selectedAccountId)
        instrumentation.runOnMainSync { editor.onAccountSelected("savings") }
        save(editor)
        withTimeout(10_000) { editor.uiState.first { it.isSuccess } }
        val saved = transactions.rows.value.values.single()
        assertEquals("savings", saved.accountId)
        val reopened = editor(SavedStateHandle(mapOf("id" to saved.id)))
        assertEquals("savings", ready(reopened).selectedAccountId)
        val detail = model(TransactionDetailViewModel::class.java) {
            TransactionDetailViewModel(SavedStateHandle(mapOf("id" to saved.id)), GetTransactionUseCase(transactions),
                DeleteTransactionUseCase(transactions), getAccounts)
        }
        val list = model(TransactionListViewModel::class.java) {
            TransactionListViewModel(GetTransactionsUseCase(transactions), getAccounts)
        }
        withTimeout(10_000) { detail.uiState.first { it.accountName == "Savings" } }
        withTimeout(10_000) { list.uiState.first { it.accountNames[saved.accountId] == "Savings" } }
        accounts.values.value = accounts.values.value.map { if (it.id == "savings") it.copy(name = "Travel") else it }
        withTimeout(10_000) { detail.uiState.first { it.accountName == "Travel" } }
        withTimeout(10_000) { list.uiState.first { it.accountNames[saved.accountId] == "Travel" } }
        assertEquals("savings", reopened.uiState.value.selectedAccountId)
    }

    @Test
    fun noAccountsBlocksSavingAndCreatingTheFirstAccountEnablesTheEntry() = runBlocking {
        accounts.values.value = emptyList()
        val editor = editor()
        assertFalse(ready(editor).canSaveWithAccount)
        save(editor)
        assertEquals(R.string.transactions_account_required, editor.uiState.value.accountError)
        assertTrue(transactions.rows.value.isEmpty())
        accounts.values.value = listOf(Account(id = "new", name = "New account", bankName = "Bank", isDefault = true))
        withTimeout(10_000) { editor.uiState.first { it.canSaveWithAccount && it.selectedAccountId == "new" } }
        save(editor)
        withTimeout(10_000) { editor.uiState.first { it.isSuccess } }
        assertEquals("new", transactions.rows.value.values.single().accountId)
    }

    @Test
    fun legacyNullAccountIsNotReplacedWithTheDefaultUntilTheUserChooses() = runBlocking {
        transactions.rows.value = mapOf("legacy" to Transaction(id = "legacy", amountMinor = 1250))
        val editor = editor(SavedStateHandle(mapOf("id" to "legacy")))
        assertNull(ready(editor).selectedAccountId)
        save(editor)
        assertEquals(R.string.transactions_account_select, editor.uiState.value.accountError)
        instrumentation.runOnMainSync { editor.onAccountSelected("savings") }
        save(editor)
        withTimeout(10_000) { editor.uiState.first { it.isSuccess } }
        assertEquals("savings", transactions.rows.value["legacy"]!!.accountId)
    }

    @Test
    fun restoredSelectionDoesNotFollowLaterDefaultChanges() = runBlocking {
        val handle = SavedStateHandle()
        val editor = editor(handle)
        ready(editor)
        instrumentation.runOnMainSync { editor.onAccountSelected("savings") }
        val restored = mutableMapOf<String, Any?>()
        instrumentation.runOnMainSync { handle.keys().forEach { restored[it] = handle.get<Any?>(it) } }
        accounts.values.value = accounts.values.value.map { it.copy(isDefault = it.id == "daily") }
        assertEquals("savings", ready(editor(SavedStateHandle(restored))).selectedAccountId)
    }

    private class MemoryTransactions : TransactionRepository {
        val rows = MutableStateFlow<Map<String, Transaction>>(emptyMap())
        override fun observeTransactions() = rows.map { Resource.Success(it.values.toList()) }
        override fun observeTransaction(id: String) = rows.map { Resource.Success(it[id]) }
        override suspend fun addTransaction(t: Transaction): Result<Unit> {
            rows.value = rows.value + (t.id to t)
            return Result.success(Unit)
        }
        override suspend fun updateTransaction(t: Transaction) = addTransaction(t)
        override suspend fun deleteTransaction(id: String): Result<Unit> {
            rows.value = rows.value - id
            return Result.success(Unit)
        }
    }

    private class MemoryAccounts : AccountRepository {
        val values = MutableStateFlow(listOf(
            Account(id = "daily", name = "Daily", bankName = "Bank", isDefault = true),
            Account(id = "savings", name = "Savings", bankName = "Bank")
        ))
        override fun observeAccounts() = values.map { Resource.Success(it) }
        override suspend fun getAccount(id: String) = Result.success(values.value.firstOrNull { it.id == id })
        override suspend fun getDefaultAccount() = Result.success(values.value.firstOrNull { it.isDefault })
        override suspend fun createAccount(account: Account): Result<Unit> = error("Unused")
        override suspend fun updateAccount(account: Account): Result<Unit> = error("Unused")
        override suspend fun setDefaultAccount(id: String): Result<Unit> = error("Unused")
    }
}
