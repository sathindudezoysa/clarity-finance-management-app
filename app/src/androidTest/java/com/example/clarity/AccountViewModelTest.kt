package com.example.clarity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.data.local.ClarityDatabase
import com.example.clarity.data.remote.AccountRemoteDataSource
import com.example.clarity.data.remote.AccountSnapshot
import com.example.clarity.data.repository.AccountRepositoryImpl
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.User
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.usecase.account.CreateAccountUseCase
import com.example.clarity.domain.usecase.account.GetAccountUseCase
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.account.SetDefaultAccountUseCase
import com.example.clarity.domain.usecase.account.UpdateAccountUseCase
import com.example.clarity.presentation.accounts.edit.AccountEditState
import com.example.clarity.presentation.accounts.edit.AccountEditViewModel
import com.example.clarity.presentation.accounts.list.AccountListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val databaseName = "account-viewmodel-test"
    private val store = ViewModelStore()
    private lateinit var database: ClarityDatabase
    private lateinit var repository: AccountRepositoryImpl
    private var nextKey = 0
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        openDatabase()
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { store.clear() }
        runBlocking { repository.stopSync().join() }
        applicationScope.cancel()
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun openDatabase() {
        database = Room.databaseBuilder(context, ClarityDatabase::class.java, databaseName).build()
        repository = AccountRepositoryImpl(database, OfflineRemote(), SignedInAuth(), Dispatchers.IO, applicationScope)
    }

    private fun <T : ViewModel> model(type: Class<T>, create: () -> T): T {
        var result: T? = null
        instrumentation.runOnMainSync {
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <V : ViewModel> create(modelClass: Class<V>): V = create() as V
            }
            result = ViewModelProvider(store, factory).get("account-${nextKey++}", type)
        }
        return checkNotNull(result)
    }

    private fun editor(handle: SavedStateHandle = SavedStateHandle()) = model(AccountEditViewModel::class.java) {
        AccountEditViewModel(handle, GetAccountUseCase(repository), CreateAccountUseCase(repository), UpdateAccountUseCase(repository))
    }

    private fun list() = model(AccountListViewModel::class.java) {
        AccountListViewModel(GetAccountsUseCase(repository), SetDefaultAccountUseCase(repository), SignedInAuth())
    }

    private suspend fun ready(editor: AccountEditViewModel): AccountEditState =
        withTimeout(10_000) { editor.uiState.first { !it.isLoading } }

    private suspend fun create(name: String) {
        val editor = editor()
        ready(editor)
        instrumentation.runOnMainSync {
            editor.onNameChange(name)
            editor.onBankNameChange("Bank")
            editor.save()
        }
        withTimeout(10_000) { editor.uiState.first { it.isSuccess } }
    }

    @Test
    fun addEditAndDefaultSwitchUpdateTheListAndReloadFromRoom() = runBlocking {
        val list = list()
        create("Daily")
        create("Savings")
        val accounts = withTimeout(10_000) { list.uiState.first { it.accounts.size == 2 } }.accounts
        assertEquals("Daily", accounts.single { it.isDefault }.name)
        val savings = accounts.single { it.name == "Savings" }
        val editor = editor(SavedStateHandle(mapOf("id" to savings.id)))
        assertEquals("Savings", ready(editor).name)
        instrumentation.runOnMainSync { editor.onNameChange("Travel"); editor.save() }
        withTimeout(10_000) { list.uiState.first { it.accounts.any { account -> account.name == "Travel" } } }
        instrumentation.runOnMainSync { list.makeDefault(savings.id) }
        withTimeout(10_000) { list.uiState.first { it.accounts.single { account -> account.isDefault }.id == savings.id } }
        instrumentation.runOnMainSync { store.clear() }
        repository.stopSync().join()
        database.close()
        openDatabase()
        val restored = withTimeout(10_000) { list().uiState.first { it.accounts.size == 2 } }.accounts
        assertEquals("Travel", restored.single { it.isDefault }.name)
    }

    @Test
    fun repositoryValidationErrorsAndDraftsSurviveViewModelRecreation() = runBlocking {
        create("Daily")
        val handle = SavedStateHandle()
        val editor = editor(handle)
        val draftId = handle.get<String>("account_edit_draft_id")
        assertNotNull(draftId)
        ready(editor)
        instrumentation.runOnMainSync { editor.save() }
        withTimeout(10_000) { editor.uiState.first { it.nameError == R.string.accounts_name_blank } }
        instrumentation.runOnMainSync { editor.onNameChange(" daily "); editor.save() }
        withTimeout(10_000) { editor.uiState.first { it.nameError == R.string.accounts_name_duplicate } }
        instrumentation.runOnMainSync {
            editor.onNameChange("Travel")
            editor.onBankNameChange("New bank")
            editor.onTypeChange("Savings")
            editor.onCurrencyChange("usd")
            editor.onDefaultChange(true)
        }
        val restoredValues = mutableMapOf<String, Any?>()
        instrumentation.runOnMainSync { handle.keys().forEach { restoredValues[it] = handle.get<Any?>(it) } }
        assertEquals(setOf("account_edit_name", "account_edit_bank_name", "account_edit_account_type",
            "account_edit_currency", "account_edit_is_default", "account_edit_form_loaded",
            "account_edit_saved", "account_edit_draft_id"), restoredValues.keys)
        val restored = editor(SavedStateHandle(restoredValues))
        assertEquals(draftId, restoredValues["account_edit_draft_id"])
        val state = ready(restored)
        assertEquals("Travel", state.name)
        assertEquals("New bank", state.bankName)
        assertEquals("Savings", state.accountType)
        assertEquals("usd", state.currency)
        assertTrue(state.isDefault)
        instrumentation.runOnMainSync { restored.save() }
        withTimeout(10_000) { restored.uiState.first { it.isSuccess } }
        assertEquals("USD", repository.getDefaultAccount().getOrThrow()!!.currency)
        assertEquals(draftId, repository.getDefaultAccount().getOrThrow()!!.id)
        val committed = editor(SavedStateHandle(restoredValues))
        assertTrue(ready(committed).isSuccess)
        assertEquals(2, database.accountDao.getAllForUser("account-ui-user").size)
    }

    @Test
    fun restoredDraftIdIsReusedForSaveRetriesAndACommitBeforeProcessDeath() = runBlocking {
        create("Daily")
        val draftId = "restored-account-draft"
        val handle = SavedStateHandle(mapOf("account_edit_draft_id" to draftId))
        val original = editor(handle)
        ready(original)
        instrumentation.runOnMainSync { original.onNameChange("daily"); original.save() }
        withTimeout(10_000) { original.uiState.first { it.nameError == R.string.accounts_name_duplicate } }
        assertNull(database.accountDao.getById(draftId))

        val values = mutableMapOf<String, Any?>()
        instrumentation.runOnMainSync { handle.keys().forEach { values[it] = handle.get<Any?>(it) } }
        val restoredHandle = SavedStateHandle(values)
        val restored = editor(restoredHandle)
        ready(restored)
        assertEquals(draftId, restoredHandle.get<String>("account_edit_draft_id"))
        instrumentation.runOnMainSync { restored.onNameChange("Travel"); restored.save() }
        withTimeout(10_000) { restored.uiState.first { it.isSuccess } }
        assertEquals("Travel", database.accountDao.getById(draftId)!!.name)

        // Restore the saved state captured before the commit: Room must identify the same draft.
        val afterProcessDeath = editor(SavedStateHandle(values))
        assertTrue(ready(afterProcessDeath).isSuccess)
        instrumentation.runOnMainSync { afterProcessDeath.save() }
        assertEquals(2, database.accountDao.getAllForUser("account-ui-user").size)
    }

    private class OfflineRemote : AccountRemoteDataSource {
        override fun observeAccounts(userId: String): Flow<AccountSnapshot> = emptyFlow()
        override suspend fun writeAccounts(userId: String, accounts: List<Account>) { error("Offline test") }
    }

    private class SignedInAuth : AuthRepository {
        override val currentUser = flowOf(User("account-ui-user", null, null))
        override suspend fun signInWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signUpWithEmailPassword(email: String, password: String): Result<User> = error("Unused")
        override suspend fun signInWithGoogle(idToken: String): Result<User> = error("Unused")
        override suspend fun sendPasswordResetEmail(email: String): Result<Unit> = error("Unused")
        override suspend fun signOut() = Unit
    }
}
