package com.example.clarity.domain.usecase.transaction

import com.example.clarity.domain.repository.TransactionProofRepository
import javax.inject.Inject

class VerifyReceiptAmountUseCase @Inject constructor(private val repository: TransactionProofRepository) {
    suspend operator fun invoke(id: String, proofId: String, amountMinor: Long, verified: Boolean) =
        repository.setVerified(id, proofId, amountMinor, verified)
}
