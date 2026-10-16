package com.example.clarity.data.remote

import com.example.clarity.data.local.entity.TransactionProofEntity
import java.io.File

interface ProofRemoteDataSource {
    suspend fun upload(proof: TransactionProofEntity, file: File)
    suspend fun list(uid: String, transactionId: String): List<TransactionProofEntity>
    suspend fun download(proof: TransactionProofEntity, destination: File)
}
