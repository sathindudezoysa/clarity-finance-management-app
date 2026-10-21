package com.example.clarity

import com.example.clarity.domain.model.*
import com.example.clarity.domain.util.TransactionCalendar
import com.example.clarity.presentation.budgets.BudgetMessages
import com.example.clarity.presentation.budgets.budgetDisableConfirmText
import com.example.clarity.presentation.budgets.budgetStatusBadgeText
import com.example.clarity.presentation.budgets.budgetStatusText
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
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

    @Test fun exactPercentagesRoundingDownProgressCoercionAndOverflowSafety() {
        val limit = BigInteger.valueOf(1_000_000)
        val zeroStatus = BudgetStatus(budget, BigInteger.ZERO, 0)
        assertEquals("0%", zeroStatus.percentText)
        assertEquals(0f, zeroStatus.progress, 0.0001f)
        assertEquals(limit, zeroStatus.remaining)

        val sixtyStatus = BudgetStatus(budget, BigInteger.valueOf(600_000), 0)
        assertEquals("60%", sixtyStatus.percentText)
        assertEquals(0.60f, sixtyStatus.progress, 0.0001f)

        val fractionalStatus = BudgetStatus(budget, BigInteger.valueOf(687_500), 0)
        assertEquals("68.75%", fractionalStatus.percentText)
        assertEquals(0.6875f, fractionalStatus.progress, 0.0001f)

        // Never shows 100% before the limit is reached.
        val justBelow = BudgetStatus(budget, BigInteger.valueOf(999_999), 0)
        assertEquals("99.99%", justBelow.percentText)
        assertEquals(BudgetThreshold.NEARING, justBelow.threshold)
        assertTrue(justBelow.progress < 1f)

        val overspent = BudgetStatus(budget, BigInteger.valueOf(1_150_000), 0)
        assertEquals("115%", overspent.percentText)
        assertEquals(1f, overspent.progress, 0.0001f)
        assertEquals(BigInteger.valueOf(-150_000), overspent.remaining)
        assertEquals(BudgetThreshold.EXCEEDED, overspent.threshold)

        val hugeSpent = BigInteger.valueOf(Long.MAX_VALUE) * BigInteger.TWO
        val overflowStatus = BudgetStatus(budget, hugeSpent, 0)
        assertTrue(overflowStatus.percent > BigDecimal.valueOf(100))
        assertEquals(1f, overflowStatus.progress, 0.0001f)
    }

    @Test fun monthNavigationSelectionReducerScrollOffsetsAndMonthlyCurrencySummaries() {
        assertEquals("2027-01", BudgetSummary.shiftMonth("2026-12", 1))
        assertEquals("2026-12", BudgetSummary.shiftMonth("2027-01", -1))
        assertEquals("October 2026", BudgetSummary.formatMonth("2026-10"))
        assertEquals(
            "Are you sure you want to disable the Food budget for October 2026?",
            budgetDisableConfirmText("Food", "2026-10")
        )
        assertEquals("Within budget", budgetStatusText(BudgetThreshold.NORMAL, false))
        assertTrue(budgetStatusBadgeText(BudgetThreshold.EXCEEDED, false).contains("Budget exceeded"))

        val octFood = BudgetStatus(budget, BigInteger.valueOf(500_000), 0)
        val octGroceries = BudgetStatus(
            Budget(BudgetPolicy.id("2026-10", "Groceries"), "owner", "2026-10", "Groceries", 1_500_000, "LKR"),
            BigInteger.valueOf(300_000), 0
        )
        val octUsdIncomplete = BudgetStatus(
            Budget(BudgetPolicy.id("2026-10", "Travel"), "owner", "2026-10", "Travel", 50_000, "USD"),
            BigInteger.valueOf(10_000), 2
        )
        val octDisabled = BudgetStatus(
            Budget(BudgetPolicy.id("2026-10", "Savings"), "owner", "2026-10", "Savings", 900_000, "LKR", active = false),
            BigInteger.valueOf(400_000), 0
        )
        val novFood = BudgetStatus(
            Budget(BudgetPolicy.id("2026-11", "Food"), "owner", "2026-11", "Food", 2_000_000, "LKR"),
            BigInteger.valueOf(250_000), 0
        )
        val all = listOf(octFood, octGroceries, octUsdIncomplete, octDisabled, novFood)

        val octView = BudgetSummary.forMonth(all, "2026-10")
        assertEquals(listOf(octFood, octGroceries, octUsdIncomplete), octView.active)
        assertEquals(listOf(octDisabled), octView.disabled)
        assertEquals(2, octView.summaries.size)

        val lkrSummary = octView.summaries.first { it.currency == "LKR" }
        assertEquals(BigInteger.valueOf(2_500_000), lkrSummary.totalLimit)
        assertEquals(BigInteger.valueOf(800_000), lkrSummary.totalSpent)
        assertEquals(BigInteger.valueOf(1_700_000), lkrSummary.remaining)
        assertEquals("32%", lkrSummary.percentText)
        assertFalse(lkrSummary.incomplete)

        val usdSummary = octView.summaries.first { it.currency == "USD" }
        assertEquals(BigInteger.valueOf(50_000), usdSummary.totalLimit)
        assertEquals(BigInteger.valueOf(10_000), usdSummary.totalSpent)
        assertEquals(2, usdSummary.uncounted)
        assertTrue(usdSummary.incomplete)

        val novView = BudgetSummary.forMonth(all, "2026-11")
        assertEquals(listOf(novFood), novView.active)
        assertTrue(novView.disabled.isEmpty())

        val emptyView = BudgetSummary.forMonth(all, "2026-12")
        assertTrue(emptyView.active.isEmpty())
        assertTrue(emptyView.disabled.isEmpty())
        assertTrue(emptyView.summaries.isEmpty())

        // Deep-link switches selectedMonth once per selectedId so user can navigate away afterwards.
        val firstSelection = BudgetSummary.resolveSelection("2026-10", null, novFood.budget.id, all)
        assertEquals("2026-11", firstSelection.selectedMonth)
        assertEquals(novFood.budget.id, firstSelection.handledSelectedId)

        val afterUserNavigatesToOct = BudgetSummary.resolveSelection(
            "2026-10",
            firstSelection.handledSelectedId,
            novFood.budget.id,
            all
        )
        assertEquals("2026-10", afterUserNavigatesToOct.selectedMonth)

        // Scroll index accounts for header (1) + summaries (2 in octView) before active cards.
        assertEquals(3, BudgetSummary.scrollIndexFor(octFood.budget.id, octView))
        assertEquals(4, BudgetSummary.scrollIndexFor(octGroceries.budget.id, octView))
        assertEquals(5, BudgetSummary.scrollIndexFor(octUsdIncomplete.budget.id, octView))
    }

    @Test fun editorValidationCoversCreateCustomTagsInvalidAmountsDuplicateEditImmutabilityAndReenable() {
        val existingActive = BudgetStatus(budget, BigInteger.valueOf(500_000), 0)
        val disabledUsd = Budget(
            id = BudgetPolicy.id("2026-10", "Gym"),
            userId = "owner",
            month = "2026-10",
            category = "Gym",
            limitMinor = 10_000,
            currency = "USD",
            active = false,
            createdAt = 100L,
            updatedAt = 200L
        )
        val statuses = listOf(existingActive, BudgetStatus(disabledUsd, BigInteger.ZERO, 0))

        // Blank category is rejected explicitly and never silently normalized to General.
        val blankCategory = BudgetSummary.validateEditor(null, "   ", "2026-10", "200.00", "LKR", "owner", statuses)
        assertFalse(blankCategory.isValid)
        assertEquals(BudgetMessages.CATEGORY_REQUIRED, blankCategory.categoryError)

        // Valid create with custom tag category.
        val customCreate = BudgetSummary.validateEditor(null, "  Pet Care ", "2026-10", "20000", "LKR", "owner", statuses)
        assertTrue(customCreate.isValid)
        assertEquals("Pet Care", customCreate.budget!!.category)
        assertEquals(2_000_000L, customCreate.budget!!.limitMinor)
        assertEquals("LKR", customCreate.budget!!.currency)

        // Invalid amounts: empty, 0, negative, non-numeric, too many decimals, huge.
        for (badAmount in listOf("", "0", "0.00", "-50", "abc", "10.123", "9999999999999999999999999")) {
            val result = BudgetSummary.validateEditor(null, "Travel", "2026-10", badAmount, "LKR", "owner", statuses)
            assertFalse("Expected invalid amount for '$badAmount'", result.isValid)
            assertEquals(BudgetMessages.AMOUNT_INVALID, result.amountError)
        }

        // Duplicate active budget rejected.
        val duplicate = BudgetSummary.validateEditor(null, " food ", "2026-10", "25000", "LKR", "owner", statuses)
        assertFalse(duplicate.isValid)
        assertEquals(BudgetMessages.DUPLICATE_BUDGET, duplicate.duplicateError)

        // Editing an existing budget keeps id, category, month, and currency immutable.
        val edited = BudgetSummary.validateEditor(
            existing = budget.copy(createdAt = 50L, updatedAt = 60L),
            rawCategory = "Tampered",
            rawMonth = "2026-11",
            rawAmount = "25000.00",
            currency = "USD",
            owner = "owner",
            statuses = statuses
        )
        assertTrue(edited.isValid)
        assertEquals(budget.id, edited.budget!!.id)
        assertEquals("Food", edited.budget!!.category)
        assertEquals("2026-10", edited.budget!!.month)
        assertEquals("LKR", edited.budget!!.currency)
        assertEquals(2_500_000L, edited.budget!!.limitMinor)
        assertEquals(50L, edited.budget!!.createdAt)

        // Re-enabling a disabled budget preserves its recorded currency and id.
        val reenabled = BudgetSummary.validateEditor(
            existing = disabledUsd,
            rawCategory = "Gym",
            rawMonth = "2026-10",
            rawAmount = "150.00",
            currency = "LKR",
            owner = "owner",
            statuses = statuses
        )
        assertTrue(reenabled.isValid)
        assertTrue(reenabled.budget!!.active)
        assertEquals(disabledUsd.id, reenabled.budget!!.id)
        assertEquals("USD", reenabled.budget!!.currency)
        assertEquals(15_000L, reenabled.budget!!.limitMinor)
    }

    @Test fun expenseMutationsUpdateEvaluatedBudgetSpendingAutomatically() {
        val foodOct = Budget(BudgetPolicy.id("2026-10", "Food"), "owner", "2026-10", "Food", 2_000_000, "LKR")
        val e1 = expense(500_000, "2026-10-05T10:00:00Z").copy(id = "e1")
        val e2 = expense(300_000, "2026-10-12T10:00:00Z").copy(id = "e2")

        // Initial: 5,000 + 3,000 = 8,000 spent, 12,000 left, 40%
        val initial = evaluate(foodOct, listOf(e1, e2))
        assertEquals(BigInteger.valueOf(800_000), initial.spent)
        assertEquals(BigInteger.valueOf(1_200_000), initial.remaining)
        assertEquals("40%", initial.percentText)

        // Edit e2 amount from 3,000 to 7,000 -> 12,000 spent (60%)
        val afterEdit = evaluate(foodOct, listOf(e1, e2.copy(amountMinor = 700_000)))
        assertEquals(BigInteger.valueOf(1_200_000), afterEdit.spent)
        assertEquals("60%", afterEdit.percentText)

        // Move e2 category from Food to Transport -> only e1 counts (5,000)
        val afterCategoryMove = evaluate(foodOct, listOf(e1, e2.copy(category = "Transport")))
        assertEquals(BigInteger.valueOf(500_000), afterCategoryMove.spent)

        // Move e2 date to November -> only e1 counts in October
        val afterMonthMove = evaluate(foodOct, listOf(e1, e2.copy(dateEpochMillis = Instant.parse("2026-11-02T10:00:00Z").toEpochMilli())))
        assertEquals(BigInteger.valueOf(500_000), afterMonthMove.spent)

        // Delete e2 (filtered out by repository before evaluate) -> only e1 counts
        val afterDelete = evaluate(foodOct, listOf(e1))
        assertEquals(BigInteger.valueOf(500_000), afterDelete.spent)
    }
}
