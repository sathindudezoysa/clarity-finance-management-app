package com.example.clarity.domain.repository

import com.example.clarity.domain.model.ProofOpenFile
import com.example.clarity.domain.model.TransactionProof
import kotlinx.coroutines.flow.Flow

interface TransactionProofRepository {
    fun observeProofs(transactionId: String): Flow<List<TransactionProof>>
    suspend fun attach(transactionId: String, contentUri: String): Result<Unit>
    suspend fun open(transactionId: String, proofId: String): Result<ProofOpenFile>
    suspend fun setVerified(transactionId: String, proofId: String, amountMinor: Long, verified: Boolean): Result<Unit>
    suspend fun removeUnavailable(transactionId: String, proofId: String): Result<Unit>
    suspend fun refresh(transactionId: String): Result<Unit>
}
