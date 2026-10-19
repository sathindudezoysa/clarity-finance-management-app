package com.example.clarity

import com.example.clarity.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.*

class BudgetPolicyTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)
    private val budget = Budget(BudgetPolicy.id("2026-10", "Food"), "owner", "2026-10", "Food", 1_000_000, "LKR")
    private fun expense(amount: Long, date: String = "2026-10-19T00:00:00Z") = Transaction(userId = "owner", amountMinor = amount,
        category = " food ", dateEpochMillis = Instant.parse(date).toEpochMilli())

    @Test fun exactBoundariesAndHighestThresholdOnly() {
        val cases = listOf(799_999L to BudgetThreshold.NORMAL, 800_000L to BudgetThreshold.NEARING,
            1_000_000L to BudgetThreshold.REACHED, 1_050_000L to BudgetThreshold.EXCEEDED)
        for ((amount, expected) in cases) {
            val status = BudgetPolicy.evaluate(budget, listOf(expense(amount)))
            assertEquals(expected, status.threshold)
            assertEquals((1_000_000L - amount).toBigInteger(), status.remaining)
        }
        val jump = BudgetPolicy.evaluate(budget, listOf(expense(1_050_000)))
        assertEquals(2, BudgetPolicy.nextAlert(0, jump))
        assertNull(BudgetPolicy.nextAlert(2, jump))
        assertNull(BudgetPolicy.nextAlert(2, BudgetPolicy.evaluate(budget, listOf(expense(800_000)))))
        assertEquals("2026-10", BudgetPolicy.currentMonth(clock.withZone(ZoneId.of("Pacific/Auckland"))))
    }

    @Test fun filtersExpenseOwnerCategoryAndUtcCalendarBoundariesWithSafeCurrencyAmounts() {
        val base = expense(100)
        val entries = listOf(base, expense(200, "2026-10-01T00:00:00Z"),
            expense(900, "2026-11-01T00:00:00Z"), expense(900, "2026-09-30T23:59:59.999Z"),
            base.copy(type = TransactionType.INCOME), base.copy(userId = "other"), base.copy(category = "Travel"),
            base.copy(id = "cash", isCash = true),
            base.copy(id = "converted", amountMinor = 999, currency = "USD", targetCurrency = "LKR", conversion = ConversionSnapshot(300, "0.3003", "ExchangeRate-API", 1, 2, true)),
            base.copy(id = "wrong-target", currency = "USD", targetCurrency = "EUR", conversion = ConversionSnapshot(777, "7.77", "ExchangeRate-API", 1, 2, true)),
            base.copy(id = "pending", currency = "USD", conversion = null, pendingConversion = true),
            base.copy(id = "original", amountMinor = 400, targetCurrency = "USD", conversion = ConversionSnapshot(1, "0.0025", "ExchangeRate-API", 1, 2, true)))
        val status = BudgetPolicy.evaluate(budget, entries)
        assertEquals(1100.toBigInteger(), status.spent)
        assertEquals(2, status.uncounted)
        val resolved = BudgetPolicy.evaluate(budget, entries, listOf(BudgetResolution("wrong-target", base.updatedAt, "LKR", 500)))
        assertEquals(1600.toBigInteger(), resolved.spent)
        assertEquals(1, resolved.uncounted)
        assertEquals(status, BudgetPolicy.evaluate(budget, entries, listOf(BudgetResolution("wrong-target", base.updatedAt - 1, "LKR", 500))))
        val partial = BudgetPolicy.evaluate(budget, listOf(expense(800_000), entries.first { it.id == "pending" }))
        assertEquals(BudgetThreshold.NEARING, partial.threshold)
        assertEquals(1, partial.uncounted)
    }

    @Test fun aggregationDoesNotOverflowAndIdentityUsesSharedCategoryNormalization() {
        val status = BudgetPolicy.evaluate(budget, listOf(expense(Long.MAX_VALUE), expense(Long.MAX_VALUE)))
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO, status.spent)
        assertEquals(BudgetThreshold.EXCEEDED, status.threshold)
        assertEquals(BudgetPolicy.id("2026-10", "Food"), BudgetPolicy.id("2026-10", " food "))
        assertNotEquals(budget.id, BudgetPolicy.id("2026-11", "Food"))
    }

    @Test fun syncConflictsRetainRecordedCurrencyAndCreationAndNeverResurrectAnOlderDisable() {
        val disabled = budget.copy(active = false, createdAt = 10, updatedAt = 30)
        for (version in listOf(20L, 30L)) {
            assertFalse(BudgetPolicy.resolveUpload(budget.copy(updatedAt = version, pendingSync = true), disabled).active)
        }
        val edit = BudgetPolicy.resolveUpload(budget.copy(limitMinor = 2_000_000, createdAt = 1, updatedAt = 40), disabled)
        assertEquals(2_000_000L, edit.limitMinor)
        assertEquals(10L, edit.createdAt)
        assertFalse(edit.pendingSync)
        val otherCurrency = budget.copy(currency = "JPY", limitMinor = 50, updatedAt = 50)
        assertEquals(disabled, BudgetPolicy.resolveUpload(otherCurrency, disabled))
        assertFalse(BudgetPolicy.resolveUpload(budget.copy(active = false, updatedAt = 30), budget.copy(updatedAt = 30)).active)
    }
}
