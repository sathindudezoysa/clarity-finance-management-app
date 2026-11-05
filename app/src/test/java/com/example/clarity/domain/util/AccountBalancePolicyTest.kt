package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

class AccountBalancePolicyTest {

    private val accountA = Account(
        id = "acc-a",
        userId = "user-1",
        name = "Checking",
        bankName = "Bank A",
        currency = "LKR"
    )

    private val accountB = Account(
        id = "acc-b",
        userId = "user-1",
        name = "Savings",
        bankName = "Bank B",
        currency = "USD"
    )

    @Test
    fun `empty transactions returns zero balance formatted dynamically`() = runBlocking {
        val balanceA = AccountBalancePolicy.calculateBalanceMinor(accountA, emptyList())
        val formattedA = AccountBalancePolicy.formatAccountBalance(accountA, balanceA)
        assertEquals(BigInteger.ZERO, balanceA)
        assertEquals("LKR 0.00", formattedA)

        val balanceB = AccountBalancePolicy.calculateBalanceMinor(accountB, emptyList())
        val formattedB = AccountBalancePolicy.formatAccountBalance(accountB, balanceB)
        assertEquals(BigInteger.ZERO, balanceB)
        assertEquals("USD 0.00", formattedB)
    }

    @Test
    fun `calculates account balance dynamically based on transactions belonging to that account`() = runBlocking {
        val transactions = listOf(
            Transaction(
                id = "tx-1",
                amountMinor = 50_000,
                type = TransactionType.INCOME,
                accountId = "acc-a",
                currency = "LKR"
            ),
            Transaction(
                id = "tx-2",
                amountMinor = 15_000,
                type = TransactionType.EXPENSE,
                accountId = "acc-a",
                currency = "LKR"
            ),
            // Belongs to account B, must not affect account A
            Transaction(
                id = "tx-3",
                amountMinor = 20_000,
                type = TransactionType.INCOME,
                accountId = "acc-b",
                currency = "USD"
            )
        )

        val balanceA = AccountBalancePolicy.calculateBalanceMinor(accountA, transactions)
        val formattedA = AccountBalancePolicy.formatAccountBalance(accountA, balanceA)
        assertEquals(BigInteger.valueOf(35_000), balanceA)
        assertEquals("LKR 350.00", formattedA)

        val balanceB = AccountBalancePolicy.calculateBalanceMinor(accountB, transactions)
        val formattedB = AccountBalancePolicy.formatAccountBalance(accountB, balanceB)
        assertEquals(BigInteger.valueOf(20_000), balanceB)
        assertEquals("USD 200.00", formattedB)
    }

    @Test
    fun `handles transaction conversion snapshot when currencies differ`() = runBlocking {
        val convertedTx = Transaction(
            id = "tx-diff",
            amountMinor = 100, // 1.00 USD
            type = TransactionType.INCOME,
            accountId = "acc-a",
            currency = "USD",
            targetCurrency = "LKR",
            conversion = ConversionSnapshot(
                amountMinor = 30_000, // 300.00 LKR
                rate = "300.0",
                provider = "ExchangeRate-API",
                rateAt = 1000L,
                fetchedAt = 1000L,
                cached = true
            )
        )

        val balance = AccountBalancePolicy.calculateBalanceMinor(accountA, listOf(convertedTx))
        assertEquals(BigInteger.valueOf(30_000), balance)
        assertEquals("LKR 300.00", AccountBalancePolicy.formatAccountBalance(accountA, balance))
    }

    @Test
    fun `formats negative balance with negative sign prefixed`() {
        val balance = BigInteger.valueOf(-12_550)
        val formatted = AccountBalancePolicy.formatAccountBalance(accountA, balance)
        assertEquals("-LKR 125.50", formatted)
    }
}
