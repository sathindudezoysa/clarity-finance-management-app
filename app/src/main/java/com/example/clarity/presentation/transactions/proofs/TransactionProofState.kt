package com.example.clarity.presentation.transactions.proofs

import com.example.clarity.domain.model.TransactionProof

data class TransactionProofState(
    val proofs: List<TransactionProof> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: Int? = null
)
