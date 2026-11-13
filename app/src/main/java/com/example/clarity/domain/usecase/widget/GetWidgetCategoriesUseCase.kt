package com.example.clarity.domain.usecase.widget

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WidgetCategory
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.repository.TransactionTagRepository
import com.example.clarity.domain.repository.WidgetQuickEntryRepository
import com.example.clarity.domain.util.MoneyUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Returns the user's most used categories for [TransactionType]. Categories are ranked by usage,
 * then filled from the user's saved tags (including custom ones) and finally the defaults.
 */
class GetWidgetCategoriesUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val tags: TransactionTagRepository,
    private val quickEntry: WidgetQuickEntryRepository
) {
    /** Instant, last-known categories for [type] (empty on first use). */
    fun cached(type: TransactionType): List<WidgetCategory> =
        quickEntry.cachedCategories(type).map { WidgetCategory(it) }

    suspend operator fun invoke(type: TransactionType, limit: Int = MAX_CATEGORIES): List<WidgetCategory> =
        coroutineScope {
            // Load both sources in parallel; usage ranking is optional so it only gets a short wait.
            val txJob = async {
                withTimeoutOrNull(TRANSACTION_TIMEOUT_MS) {
                    transactions.observeTransactions().first { it.data != null }.data
                }.orEmpty()
            }
            // The tag flow emits defaults first and the stored tags second, so wait for the second value.
            val tagJob = async {
                withTimeoutOrNull(TAG_TIMEOUT_MS) {
                    tags.observeTagsByType(type).take(2).toList().lastOrNull()
                }.orEmpty()
            }
            val all = txJob.await()
            val names = rank(all, tagJob.await(), type, limit)
            quickEntry.saveCategories(type, names)
            names.map { WidgetCategory(it, quickEntry.lastAmount(type, it) ?: latestAmount(all, type, it)) }
        }

    internal fun rank(all: List<Transaction>, saved: List<String>, type: TransactionType, limit: Int): List<String> {
        val used = all
            .filter { it.type == type && !TransactionTags.isExcludedFromIncomeExpenseSummary(it.category) }
            .groupBy { TransactionTags.key(it.category) }
            .values
            .sortedWith(
                compareByDescending<List<Transaction>> { it.size }
                    .thenByDescending { group -> group.maxOf { it.dateEpochMillis } }
            )
            .map { TransactionTags.normalize(it.first().category) }
        // Defaults that belong only to the other type must never leak into this tab.
        val otherOnly = TransactionTags.defaultsFor(
            if (type == TransactionType.EXPENSE) TransactionType.INCOME else TransactionType.EXPENSE
        ).map { TransactionTags.key(it) }.toSet() - TransactionTags.defaultsFor(type).map { TransactionTags.key(it) }.toSet()
        val usedKeys = used.map { TransactionTags.key(it) }.toSet()
        val savedTags = saved.map { it.trim() }
            .filter {
                it.isNotEmpty() && !TransactionTags.isExcludedFromIncomeExpenseSummary(it) &&
                    (TransactionTags.key(it) !in otherOnly || TransactionTags.key(it) in usedKeys)
            }
        return (used + savedTags + TransactionTags.defaultsFor(type))
            .distinctBy { TransactionTags.key(it) }
            .take(limit)
    }

    private fun latestAmount(all: List<Transaction>, type: TransactionType, category: String): String? {
        val key = TransactionTags.key(category)
        val tx = all.filter { it.type == type && TransactionTags.key(it.category) == key }
            .maxByOrNull { it.dateEpochMillis } ?: return null
        return runCatching { MoneyUtils.formatMinor(tx.amountMinor, tx.currency) }.getOrNull()
    }

    private companion object {
        const val MAX_CATEGORIES = 16
        const val TRANSACTION_TIMEOUT_MS = 2_000L
        const val TAG_TIMEOUT_MS = 4_000L
    }
}
