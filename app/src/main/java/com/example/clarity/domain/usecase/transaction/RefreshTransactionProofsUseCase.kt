package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionProofRepository
import javax.inject.Inject

class RefreshTransactionProofsUseCase @Inject constructor(private val repository: TransactionProofRepository) {
    suspend operator fun invoke(id: String) = repository.refresh(id)
}
