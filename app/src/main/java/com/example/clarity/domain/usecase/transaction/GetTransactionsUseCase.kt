package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionRepository
import javax.inject.Inject

class GetTransactionsUseCase @Inject constructor(private val repository: TransactionRepository) {
    operator fun invoke() = repository.observeTransactions()
}
