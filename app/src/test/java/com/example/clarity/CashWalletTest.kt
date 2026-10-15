package com.example.clarity

import com.example.clarity.data.local.WalletMoneyParts
import com.example.clarity.domain.model.*
import com.example.clarity.domain.util.MoneyUtils
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger

class CashWalletTest {
    private val option = WalletTopUpOption("bank", true, 100000, 500000)

    @Test fun lowBalanceOffersReminderWithoutCreatingMoney() {
        assertTrue(CashWalletRules.needsReminder(BigInteger.valueOf(99999), option))
        assertFalse(CashWalletRules.needsReminder(BigInteger.valueOf(100000), option))
        assertFalse(CashWalletRules.needsReminder(BigInteger.valueOf(100001), option))
    }
    @Test fun disabledOrMissingReminderDoesNotPrompt() {
        assertFalse(CashWalletRules.needsReminder(BigInteger.ZERO, option.copy(enabled = false)))
        assertFalse(CashWalletRules.needsReminder(BigInteger.ZERO, null))
    }

    @Test fun automaticExecutionRequiresExplicitEnabledOptInAndLowBalance() {
        val automatic = option.copy(automaticExecution = true)
        assertFalse(CashWalletRules.needsAutomaticTopUp(BigInteger.ZERO, option))
        assertFalse(CashWalletRules.needsAutomaticTopUp(BigInteger.ZERO, null))
        assertFalse(CashWalletRules.needsAutomaticTopUp(BigInteger.ZERO, automatic.copy(enabled = false)))
        assertTrue(CashWalletRules.needsAutomaticTopUp(BigInteger.valueOf(99999), automatic))
        assertTrue(CashWalletRules.needsAutomaticTopUp(BigInteger.valueOf(-1), automatic))
        assertFalse(CashWalletRules.needsAutomaticTopUp(BigInteger.valueOf(100000), automatic))
        assertFalse(CashWalletRules.needsAutomaticTopUp(BigInteger.valueOf(100001), automatic))
        assertFalse(CashWalletRules.needsReminder(BigInteger.ZERO, automatic))
    }

    @Test fun automaticTransferPreservesCombinedFundsAfterExpense() {
        val automatic = WalletTopUpOption("bank", true, 500, 1000, true)
        val cashAfterExpense = BigInteger.valueOf(300)
        val bank = BigInteger.valueOf(10000)
        val amount = CashWalletRules.automaticTopUpAmount(cashAfterExpense, automatic, bank, true)!!
        assertEquals(1000L, amount)
        assertEquals(BigInteger.valueOf(10300), (cashAfterExpense + BigInteger.valueOf(amount)) + (bank - BigInteger.valueOf(amount)))
    }

    @Test fun automaticTransferCanCoverExpenseButCannotLeaveCashNegative() {
        val automatic = WalletTopUpOption("bank", true, 500, 1000, true)
        assertEquals(1000L, CashWalletRules.automaticTopUpAmount(BigInteger.valueOf(-500), automatic, BigInteger.valueOf(10000), true))
        assertTrue(runCatching { CashWalletRules.automaticTopUpAmount(BigInteger.valueOf(-1500), automatic, BigInteger.valueOf(10000), true) }
            .exceptionOrNull() is WalletValidationException)
    }

    @Test fun automaticTransferRejectsInsufficientInactiveAndMissingBank() {
        val automatic = WalletTopUpOption("bank", true, 500, 1000, true)
        listOf(BigInteger.valueOf(999) to true, BigInteger.valueOf(10000) to false, null to false).forEach { (balance, active) ->
            assertTrue(runCatching { CashWalletRules.automaticTopUpAmount(BigInteger.ZERO, automatic, balance, active) }
                .exceptionOrNull() is WalletValidationException)
        }
    }

    @Test fun automaticTransferRejectsOverflowAndNonPositiveAmounts() {
        val maximum = BigInteger.valueOf(Long.MAX_VALUE)
        val automatic = WalletTopUpOption("bank", true, Long.MAX_VALUE, 5, true)
        assertTrue(runCatching { CashWalletRules.automaticTopUpAmount(maximum - BigInteger.ONE, automatic, maximum, true) }
            .exceptionOrNull() is WalletValidationException)
        listOf(0L, -1L).forEach { amount ->
            assertTrue(runCatching { CashWalletRules.automaticTopUpAmount(BigInteger.ZERO, automatic.copy(amountMinor = amount), maximum, true) }
                .exceptionOrNull() is WalletValidationException)
        }
    }

    @Test fun reminderModeAndRecoveredBalanceNeverPlanAnAutomaticWithdrawal() {
        assertNull(CashWalletRules.automaticTopUpAmount(BigInteger.ZERO, option, null, false))
        val automatic = option.copy(automaticExecution = true)
        assertNull(CashWalletRules.automaticTopUpAmount(BigInteger.valueOf(100000), automatic, null, false))
    }
    @Test fun exactSplitSumsCanExceedLongWithoutRounding() {
        val expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2))
        assertEquals(expected, WalletMoneyParts(18446744072L, 1709551614L).value())
        assertEquals(BigInteger.valueOf(-1500), WalletMoneyParts(-1, 999998500).value())
    }
    @Test fun catalogHasSeventeenIndependentStableIds() {
        assertEquals(17, BankCatalog.banks.size)
        assertEquals(17, BankCatalog.banks.map { it.id }.toSet().size)
        assertNotEquals(BankCatalog.find("hsbc")!!.id, BankCatalog.find("ntb")!!.id)
        assertNull(BankCatalog.find("untrusted-bank-id"))
    }
    @Test fun searchSupportsNamesAbbreviationsAndCase() {
        assertEquals("hnb", BankCatalog.search("hNb").single().id)
        assertEquals("boc", BankCatalog.search("BOC").single().id)
        assertEquals("commercial", BankCatalog.search("Commercial").single().id)
        assertEquals("nsb", BankCatalog.search("national savings").single().id)
        assertEquals("hsbc", BankCatalog.search("Hongkong").single().id)
        assertTrue(BankCatalog.search("no such institution").isEmpty())
        assertEquals(17, BankCatalog.search(" ").size)
    }
    @Test fun allMoneyInputStaysExactAndRejectsInvalidValues() {
        assertEquals(500001L, MoneyUtils.parseMinor("5000.01").getOrThrow())
        listOf("", "1.001", "NaN", "1e9", "92233720368547758.08").forEach { assertTrue(MoneyUtils.parseMinor(it).isFailure) }
    }
    @Test fun reminderSettingsDoNotAlterBalancesOrSuggestAnAutomaticTransfer() {
        val wallet = CashWallet(balanceMinor = BigInteger.valueOf(700), topUpOption = option)
        assertTrue(CashWalletRules.needsReminder(wallet.balanceMinor, option))
        assertEquals(BigInteger.valueOf(700), wallet.balanceMinor)
        assertTrue(wallet.topUpHistory.isEmpty())
    }
}
