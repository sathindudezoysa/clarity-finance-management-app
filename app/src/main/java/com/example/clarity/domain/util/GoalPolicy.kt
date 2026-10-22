package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.GoalProgressOperation
import com.example.clarity.domain.model.GoalProgressOperationState
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId

data class GoalContributionPreview(
    val accountBeforeMinor: BigInteger,
    val accountAfterMinor: BigInteger,
    val goalBeforeMinor: Long,
    val goalAfterMinor: Long,
    val remainingAfterMinor: Long
)

object GoalPolicy {
    const val MAX_NAME_LENGTH = 100
    const val MAX_DESCRIPTION_LENGTH = 500
    private const val MIN_EPOCH_DAY = -365243219162L
    private const val MAX_EPOCH_DAY = 365241780471L

    fun validId(id: String): Boolean =
        id.isNotBlank() &&
            id.length <= 128 &&
            id != "." &&
            id != ".." &&
            id.none { it == '/' || it == '\\' || it.isISOControl() }

    fun validEpochDay(epochDay: Long): Boolean =
        epochDay in MIN_EPOCH_DAY..MAX_EPOCH_DAY &&
            runCatching { LocalDate.ofEpochDay(epochDay) }.isSuccess

    fun todayEpochDay(clock: Clock, zone: ZoneId = ZoneId.systemDefault()): Long =
        TransactionCalendar.dateAt(clock.millis(), zone).toEpochDay()

