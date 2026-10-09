package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.TransactionRepository
import javax.inject.Inject

class AddTransactionUseCase @Inject constructor(private val repository: TransactionRepository) {
    suspend operator fun invoke(transaction: Transaction): Result<Unit> {
        if (transaction.amountMinor <= 0) {
            return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        }
        val normalized = transaction.copy(
            category = transaction.category.trim().ifBlank { "General" },
            note = transaction.note.trim()
        )
        if (normalized.category.length > 100 || normalized.note.length > 2000) {
            return Result.failure(IllegalArgumentException("Category or note is too long"))
        }
        return repository.addTransaction(normalized)
    }
}
