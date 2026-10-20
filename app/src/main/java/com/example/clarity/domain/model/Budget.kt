package com.example.clarity.domain.model

import java.math.BigInteger
import java.security.MessageDigest
import java.time.Clock
import java.time.YearMonth
import java.time.ZoneId
import com.example.clarity.domain.util.TransactionCalendar

data class Budget(
    val id: String, val userId: String, val month: String, val category: String,
    val limitMinor: Long, val currency: String, val active: Boolean = true,
    val createdAt: Long = 0, val updatedAt: Long = 0, val pendingSync: Boolean = false
)

enum class BudgetThreshold(val level: Int) { NORMAL(0), NEARING(1), REACHED(2), EXCEEDED(3) }

data class BudgetStatus(val budget: Budget, val spent: BigInteger, val uncounted: Int) {
    val remaining: BigInteger get() = budget.limitMinor.toBigInteger() - spent
    val threshold: BudgetThreshold get() = BudgetPolicy.threshold(spent, budget.limitMinor)
}

/** A separate, device-local resolution; never changes the transaction's historical snapshot. */
data class BudgetResolution(val transactionId: String, val transactionVersion: Long, val currency: String, val amountMinor: Long)

object BudgetPolicy {
    const val NEARING_PERCENT = 80
    const val REACHED_PERCENT = 100
    fun currentMonth(clock: Clock, zone: ZoneId = ZoneId.systemDefault()): String = TransactionCalendar.currentMonth(clock, zone)
    fun categoryKey(category: String): String = MessageDigest.getInstance("SHA-256")
        .digest(TransactionTags.key(category).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun id(month: String, category: String) = "${month}_${categoryKey(category)}"
    fun validate(budget: Budget) {
        require(budget.userId.length in 1..128)
        require(budget.month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])")) && YearMonth.parse(budget.month).year > 0)
        require(budget.category == TransactionTags.normalize(budget.category) && budget.category.length <= 100)
        require(budget.id == id(budget.month, budget.category))
        require(budget.limitMinor > 0 && budget.currency in Currencies.supported)
        require(budget.createdAt >= 0 && budget.updatedAt >= budget.createdAt)
    }
    fun threshold(spent: BigInteger, limit: Long): BudgetThreshold {
        require(limit > 0)
        val exactLimit = limit.toBigInteger()
        return when {
            spent > exactLimit -> BudgetThreshold.EXCEEDED
            spent == exactLimit -> BudgetThreshold.REACHED
            spent * REACHED_PERCENT.toBigInteger() >= exactLimit * NEARING_PERCENT.toBigInteger() -> BudgetThreshold.NEARING
            else -> BudgetThreshold.NORMAL
        }
    }
    // A higher delivered/claimed threshold also consumes the obsolete lower threshold.
    fun nextAlert(previousLevel: Int, status: BudgetStatus): Int? =
        status.threshold.level.takeIf { status.budget.active && it > previousLevel }

    fun resolveUpload(proposed: Budget, existing: Budget?): Budget {
        if (existing == null) return proposed.copy(pendingSync = false)
        require(existing.id == proposed.id && existing.userId == proposed.userId)
        // A different recorded currency wins as a complete budget, never as a relabeled limit.
        val selected = if (existing.currency != proposed.currency || existing.updatedAt > proposed.updatedAt ||
            (existing.updatedAt == proposed.updatedAt && (!existing.active || proposed.active))) existing else proposed
        return selected.copy(createdAt = existing.createdAt, currency = existing.currency, pendingSync = false)
    }

    fun eligible(budget: Budget, transaction: Transaction, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val month = YearMonth.parse(budget.month)
        val (start, end) = TransactionCalendar.monthBounds(month, zone)
        return transaction.userId == budget.userId && transaction.type == TransactionType.EXPENSE &&
            TransactionTags.key(transaction.category) == TransactionTags.key(budget.category) &&
            transaction.dateEpochMillis >= start && transaction.dateEpochMillis < end
    }
    /** Input is the repository's non-deleted transaction stream, including unsynced cash expenses. */
    fun evaluate(budget: Budget, transactions: List<Transaction>, resolutions: List<BudgetResolution> = emptyList(),
        zone: ZoneId = ZoneId.systemDefault()): BudgetStatus {
        var total = BigInteger.ZERO
        var uncounted = 0
        val resolved = resolutions.associateBy { Triple(it.transactionId, it.transactionVersion, it.currency) }
        transactions.filter { eligible(budget, it, zone) }.forEach { transaction ->
            val amount = when {
                transaction.currency == budget.currency -> transaction.amountMinor
                !transaction.pendingConversion && transaction.targetCurrency == budget.currency -> transaction.conversion?.amountMinor
                else -> resolved[Triple(transaction.id, transaction.updatedAt, budget.currency)]?.amountMinor
            }
            if (amount == null) uncounted++ else total += amount.toBigInteger()
        }
        return BudgetStatus(budget, total, uncounted)
    }
}
