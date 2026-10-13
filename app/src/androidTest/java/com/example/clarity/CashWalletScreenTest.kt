package com.example.clarity

import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.CashWalletRepository
import com.example.clarity.domain.usecase.wallet.*
import com.example.clarity.presentation.theme.ClarityTheme
import com.example.clarity.presentation.wallet.CashWalletScreen
import com.example.clarity.presentation.wallet.CashWalletViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger

@RunWith(AndroidJUnit4::class)
class CashWalletScreenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun defaultCashExpenseNeedsOnlyAmountAndSaveWithoutOptionalFields() {
        val repo = ScreenRepository()
        val store = ViewModelStore()
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = CashWalletViewModel(ObserveCashWalletUseCase(repo), RecordCashTransactionUseCase(repo),
                    TopUpCashWalletUseCase(repo), AddWalletBankAccountUseCase(repo), ConfigureWalletTopUpUseCase(repo))
                store.put("wallet", viewModel)
                activity.setContent { ClarityTheme { CashWalletScreen({}, {}, viewModel) } }
            }
            awaitNode { it.text?.toString() == "10.00" }
            val amount = awaitNode { it.isEditable }
            val input = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "2.50") }
            assertTrue(amount.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, input))
            assertTrue(awaitNode { it.isClickable && !it.isEditable && hasText(it, "Save") }
                .performAction(AccessibilityNodeInfo.ACTION_CLICK))
            awaitNode { it.text?.toString() == "7.50" }
            assertEquals(250L, repo.recordedAmount)
            assertEquals(TransactionType.EXPENSE, repo.recordedType)
            assertEquals("", repo.recordedNote)
            assertEquals("", repo.recordedCategory)
            scenario.onActivity { store.clear() }
        }
    }

    private fun awaitNode(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            instrumentation.waitForIdleSync()
            if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
            instrumentation.uiAutomation.rootInActiveWindow?.let { root -> find(root, predicate)?.let { return it } }
            SystemClock.sleep(50)
        }
        throw AssertionError("Wallet control was not available")
    }
    private fun find(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (index in 0 until node.childCount) node.getChild(index)?.let { find(it, predicate)?.let { found -> return found } }
        return null
    }
    private fun hasText(node: AccessibilityNodeInfo, value: String) = find(node) { it.text?.toString() == value } != null

    private class ScreenRepository : CashWalletRepository {
        private val wallet = MutableStateFlow(CashWallet(balanceMinor = BigInteger.valueOf(1000)))
        var recordedAmount: Long? = null
        var recordedType: TransactionType? = null
        var recordedCategory: String? = null
        var recordedNote: String? = null
        override fun observeWallet() = wallet
        override suspend fun recordCash(amountMinor: Long, type: TransactionType, category: String, note: String): Result<Unit> {
            recordedAmount = amountMinor; recordedType = type; recordedCategory = category; recordedNote = note
            wallet.value = wallet.value.copy(balanceMinor = wallet.value.balanceMinor - BigInteger.valueOf(amountMinor))
            return Result.success(Unit)
        }
        override suspend fun topUp(bankAccountId: String, amountMinor: Long): Result<Unit> = error("Unused")
        override suspend fun addBankAccount(name: String, openingBalanceMinor: Long): Result<Unit> = error("Unused")
        override suspend fun configureTopUp(option: WalletTopUpOption): Result<Unit> = error("Unused")
    }
}
