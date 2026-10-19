package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionProofRepository
import javax.inject.Inject

class OpenTransactionProofUseCase @Inject constructor(private val repository: TransactionProofRepository) {
    suspend operator fun invoke(id: String, proofId: String) = repository.open(id, proofId)
}
