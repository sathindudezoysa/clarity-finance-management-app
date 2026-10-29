package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionProofRepository
import javax.inject.Inject

class ObserveTransactionProofsUseCase @Inject constructor(private val repository: TransactionProofRepository) {
    operator fun invoke(id: String) = repository.observeProofs(id)
    fun observeAttachedTransactionIds() = repository.observeAttachedTransactionIds()
}
