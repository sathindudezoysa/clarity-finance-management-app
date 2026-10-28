package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.TransactionTagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTransactionTagsUseCase @Inject constructor(private val repository: TransactionTagRepository) {
    operator fun invoke(): Flow<List<String>> = repository.observeTags().map { saved ->
        (TransactionTags.predefined + saved.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 100 }
            .sortedWith(String.CASE_INSENSITIVE_ORDER))
            .distinctBy { TransactionTags.key(it) }
    }

    operator fun invoke(type: TransactionType): Flow<List<String>> =
        repository.observeTagsByType(type).map { saved ->
            val defaults = TransactionTags.defaultsFor(type)
            val otherTypeOnlyDefaults = when (type) {
                TransactionType.EXPENSE -> TransactionTags.defaultIncomeCategories
                TransactionType.INCOME -> TransactionTags.defaultExpenseCategories
            }.filterNot { other -> defaults.any { TransactionTags.key(it) == TransactionTags.key(other) } }
                .map { TransactionTags.key(it) }
                .toSet()

            val customOrMatching = saved.map { it.trim() }
                .filter { it.isNotEmpty() && it.length <= 100 && TransactionTags.key(it) !in otherTypeOnlyDefaults }
                .sortedWith(String.CASE_INSENSITIVE_ORDER)

            (defaults + customOrMatching).distinctBy { TransactionTags.key(it) }
        }
}
