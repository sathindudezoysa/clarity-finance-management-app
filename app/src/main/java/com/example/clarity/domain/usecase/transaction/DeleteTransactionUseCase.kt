package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionRepository
import javax.inject.Inject

class DeleteTransactionUseCase @Inject constructor(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: String): Result<Unit> = repository.deleteTransaction(id)
}
