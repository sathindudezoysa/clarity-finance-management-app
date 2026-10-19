package com.example.clarity

import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.util.IncomePolicy
import com.example.clarity.data.mapper.toIncomeSource
import com.example.clarity.data.mapper.toRemoteMap
import com.example.clarity.data.mapper.toEntity
import com.example.clarity.data.mapper.toDomain
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class IncomePolicyTest {
    private val source = IncomeSource("salary", "owner", "Salary", 125000, "bank", true, createdAt = 10, updatedAt = 10)
    @Test fun newRecurringSourceRequiresConfirmation() { assertTrue(source.isDue(YearMonth.of(2026, 10))) }
    @Test fun confirmationSuppressesOnlyTheConfirmedMonth() {
        val paid = source.copy(lastConfirmedMonth = "2026-10")
        assertFalse(paid.isDue(YearMonth.of(2026, 10)))
        assertTrue(paid.isDue(YearMonth.of(2026, 11)))
    }
    @Test fun decemberConfirmationPromptsAgainInJanuary() {
        assertTrue(source.copy(lastConfirmedMonth = "2026-12").isDue(YearMonth.of(2027, 1)))
    }
    @Test fun nonRecurringAndStoppedSourcesDoNotPrompt() {
        assertFalse(source.copy(recurring = false).isDue(YearMonth.of(2026, 10)))
        assertFalse(source.copy(active = false).isDue(YearMonth.of(2026, 10)))
    }
    @Test fun futureConfirmationDoesNotPromptAnEarlierMonth() {
        assertFalse(source.copy(lastConfirmedMonth = "2027-01").isDue(YearMonth.of(2026, 12)))
    }
    @Test fun monthlyIdentitySurvivesRetriesAndSeparatesSourcesOwnersAndYears() {
        val month = YearMonth.of(2026, 10)
        val id = IncomePolicy.transactionId("owner", "salary", month)
        assertEquals(id, IncomePolicy.transactionId("owner", "salary", month))
        assertNotEquals(id, IncomePolicy.transactionId("other", "salary", month))
        assertNotEquals(id, IncomePolicy.transactionId("owner", "freelance", month))
        assertNotEquals(id, IncomePolicy.transactionId("owner", "salary", month.plusYears(1)))
        assertTrue(IncomePolicy.validId(id))
    }
    @Test fun unsafeIdentifiersAndMalformedPeriodsAreRejected() {
        listOf("", ".", "..", "../owner", "a\\b", "bad\nname", "a".repeat(129)).forEach { assertFalse(it, IncomePolicy.validId(it)) }
        listOf("2026-0", "2026-00", "2026-13", "0000-01", "2026-10-01", "2026-1x").forEach { assertFalse(it, IncomePolicy.validMonth(it)) }
    }
    @Test fun latestConfirmationCannotMoveBackwardsAcrossDevices() {
        assertEquals("2027-01", IncomePolicy.latestMonth("2027-01", "2026-12"))
        assertEquals("2026-10", IncomePolicy.latestMonth(null, "2026-10"))
        assertEquals("2026-10", IncomePolicy.latestMonth("2026-10", null))
        assertNull(IncomePolicy.latestMonth(null, null))
    }
    @Test fun validationRejectsBadNamesAndAmountsAndPreservesMinorUnitPrecision() {
        listOf(source.copy(name = "  "), source.copy(name = "a".repeat(101)), source.copy(amountMinor = 0), source.copy(amountMinor = -1)).forEach {
            assertThrows(IllegalArgumentException::class.java) { IncomePolicy.validate(it) }
        }
        IncomePolicy.validate(source.copy(amountMinor = Long.MAX_VALUE))
    }
    @Test fun localAndRemoteMappingsPreserveConfirmationAndExcludeOutboxFields() {
        val paid = source.copy(lastConfirmedMonth = "2026-10", pendingSync = true)
        assertEquals(paid, paid.toEntity().toDomain())
        val map = paid.toRemoteMap()
        assertFalse(map.containsKey("pendingSync"))
        assertEquals(paid.copy(pendingSync = false), map.toIncomeSource("salary", "owner"))
    }
    @Test fun remoteParsingRejectsWrongOwnerMalformedMoneyAndMonths() {
        val data = source.toRemoteMap()
        assertNull(data.toIncomeSource("salary", "other"))
        assertNull(data.toIncomeSource("other-id", "owner"))
        assertNull((data + ("amountMinor" to 1.25)).toIncomeSource("salary", "owner"))
        assertNull((data + ("lastConfirmedMonth" to "2026-13")).toIncomeSource("salary", "owner"))
        assertNull((data - "lastConfirmedMonth").toIncomeSource("salary", "owner"))
    }
}
