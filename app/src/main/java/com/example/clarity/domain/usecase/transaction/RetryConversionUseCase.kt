package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionRepository
import javax.inject.Inject

class RetryConversionUseCase @Inject constructor(private val repository: TransactionRepository) {
    suspend operator fun invoke(id: String) = repository.retryConversion(id)
}
