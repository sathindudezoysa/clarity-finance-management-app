package com.example.clarity

import android.content.Context
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
import com.example.clarity.domain.repository.CurrencyPreference
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.usecase.account.CreateAccountUseCase
import com.example.clarity.domain.usecase.account.GetAccountUseCase
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.account.SetDefaultAccountUseCase
import com.example.clarity.domain.usecase.account.UpdateAccountUseCase
import com.example.clarity.domain.usecase.auth.ObserveCurrentUserUseCase
import com.example.clarity.domain.usecase.auth.SignOutUseCase
import com.example.clarity.domain.usecase.auth.UpdateUserProfileUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.currency.SetCurrencyPreferenceUseCase
import com.example.clarity.presentation.accounts.edit.AccountEditState
import com.example.clarity.presentation.accounts.edit.AccountEditViewModel
import com.example.clarity.presentation.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDefaultAccountViewModelTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val databaseName = "settings-default-account-viewmodel-test"
    private val prefsName = "settings-default-account-prefs"
    private val store = ViewModelStore()
    private lateinit var database: ClarityDatabase
    private lateinit var repository: AccountRepositoryImpl
    private var nextKey = 0
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
        openDatabase()
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { store.clear() }
        runBlocking { repository.stopSync().join() }
        applicationScope.cancel()
        database.close()
        context.deleteDatabase(databaseName)
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
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
            result = ViewModelProvider(store, factory).get("settings-${nextKey++}", type)
        }
        return checkNotNull(result)
    }

    private fun editor(handle: SavedStateHandle = SavedStateHandle()) = model(AccountEditViewModel::class.java) {
        AccountEditViewModel(handle, GetAccountUseCase(repository), CreateAccountUseCase(repository), UpdateAccountUseCase(repository))
    }

    private fun settings() = model(SettingsViewModel::class.java) {
        val auth = SignedInAuth()
        val currencyPrefs = FakeCurrencyPreferences()
        SettingsViewModel(
            prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE),
            observeCurrencyPreference = ObserveCurrencyPreferenceUseCase(currencyPrefs),
            setCurrencyPreference = SetCurrencyPreferenceUseCase(currencyPrefs),
            observeCurrentUser = ObserveCurrentUserUseCase(auth),
            updateUserProfile = UpdateUserProfileUseCase(auth),
            getAccounts = GetAccountsUseCase(repository, currencyPrefs),
            setDefaultAccountUseCase = SetDefaultAccountUseCase(repository),
            signOutUseCase = SignOutUseCase(auth)
        )
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
    fun setDefaultAccountInSettingsSwitchesDefaultBankAccount() = runBlocking {
        create("Daily")
        create("Savings")
        val settings = settings()
        val initialState = withTimeout(10_000) { settings.uiState.first { it.bankAccounts.size == 2 } }
        assertEquals("Daily", initialState.bankAccounts.single { it.isDefault }.name)
        assertEquals("Daily (Bank)", initialState.defaultAccountName)

        val savings = initialState.bankAccounts.single { it.name == "Savings" }
        instrumentation.runOnMainSync { settings.setDefaultAccount(savings.id) }

        val updatedState = withTimeout(10_000) {
            settings.uiState.first { state ->
                state.changingDefaultId == null && state.bankAccounts.singleOrNull { it.isDefault }?.id == savings.id
            }
        }
        assertEquals("Savings", updatedState.bankAccounts.single { it.isDefault }.name)
        assertEquals("Savings (Bank)", updatedState.defaultAccountName)
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

    private class FakeCurrencyPreferences : CurrencyPreferenceRepository {
        override fun observe(): Flow<CurrencyPreference> = flowOf(CurrencyPreference())
        override suspend fun set(currency: String): Result<Unit> = Result.success(Unit)
        override suspend fun current(): String = "LKR"
    }
}

