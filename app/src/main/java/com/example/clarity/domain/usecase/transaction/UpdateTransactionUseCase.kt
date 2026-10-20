package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.repository.AccountRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class UpdateTransactionUseCase @Inject constructor(
    private val repository: TransactionRepository,
    private val accountRepository: AccountRepository
) {
    suspend operator fun invoke(transaction: Transaction): Result<Unit> {
        if (transaction.amountMinor <= 0) {
            return Result.failure(IllegalArgumentException("Amount must be greater than zero"))
        }
        val normalized = transaction.copy(
            category = com.example.clarity.domain.model.TransactionTags.normalize(transaction.category),
            note = transaction.note.trim()
        )
        if (normalized.category.length > 100 || normalized.note.length > 2000) {
            return Result.failure(IllegalArgumentException("Category or note is too long"))
        }
        val accountId = normalized.accountId
        if (accountId.isNullOrBlank()) {
            return Result.failure(TransactionException(TransactionError.ACCOUNT_REQUIRED, "Select a bank account"))
        }
        val account = accountRepository.getAccount(accountId).getOrElse { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        if (account == null) {
            return Result.failure(TransactionException(TransactionError.ACCOUNT_NOT_FOUND, "Bank account not found"))
        }
        return repository.updateTransaction(normalized)
    }
}
