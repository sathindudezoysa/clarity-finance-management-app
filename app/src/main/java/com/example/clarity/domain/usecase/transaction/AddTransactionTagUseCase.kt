package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.TransactionTagRepository
import javax.inject.Inject

class AddTransactionTagUseCase @Inject constructor(
    private val repository: TransactionTagRepository
) {
    suspend operator fun invoke(name: String, type: TransactionType): Result<Unit> =
        repository.addCustomTag(name, type)
}
