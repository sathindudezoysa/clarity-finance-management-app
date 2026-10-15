package com.example.clarity

import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.CashWalletRepository
import com.example.clarity.domain.usecase.wallet.*
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.wallet.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicReference

/** Exercise actual accessibility controls without connecting to a bank or a Firebase account. */
@RunWith(AndroidJUnit4::class)
class WalletInteractionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun lightBankGridSearchSelectsStableIdentityOnSmallScreen() = bankGrid(false)
    @Test fun darkBankGridSearchSelectsStableIdentityWithLargeText() = bankGrid(true)

    private fun bankGrid(dark: Boolean) {
        val selected = AtomicReference<String>()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent {
                ClarityTheme(darkTheme = dark, dynamicColor = false) {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density, if (dark) 1.5f else 1f)) {
                        Box(Modifier.size(320.dp, 600.dp)) { BankSelectionScreen({}, selected::set) }
                    }
                }
            } }
            await { it.text?.toString() == "Select Bank" }
            setText(await { it.isEditable }, "no-such-bank")
            await { it.text?.toString()?.startsWith("No matching banks.") == true }
            setText(await { it.isEditable }, "hNb")
            val card = await { it.isClickable && hasText(it, "Hatton National Bank") }
            assertTrue(card.isEnabled)
            capture(if (dark) "bank-grid-dark-large-text" else "bank-grid-light-small-screen")
            assertTrue(card.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            assertEquals("hnb", selected.get())
        }
    }

    @Test fun reminderRequiresConfirmationAndRetryAfterRecreationUsesSameOperation() {
        val repo = InteractionRepository()
        val store = ViewModelStore()
        var saved = SavedStateHandle()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            fun showWallet() = scenario.onActivity { activity ->
                val vm = walletViewModel(repo, saved)
                store.put("wallet", vm)
                activity.setContent { ClarityTheme { CashWalletScreen({}, {}, vm) } }
            }
            showWallet()
            click("Review Top-up")
            await { it.text?.toString() == "Review Transfer" }
            assertEquals(0, repo.transferAttempts)
            capture("transfer-review")
            // Simulate a committed transfer whose success acknowledgement was lost.
            click("Confirm Top-up")
            await { it.text?.toString() == "We couldn't save your changes. Please try again." }
            assertEquals(1, repo.operations.size)
            scenario.onActivity {
                val snapshot = saved.keys().associateWith { key -> saved.get<Any?>(key) }
                saved = SavedStateHandle(snapshot)
                store.clear()
            }
            showWallet()
            click("Confirm Top-up")
            await { it.text?.toString() == "Top-up recorded" }
            assertEquals(2, repo.transferAttempts)
            assertEquals(1, repo.operations.size)
            assertEquals(BigInteger.valueOf(1100), repo.wallet.value.balanceMinor)
            assertEquals(BigInteger.valueOf(9000), repo.wallet.value.accounts.single().balanceMinor)
            scenario.onActivity { store.clear() }
        }
    }

    @Test fun accountFormRetainsSelectedBankAndRecoversFromValidationFailure() {
        val repo = InteractionRepository()
        val store = ViewModelStore()
        val navigatedBack = AtomicReference(false)
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var vm: BankAccountViewModel
            scenario.onActivity { activity ->
                vm = BankAccountViewModel(ObserveCashWalletUseCase(repo), SaveWalletBankAccountUseCase(repo),
                    ArchiveWalletBankAccountUseCase(repo), GetWalletBankActivityUseCase(repo), SavedStateHandle(mapOf("bankId" to "sampath")))
                store.put("account", vm)
                activity.setContent { ClarityTheme { BankAccountScreen({}, { navigatedBack.set(true) }, {}, vm) } }
            }
            await { it.text?.toString() == "Sampath Bank" }
            // Drive validation on the main thread; the resulting feedback must be accessible.
            scenario.onActivity { vm.save() }
            awaitScrolled { it.text?.toString() == "Enter an account nickname." }
            assertFalse(navigatedBack.get())
            scenario.onActivity { vm.nickname("Daily Savings"); vm.lastFour("1234"); vm.opening("500.00"); vm.save() }
            instrumentation.waitForIdleSync()
            val deadline = SystemClock.uptimeMillis() + 5000
            while (!navigatedBack.get() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
            assertTrue(navigatedBack.get())
            assertEquals("sampath", repo.savedAccount!!.bankId)
            assertEquals(50000L, repo.savedAccount!!.openingBalanceMinor)
            scenario.onActivity { store.clear() }
        }
    }

    private fun walletViewModel(repo: CashWalletRepository, saved: SavedStateHandle) = CashWalletViewModel(
        ObserveCashWalletUseCase(repo), RecordCashTransactionUseCase(repo), TopUpCashWalletUseCase(repo),
        ConfigureWalletTopUpUseCase(repo), AdjustCashBalanceUseCase(repo), saved)

    private fun click(text: String) = assertTrue(awaitScrolled { it.isClickable && !it.isEditable && hasText(it, text) }
        .performAction(AccessibilityNodeInfo.ACTION_CLICK))
    private fun setText(node: AccessibilityNodeInfo, value: String) = assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
        Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }))
    private fun awaitScrolled(predicate: (AccessibilityNodeInfo) -> Boolean) = await(scroll = true, predicate = predicate)
    private fun await(scroll: Boolean = false, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
                find(root, predicate)?.let { return it }
                if (scroll) find(root) { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            SystemClock.sleep(100)
        }
        throw AssertionError("Expected wallet control was not accessible")
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }
    private fun hasText(node: AccessibilityNodeInfo, value: String) = find(node) { it.text?.toString() == value } != null
    private fun capture(name: String) {
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "wallet-validation").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private class InteractionRepository : CashWalletRepository {
        val wallet = MutableStateFlow(CashWallet(BigInteger.valueOf(100), listOf(
            WalletBankAccount("bank", "Personal", BigInteger.valueOf(10000), bankId = "hnb", isDefault = true)),
            topUpOption = WalletTopUpOption("bank", true, 500, 1000), ownerId = "a"))
        val operations = mutableSetOf<String>()
        var transferAttempts = 0
        var savedAccount: WalletBankAccountInput? = null
        override fun observeWallet() = wallet
        override suspend fun topUp(bankAccountId: String, amountMinor: Long, operationId: String): Result<Unit> {
            transferAttempts++
            if (operations.add(operationId)) {
                val previous = wallet.value
                wallet.value = previous.copy(balanceMinor = previous.balanceMinor + BigInteger.valueOf(amountMinor),
                    accounts = previous.accounts.map { it.copy(balanceMinor = it.balanceMinor - BigInteger.valueOf(amountMinor)) })
                return Result.failure(IllegalStateException("Lost acknowledgement"))
            }
            return Result.success(Unit)
        }
        override suspend fun saveBankAccount(input: WalletBankAccountInput): Result<Unit> {
            if (input.nickname.isBlank()) return Result.failure(WalletValidationException("Enter an account nickname."))
            savedAccount = input
            return Result.success(Unit)
        }
        override suspend fun bankActivity(id: String) = Result.success(emptyList<WalletActivity>())
        override suspend fun archiveBankAccount(id: String) = Result.success(Unit)
        override suspend fun addBankAccount(name: String, openingBalanceMinor: Long): Result<Unit> = error("Unused")
        override suspend fun recordCash(amountMinor: Long, type: TransactionType, category: String, note: String): Result<Unit> = error("Unused")
        override suspend fun configureTopUp(option: WalletTopUpOption): Result<Unit> = error("Unused")
        override suspend fun adjustCashBalance(targetMinor: Long, note: String, operationId: String): Result<Unit> = error("Unused")
    }
}
