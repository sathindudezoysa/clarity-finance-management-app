package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.repository.TransactionTagRepository
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class GetTransactionTagsUseCase @Inject constructor(private val repository: TransactionTagRepository) {
    operator fun invoke() = repository.observeTags().map { saved ->
        (TransactionTags.predefined + saved.map { it.trim() }
            .filter { it.isNotEmpty() && it.length <= 100 }
            .sortedWith(String.CASE_INSENSITIVE_ORDER))
            .distinctBy { TransactionTags.key(it) }
    }
}
