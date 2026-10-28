package com.example.clarity.presentation.transactions.proofs

import com.example.clarity.domain.model.TransactionProof

data class TransactionProofState(
    val proofs: List<TransactionProof> = emptyList(),
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val error: String? = null
)

object ProofMessages {
    const val LOAD_ERROR = "Could not load proofs. Reopen the transaction to try again."
    const val ATTACH_ERROR = "Could not attach this file. Choose an accessible photo or PDF up to 10 MB, with no more than 10 proofs per transaction."
    const val OPEN_ERROR = "Could not open this proof. Check your connection and try again."
    const val VERIFY_ERROR = "Could not update verification. Review the current amount and try again."
    const val SYNC_ERROR = "Could not sync proofs. Local copies are safe. Check your connection and try again."
    const val STORAGE_PLAN_REQUIRED = "Proof is not synced with the cloud because you need to upgrade the Firebase plan to enable Firebase Storage. Saved locally on this device."
    const val VIEWER_ERROR = "No app could open this proof. Install a photo or PDF viewer and try again."
    const val REMOVE_ERROR = "Could not remove this missing proof. Please try again."
}
