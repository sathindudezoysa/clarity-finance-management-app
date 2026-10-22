package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.GoalProgressOperation
import com.example.clarity.domain.model.GoalProgressOperationState
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigInteger

class GoalPolicyTest {
    private val todayEpochDay = 20_000L
    private val futureEpochDay = 20_100L
    private val pastEpochDay = 19_900L

    private fun sampleGoal(
        id: String = "goal-1",
        userId: String = "user-1",
        name: String = "Emergency Fund",
        description: String = "Six months of expenses",
        targetMinor: Long = 100_000L,
        savedMinor: Long = 25_000L,
        currency: String = "LKR",
        targetDateEpochDay: Long = futureEpochDay,
        status: GoalStatus = GoalPolicy.deriveWriteStatus(savedMinor, targetMinor)
    ) = FinancialGoal(
        id = id,
        userId = userId,
        name = name,
        description = description,
        targetMinor = targetMinor,
        savedMinor = savedMinor,
        currency = currency,
        targetDateEpochDay = targetDateEpochDay,
        status = status,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        pendingSync = false
    )

    private fun sampleAccount(
        id: String = "acc-1",
        userId: String = "user-1",
        name: String = "Main Savings",
        bankName: String = "BOC",
        currency: String = "LKR"
    ) = Account(
        id = id,
        userId = userId,
        name = name,
        bankName = bankName,
        accountType = "SAVINGS",
        currency = currency,
        isDefault = true,
        createdAt = 1_000L,
        updatedAt = 1_000L
    )

    private fun sampleTx(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        accountId: String = "acc-1",
        currency: String = "LKR",
        targetCurrency: String = "LKR",
        pendingConversion: Boolean = false
    ) = Transaction(
        id = id,
        userId = "user-1",
        type = type,
        amountMinor = amountMinor,
        category = "General",
        note = "",
        dateEpochMillis = 1_000L,
        createdAt = 1_000L,
        updatedAt = 1_000L,
        accountId = accountId,
        currency = currency,
        targetCurrency = targetCurrency,
        conversion = if (pendingConversion) null else ConversionSnapshot.identity(amountMinor),
        pendingConversion = pendingConversion
    )

    private fun assertGoalError(expected: GoalError, block: () -> Unit) {
        try {
            block()
            fail("Expected GoalException($expected)")
        } catch (e: GoalException) {
            assertEquals(expected, e.error)
        }
    }

    @Test
    fun validateForCreate_acceptsValidGoalAndDerivesStatus() {
        val active = GoalPolicy.validateForCreate(
            sampleGoal(name = "  New Laptop  ", description = " Work machine ", targetMinor = 250_000L, savedMinor = 0L),
            todayEpochDay
        )
        assertEquals("New Laptop", active.name)
        assertEquals("Work machine", active.description)
        assertEquals(GoalStatus.ACTIVE, active.status)

        val completed = GoalPolicy.validateForCreate(
            sampleGoal(targetMinor = 250_000L, savedMinor = 250_000L),
            todayEpochDay
        )
        assertEquals(GoalStatus.COMPLETED, completed.status)
    }

    @Test
    fun validateForCreate_rejectsInvalidNameAndDescription() {
        assertGoalError(GoalError.NAME_BLANK) {
            GoalPolicy.validateForCreate(sampleGoal(name = "   "), todayEpochDay)
        }
        assertGoalError(GoalError.NAME_TOO_LONG) {
            GoalPolicy.validateForCreate(sampleGoal(name = "a".repeat(101)), todayEpochDay)
        }
        assertGoalError(GoalError.DESCRIPTION_TOO_LONG) {
            GoalPolicy.validateForCreate(sampleGoal(description = "d".repeat(501)), todayEpochDay)
        }
    }

