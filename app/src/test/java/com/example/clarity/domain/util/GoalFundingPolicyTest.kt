package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class GoalFundingPolicyTest {

    private val userId = "user-123"
    private val cashWallet = Account.defaultCashWallet(
        currency = "LKR",
        isDefault = true,
        userId = userId
    )
    private val bankAccount = Account(
        id = "bank-1",
        userId = userId,
        name = "Main Checking",
        bankName = "Commercial Bank",
        accountType = "Savings",
        currency = "LKR",
        isDefault = false
    )

    @Test
    fun `deterministic goalAccountId and isGoalAccount identify hidden goal accounts`() {
        val goalId = "goal-abc-999"
        val expectedId = "goal_account_goal-abc-999"
        assertEquals(expectedId, Account.goalAccountId(goalId))

        val hidden = Account.goalAccount(
            goalId = goalId,
            currency = "LKR",
            userId = userId,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        assertEquals(expectedId, hidden.id)
        assertEquals("Goal Account $goalId", hidden.name)
        assertEquals("Clarity Goals", hidden.bankName)
        assertEquals("GOAL", hidden.accountType)
        assertEquals("LKR", hidden.currency)
        assertFalse(hidden.isDefault)
        assertTrue(hidden.isGoalAccount)
        assertFalse(hidden.isCashWallet)

        assertFalse(cashWallet.isGoalAccount)
        assertFalse(bankAccount.isGoalAccount)
    }

    @Test
    fun `filterVisibleAccounts removes hidden goal accounts while preserving cash and bank accounts`() {
        val goalAcc1 = Account.goalAccount(goalId = "g1", currency = "LKR", userId = userId)
        val goalAcc2 = Account.goalAccount(goalId = "g2", currency = "USD", userId = userId)
        val all = listOf(cashWallet, goalAcc1, bankAccount, goalAcc2)

        val visible = GoalFundingPolicy.filterVisibleAccounts(all)
        assertEquals(listOf(cashWallet, bankAccount), visible)
        assertTrue(visible.none { it.isGoalAccount })
    }

    @Test
    fun `cash limited when cash balance is less than net worth`() {
        val transactions = listOf(
            Transaction(
                id = "t1",
                userId = userId,
                type = TransactionType.INCOME,
                amountMinor = 50_000L,
                category = "Salary",
                accountId = bankAccount.id,
                currency = "LKR",
                targetCurrency = "LKR"
            ),
            Transaction(
                id = "t2",
                userId = userId,
                type = TransactionType.INCOME,
                amountMinor = 15_000L,
                category = "Cash Income",
                accountId = cashWallet.id,
                currency = "LKR",
                targetCurrency = "LKR"
            )
        )

        val availability = GoalFundingPolicy.evaluate(
            transactions = transactions,
            accounts = listOf(cashWallet, bankAccount),
            currency = "LKR"
        )

        assertEquals(BigInteger.valueOf(15_000L), availability.cashBalanceMinor)
        assertEquals(BigInteger.valueOf(65_000L), availability.netWorthMinor)
        assertEquals(BigInteger.valueOf(15_000L), availability.availableToContributeMinor)
        assertEquals(GoalFundingLimitFactor.CASH_WALLET, availability.limitingFactor)
        assertTrue(availability.canAfford(15_000L))
        assertFalse(availability.canAfford(15_001L))
    }

    @Test
    fun `net worth limited when bank has net deficit and net worth is lower than cash balance`() {
        val transactions = listOf(
            Transaction(
                id = "t1",
                userId = userId,
                type = TransactionType.INCOME,
                amountMinor = 30_000L,
                category = "Cash Income",
                accountId = cashWallet.id,
                currency = "LKR",
                targetCurrency = "LKR"
            ),
            Transaction(
                id = "t2",
                userId = userId,
                type = TransactionType.EXPENSE,
                amountMinor = 12_000L,
                category = "Bills",
                accountId = bankAccount.id,
                currency = "LKR",
                targetCurrency = "LKR"
            )
        )

        val availability = GoalFundingPolicy.evaluate(
            transactions = transactions,
            accounts = listOf(cashWallet, bankAccount),
            currency = "LKR"
        )

        assertEquals(BigInteger.valueOf(30_000L), availability.cashBalanceMinor)
        assertEquals(BigInteger.valueOf(18_000L), availability.netWorthMinor)
        assertEquals(BigInteger.valueOf(18_000L), availability.availableToContributeMinor)
        assertEquals(GoalFundingLimitFactor.NET_WORTH, availability.limitingFactor)
        assertTrue(availability.canAfford(18_000L))
        assertFalse(availability.canAfford(18_001L))
    }

    @Test
    fun `equal cash balance and net worth reports BOTH limiting factor`() {
        val transactions = listOf(
            Transaction(
                id = "t1",
                userId = userId,
                type = TransactionType.INCOME,
                amountMinor = 25_000L,
                category = "Cash Income",
                accountId = cashWallet.id,
                currency = "LKR",
                targetCurrency = "LKR"
            )
        )

        val availability = GoalFundingPolicy.evaluate(
            transactions = transactions,
            accounts = listOf(cashWallet),
            currency = "LKR"
        )

        assertEquals(BigInteger.valueOf(25_000L), availability.cashBalanceMinor)
        assertEquals(BigInteger.valueOf(25_000L), availability.netWorthMinor)
        assertEquals(BigInteger.valueOf(25_000L), availability.availableToContributeMinor)
        assertEquals(GoalFundingLimitFactor.BOTH, availability.limitingFactor)
    }

    @Test
    fun `zero and negative balances clamp availableToContributeMinor to zero`() {
        val emptyAvailability = GoalFundingPolicy.evaluate(
            transactions = emptyList(),
            accounts = listOf(cashWallet),
            currency = "LKR"
        )
        assertEquals(BigInteger.ZERO, emptyAvailability.availableToContributeMinor)
        assertFalse(emptyAvailability.canAfford(1L))

        val negativeTransactions = listOf(
            Transaction(
                id = "t1",
                userId = userId,
                type = TransactionType.EXPENSE,
                amountMinor = 5_000L,
                category = "Cash Expense",
                accountId = cashWallet.id,
                currency = "LKR",
                targetCurrency = "LKR"
            )
        )
        val negativeAvailability = GoalFundingPolicy.evaluate(
            transactions = negativeTransactions,
            accounts = listOf(cashWallet),
            currency = "LKR"
        )
        assertEquals(BigInteger.valueOf(-5_000L), negativeAvailability.cashBalanceMinor)
        assertEquals(BigInteger.valueOf(-5_000L), negativeAvailability.netWorthMinor)
        assertEquals(BigInteger.ZERO, negativeAvailability.availableToContributeMinor)
        assertFalse(negativeAvailability.canAfford(1L))
    }

    @Test
    fun `overflow beyond Long_MAX_VALUE is handled safely with BigInteger`() {
        val transactions = listOf(
            Transaction(
                id = "t1",
                userId = userId,
                type = TransactionType.INCOME,
                amountMinor = Long.MAX_VALUE,
                category = "Cash Income",
                accountId = cashWallet.id,
                currency = "LKR",
                targetCurrency = "LKR"
            ),
            Transaction(
                id = "t2",
                userId = userId,
                type = TransactionType.INCOME,
                amountMinor = Long.MAX_VALUE,
                category = "Cash Income",
                accountId = cashWallet.id,
                currency = "LKR",
                targetCurrency = "LKR"
            )
        )

        val availability = GoalFundingPolicy.evaluate(
            transactions = transactions,
            accounts = listOf(cashWallet),
            currency = "LKR"
        )

        val expected = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2L))
        assertEquals(expected, availability.cashBalanceMinor)
        assertEquals(expected, availability.netWorthMinor)
        assertEquals(expected, availability.availableToContributeMinor)
        assertTrue(availability.canAfford(Long.MAX_VALUE))
    }

    @Test
    fun `unresolved foreign currency conversions never overestimate availability`() {
        val resolvedForeignIncome = Transaction(
            id = "tx-resolved-income",
            userId = userId,
            type = TransactionType.INCOME,
            amountMinor = 10_00L,
            category = "Freelance",
            accountId = cashWallet.id,
            currency = "USD",
            targetCurrency = "LKR",
            conversion = ConversionSnapshot(
                amountMinor = 3_000_00L,
                rate = "300",
                provider = "ExchangeRate-API",
                rateAt = 100L,
                fetchedAt = 100L,
                cached = false
            ),
            pendingConversion = false
        )
        val pendingForeignIncome = Transaction(
            id = "tx-pending-income",
            userId = userId,
            type = TransactionType.INCOME,
            amountMinor = 50_00L,
            category = "Freelance",
            accountId = cashWallet.id,
            currency = "USD",
            targetCurrency = "LKR",
            conversion = null,
            pendingConversion = true
        )
        val mismatchedTargetIncome = Transaction(
            id = "tx-mismatched-income",
            userId = userId,
            type = TransactionType.INCOME,
            amountMinor = 20_00L,
            category = "Freelance",
            accountId = cashWallet.id,
            currency = "EUR",
            targetCurrency = "USD",
            conversion = ConversionSnapshot(
                amountMinor = 22_00L,
                rate = "1.1",
                provider = "ExchangeRate-API",
                rateAt = 100L,
                fetchedAt = 100L,
                cached = false
            ),
            pendingConversion = false
        )
        val resolvedForeignExpense = Transaction(
            id = "tx-resolved-expense",
            userId = userId,
            type = TransactionType.EXPENSE,
            amountMinor = 2_00L,
            category = "Shopping",
            accountId = cashWallet.id,
            currency = "USD",
            targetCurrency = "LKR",
            conversion = ConversionSnapshot(
                amountMinor = 600_00L,
                rate = "300",
                provider = "ExchangeRate-API",
                rateAt = 100L,
                fetchedAt = 100L,
                cached = false
            ),
            pendingConversion = false
        )

        assertNull(GoalFundingPolicy.resolveTransactionMinor(pendingForeignIncome, "LKR"))
        assertNull(GoalFundingPolicy.resolveTransactionMinor(mismatchedTargetIncome, "LKR"))
        assertNotNull(GoalFundingPolicy.resolveTransactionMinor(resolvedForeignIncome, "LKR"))

        val availability = GoalFundingPolicy.evaluate(
            transactions = listOf(
                resolvedForeignIncome,
                pendingForeignIncome,
                mismatchedTargetIncome,
                resolvedForeignExpense
            ),
            accounts = listOf(cashWallet),
            currency = "LKR"
        )

        // Only resolved LKR income (300,000) minus resolved LKR expense (60,000) = 240,000
        assertEquals(BigInteger.valueOf(240_000L), availability.cashBalanceMinor)
        assertEquals(BigInteger.valueOf(240_000L), availability.netWorthMinor)
        assertEquals(BigInteger.valueOf(240_000L), availability.availableToContributeMinor)
    }

    @Test
    fun `goal contribution expense deducts from both cash balance and net worth`() {
        val initialIncome = Transaction(
            id = "income-1",
            userId = userId,
            type = TransactionType.INCOME,
            amountMinor = 100_000L,
            category = "Cash Income",
            accountId = cashWallet.id,
            currency = "LKR",
            targetCurrency = "LKR"
        )
        val contributionExpense = Transaction(
            id = "contrib-1",
            userId = userId,
            type = TransactionType.EXPENSE,
            amountMinor = 25_000L,
            category = TransactionTags.GOAL_CONTRIBUTION,
            note = "Goal: Emergency Fund",
            accountId = cashWallet.id,
            currency = "LKR",
            targetCurrency = "LKR"
        )

        val availability = GoalFundingPolicy.evaluate(
            transactions = listOf(initialIncome, contributionExpense),
            accounts = listOf(cashWallet),
            currency = "LKR"
        )

        assertEquals(BigInteger.valueOf(75_000L), availability.cashBalanceMinor)
        assertEquals(BigInteger.valueOf(75_000L), availability.netWorthMinor)
        assertEquals(BigInteger.valueOf(75_000L), availability.availableToContributeMinor)
        assertTrue(TransactionTags.isExcludedFromIncomeExpenseSummary(TransactionTags.GOAL_CONTRIBUTION))
        assertTrue(TransactionTags.isExcludedFromIncomeExpenseSummary(TransactionTags.CASH_TOP_UP))
        assertFalse(TransactionTags.isExcludedFromIncomeExpenseSummary("Food"))
    }
}

