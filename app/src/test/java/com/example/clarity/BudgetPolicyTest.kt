package com.example.clarity

import com.example.clarity.domain.model.*
import com.example.clarity.domain.util.TransactionCalendar
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.*

class BudgetPolicyTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC)
    private val budget = Budget(BudgetPolicy.id("2026-10", "Food"), "owner", "2026-10", "Food", 1_000_000, "LKR")
    private fun expense(amount: Long, date: String = "2026-10-19T00:00:00Z") = Transaction(userId = "owner", amountMinor = amount,
        category = " food ", dateEpochMillis = Instant.parse(date).toEpochMilli())
    private fun evaluate(budget: Budget, entries: List<Transaction>, resolutions: List<BudgetResolution> = emptyList()) =
        BudgetPolicy.evaluate(budget, entries, resolutions, ZoneOffset.UTC)

    @Test fun exactBoundariesAndHighestThresholdOnly() {
        val cases = listOf(799_999L to BudgetThreshold.NORMAL, 800_000L to BudgetThreshold.NEARING,
            1_000_000L to BudgetThreshold.REACHED, 1_050_000L to BudgetThreshold.EXCEEDED)
        for ((amount, expected) in cases) {
            val status = evaluate(budget, listOf(expense(amount)))
            assertEquals(expected, status.threshold)
            assertEquals((1_000_000L - amount).toBigInteger(), status.remaining)
        }
        var consumed = 0
        val delivered = listOf(800_000L, 1_000_000L, 1_050_000L).map { amount ->
            BudgetPolicy.nextAlert(consumed, evaluate(budget, listOf(expense(amount)))).also { consumed = requireNotNull(it) }
        }
        assertEquals(listOf(1, 2, 3), delivered)
        val jump = evaluate(budget, listOf(expense(1_050_000)))
        assertEquals(3, BudgetPolicy.nextAlert(0, jump))
        assertNull(BudgetPolicy.nextAlert(3, jump))
        assertNull(BudgetPolicy.nextAlert(3, evaluate(budget, listOf(expense(800_000)))))
        assertEquals("2026-10", BudgetPolicy.currentMonth(clock, ZoneId.of("Pacific/Auckland")))
    }

    @Test fun filtersExpenseOwnerCategoryAndUtcCalendarBoundariesWithSafeCurrencyAmounts() {
        val base = expense(100)
        val entries = listOf(base, expense(200, "2026-10-01T00:00:00Z"),
            expense(900, "2026-11-01T00:00:00Z"), expense(900, "2026-09-30T23:59:59.999Z"),
            base.copy(type = TransactionType.INCOME), base.copy(userId = "other"), base.copy(category = "Travel"),
            base.copy(id = "cash", accountId = "cash_wallet_default"),
            base.copy(id = "converted", amountMinor = 999, currency = "USD", targetCurrency = "LKR", conversion = ConversionSnapshot(300, "0.3003", "ExchangeRate-API", 1, 2, true)),
            base.copy(id = "wrong-target", currency = "USD", targetCurrency = "EUR", conversion = ConversionSnapshot(777, "7.77", "ExchangeRate-API", 1, 2, true)),
            base.copy(id = "pending", currency = "USD", conversion = null, pendingConversion = true),
            base.copy(id = "original", amountMinor = 400, targetCurrency = "USD", conversion = ConversionSnapshot(1, "0.0025", "ExchangeRate-API", 1, 2, true)))
        val status = evaluate(budget, entries)
        assertEquals(1100.toBigInteger(), status.spent)
        assertEquals(2, status.uncounted)
        val resolved = evaluate(budget, entries, listOf(BudgetResolution("wrong-target", base.updatedAt, "LKR", 500)))
        assertEquals(1600.toBigInteger(), resolved.spent)
        assertEquals(1, resolved.uncounted)
        assertEquals(status, evaluate(budget, entries, listOf(BudgetResolution("wrong-target", base.updatedAt - 1, "LKR", 500))))
        val partial = evaluate(budget, listOf(expense(800_000), entries.first { it.id == "pending" }))
        assertEquals(BudgetThreshold.NEARING, partial.threshold)
        assertEquals(1, partial.uncounted)
    }

    @Test fun aggregationDoesNotOverflowAndIdentityUsesSharedCategoryNormalization() {
        val status = evaluate(budget, listOf(expense(Long.MAX_VALUE), expense(Long.MAX_VALUE)))
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

    @Test fun localDatePickerAndMonthBoundariesAgreeAcrossZonesAndDaylightSaving() {
        val colombo = ZoneId.of("Asia/Colombo")
        val selected = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
        val stored = TransactionCalendar.fromPickerDate(selected, colombo)
        assertEquals(Instant.parse("2026-09-30T18:30:00Z").toEpochMilli(), stored)
        assertEquals(LocalDate.of(2026, 10, 1), TransactionCalendar.dateAt(stored, colombo))
        assertEquals(selected, TransactionCalendar.pickerDate(stored, colombo))
        val atStart = Clock.fixed(Instant.ofEpochMilli(stored), ZoneOffset.UTC)
        assertEquals("2026-10", BudgetPolicy.currentMonth(atStart, colombo))
        assertEquals("2026-09", BudgetPolicy.currentMonth(atStart, ZoneOffset.UTC))
        for ((zone, month) in listOf(colombo to "2026-10", ZoneId.of("America/New_York") to "2026-11")) {
            val localBudget = budget.copy(id = BudgetPolicy.id(month, "Food"), month = month)
            val (start, end) = TransactionCalendar.monthBounds(YearMonth.parse(month), zone)
            val base = expense(1)
            for ((timestamp, eligible) in listOf(start - 1 to false, start to true, end - 1 to true, end to false)) {
                assertEquals(eligible, BudgetPolicy.eligible(localBudget, base.copy(dateEpochMillis = timestamp), zone))
            }
        }
        assertEquals(100.toBigInteger(), BudgetPolicy.evaluate(budget,
            listOf(expense(100).copy(dateEpochMillis = stored)), zone = colombo).spent)
    }

    @Test fun yearlyBudgetPeriodValidationAndCalendarYearBoundariesWorkIndependentlyOfMonthlyBudgets() {
        assertEquals(BudgetPeriod.MONTHLY, BudgetPolicy.periodOf("2026-10"))
        assertEquals(BudgetPeriod.YEARLY, BudgetPolicy.periodOf("2026"))
        for (invalid in listOf("0000", "0000-01", "2026-13", "20261", "26", "2026-1", "")) {
            assertThrows(IllegalArgumentException::class.java) { BudgetPolicy.periodOf(invalid) }
            val badBudget = budget.copy(id = BudgetPolicy.id(invalid, "Food"), month = invalid)
            assertThrows(IllegalArgumentException::class.java) { BudgetPolicy.validate(badBudget) }
        }
        val yearly = Budget(BudgetPolicy.id("2026", "Food"), "owner", "2026", "Food", 12_000_000, "LKR")
        assertEquals(69, yearly.id.length)
        assertEquals(72, budget.id.length)
        assertNotEquals(yearly.id, budget.id)
        assertEquals(BudgetPeriod.YEARLY, yearly.period)
        assertEquals(BudgetPeriod.MONTHLY, budget.period)
        BudgetPolicy.validate(yearly)

        for (zone in listOf(ZoneOffset.UTC, ZoneId.of("Asia/Colombo"))) {
            val (start, end) = TransactionCalendar.yearBounds(Year.parse("2026"), zone)
            val base = expense(1)
            for ((timestamp, eligible) in listOf(start - 1 to false, start to true, end - 1 to true, end to false)) {
                assertEquals(eligible, BudgetPolicy.eligible(yearly, base.copy(dateEpochMillis = timestamp), zone))
            }
        }
        val txs = listOf(
            expense(100_000, "2026-01-15T10:00:00Z"),
            expense(250_000, "2026-10-19T10:00:00Z"),
            expense(400_000, "2027-01-01T00:00:00Z")
        )
        assertEquals(350_000.toBigInteger(), evaluate(yearly, txs).spent)
        assertEquals(250_000.toBigInteger(), evaluate(budget, txs).spent)

        assertTrue(BudgetPolicy.isCurrentPeriod(yearly, clock, ZoneOffset.UTC))
        assertFalse(BudgetPolicy.isCurrentPeriod(yearly.copy(id = BudgetPolicy.id("2025", "Food"), month = "2025"), clock, ZoneOffset.UTC))
        assertTrue(BudgetPolicy.isCurrentPeriod(budget, clock, ZoneOffset.UTC))
        assertFalse(BudgetPolicy.isCurrentPeriod(budget.copy(id = BudgetPolicy.id("2026-09", "Food"), month = "2026-09"), clock, ZoneOffset.UTC))
    }
}
