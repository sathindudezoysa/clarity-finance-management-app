package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionRepository
import javax.inject.Inject

class GetTransactionUseCase @Inject constructor(private val repository: TransactionRepository) {
    operator fun invoke(id: String) = repository.observeTransaction(id)
}
