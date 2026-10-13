package com.example.clarity

import com.example.clarity.data.local.entity.*
import com.example.clarity.data.mapper.walletSnapshot
import com.example.clarity.domain.model.CashWalletRules
import com.example.clarity.domain.model.WalletTopUpOption
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class CashWalletTest {
    private val enabled = WalletTopUpOption("bank", true, 500, 2000)

    @Test fun spendingBelowThresholdTriggersOneTopUp() {
        assertEquals(2000L, CashWalletRules.topUpNeeded(BigInteger.valueOf(1000), 600, enabled))
    }
    @Test fun thresholdEqualityDoesNotTriggerWithdrawal() {
        assertEquals(0L, CashWalletRules.topUpNeeded(BigInteger.valueOf(1000), 500, enabled))
    }
    @Test fun disabledOptionNeverTriggersWithdrawal() {
        assertEquals(0L, CashWalletRules.topUpNeeded(BigInteger.ZERO, 100, enabled.copy(enabled = false)))
        assertEquals(0L, CashWalletRules.topUpNeeded(BigInteger.ZERO, 100, null))
    }
    @Test fun automaticWithdrawalCanCoverExpenseFromEmptyWallet() {
        assertEquals(2000L, CashWalletRules.topUpNeeded(BigInteger.ZERO, 1200, enabled))
    }
    @Test fun insufficientAutomaticAmountRejectsPurchase() {
        assertTrue(runCatching { CashWalletRules.topUpNeeded(BigInteger.ZERO, 2100, enabled) }.isFailure)
    }
    @Test fun invalidMoneyIsRejected() {
        assertTrue(runCatching { CashWalletRules.topUpNeeded(BigInteger.ZERO, 0, enabled) }.isFailure)
        assertTrue(runCatching { CashWalletRules.topUpNeeded(BigInteger.ZERO, 100, enabled.copy(amountMinor = 0)) }.isFailure)
    }
    @Test fun bankWithdrawalAndCashActivityProduceConsistentBalances() {
        val accounts = listOf(WalletBankAccountEntity("u", "bank", "Daily", 10000))
        val transfers = listOf(WalletTopUpEntity("u", "transfer", "bank", 2000, 1, false))
        val expense = TransactionEntity("cash", "u", "EXPENSE", 600, "Food", "Lunch", 2, 2, 2)
        val income = expense.copy(id = "income", type = "INCOME", amountMinor = 100)
        val wallet = walletSnapshot(accounts, transfers, listOf(expense, income), null)
        assertEquals(BigInteger.valueOf(1500), wallet.balanceMinor)
        assertEquals(BigInteger.valueOf(8000), wallet.accounts.single().balanceMinor)
        assertEquals(3, wallet.activity.size)
        assertEquals("cash", wallet.activity.first().transactionId)
        assertNull(wallet.activity.last().transactionId)
        // Recalculating after a linked expense edit or deletion keeps wallet totals current.
        assertEquals(BigInteger.valueOf(1800), walletSnapshot(accounts, transfers, listOf(expense.copy(amountMinor = 200)), null).balanceMinor)
        assertEquals(BigInteger.valueOf(2000), walletSnapshot(accounts, transfers, emptyList(), null).balanceMinor)
    }
    @Test fun accumulatedBalancesDoNotOverflowLong() {
        val cash = (1..2).map { TransactionEntity("$it", "u", "INCOME", Long.MAX_VALUE, "General", "", 1, 1, 1) }
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO), walletSnapshot(emptyList(), emptyList(), cash, null).balanceMinor)
    }
}
