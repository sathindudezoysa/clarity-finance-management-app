package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashTopUpPolicyTest {

    private val cashWalletLkr = Account.defaultCashWallet(currency = "LKR", userId = "user-1")
    private val goalAccountLkr = Account.goalAccount(goalId = "goal-1", currency = "LKR", userId = "user-1")
    private val bankLkr1 = Account(
        id = "acc-lkr-1",
        userId = "user-1",
        name = "Everyday Checking",
        bankName = "Commercial Bank",
        currency = "LKR",
        isDefault = false
    )
    private val bankLkrDefault = Account(
        id = "acc-lkr-default",
        userId = "user-1",
        name = "Salary Savings",
        bankName = "Sampath Bank",
        currency = "LKR",
        isDefault = true
    )
    private val bankUsdDefault = Account(
        id = "acc-usd-default",
        userId = "user-1",
        name = "USD Account",
        bankName = "HSBC",
        currency = "USD",
        isDefault = true
    )

    @Test
    fun eligibleSourceAccounts_onlyIncludesBankAccountsMatchingDefaultCurrency() {
        val allAccounts = listOf(cashWalletLkr, goalAccountLkr, bankLkr1, bankLkrDefault, bankUsdDefault)

        val lkrEligible = CashTopUpPolicy.eligibleSourceAccounts(allAccounts, defaultCurrency = "LKR")
        assertEquals(listOf(bankLkr1, bankLkrDefault), lkrEligible)

        val usdEligible = CashTopUpPolicy.eligibleSourceAccounts(allAccounts, defaultCurrency = "USD")
        assertEquals(listOf(bankUsdDefault), usdEligible)

        val eurEligible = CashTopUpPolicy.eligibleSourceAccounts(allAccounts, defaultCurrency = "EUR")
        assertTrue(eurEligible.isEmpty())
    }

    @Test
    fun canTopUpFrom_enforcesSameDefaultCurrencyAndNonWalletNonGoalAccount() {
        assertTrue(CashTopUpPolicy.canTopUpFrom(bankLkr1, "LKR"))
        assertTrue(CashTopUpPolicy.canTopUpFrom(bankLkr1, "lkr"))

        assertFalse(CashTopUpPolicy.canTopUpFrom(bankUsdDefault, "LKR"))
        assertFalse(CashTopUpPolicy.canTopUpFrom(cashWalletLkr, "LKR"))
        assertFalse(CashTopUpPolicy.canTopUpFrom(goalAccountLkr, "LKR"))
        assertFalse(CashTopUpPolicy.canTopUpFrom(null, "LKR"))
        assertFalse(CashTopUpPolicy.canTopUpFrom(bankLkr1, ""))
    }

    @Test
    fun resolveSelectedSourceAccountId_dropsMismatchedSelectionWhenDefaultCurrencyChanges() {
        val lkrEligible = listOf(bankLkr1, bankLkrDefault)

        // Previous selection was a USD account -> falls back to default LKR account
        assertEquals(
            bankLkrDefault.id,
            CashTopUpPolicy.resolveSelectedSourceAccountId(
                currentSelectedId = bankUsdDefault.id,
                eligibleAccounts = lkrEligible
            )
        )

        // Keeps valid matching selection
        assertEquals(
            bankLkr1.id,
            CashTopUpPolicy.resolveSelectedSourceAccountId(
                currentSelectedId = bankLkr1.id,
                eligibleAccounts = lkrEligible
            )
        )

        // Returns empty when no matching-currency account exists
        assertEquals(
            "",
            CashTopUpPolicy.resolveSelectedSourceAccountId(
                currentSelectedId = bankLkr1.id,
                eligibleAccounts = emptyList()
            )
        )
    }
}