    fun parseAmountMinor(text: String, currency: String): Result<Long> = runCatching {
        if (currency !in Currencies.supported) {
            throw GoalException(GoalError.INVALID_CURRENCY)
        }
        val digits = Currencies.digits(currency)
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > 40) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        if (trimmed.any { it == '+' || it == '-' }) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        val pattern = if (digits == 0) {
            Regex("[0-9]+")
        } else {
            Regex("[0-9]+(?:\\.[0-9]{1,$digits})?")
        }
        if (!trimmed.matches(pattern)) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        val parsed = try {
            BigDecimal(trimmed).movePointRight(digits).longValueExact()
        } catch (_: ArithmeticException) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        } catch (_: NumberFormatException) {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }
        MoneyUtils.parseMinor(trimmed, currency).getOrElse {
            throw GoalException(GoalError.INVALID_AMOUNT)
        }.also {
            if (it != parsed) throw GoalException(GoalError.INVALID_AMOUNT)
        }
    }

    fun deriveWriteStatus(savedMinor: Long, targetMinor: Long): GoalStatus =
        if (targetMinor > 0L && savedMinor == targetMinor) GoalStatus.COMPLETED else GoalStatus.ACTIVE

    fun remainingMinor(targetMinor: Long, savedMinor: Long): Long {
        if (targetMinor <= 0L || savedMinor >= targetMinor) return 0L
        if (savedMinor <= 0L) return targetMinor
        return BigInteger.valueOf(targetMinor)
            .subtract(BigInteger.valueOf(savedMinor))
            .max(BigInteger.ZERO)
            .longValueExact()
    }

    fun progressPercent(savedMinor: Long, targetMinor: Long): Int {
        if (targetMinor <= 0L || savedMinor <= 0L) return 0
        if (savedMinor >= targetMinor) return 100
        val floored = BigInteger.valueOf(savedMinor)
            .multiply(BigInteger.valueOf(100L))
            .divide(BigInteger.valueOf(targetMinor))
            .toInt()
        return floored.coerceIn(0, 99)
    }

    fun progressFraction(savedMinor: Long, targetMinor: Long): Float {
        if (targetMinor <= 0L || savedMinor <= 0L) return 0f
        if (savedMinor >= targetMinor) return 1f
        return BigDecimal.valueOf(savedMinor)
            .divide(BigDecimal.valueOf(targetMinor), 6, RoundingMode.DOWN)
            .toFloat()
            .coerceIn(0f, 0.9999f)
    }

    fun isOverdue(goal: FinancialGoal, todayEpochDay: Long): Boolean =
        goal.status == GoalStatus.ACTIVE &&
            goal.savedMinor < goal.targetMinor &&
            goal.targetDateEpochDay < todayEpochDay

    /**
     * Shared pure definition of funds reserved from an account by goal contributions:
     * sum of amountMinor of ADD operations with sourceAccountId == accountId in state PENDING or APPLIED.
     * REJECTED operations and legacy operations with null sourceAccountId are excluded.
     */
    fun reservedFromAccount(
        accountId: String,
        operations: Iterable<GoalProgressOperation>
    ): BigInteger {
        if (accountId.isBlank()) return BigInteger.ZERO
        var total = BigInteger.ZERO
        for (op in operations) {
            if (op.type == GoalProgressOperationType.ADD &&
                op.sourceAccountId == accountId &&
                op.amountMinor > 0L &&
                (op.state == GoalProgressOperationState.PENDING || op.state == GoalProgressOperationState.APPLIED)
            ) {
                total = total.add(BigInteger.valueOf(op.amountMinor))
            }
        }
        return total
    }

    fun reservedByAccount(operations: Iterable<GoalProgressOperation>): Map<String, BigInteger> {
        val result = mutableMapOf<String, BigInteger>()
        for (op in operations) {
            val accId = op.sourceAccountId
            if (op.type == GoalProgressOperationType.ADD &&
                !accId.isNullOrBlank() &&
                op.amountMinor > 0L &&
                (op.state == GoalProgressOperationState.PENDING || op.state == GoalProgressOperationState.APPLIED)
            ) {
                val current = result[accId] ?: BigInteger.ZERO
                result[accId] = current.add(BigInteger.valueOf(op.amountMinor))
            }
        }
        return result
    }

    /**
     * Net of active transactions for an account, computed identically to TransactionBalanceGuard.netMinor:
     * INCOME adds conversion.amountMinor; any other type subtracts conversion.amountMinor.
     */
    fun netTransactionsForAccount(
        accountId: String,
        transactions: Iterable<Transaction>
    ): BigInteger {
        var total = BigInteger.ZERO
        for (tx in transactions) {
            if (tx.accountId != accountId) continue
            val converted = tx.conversion?.amountMinor ?: continue
            val amount = BigInteger.valueOf(converted)
            if (tx.type == TransactionType.INCOME) {
                total = total.add(amount)
            } else {
                total = total.subtract(amount)
            }
        }
        return total
    }

    /**
     * Verifies that every active transaction on the account has a resolved conversion snapshot
     * denominated in the account's own currency so that TransactionBalanceGuard's per-account sum
     * is provably in [accountCurrency] minor units without currency conversion.
     */
    fun hasConsistentCurrencyUnits(
        accountCurrency: String,
        accountId: String,
        transactions: Iterable<Transaction>
    ): Boolean {
        if (accountCurrency !in Currencies.supported) return false
        for (tx in transactions) {
            if (tx.accountId != accountId) continue
            if (tx.pendingConversion || tx.conversion == null || tx.targetCurrency != accountCurrency) {
                return false
            }
        }
        return true
    }

    /**
     * Shared pure definition of an account's available balance:
     * net of the account's active transactions minus reservedFromAccount.
     */
    fun availableBalanceForAccount(
        accountId: String,
        transactions: Iterable<Transaction>,
        operations: Iterable<GoalProgressOperation>
    ): BigInteger =
        netTransactionsForAccount(accountId, transactions)
            .subtract(reservedFromAccount(accountId, operations))

    /**
     * Validates a new ADD contribution against the goal and source account in the exact required order:
     * 1. signed in
     * 2. goal exists
     * 3. account selected / exists / owned
     * 4. currency match (and consistent per-account transaction units)
     * 5. amount > 0
     * 6. amount <= goal remaining
     * 7. amount <= account available balance ("Insufficient account balance.")
     */
    fun validateContributionWithAccount(
        uid: String,
        goal: FinancialGoal?,
        sourceAccountId: String?,
        account: Account?,
        accountCurrencyUnitsValid: Boolean,
        availableBalanceMinor: BigInteger,
        amountMinor: Long
    ): FinancialGoal {
        if (uid.isBlank() || (goal != null && goal.userId.isNotEmpty() && goal.userId != uid)) {
            throw GoalException(GoalError.SIGN_IN_REQUIRED)
        }
        if (goal == null) {
            throw GoalException(GoalError.NOT_FOUND)
        }
        if (sourceAccountId.isNullOrBlank()) {
            throw GoalException(GoalError.ACCOUNT_REQUIRED)
        }
        if (!validId(sourceAccountId)) {
            throw GoalException(GoalError.INVALID_ID)
        }
        if (account == null || account.userId != uid || account.id != sourceAccountId) {
            throw GoalException(GoalError.ACCOUNT_NOT_FOUND)
        }
        if (account.currency != goal.currency || !accountCurrencyUnitsValid) {
            throw GoalException(GoalError.ACCOUNT_CURRENCY_MISMATCH)
        }
        val rem = remainingMinor(goal.targetMinor, goal.savedMinor)
        if (amountMinor <= 0L) {
            throw GoalException(GoalError.CONTRIBUTION_NON_POSITIVE, rem, goal.currency)
        }
        val newSavedBig = BigInteger.valueOf(goal.savedMinor).add(BigInteger.valueOf(amountMinor))
        if (newSavedBig > BigInteger.valueOf(goal.targetMinor)) {
            throw GoalException(GoalError.EXCEEDS_TARGET, rem, goal.currency)
        }
        if (BigInteger.valueOf(amountMinor) > availableBalanceMinor) {
            throw GoalException(GoalError.INSUFFICIENT_ACCOUNT_BALANCE, rem, goal.currency)
        }
        val newSaved = newSavedBig.longValueExact()
        return goal.copy(
            savedMinor = newSaved,
            status = deriveWriteStatus(newSaved, goal.targetMinor)
        )
    }

    fun previewContribution(
        goal: FinancialGoal,
        accountAvailableMinor: BigInteger,
        amountMinor: Long
    ): GoalContributionPreview? {
        if (amountMinor <= 0L) return null
        val newGoalSaved = BigInteger.valueOf(goal.savedMinor).add(BigInteger.valueOf(amountMinor))
        if (newGoalSaved > BigInteger.valueOf(goal.targetMinor)) return null
        val accountAfter = accountAvailableMinor.subtract(BigInteger.valueOf(amountMinor))
        val goalAfterLong = newGoalSaved.longValueExact()
        return GoalContributionPreview(
            accountBeforeMinor = accountAvailableMinor,
            accountAfterMinor = accountAfter,
            goalBeforeMinor = goal.savedMinor,
            goalAfterMinor = goalAfterLong,
            remainingAfterMinor = remainingMinor(goal.targetMinor, goalAfterLong)
        )
    }

    fun validateForCreate(goal: FinancialGoal, todayEpochDay: Long): FinancialGoal {
        val normalized = normalizeAndValidateCommon(goal, isEdit = false)
        if (normalized.targetDateEpochDay <= todayEpochDay) {
            throw GoalException(GoalError.DATE_NOT_IN_FUTURE)
        }
        return normalized.copy(
            status = deriveWriteStatus(normalized.savedMinor, normalized.targetMinor)
        )
    }

    fun validateForEdit(
        existing: FinancialGoal,
        updated: FinancialGoal,
        todayEpochDay: Long
    ): FinancialGoal {
        if (existing.id != updated.id ||
            (updated.userId.isNotEmpty() && existing.userId != updated.userId) ||
            existing.currency != updated.currency ||
            existing.createdAt != updated.createdAt
        ) {
            throw GoalException(GoalError.IMMUTABLE_FIELD_CHANGED)
        }
        val merged = updated.copy(
            id = existing.id,
            userId = existing.userId,
            currency = existing.currency,
            savedMinor = existing.savedMinor,
            createdAt = existing.createdAt
        )
        val normalized = normalizeAndValidateCommon(merged, isEdit = true)
        if (normalized.targetDateEpochDay != existing.targetDateEpochDay &&
            normalized.targetDateEpochDay <= todayEpochDay
        ) {
            throw GoalException(GoalError.DATE_NOT_IN_FUTURE)
        }
        return normalized.copy(
            status = deriveWriteStatus(normalized.savedMinor, normalized.targetMinor)
        )
    }

    fun validateStoredOrRemote(goal: FinancialGoal) {
        if (!validId(goal.id) || (goal.userId.isNotEmpty() && !validId(goal.userId))) {
            throw GoalException(GoalError.INVALID_ID)
        }
        val trimmedName = goal.name.trim()
        if (trimmedName.isEmpty()) throw GoalException(GoalError.NAME_BLANK)
        if (goal.name.length > MAX_NAME_LENGTH || goal.name.any { it.isISOControl() }) {
            throw GoalException(GoalError.NAME_TOO_LONG)
        }
        if (goal.description.length > MAX_DESCRIPTION_LENGTH) {
            throw GoalException(GoalError.DESCRIPTION_TOO_LONG)
        }
        if (goal.currency !in Currencies.supported) {
            throw GoalException(GoalError.INVALID_CURRENCY)
        }
        if (goal.targetMinor <= 0L) throw GoalException(GoalError.TARGET_NON_POSITIVE)
        if (goal.savedMinor < 0L) throw GoalException(GoalError.SAVED_NEGATIVE)
        if (goal.savedMinor > goal.targetMinor) throw GoalException(GoalError.SAVED_EXCEEDS_TARGET)
        if (!validEpochDay(goal.targetDateEpochDay)) throw GoalException(GoalError.INVALID_DATE)
        if (goal.createdAt < 0L || goal.updatedAt < goal.createdAt) {
            throw GoalException(GoalError.INVALID_DATE)
        }
        if (goal.status != GoalStatus.ARCHIVED) {
            val expected = deriveWriteStatus(goal.savedMinor, goal.targetMinor)
            if (goal.status != expected) {
                throw GoalException(GoalError.STATUS_MISMATCH)
            }
        }
    }

    fun validateOperation(operation: GoalProgressOperation, requireSourceAccountOnAdd: Boolean = false) {
        if (!validId(operation.id) || !validId(operation.goalId)) {
            throw GoalException(GoalError.INVALID_ID)
        }
        if (operation.userId.isNotEmpty() && !validId(operation.userId)) {
            throw GoalException(GoalError.INVALID_ID)
        }
        if (operation.createdAt < 0L) {
            throw GoalException(GoalError.INVALID_DATE)
        }
        when (operation.type) {
            GoalProgressOperationType.ADD -> {
                if (operation.amountMinor <= 0L) {
                    throw GoalException(GoalError.CONTRIBUTION_NON_POSITIVE)
                }
                val sourceId = operation.sourceAccountId
                if (requireSourceAccountOnAdd && sourceId.isNullOrBlank()) {
                    throw GoalException(GoalError.ACCOUNT_REQUIRED)
                }
                if (sourceId != null && !validId(sourceId)) {
                    throw GoalException(GoalError.INVALID_ID)
                }
            }
            GoalProgressOperationType.SET -> {
                if (operation.amountMinor < 0L) {
                    throw GoalException(GoalError.SAVED_NEGATIVE)
                }
                if (operation.sourceAccountId != null) {
                    throw GoalException(GoalError.INVALID_ID)
                }
            }
        }
    }

    fun applyOperation(
        goal: FinancialGoal,
        type: GoalProgressOperationType,
        amountMinor: Long
    ): FinancialGoal {
        val rem = remainingMinor(goal.targetMinor, goal.savedMinor)
        val newSaved: Long = when (type) {
            GoalProgressOperationType.ADD -> {
                if (amountMinor <= 0L) {
                    throw GoalException(GoalError.CONTRIBUTION_NON_POSITIVE, rem, goal.currency)
                }
                val sum = BigInteger.valueOf(goal.savedMinor).add(BigInteger.valueOf(amountMinor))
                if (sum > BigInteger.valueOf(goal.targetMinor)) {
                    throw GoalException(GoalError.EXCEEDS_TARGET, rem, goal.currency)
                }
                sum.longValueExact()
            }
            GoalProgressOperationType.SET -> {
                if (amountMinor < 0L) {
                    throw GoalException(GoalError.SAVED_NEGATIVE, rem, goal.currency)
                }
                if (amountMinor > goal.targetMinor) {
                    throw GoalException(GoalError.EXCEEDS_TARGET, rem, goal.currency)
                }
                amountMinor
            }
        }
        return goal.copy(
            savedMinor = newSaved,
            status = deriveWriteStatus(newSaved, goal.targetMinor)
        )
    }

    fun replayPendingOperations(
        baseSavedMinor: Long,
        targetMinor: Long,
        pendingOperations: List<GoalProgressOperation>
    ): Long {
        var currentSaved = baseSavedMinor.coerceIn(0L, targetMinor)
        val sorted = pendingOperations
            .filter { it.state == GoalProgressOperationState.PENDING }
            .sortedWith(compareBy<GoalProgressOperation> { it.createdAt }.thenBy { it.id })
        for (op in sorted) {
            val candidate: BigInteger = when (op.type) {
                GoalProgressOperationType.ADD -> {
                    if (op.amountMinor <= 0L) continue
                    BigInteger.valueOf(currentSaved).add(BigInteger.valueOf(op.amountMinor))
                }
                GoalProgressOperationType.SET -> {
                    if (op.amountMinor < 0L) continue
                    BigInteger.valueOf(op.amountMinor)
                }
            }
            if (candidate >= BigInteger.ZERO && candidate <= BigInteger.valueOf(targetMinor)) {
                currentSaved = candidate.longValueExact()
            }
        }
        return currentSaved
    }

    private fun normalizeAndValidateCommon(goal: FinancialGoal, isEdit: Boolean): FinancialGoal {
        if (!validId(goal.id) || (goal.userId.isNotEmpty() && !validId(goal.userId))) {
            throw GoalException(GoalError.INVALID_ID)
        }
        val trimmedName = goal.name.trim()
        if (trimmedName.isEmpty()) {
            throw GoalException(GoalError.NAME_BLANK)
        }
        if (trimmedName.length > MAX_NAME_LENGTH || trimmedName.any { it.isISOControl() }) {
            throw GoalException(GoalError.NAME_TOO_LONG)
        }
        val trimmedDescription = goal.description.trim()
        if (trimmedDescription.length > MAX_DESCRIPTION_LENGTH) {
            throw GoalException(GoalError.DESCRIPTION_TOO_LONG)
        }
        if (goal.currency !in Currencies.supported) {
            throw GoalException(GoalError.INVALID_CURRENCY)
        }
        if (goal.targetMinor <= 0L) {
            throw GoalException(GoalError.TARGET_NON_POSITIVE)
        }
        if (goal.savedMinor < 0L) {
            throw GoalException(GoalError.SAVED_NEGATIVE)
        }
        if (goal.savedMinor > goal.targetMinor) {
            throw GoalException(
                if (isEdit) GoalError.TARGET_BELOW_SAVED else GoalError.SAVED_EXCEEDS_TARGET,
                remainingMinor = remainingMinor(goal.targetMinor, goal.savedMinor),
                currency = goal.currency
            )
        }
        if (!validEpochDay(goal.targetDateEpochDay)) {
            throw GoalException(GoalError.INVALID_DATE)
        }
        return goal.copy(
            name = trimmedName,
            description = trimmedDescription
        )
    }
}
