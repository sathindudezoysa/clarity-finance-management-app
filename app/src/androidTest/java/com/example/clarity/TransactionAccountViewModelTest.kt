package com.example.clarity

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.ExchangeRate
import com.example.clarity.domain.model.ProofOpenFile
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionProof
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.CurrencyPreference
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.repository.TransactionProofRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.repository.TransactionTagRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.transaction.*
import com.example.clarity.domain.util.Resource
import com.example.clarity.presentation.transactions.detail.TransactionDetailViewModel
import com.example.clarity.presentation.transactions.detail.TransactionDetailScreen
import com.example.clarity.presentation.transactions.edit.TransactionEditViewModel
import com.example.clarity.presentation.transactions.edit.TransactionEditScreen
import com.example.clarity.presentation.transactions.list.TransactionListViewModel
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.transactions.TransactionMessages
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CompletableDeferred
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
    private val preferences = object : CurrencyPreferenceRepository {
        override fun observe() = flowOf(CurrencyPreference("LKR"))
        override suspend fun set(currency: String) = Result.success(Unit)
        override suspend fun current() = "LKR"
    }
    private val rates = object : ExchangeRateRepository {
        override suspend fun cached(source: String, target: String) = ExchangeRate(source, target, "1", "ExchangeRate-API", 100, 100, 86_500_000)
        override suspend fun latest(source: String, target: String) = cached(source, target)!!
    }
    private val proofs = object : TransactionProofRepository {
        override fun observeProofs(transactionId: String) = flowOf(emptyList<TransactionProof>())
        override suspend fun attach(transactionId: String, contentUri: String) = Result.success(Unit)
        override suspend fun open(transactionId: String, proofId: String): Result<ProofOpenFile> = error("Unused")
        override suspend fun setVerified(transactionId: String, proofId: String, amountMinor: Long, verified: Boolean) = Result.success(Unit)
        override suspend fun removeUnavailable(transactionId: String, proofId: String) = Result.success(Unit)
        override suspend fun refresh(transactionId: String) = Result.success(Unit)
    }
    private val getAccounts = GetAccountsUseCase(accounts, preferences)

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
        TransactionEditViewModel(handle, GetTransactionUseCase(transactions), AddTransactionUseCase(transactions, accounts),
            UpdateTransactionUseCase(transactions, accounts), GetTransactionTagsUseCase(object : TransactionTagRepository {
                override fun observeTags() = flowOf(emptyList<String>())
            }), getAccounts, com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase(preferences), PreviewConversionUseCase(rates))
    }

    private suspend fun ready(editor: TransactionEditViewModel) = withTimeout(10_000) {
        editor.uiState.first { !it.isLoading && !it.isAccountsLoading }
    }

    private fun save(editor: TransactionEditViewModel) = instrumentation.runOnMainSync {
        editor.save(TransactionType.EXPENSE, "12.50", "Food", "Lunch", 100)
    }

    @Test
    fun accountAndTransactionEmissionOrdersBothPrioritizeTheSavedAccount() = runBlocking {
        val transaction = Transaction(id = "editing", amountMinor = 1250, accountId = "savings")
        for (accountsFirst in listOf(true, false)) {
            instrumentation.runOnMainSync {
                accounts.status.value = Resource.Loading()
                transactions.status.value = Resource.Loading()
            }
            val handle = SavedStateHandle(mapOf("id" to transaction.id))
            val editor = editor(handle)
            instrumentation.runOnMainSync {
                if (accountsFirst) accounts.status.value = Resource.Success(accounts.values.value)
                else transactions.status.value = Resource.Success(transaction)
            }
            withTimeout(10_000) { editor.uiState.first {
                if (accountsFirst) !it.isAccountsLoading else !it.isLoading
            } }
            assertNull(editor.uiState.value.selectedAccountId)
            instrumentation.runOnMainSync {
                accounts.status.value = Resource.Success(accounts.values.value)
                transactions.status.value = Resource.Success(transaction)
            }
            withTimeout(10_000) { editor.uiState.first { it.selectedAccountId == "savings" } }
            assertEquals("savings", handle.get<String>("selected_account_id"))
            assertEquals(true, handle.get<Boolean>("account_selection_initialized"))
        }
    }

    @Test
    fun explicitSelectionDuringLoadingWinsOverLaterInitializationAndRapidEmissions() = runBlocking {
        val transaction = Transaction(id = "editing", amountMinor = 1250, accountId = "savings")
        transactions.status.value = Resource.Loading()
        val handle = SavedStateHandle(mapOf("id" to transaction.id))
        val editor = editor(handle)
        withTimeout(10_000) { editor.uiState.first { !it.isAccountsLoading } }
        instrumentation.runOnMainSync { editor.onAccountSelected("daily") }
        instrumentation.runOnMainSync { transactions.status.value = Resource.Success(transaction) }
        ready(editor)
        assertEquals("daily", editor.uiState.value.selectedAccountId)
        repeat(40) { index ->
            instrumentation.runOnMainSync {
                editor.onAccountSelected(if (index % 2 == 0) "savings" else "daily")
                accounts.values.value = accounts.values.value.map { it.copy(isDefault = it.id == "savings") }
                transactions.status.value = Resource.Success(transaction.copy(note = "emission-$index"))
            }
        }
        withTimeout(10_000) { editor.uiState.first { it.transaction?.note == "emission-39" } }
        assertEquals("daily", editor.uiState.value.selectedAccountId)
        assertEquals("daily", handle.get<String>("selected_account_id"))
        val restored = mutableMapOf<String, Any?>()
        instrumentation.runOnMainSync { handle.keys().forEach { restored[it] = handle.get<Any?>(it) } }
        assertEquals("daily", ready(editor(SavedStateHandle(restored))).selectedAccountId)
    }

    @Test
    fun temporaryAccountFailuresPreserveSelectionButRemovalRequiresAnExplicitReplacement() = runBlocking {
        val handle = SavedStateHandle()
        val editor = editor(handle)
        ready(editor)
        instrumentation.runOnMainSync { editor.onAccountSelected("savings") }
        val available = accounts.values.value
        instrumentation.runOnMainSync { accounts.status.value = Resource.Loading() }
        withTimeout(10_000) { editor.uiState.first { it.isAccountsLoading } }
        assertEquals("savings", editor.uiState.value.selectedAccountId)
        save(editor)
        assertTrue(transactions.rows.value.isEmpty())
        instrumentation.runOnMainSync { accounts.status.value = Resource.Error("Temporary sync failure") }
        val offline = withTimeout(10_000) { editor.uiState.first { !it.isAccountsLoading && it.accountsError != null } }
        assertEquals(available, offline.accounts)
        assertEquals("savings", offline.selectedAccountId)
        assertTrue(offline.canSaveWithAccount)
        instrumentation.runOnMainSync { accounts.status.value = Resource.Success(available.filter { it.id == "daily" }) }
        val removed = withTimeout(10_000) { editor.uiState.first { it.accountError == TransactionMessages.ACCOUNT_UNAVAILABLE } }
        assertEquals("savings", removed.selectedAccountId)
        assertEquals("savings", handle.get<String>("selected_account_id"))
        assertFalse(removed.canSaveWithAccount)
        save(editor)
        assertTrue(transactions.rows.value.isEmpty())
        instrumentation.runOnMainSync { accounts.status.value = Resource.Success(emptyList()) }
        withTimeout(10_000) { editor.uiState.first { it.accounts.isEmpty() } }
        instrumentation.runOnMainSync { accounts.status.value = Resource.Success(available) }
        withTimeout(10_000) { editor.uiState.first { it.canSaveWithAccount } }
        assertEquals("savings", editor.uiState.value.selectedAccountId)
        instrumentation.runOnMainSync { editor.onAccountSelected("daily") }
        assertNull(editor.uiState.value.accountError)
        assertEquals("daily", handle.get<String>("selected_account_id"))
    }

    @Test
    fun createInitializationWaitsForDataAndRestoredIdsAreNotReplacedByDefaults() = runBlocking {
        accounts.status.value = Resource.Error("No cached data yet")
        val editor = editor()
        ready(editor)
        assertNull(editor.uiState.value.selectedAccountId)
        instrumentation.runOnMainSync { accounts.status.value = Resource.Error("Offline", accounts.values.value) }
        withTimeout(10_000) { editor.uiState.first { it.selectedAccountId == "daily" } }
        val handle = SavedStateHandle(mapOf("selected_account_id" to "savings", "account_selection_initialized" to false))
        val restored = editor(handle)
        assertEquals("savings", ready(restored).selectedAccountId)
        assertEquals(true, handle.get<Boolean>("account_selection_initialized"))
        instrumentation.runOnMainSync {
            accounts.status.value = Resource.Success(accounts.values.value.map { it.copy(isDefault = it.id == "daily") })
        }
        instrumentation.waitForIdleSync()
        assertEquals("savings", restored.uiState.value.selectedAccountId)
    }

    @Test
    fun incorrectlyTypedStringRetrievalThrowsButDetailExtractionRemainsSafe() {
        val handle = SavedStateHandle(mapOf("id" to 42))
        try {
            val id: String? = handle.get<String>("id")
            fail("Typed retrieval must not be used for unchecked arguments: $id")
        } catch (_: ClassCastException) { }
        val detail = model(TransactionDetailViewModel::class.java) {
            TransactionDetailViewModel(handle, GetTransactionUseCase(transactions), DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
        }
        assertEquals(TransactionMessages.NOT_FOUND, detail.uiState.value.error)
        assertTrue(transactions.observedIds.isEmpty())
    }

    @Test
    fun invalidDetailIdsExposeAnErrorWithoutStartingQueriesOrDeleting() {
        for (invalid in listOf(null, "", " \t ", 42, true, listOf("id"), "a/b", "a".repeat(129))) {
            val detail = model(TransactionDetailViewModel::class.java) {
                TransactionDetailViewModel(SavedStateHandle(mapOf("id" to invalid)), GetTransactionUseCase(transactions),
                    DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
            }
            assertFalse(detail.uiState.value.isLoading)
            assertEquals(TransactionMessages.NOT_FOUND, detail.uiState.value.error)
            instrumentation.runOnMainSync { detail.showDeleteDialog(); detail.confirmDelete() }
            assertFalse(detail.uiState.value.isDeleteDialogVisible)
            assertFalse(detail.uiState.value.isDeleting)
        }
        assertTrue(transactions.observedIds.isEmpty())
        assertEquals(0, accounts.observations)
        assertTrue(transactions.deletedIds.isEmpty())
    }

    @Test
    fun invalidDetailScreenKeepsTheBackActionAvailable() = runBlocking {
        val detail = model(TransactionDetailViewModel::class.java) {
            TransactionDetailViewModel(SavedStateHandle(), GetTransactionUseCase(transactions),
                DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
        }
        val back = CompletableDeferred<Unit>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ClarityTheme { TransactionDetailScreen(onBack = { back.complete(Unit) }, onEdit = {}, viewModel = detail) }
                }
            }
            val error = TransactionMessages.NOT_FOUND
            awaitNode { it.text?.toString() == error }
            val backLabel = TransactionMessages.BACK_LABEL
            val button = awaitNode {
                it.isClickable && findNode(it) { child ->
                    child.text?.toString() == backLabel || child.contentDescription?.toString() == backLabel
                } != null
            }
            assertTrue(button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            withTimeout(10_000) { back.await() }
        }
    }

    @Test
    fun validDetailStillLoadsAndDeletesTheRequestedTransaction() = runBlocking {
        val transaction = Transaction(id = "valid-id", amountMinor = 1250)
        transactions.rows.value = mapOf(transaction.id to transaction)
        val detail = model(TransactionDetailViewModel::class.java) {
            TransactionDetailViewModel(SavedStateHandle(mapOf("id" to transaction.id)), GetTransactionUseCase(transactions),
                DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
        }
        withTimeout(10_000) { detail.uiState.first { it.transaction == transaction && !it.isLoading } }
        instrumentation.runOnMainSync { detail.showDeleteDialog() }
        assertTrue(detail.uiState.value.isDeleteDialogVisible)
        instrumentation.runOnMainSync { detail.confirmDelete() }
        withTimeout(10_000) { detail.uiState.first { it.isDeleted } }
        assertEquals(listOf(transaction.id), transactions.observedIds)
        assertEquals(listOf(transaction.id), transactions.deletedIds)
        assertTrue(transactions.rows.value.isEmpty())
    }

    @Test
    fun typedAuthenticationFailuresPreserveCachedTransactionsAndUseLocalizedErrors() = runBlocking<Unit> {
        val transaction = Transaction(id = "transaction", amountMinor = 1250, accountId = "daily")
        transactions.rows.value = mapOf(transaction.id to transaction)
        val detail = model(TransactionDetailViewModel::class.java) {
            TransactionDetailViewModel(SavedStateHandle(mapOf("id" to transaction.id)), GetTransactionUseCase(transactions),
                DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
        }
        val list = model(TransactionListViewModel::class.java) {
            TransactionListViewModel(
                GetTransactionsUseCase(transactions),
                getAccounts,
                ObserveCurrencyPreferenceUseCase(preferences),
                ObserveTransactionProofsUseCase(proofs),
                rates
            )
        }
        val editor = editor(SavedStateHandle(mapOf("id" to transaction.id)))
        ready(editor)
        instrumentation.runOnMainSync {
            transactions.failure.value = TransactionException(TransactionError.SIGN_IN_REQUIRED, "Token expired")
            accounts.status.value = Resource.Error("Different diagnostic", accounts.values.value,
                AccountException(AccountError.SIGN_IN_REQUIRED, "Account token expired"))
        }
        val detailState = withTimeout(10_000) { detail.uiState.first { it.error == TransactionMessages.SIGN_IN } }
        val listState = withTimeout(10_000) { list.uiState.first { it.error == TransactionMessages.SIGN_IN } }
        val editState = withTimeout(10_000) { editor.uiState.first {
            it.error == TransactionMessages.SIGN_IN && it.accountsError == TransactionMessages.SIGN_IN
        } }
        assertEquals(transaction, detailState.transaction)
        assertEquals(listOf(transaction), listState.transactions)
        assertEquals(transaction, editState.transaction)
        assertEquals(accounts.values.value, editState.accounts)
        assertFalse(detailState.isLoading)

        instrumentation.runOnMainSync {
            transactions.diagnosticMessage = "Please sign in"
            transactions.failure.value = IllegalStateException("Please sign in")
            accounts.status.value = Resource.Error("Please sign in", accounts.values.value)
        }
        withTimeout(10_000) { detail.uiState.first { it.error == TransactionMessages.LOAD_ERROR } }
        withTimeout(10_000) { list.uiState.first { it.error == TransactionMessages.LOAD_ERROR } }
        withTimeout(10_000) { editor.uiState.first {
            it.error == TransactionMessages.LOAD_ERROR && it.accountsError == TransactionMessages.ACCOUNTS_LOAD_ERROR
        } }
    }

    @Test
    fun temporaryTransactionFailuresKeepCachedRowsAndSignOutClearsThem() = runBlocking {
        val transaction = Transaction(id = "cached", amountMinor = 1250, accountId = "daily")
        transactions.rows.value = mapOf(transaction.id to transaction)
        val detail = model(TransactionDetailViewModel::class.java) {
            TransactionDetailViewModel(SavedStateHandle(mapOf("id" to transaction.id)), GetTransactionUseCase(transactions),
                DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
        }
        val list = model(TransactionListViewModel::class.java) {
            TransactionListViewModel(
                GetTransactionsUseCase(transactions),
                getAccounts,
                ObserveCurrencyPreferenceUseCase(preferences),
                ObserveTransactionProofsUseCase(proofs),
                rates
            )
        }
        withTimeout(10_000) { detail.uiState.first { it.transaction != null } }
        withTimeout(10_000) { list.uiState.first { it.transactions.isNotEmpty() } }
        instrumentation.runOnMainSync {
            transactions.status.value = Resource.Error("Temporary failure")
            transactions.listStatus.value = Resource.Error("Temporary failure")
        }
        assertEquals(transaction, withTimeout(10_000) { detail.uiState.first { it.error != null } }.transaction)
        assertEquals("Daily", detail.uiState.value.accountName)
        assertEquals(listOf(transaction), withTimeout(10_000) { list.uiState.first { it.error != null } }.transactions)
        instrumentation.runOnMainSync {
            val cause = TransactionException(TransactionError.SIGN_IN_REQUIRED)
            transactions.status.value = Resource.Error("Authentication required", cause = cause)
            transactions.listStatus.value = Resource.Error("Authentication required", cause = cause)
        }
        assertNull(withTimeout(10_000) { detail.uiState.first { it.error == TransactionMessages.SIGN_IN } }.transaction)
        assertTrue(withTimeout(10_000) { list.uiState.first { it.error == TransactionMessages.SIGN_IN } }.transactions.isEmpty())
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
                DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
        }
        val list = model(TransactionListViewModel::class.java) {
            TransactionListViewModel(
                GetTransactionsUseCase(transactions),
                getAccounts,
                ObserveCurrencyPreferenceUseCase(preferences),
                ObserveTransactionProofsUseCase(proofs),
                rates
            )
        }
        withTimeout(10_000) { detail.uiState.first { it.accountName == "Savings" } }
        withTimeout(10_000) { list.uiState.first { it.accountNames[saved.accountId] == "Savings" } }
        accounts.values.value = accounts.values.value.map { if (it.id == "savings") it.copy(name = "Travel") else it }
        withTimeout(10_000) { detail.uiState.first { it.accountName == "Travel" } }
        withTimeout(10_000) { list.uiState.first { it.accountNames[saved.accountId] == "Travel" } }
        assertEquals("savings", reopened.uiState.value.selectedAccountId)
    }

    @Test
    fun accountNamesSurviveReloadsWithoutDataAndRefreshWhenAccountDataIsAvailable() = runBlocking {
        val transaction = Transaction(id = "transaction", amountMinor = 1250, accountId = "daily")
        transactions.rows.value = mapOf(transaction.id to transaction)
        val detail = model(TransactionDetailViewModel::class.java) {
            TransactionDetailViewModel(SavedStateHandle(mapOf("id" to transaction.id)), GetTransactionUseCase(transactions),
                DeleteTransactionUseCase(transactions), getAccounts, RetryConversionUseCase(transactions))
        }
        val list = model(TransactionListViewModel::class.java) {
            TransactionListViewModel(
                GetTransactionsUseCase(transactions),
                getAccounts,
                ObserveCurrencyPreferenceUseCase(preferences),
                ObserveTransactionProofsUseCase(proofs),
                rates
            )
        }
        withTimeout(10_000) { detail.uiState.first { it.accountName == "Daily" } }
        withTimeout(10_000) { list.uiState.first { it.accountNames["daily"] == "Daily" } }
        val originalNames = list.uiState.value.accountNames

        suspend fun emitAccounts(result: Resource<List<Account>>, marker: String) {
            instrumentation.runOnMainSync { accounts.status.value = result }
            instrumentation.waitForIdleSync()
            transactions.rows.value = mapOf(transaction.id to transaction.copy(note = marker))
            // Wait for both combined streams to process the new account status.
            withTimeout(10_000) { detail.uiState.first { it.transaction?.note == marker } }
            withTimeout(10_000) { list.uiState.first { it.transactions.singleOrNull()?.note == marker } }
        }

        emitAccounts(Resource.Loading(), "loading")
        assertEquals("Daily", detail.uiState.value.accountName)
        assertEquals(originalNames, list.uiState.value.accountNames)
        emitAccounts(Resource.Error("Reload failed"), "no-data-error")
        assertEquals("Daily", detail.uiState.value.accountName)
        assertEquals(originalNames, list.uiState.value.accountNames)
        val renamed = accounts.values.value.first { it.id == "daily" }.copy(name = "Renamed")
        emitAccounts(Resource.Error("Offline", listOf(renamed)), "cached-error")
        assertEquals("Renamed", detail.uiState.value.accountName)
        assertEquals(mapOf("daily" to "Renamed"), list.uiState.value.accountNames)
        emitAccounts(Resource.Success(emptyList()), "removed")
        assertNull(detail.uiState.value.accountName)
        assertTrue(list.uiState.value.accountNames.isEmpty())
    }

    @Test
    fun noAccountsBlocksSavingAndCreatingTheFirstAccountEnablesTheEntry() = runBlocking {
        accounts.values.value = emptyList()
        val editor = editor()
        assertFalse(ready(editor).canSaveWithAccount)
        save(editor)
        assertEquals(TransactionMessages.ACCOUNT_REQUIRED, editor.uiState.value.accountError)
        assertTrue(transactions.rows.value.isEmpty())
        accounts.values.value = listOf(Account(id = "new", name = "New account", bankName = "Bank", isDefault = true))
        withTimeout(10_000) { editor.uiState.first { it.canSaveWithAccount && it.selectedAccountId == "new" } }
        save(editor)
        withTimeout(10_000) { editor.uiState.first { it.isSuccess } }
        assertEquals("new", transactions.rows.value.values.single().accountId)
    }

    @Test
    fun legacyNullAccountPreselectsTheDefaultOnlyInTheFormUntilSaved() = runBlocking {
        transactions.rows.value = mapOf("legacy" to Transaction(id = "legacy", amountMinor = 1250))
        val editor = editor(SavedStateHandle(mapOf("id" to "legacy")))
        assertEquals("daily", ready(editor).selectedAccountId)
        assertNull(transactions.rows.value["legacy"]!!.accountId)
        save(editor)
        withTimeout(10_000) { editor.uiState.first { it.isSuccess } }
        assertEquals("daily", transactions.rows.value["legacy"]!!.accountId)
    }

    @Test
    fun unavailableSavedAccountRequiresAnExplicitReplacementBeforeSaving() = runBlocking {
        transactions.rows.value = mapOf("legacy" to Transaction(id = "legacy", amountMinor = 1250, accountId = "missing"))
        val editor = editor(SavedStateHandle(mapOf("id" to "legacy")))
        val state = ready(editor)
        assertEquals("missing", state.selectedAccountId)
        assertEquals(TransactionMessages.ACCOUNT_UNAVAILABLE, state.accountError)
        assertFalse(state.canSaveWithAccount)
        save(editor)
        assertEquals("missing", transactions.rows.value["legacy"]!!.accountId)
    }

    @Test
    fun legacyTransactionWithoutADefaultShowsAnAccountErrorOnSave() = runBlocking {
        accounts.values.value = accounts.values.value.map { it.copy(isDefault = false) }
        val legacy = Transaction(id = "legacy", amountMinor = 1250)
        transactions.rows.value = mapOf(legacy.id to legacy)
        val editor = editor(SavedStateHandle(mapOf("id" to legacy.id)))
        assertNull(ready(editor).selectedAccountId)
        save(editor)
        assertEquals(TransactionMessages.ACCOUNT_SELECT, editor.uiState.value.accountError)
        assertEquals(legacy, transactions.rows.value[legacy.id])
    }

    @Test
    fun legacyTransactionSaveButtonRemainsEnabledAndDisplaysTheAccountFieldError() = runBlocking {
        accounts.values.value = emptyList()
        val legacy = Transaction(id = "legacy", amountMinor = 1250)
        transactions.rows.value = mapOf(legacy.id to legacy)
        val editor = editor(SavedStateHandle(mapOf("id" to legacy.id)))
        ready(editor)
        assertNull(editor.uiState.value.accountError)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ClarityTheme { TransactionEditScreen(onBack = {}, viewModel = editor) }
                }
            }
            val saveLabel = TransactionMessages.SAVE_LABEL
            val saveButton = awaitNode { node ->
                node.isClickable && findNode(node) { it.text?.toString() == saveLabel } != null
            }
            assertTrue(saveButton.isEnabled)
            assertTrue(saveButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            withTimeout(10_000) { editor.uiState.first { it.accountError == TransactionMessages.ACCOUNT_REQUIRED } }
            val error = TransactionMessages.ACCOUNT_REQUIRED
            awaitNode(scrollBack = true) { it.text?.toString() == error }
            assertEquals(legacy, transactions.rows.value[legacy.id])
        }
    }

    private fun awaitNode(scrollBack: Boolean = false, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
                findNode(root, predicate)?.let { return it }
                findNode(root) { it.isScrollable }?.performAction(if (scrollBack)
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("Expected transaction form control or account error was unavailable")
    }

    private fun findNode(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { child -> findNode(child, predicate)?.let { return it } }
        }
        return null
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

    @Test
    fun detailScreenReusesEditFormToUpdateAndDeleteExistingTransactions() = runBlocking {
        val existing = Transaction(
            id = "tx-update",
            type = TransactionType.EXPENSE,
            amountMinor = 1500,
            category = "Food",
            note = "Coffee",
            dateEpochMillis = 1_700_000_000_000L,
            accountId = "daily"
        )
        transactions.rows.value = mapOf(existing.id to existing)
        val editVm = editor(SavedStateHandle(mapOf("id" to existing.id))).also {
            instrumentation.runOnMainSync { it.injectDeleteTransaction(DeleteTransactionUseCase(transactions)) }
        }
        val loaded = ready(editVm)
        assertTrue(loaded.isEditMode)
        assertTrue(loaded.canDelete)
        assertEquals(existing, loaded.transaction)
        assertEquals("daily", loaded.selectedAccountId)

        val closed = CompletableDeferred<Unit>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    ClarityTheme {
                        TransactionDetailScreen(onBack = { closed.complete(Unit) }, viewModel = editVm)
                    }
                }
            }
            awaitNode { it.text?.toString() == "Update Transaction" }
            instrumentation.runOnMainSync {
                editVm.onAccountSelected("savings")
                editVm.save(TransactionType.INCOME, "45.00", "Salary", "Updated note", 1_700_100_000_000L)
            }
            withTimeout(10_000) { closed.await() }
        }

        val updated = checkNotNull(transactions.rows.value[existing.id])
        assertEquals(TransactionType.INCOME, updated.type)
        assertEquals(4500L, updated.amountMinor)
        assertEquals("Salary", updated.category)
        assertEquals("Updated note", updated.note)
        assertEquals(1_700_100_000_000L, updated.dateEpochMillis)
        assertEquals("savings", updated.accountId)

        val deleteVm = editor(SavedStateHandle(mapOf("id" to existing.id))).also {
            instrumentation.runOnMainSync { it.injectDeleteTransaction(DeleteTransactionUseCase(transactions)) }
        }
        ready(deleteVm)
        instrumentation.runOnMainSync { deleteVm.showDeleteDialog() }
        assertTrue(deleteVm.uiState.value.isDeleteDialogVisible)
        instrumentation.runOnMainSync { deleteVm.confirmDelete() }
        withTimeout(10_000) { deleteVm.uiState.first { it.isDeleted } }
        assertTrue(transactions.rows.value.isEmpty())
    }

    private class MemoryTransactions : TransactionRepository {
        override suspend fun retryConversion(id: String) = Result.success(Unit)
        val rows = MutableStateFlow<Map<String, Transaction>>(emptyMap())
        val status = MutableStateFlow<Resource<Transaction?>?>(null)
        val listStatus = MutableStateFlow<Resource<List<Transaction>>?>(null)
        val failure = MutableStateFlow<Throwable?>(null)
        var diagnosticMessage = "Diagnostic text may change"
        val observedIds = mutableListOf<String>()
        val deletedIds = mutableListOf<String>()
        override fun observeTransactions(): Flow<Resource<List<Transaction>>> = combine(rows, failure, listStatus) { values, cause, result ->
            result ?: if (cause == null) Resource.Success(values.values.toList())
            else Resource.Error(diagnosticMessage, values.values.toList(), cause)
        }
        override fun observeTransaction(id: String): Flow<Resource<Transaction?>> {
            observedIds.add(id)
            return combine(rows, failure, status) { values, cause, result ->
                result ?: if (cause == null) Resource.Success(values[id]) else Resource.Error(diagnosticMessage, values[id], cause)
            }
        }
        override suspend fun addTransaction(t: Transaction): Result<Unit> {
            rows.value = rows.value + (t.id to t)
            return Result.success(Unit)
        }
        override suspend fun updateTransaction(t: Transaction) = addTransaction(t)
        override suspend fun deleteTransaction(id: String): Result<Unit> {
            deletedIds.add(id)
            rows.value = rows.value - id
            return Result.success(Unit)
        }
    }

    private class MemoryAccounts : AccountRepository {
        var observations = 0
        val status = MutableStateFlow<Resource<List<Account>>?>(null)
        val values = MutableStateFlow(listOf(
            Account(id = "daily", name = "Daily", bankName = "Bank", isDefault = true),
            Account(id = "savings", name = "Savings", bankName = "Bank")
        ))
        override fun observeAccounts(): Flow<Resource<List<Account>>> {
            observations++
            return combine(values, status) { accounts, result -> result ?: Resource.Success(accounts) }
        }
        override suspend fun getAccount(id: String) = Result.success(values.value.firstOrNull { it.id == id })
        override suspend fun getDefaultAccount() = Result.success(values.value.firstOrNull { it.isDefault })
        override suspend fun createAccount(account: Account): Result<Unit> = Result.success(Unit)
        override suspend fun updateAccount(account: Account): Result<Unit> = Result.success(Unit)
        override suspend fun setDefaultAccount(id: String): Result<Unit> = error("Unused")
    }
}
