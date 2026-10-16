package com.example.clarity.domain.model

data class TransactionProof(
    val id: String,
    val transactionId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val verifiedAmountMinor: Long? = null,
    val pendingUpload: Boolean = true
) {
    fun verifies(amountMinor: Long): Boolean = amountMinor > 0 && verifiedAmountMinor == amountMinor
}

data class ProofOpenFile(val uri: String, val mimeType: String)

object ProofPolicy {
    const val MAX_BYTES = 10L * 1024 * 1024
    const val MAX_ATTACHMENTS = 10
    val mimeTypes = setOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif", "application/pdf")

    fun requireId(id: String) {
        require(id.isNotBlank() && id.length <= 128 && id != "." && id != ".." &&
            id.none { it == '/' || it == '\\' || it.isISOControl() }) { "Invalid proof or transaction ID" }
    }

    fun validate(mimeType: String, sizeBytes: Long) {
        require(mimeType in mimeTypes) { "Choose a photo or PDF" }
        require(sizeBytes in 1..MAX_BYTES) { "Proofs must be between 1 byte and 10 MB" }
    }
}
