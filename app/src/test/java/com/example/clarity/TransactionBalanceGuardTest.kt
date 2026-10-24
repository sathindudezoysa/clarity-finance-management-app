package com.example.clarity

import com.example.clarity.data.local.TransactionBalanceGuard
import com.example.clarity.data.local.entity.TransactionEntity
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TransactionBalanceGuardTest {

    private fun entity(
        id: String,
        type: String,
        amountMinor: Long,
        accountId: String? = "acc-a",
        category: String = "General",
        note: String = "",
        currency: String = "LKR",
        targetCurrency: String = "LKR",
        conversion: ConversionSnapshot? = if (currency == targetCurrency) ConversionSnapshot.identity(amountMinor) else null
    ) = TransactionEntity(
        id = id,
        userId = "user-1",
        type = type,
        amountMinor = amountMinor,
        category = category,
        note = note,
        dateEpochMillis = 1_000L,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        accountId = accountId,
        currency = currency,
        targetCurrency = targetCurrency,
        conversion = conversion,
        pendingConversion = conversion == null
    )

    private fun assertInsufficientBalance(block: () -> Unit) {
        try {
            block()
            fail("Expected TransactionException(INSUFFICIENT_BALANCE)")
        } catch (e: TransactionException) {
            assertEquals(TransactionError.INSUFFICIENT_BALANCE, e.error)
        }
    }

    @Test
    fun expenseWithNoIncomeThrowsInsufficientBalance() {
        val before = emptyList<TransactionEntity>()
        val exp = entity("tx-1", "EXPENSE", 1_000, accountId = "acc-a")
        assertInsufficientBalance {
            TransactionBalanceGuard.check(before, listOf(exp), previousAccountId = null, newAccountId = "acc-a")
        }
    }

    @Test
    fun incomeThenExpensePasses() {
        val inc = entity("inc-1", "INCOME", 100_000, accountId = "acc-a")
        val exp = entity("exp-1", "EXPENSE", 1_000, accountId = "acc-a")
        TransactionBalanceGuard.check(listOf(inc), listOf(inc, exp), previousAccountId = null, newAccountId = "acc-a")
    }

    @Test
    fun expenseAboveBalanceThrowsAndExpenseEqualToBalancePasses() {
        val inc = entity("inc-1", "INCOME", 5_000, accountId = "acc-a")
        val equalExp = entity("exp-eq", "EXPENSE", 5_000, accountId = "acc-a")
        TransactionBalanceGuard.check(listOf(inc), listOf(inc, equalExp), previousAccountId = null, newAccountId = "acc-a")

        val aboveExp = entity("exp-gt", "EXPENSE", 5_001, accountId = "acc-a")
        assertInsufficientBalance {
            TransactionBalanceGuard.check(listOf(inc), listOf(inc, aboveExp), previousAccountId = null, newAccountId = "acc-a")
        }
    }

    @Test
    fun editingNoteOrCategoryWhileBalanceAlreadyNegativePasses() {
        val exp = entity("exp-1", "EXPENSE", 5_000, accountId = "acc-a", category = "Food", note = "Old")
        val edited = exp.copy(category = "Groceries", note = "Updated note")
        TransactionBalanceGuard.check(listOf(exp), listOf(edited), previousAccountId = "acc-a", newAccountId = "acc-a")
    }

    @Test
    fun addingIncomeWhileBalanceIsNegativePasses() {
        val exp = entity("exp-1", "EXPENSE", 10_000, accountId = "acc-a")
        val partialIncome = entity("inc-1", "INCOME", 3_000, accountId = "acc-a")
        TransactionBalanceGuard.check(listOf(exp), listOf(exp, partialIncome), previousAccountId = null, newAccountId = "acc-a")
    }

    @Test
    fun deletingIncomeThatFundsExpenseThrows() {
        val inc = entity("inc-1", "INCOME", 5_000, accountId = "acc-a")
        val exp = entity("exp-1", "EXPENSE", 2_000, accountId = "acc-a")
        assertInsufficientBalance {
            TransactionBalanceGuard.check(listOf(inc, exp), listOf(exp), previousAccountId = "acc-a", newAccountId = "acc-a")
        }
    }

    @Test
    fun movingExpenseOrIncomeBetweenAccountsChecksBothAccounts() {
        val incA = entity("inc-a", "INCOME", 10_000, accountId = "acc-a")
        val incB = entity("inc-b", "INCOME", 1_000, accountId = "acc-b")
        val exp = entity("exp-1", "EXPENSE", 3_000, accountId = "acc-a")
        val before = listOf(incA, incB, exp)

        // Moving 3_000 expense from acc-a to acc-b (which only has 1_000) makes acc-b negative (-2_000) even though total is +8_000.
        val movedExpToB = listOf(incA, incB, exp.copy(accountId = "acc-b"))
        assertInsufficientBalance {
            TransactionBalanceGuard.check(before, movedExpToB, previousAccountId = "acc-a", newAccountId = "acc-b")
        }

        // Moving 10_000 income from acc-a to acc-b leaves acc-a at -3_000 ( checked via previousAccountId ).
        val movedIncToB = listOf(incA.copy(accountId = "acc-b"), incB, exp)
        assertInsufficientBalance {
            TransactionBalanceGuard.check(before, movedIncToB, previousAccountId = "acc-a", newAccountId = "acc-b")
        }
    }

    @Test
    fun pendingConversionRowIsIgnoredAndNeverThrows() {
        val pendingUsdExpense = entity(
            id = "usd-exp",
            type = "EXPENSE",
            amountMinor = 50_000,
            accountId = "acc-a",
            currency = "USD",
            targetCurrency = "LKR",
            conversion = null
        )
        TransactionBalanceGuard.check(
            before = emptyList(),
            after = listOf(pendingUsdExpense),
            previousAccountId = null,
            newAccountId = "acc-a"
        )
        assertEquals(java.math.BigInteger.ZERO, TransactionBalanceGuard.netMinor(listOf(pendingUsdExpense)))
    }
}

