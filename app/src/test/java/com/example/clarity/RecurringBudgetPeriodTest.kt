package com.example.clarity

import com.example.clarity.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class RecurringBudgetPeriodTest {
    private fun expense(amount: Long, isoUtc: String) = Transaction(
        userId = "owner",
        amountMinor = amount,
        category = "Food",
        dateEpochMillis = Instant.parse(isoUtc).toEpochMilli()
    )

    @Test
    fun monthlyRolloverOctoberToNovemberResetsSpendingAndUpdatesActivePeriod() {
        val budget = Budget(BudgetPolicy.id("2026-10", "Food"), "owner", "2026-10", "Food", 1_000_000, "LKR")
        val txs = listOf(
            expense(850_000, "2026-10-25T10:00:00Z"),
            expense(200_000, "2026-11-01T00:00:00Z")
        )
        val octClock = Clock.fixed(Instant.parse("2026-10-31T23:59:59Z"), ZoneOffset.UTC)
        val novStartClock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC)

        val octStatus = BudgetPolicy.evaluate(budget, txs.take(1), emptyList(), ZoneOffset.UTC, octClock)
        assertEquals("2026-10", octStatus.activePeriod)
        assertEquals(850_000.toBigInteger(), octStatus.spent)

        val novResetStatus = BudgetPolicy.evaluate(budget, txs.take(1), emptyList(), ZoneOffset.UTC, novStartClock)
        assertEquals("2026-11", novResetStatus.activePeriod)
        assertEquals(BigInteger.ZERO, novResetStatus.spent)

        val novWithSpend = BudgetPolicy.evaluate(budget, txs, emptyList(), ZoneOffset.UTC, novStartClock)
        assertEquals("2026-11", novWithSpend.activePeriod)
        assertEquals(200_000.toBigInteger(), novWithSpend.spent)
    }

    @Test
    fun monthlyRolloverDecemberToJanuaryCrossesYearBoundary() {
        val budget = Budget(BudgetPolicy.id("2026-12", "Food"), "owner", "2026-12", "Food", 1_000_000, "LKR")
        val decTx = expense(900_000, "2026-12-31T18:00:00Z")
        val janTx = expense(150_000, "2027-01-01T00:00:00Z")

        val decClock = Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC)
        val janClock = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC)

        val decStatus = BudgetPolicy.evaluate(budget, listOf(decTx, janTx), emptyList(), ZoneOffset.UTC, decClock)
        assertEquals("2026-12", decStatus.activePeriod)
        assertEquals(900_000.toBigInteger(), decStatus.spent)

        val janStatus = BudgetPolicy.evaluate(budget, listOf(decTx, janTx), emptyList(), ZoneOffset.UTC, janClock)
        assertEquals("2027-01", janStatus.activePeriod)
        assertEquals(150_000.toBigInteger(), janStatus.spent)
    }

    @Test
    fun leapYearFebruaryIncludesFeb29AndResetsOnMarch1() {
        val budget = Budget(BudgetPolicy.id("2026-10", "Food"), "owner", "2026-10", "Food", 1_000_000, "LKR")
        val feb29Tx = expense(400_000, "2028-02-29T23:59:59Z")
        val mar1Tx = expense(100_000, "2028-03-01T00:00:00Z")

        val leapClock = Clock.fixed(Instant.parse("2028-02-29T12:00:00Z"), ZoneOffset.UTC)
        val marchClock = Clock.fixed(Instant.parse("2028-03-01T00:00:00Z"), ZoneOffset.UTC)

        val febStatus = BudgetPolicy.evaluate(budget, listOf(feb29Tx, mar1Tx), emptyList(), ZoneOffset.UTC, leapClock)
        assertEquals("2028-02", febStatus.activePeriod)
        assertEquals(400_000.toBigInteger(), febStatus.spent)

        val marStatus = BudgetPolicy.evaluate(budget, listOf(feb29Tx, mar1Tx), emptyList(), ZoneOffset.UTC, marchClock)
        assertEquals("2028-03", marStatus.activePeriod)
        assertEquals(100_000.toBigInteger(), marStatus.spent)
    }

    @Test
    fun yearlyRolloverResetsOnJanuary1() {
        val yearly = Budget(BudgetPolicy.id("2026", "Food"), "owner", "2026", "Food", 12_000_000, "LKR")
        val tx2026 = expense(10_000_000, "2026-12-31T23:59:59Z")
        val tx2027 = expense(500_000, "2027-01-01T00:00:00Z")

        val clock2026 = Clock.fixed(Instant.parse("2026-12-31T20:00:00Z"), ZoneOffset.UTC)
        val clock2027 = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC)

        val status2026 = BudgetPolicy.evaluate(yearly, listOf(tx2026), emptyList(), ZoneOffset.UTC, clock2026)
        assertEquals("2026", status2026.activePeriod)
        assertEquals(10_000_000.toBigInteger(), status2026.spent)

        val status2027Empty = BudgetPolicy.evaluate(yearly, listOf(tx2026), emptyList(), ZoneOffset.UTC, clock2027)
        assertEquals("2027", status2027Empty.activePeriod)
        assertEquals(BigInteger.ZERO, status2027Empty.spent)

        val status2027WithSpend = BudgetPolicy.evaluate(yearly, listOf(tx2026, tx2027), emptyList(), ZoneOffset.UTC, clock2027)
        assertEquals("2027", status2027WithSpend.activePeriod)
        assertEquals(500_000.toBigInteger(), status2027WithSpend.spent)
    }

    @Test
    fun budgetCreatedOn31stRenewsCleanlyInShorterMonthsAndAlertKeyIncludesActivePeriod() {
        val jan31Budget = Budget(BudgetPolicy.id("2026-01", "Food"), "owner", "2026-01", "Food", 1_000_000, "LKR")
        val febClock = Clock.fixed(Instant.parse("2026-02-28T12:00:00Z"), ZoneOffset.UTC)
        val aprClock = Clock.fixed(Instant.parse("2026-04-30T12:00:00Z"), ZoneOffset.UTC)

        assertEquals("2026-02", BudgetPolicy.activePeriodKey(jan31Budget, febClock, ZoneOffset.UTC))
        assertEquals("2026-04", BudgetPolicy.activePeriodKey(jan31Budget, aprClock, ZoneOffset.UTC))

        val (febStart, febEnd) = BudgetPolicy.activePeriodBounds(jan31Budget, febClock, ZoneOffset.UTC)
        assertEquals(Instant.parse("2026-02-01T00:00:00Z").toEpochMilli(), febStart)
        assertEquals(Instant.parse("2026-03-01T00:00:00Z").toEpochMilli(), febEnd)

        assertEquals(jan31Budget.id, BudgetPolicy.alertKey(jan31Budget, "2026-01"))
        assertNotEquals(
            BudgetPolicy.alertKey(jan31Budget, "2026-01"),
            BudgetPolicy.alertKey(jan31Budget, "2026-02")
        )
    }
}