    @Test
    fun validateForCreate_rejectsInvalidTargetSavedAndDates() {
        assertGoalError(GoalError.TARGET_NON_POSITIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = 0L, savedMinor = 0L), todayEpochDay)
        }
        assertGoalError(GoalError.TARGET_NON_POSITIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = -500L, savedMinor = 0L), todayEpochDay)
        }
        assertGoalError(GoalError.SAVED_NEGATIVE) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = 10_000L, savedMinor = -1L), todayEpochDay)
        }
        assertGoalError(GoalError.SAVED_EXCEEDS_TARGET) {
            GoalPolicy.validateForCreate(sampleGoal(targetMinor = 10_000L, savedMinor = 10_001L), todayEpochDay)
        }
        assertGoalError(GoalError.DATE_NOT_IN_FUTURE) {
            GoalPolicy.validateForCreate(sampleGoal(targetDateEpochDay = todayEpochDay), todayEpochDay)
        }
        assertGoalError(GoalError.DATE_NOT_IN_FUTURE) {
            GoalPolicy.validateForCreate(sampleGoal(targetDateEpochDay = pastEpochDay), todayEpochDay)
        }
        assertGoalError(GoalError.INVALID_DATE) {
            GoalPolicy.validateForCreate(sampleGoal(targetDateEpochDay = Long.MAX_VALUE), todayEpochDay)
        }
    }

    @Test
    fun validateForEdit_allowsUnchangedPastDeadlineAndRejectsTargetBelowSavedOrNewPastDeadline() {
        val existingWithPastDeadline = sampleGoal(
            targetMinor = 100_000L,
            savedMinor = 40_000L,
            targetDateEpochDay = pastEpochDay
        )
        val edited = GoalPolicy.validateForEdit(
            existing = existingWithPastDeadline,
            updated = existingWithPastDeadline.copy(
                name = "Updated Name",
                description = "Updated description",
                targetMinor = 80_000L,
                targetDateEpochDay = pastEpochDay
            ),
            todayEpochDay = todayEpochDay
        )
        assertEquals("Updated Name", edited.name)
        assertEquals(80_000L, edited.targetMinor)
        assertEquals(40_000L, edited.savedMinor)

        assertGoalError(GoalError.DATE_NOT_IN_FUTURE) {
            GoalPolicy.validateForEdit(
                existing = existingWithPastDeadline,
                updated = existingWithPastDeadline.copy(targetDateEpochDay = todayEpochDay),
                todayEpochDay = todayEpochDay
            )
        }

        assertGoalError(GoalError.TARGET_BELOW_SAVED) {
            GoalPolicy.validateForEdit(
                existing = existingWithPastDeadline,
                updated = existingWithPastDeadline.copy(targetMinor = 39_999L),
                todayEpochDay = todayEpochDay
            )
        }

        val completedByTargetEdit = GoalPolicy.validateForEdit(
            existing = existingWithPastDeadline,
            updated = existingWithPastDeadline.copy(targetMinor = 40_000L),
            todayEpochDay = todayEpochDay
        )
        assertEquals(GoalStatus.COMPLETED, completedByTargetEdit.status)
    }

    @Test
    fun applyOperation_handlesAddAndSetAndStatusTransitionsAndOverflow() {
        val goal = sampleGoal(targetMinor = 100_000L, savedMinor = 25_000L)

        val afterAdd = GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, 25_000L)
        assertEquals(50_000L, afterAdd.savedMinor)
        assertEquals(GoalStatus.ACTIVE, afterAdd.status)

        val completed = GoalPolicy.applyOperation(afterAdd, GoalProgressOperationType.ADD, 50_000L)
        assertEquals(100_000L, completed.savedMinor)
        assertEquals(GoalStatus.COMPLETED, completed.status)

        assertGoalError(GoalError.CONTRIBUTION_NON_POSITIVE) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, 0L)
        }
        assertGoalError(GoalError.CONTRIBUTION_NON_POSITIVE) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, -100L)
        }

        assertGoalError(GoalError.EXCEEDS_TARGET) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.ADD, 75_001L)
        }

        val hugeGoal = sampleGoal(targetMinor = Long.MAX_VALUE, savedMinor = Long.MAX_VALUE - 10L)
        assertGoalError(GoalError.EXCEEDS_TARGET) {
            GoalPolicy.applyOperation(hugeGoal, GoalProgressOperationType.ADD, 20L)
        }

        val setZero = GoalPolicy.applyOperation(completed, GoalProgressOperationType.SET, 0L)
        assertEquals(0L, setZero.savedMinor)
        assertEquals(GoalStatus.ACTIVE, setZero.status)

        val setMid = GoalPolicy.applyOperation(setZero, GoalProgressOperationType.SET, 60_000L)
        assertEquals(60_000L, setMid.savedMinor)
        assertEquals(GoalStatus.ACTIVE, setMid.status)

        val setTarget = GoalPolicy.applyOperation(setMid, GoalProgressOperationType.SET, 100_000L)
        assertEquals(100_000L, setTarget.savedMinor)
        assertEquals(GoalStatus.COMPLETED, setTarget.status)

        assertGoalError(GoalError.SAVED_NEGATIVE) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.SET, -1L)
        }
        assertGoalError(GoalError.EXCEEDS_TARGET) {
            GoalPolicy.applyOperation(goal, GoalProgressOperationType.SET, 100_001L)
        }
    }

    @Test
    fun reservedFromAccount_andAvailableBalanceForAccount_respectStateTypeAndAccount() {
        val transactions = listOf(
            sampleTx("tx-inc-1", TransactionType.INCOME, 100_000L, accountId = "acc-1"),
            sampleTx("tx-exp-1", TransactionType.EXPENSE, 20_000L, accountId = "acc-1"),
            sampleTx("tx-inc-2", TransactionType.INCOME, 50_000L, accountId = "acc-2")
        )
        val operations = listOf(
            // Counted on acc-1 (PENDING ADD)
            GoalProgressOperation(
                id = "op-1",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 15_000L,
                sourceAccountId = "acc-1",
                createdAt = 1_100L,
                state = GoalProgressOperationState.PENDING
            ),
            // Counted on acc-1 (APPLIED ADD)
            GoalProgressOperation(
                id = "op-2",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 10_000L,
                sourceAccountId = "acc-1",
                createdAt = 1_200L,
                state = GoalProgressOperationState.APPLIED
            ),
            // NOT counted: REJECTED ADD restores available balance
            GoalProgressOperation(
                id = "op-3",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 30_000L,
                sourceAccountId = "acc-1",
                createdAt = 1_300L,
                state = GoalProgressOperationState.REJECTED
            ),
            // NOT counted: SET operation never touches an account
            GoalProgressOperation(
                id = "op-4",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.SET,
                amountMinor = 40_000L,
                sourceAccountId = null,
                createdAt = 1_400L,
                state = GoalProgressOperationState.APPLIED
            ),
            // NOT counted on acc-1: legacy ADD operation with null sourceAccountId
            GoalProgressOperation(
                id = "op-5",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 5_000L,
                sourceAccountId = null,
                createdAt = 1_500L,
                state = GoalProgressOperationState.APPLIED
            ),
            // Counted on acc-2, not acc-1
            GoalProgressOperation(
                id = "op-6",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 12_000L,
                sourceAccountId = "acc-2",
                createdAt = 1_600L,
                state = GoalProgressOperationState.PENDING
            )
        )

        // acc-1: net tx = 100_000 - 20_000 = 80_000; reserved = 15_000 + 10_000 = 25_000; available = 55_000
        assertEquals(BigInteger.valueOf(25_000L), GoalPolicy.reservedFromAccount("acc-1", operations))
        assertEquals(BigInteger.valueOf(80_000L), GoalPolicy.netTransactionsForAccount("acc-1", transactions))
        assertEquals(
            BigInteger.valueOf(55_000L),
            GoalPolicy.availableBalanceForAccount("acc-1", transactions, operations)
        )

        // acc-2: net tx = 50_000; reserved = 12_000; available = 38_000
        assertEquals(BigInteger.valueOf(12_000L), GoalPolicy.reservedFromAccount("acc-2", operations))
        assertEquals(
            BigInteger.valueOf(38_000L),
            GoalPolicy.availableBalanceForAccount("acc-2", transactions, operations)
        )
    }

    @Test
    fun validateContributionWithAccount_enforcesExactValidationOrderAndBoundaries() {
        val goal = sampleGoal(targetMinor = 100_000L, savedMinor = 40_000L, currency = "LKR") // remaining = 60_000
        val account = sampleAccount(id = "acc-1", userId = "user-1", currency = "LKR")

        // 1. Signed in check first
        assertGoalError(GoalError.SIGN_IN_REQUIRED) {
            GoalPolicy.validateContributionWithAccount(
                uid = "",
                goal = null,
                sourceAccountId = null,
                account = null,
                accountCurrencyUnitsValid = false,
                availableBalanceMinor = BigInteger.ZERO,
                amountMinor = -1L
            )
        }

        // 2. Goal exists check second
        assertGoalError(GoalError.NOT_FOUND) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = null,
                sourceAccountId = null,
                account = null,
                accountCurrencyUnitsValid = false,
                availableBalanceMinor = BigInteger.ZERO,
                amountMinor = -1L
            )
        }

        // 3. Account selected / valid / exists / owned check third
        assertGoalError(GoalError.ACCOUNT_REQUIRED) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "   ",
                account = null,
                accountCurrencyUnitsValid = false,
                availableBalanceMinor = BigInteger.ZERO,
                amountMinor = -1L
            )
        }
        assertGoalError(GoalError.ACCOUNT_NOT_FOUND) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "acc-missing",
                account = null,
                accountCurrencyUnitsValid = false,
                availableBalanceMinor = BigInteger.ZERO,
                amountMinor = -1L
            )
        }
        assertGoalError(GoalError.ACCOUNT_NOT_FOUND) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "acc-1",
                account = account.copy(userId = "other-user"),
                accountCurrencyUnitsValid = true,
                availableBalanceMinor = BigInteger.valueOf(500_000L),
                amountMinor = 10_000L
            )
        }

        // 4. Currency match (and consistent transaction units) fourth
        assertGoalError(GoalError.ACCOUNT_CURRENCY_MISMATCH) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "acc-1",
                account = account.copy(currency = "USD"),
                accountCurrencyUnitsValid = true,
                availableBalanceMinor = BigInteger.ZERO,
                amountMinor = -1L
            )
        }
        assertGoalError(GoalError.ACCOUNT_CURRENCY_MISMATCH) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "acc-1",
                account = account,
                accountCurrencyUnitsValid = false,
                availableBalanceMinor = BigInteger.valueOf(500_000L),
                amountMinor = 10_000L
            )
        }

        // 5. Amount > 0 fifth
        assertGoalError(GoalError.CONTRIBUTION_NON_POSITIVE) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "acc-1",
                account = account,
                accountCurrencyUnitsValid = true,
                availableBalanceMinor = BigInteger.ZERO,
                amountMinor = 0L
            )
        }

        // 6. Amount <= goal remaining sixth (checked before account balance!)
        assertGoalError(GoalError.EXCEEDS_TARGET) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "acc-1",
                account = account,
                accountCurrencyUnitsValid = true,
                availableBalanceMinor = BigInteger.valueOf(10_000L),
                amountMinor = 60_001L // exceeds remaining 60_000
            )
        }

        // 7. Amount <= account available balance seventh
        assertGoalError(GoalError.INSUFFICIENT_ACCOUNT_BALANCE) {
            GoalPolicy.validateContributionWithAccount(
                uid = "user-1",
                goal = goal,
                sourceAccountId = "acc-1",
                account = account,
                accountCurrencyUnitsValid = true,
                availableBalanceMinor = BigInteger.valueOf(50_000L),
                amountMinor = 50_001L
            )
        }

        // Exact boundary: amount == availableBalanceMinor (50_000) succeeds
        val exactBalanceResult = GoalPolicy.validateContributionWithAccount(
            uid = "user-1",
            goal = goal,
            sourceAccountId = "acc-1",
            account = account,
            accountCurrencyUnitsValid = true,
            availableBalanceMinor = BigInteger.valueOf(50_000L),
            amountMinor = 50_000L
        )
        assertEquals(90_000L, exactBalanceResult.savedMinor)
        assertEquals(GoalStatus.ACTIVE, exactBalanceResult.status)

        // Exact boundary: amount == remaining (60_000) with sufficient balance completes goal
        val exactRemainingResult = GoalPolicy.validateContributionWithAccount(
            uid = "user-1",
            goal = goal,
            sourceAccountId = "acc-1",
            account = account,
            accountCurrencyUnitsValid = true,
            availableBalanceMinor = BigInteger.valueOf(60_000L),
            amountMinor = 60_000L
        )
        assertEquals(100_000L, exactRemainingResult.savedMinor)
        assertEquals(GoalStatus.COMPLETED, exactRemainingResult.status)

        // Preview calculation check
        val preview = GoalPolicy.previewContribution(
            goal = goal,
            accountAvailableMinor = BigInteger.valueOf(50_000L),
            amountMinor = 15_000L
        )
        assertNotNull(preview)
        assertEquals(BigInteger.valueOf(50_000L), preview!!.accountBeforeMinor)
        assertEquals(BigInteger.valueOf(35_000L), preview.accountAfterMinor)
        assertEquals(40_000L, preview.goalBeforeMinor)
        assertEquals(55_000L, preview.goalAfterMinor)
        assertEquals(45_000L, preview.remainingAfterMinor)
        assertNull(GoalPolicy.previewContribution(goal, BigInteger.valueOf(50_000L), 60_001L))
    }

    @Test
    fun progressAndRemainingCalculations_floorPercentSo99Point9NeverShows100() {
        assertEquals(0, GoalPolicy.progressPercent(0L, 1_000L))
        assertEquals(0f, GoalPolicy.progressFraction(0L, 1_000L), 0.0001f)
        assertEquals(1_000L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 0L))

        assertEquals(50, GoalPolicy.progressPercent(500L, 1_000L))
        assertEquals(0.5f, GoalPolicy.progressFraction(500L, 1_000L), 0.0001f)
        assertEquals(500L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 500L))

        assertEquals(99, GoalPolicy.progressPercent(999L, 1_000L))
        assertTrue(GoalPolicy.progressFraction(999L, 1_000L) < 1f)
        assertEquals(1L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 999L))

        assertEquals(100, GoalPolicy.progressPercent(1_000L, 1_000L))
        assertEquals(1f, GoalPolicy.progressFraction(1_000L, 1_000L), 0.0001f)
        assertEquals(0L, GoalPolicy.remainingMinor(targetMinor = 1_000L, savedMinor = 1_000L))

        val overdueActive = sampleGoal(savedMinor = 50_000L, targetDateEpochDay = pastEpochDay, status = GoalStatus.ACTIVE)
        val pastCompleted = sampleGoal(savedMinor = 100_000L, targetDateEpochDay = pastEpochDay, status = GoalStatus.COMPLETED)
        assertTrue(GoalPolicy.isOverdue(overdueActive, todayEpochDay))
        assertFalse(GoalPolicy.isOverdue(pastCompleted, todayEpochDay))
    }

    @Test
    fun parseAmountMinor_rejectsSignsInvalidDecimalsAndOverflow() {
        assertEquals(123_45L, GoalPolicy.parseAmountMinor("123.45", "LKR").getOrThrow())
        assertEquals(123_00L, GoalPolicy.parseAmountMinor("123", "LKR").getOrThrow())
        assertEquals(0L, GoalPolicy.parseAmountMinor("0", "LKR").getOrThrow())
        assertEquals(500L, GoalPolicy.parseAmountMinor("500", "JPY").getOrThrow())
        assertEquals(1_250L, GoalPolicy.parseAmountMinor("1.25", "KWD").getOrThrow())

        assertTrue(GoalPolicy.parseAmountMinor("", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("   ", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("+100", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("-100", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("12.345", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("12.3", "JPY").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("abc", "LKR").isFailure)
        assertTrue(GoalPolicy.parseAmountMinor("99999999999999999999999", "LKR").isFailure)
    }

    @Test
    fun replayPendingOperations_replaysInOrderAndDistinctlyHandlesAddVersusSet() {
        val ops = listOf(
            GoalProgressOperation(
                id = "op-1",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 15_000L,
                sourceAccountId = "acc-1",
                createdAt = 1_100L,
                state = GoalProgressOperationState.PENDING
            ),
            GoalProgressOperation(
                id = "op-2",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.SET,
                amountMinor = 10_000L,
                createdAt = 1_200L,
                state = GoalProgressOperationState.PENDING
            ),
            GoalProgressOperation(
                id = "op-3",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 90_000L,
                sourceAccountId = "acc-1",
                createdAt = 1_300L,
                state = GoalProgressOperationState.PENDING
            ),
            GoalProgressOperation(
                id = "op-applied",
                userId = "user-1",
                goalId = "goal-1",
                type = GoalProgressOperationType.ADD,
                amountMinor = 50_000L,
                sourceAccountId = "acc-1",
                createdAt = 1_050L,
                state = GoalProgressOperationState.APPLIED
            )
        )
        val replayed = GoalPolicy.replayPendingOperations(
            baseSavedMinor = 20_000L,
            targetMinor = 100_000L,
            pendingOperations = ops
        )
        assertEquals(100_000L, replayed)
    }
}
