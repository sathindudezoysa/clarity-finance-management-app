package com.example.clarity

import com.example.clarity.data.local.ProofFileSignature
import com.example.clarity.data.local.entity.TransactionProofEntity
import com.example.clarity.data.mapper.toDomain
import com.example.clarity.domain.model.ProofPolicy
import com.example.clarity.domain.model.TransactionProof
import org.junit.Assert.*
import org.junit.Test

class TransactionProofTest {
    private val proof = TransactionProof("proof", "transaction", "Receipt.pdf", "application/pdf", 100, 1)

    @Test fun unverifiedReceiptNeverShowsVerification() {
        assertFalse(proof.verifies(1250))
        assertFalse(proof.verifies(0))
    }
    @Test fun verificationRequiresTheCurrentPositiveAmount() {
        val verified = proof.copy(verifiedAmountMinor = 1250)
        assertTrue(verified.verifies(1250))
        assertFalse(verified.verifies(1251))
        assertFalse(verified.copy(verifiedAmountMinor = null).verifies(1250))
        assertFalse(verified.copy(verifiedAmountMinor = 0).verifies(0))
    }
    @Test fun exactLargeAmountsDoNotLosePrecision() {
        assertTrue(proof.copy(verifiedAmountMinor = Long.MAX_VALUE).verifies(Long.MAX_VALUE))
        assertFalse(proof.copy(verifiedAmountMinor = Long.MAX_VALUE).verifies(Long.MAX_VALUE - 1))
    }
    @Test fun fileLimitsAndAllowedTypesAreEnforced() {
        ProofPolicy.mimeTypes.forEach { ProofPolicy.validate(it, 1); ProofPolicy.validate(it, ProofPolicy.MAX_BYTES) }
        for (bytes in listOf(-1L, 0L, ProofPolicy.MAX_BYTES + 1)) {
            assertThrows(IllegalArgumentException::class.java) { ProofPolicy.validate("application/pdf", bytes) }
        }
        listOf("text/html", "image/svg+xml", "application/zip", "application/octet-stream").forEach {
            assertThrows(IllegalArgumentException::class.java) { ProofPolicy.validate(it, 100) }
        }
    }
    @Test fun identifiersCannotEscapeTheirOwnersStoragePath() {
        listOf("", "../owner", "other/proof", ".", "..", "a".repeat(129)).forEach {
            assertThrows(IllegalArgumentException::class.java) { ProofPolicy.requireId(it) }
        }
        ProofPolicy.requireId("receipt-id_123")
        ProofPolicy.requireId("legacy.transaction-id")
    }
    @Test fun renamedExecutableOrHtmlIsRejected() {
        listOf("application/pdf", "image/jpeg", "image/png", "image/webp", "image/heic").forEach {
            assertFalse(ProofFileSignature.matches(it, "<html>fake receipt</html>".toByteArray()))
            assertFalse(ProofFileSignature.matches(it, byteArrayOf()))
        }
    }
    @Test fun supportedFileHeadersAreRecognized() {
        assertTrue(ProofFileSignature.matches("application/pdf", "%PDF-1.4".toByteArray()))
        assertTrue(ProofFileSignature.matches("image/jpeg", byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())))
        assertTrue(ProofFileSignature.matches("image/png", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        assertTrue(ProofFileSignature.matches("image/gif", "GIF89a".toByteArray()))
        assertTrue(ProofFileSignature.matches("image/webp", "RIFF1234WEBP".toByteArray()))
        assertTrue(ProofFileSignature.matches("image/heic", "1234ftypheic".toByteArray()))
    }
    @Test fun roomMappingPreservesAssociationVerificationAndPendingState() {
        val row = TransactionProofEntity("owner", "transaction", "proof", "Receipt.pdf", "application/pdf", 100, 1, 1250, true, 2)
        assertEquals(proof.copy(verifiedAmountMinor = 1250), row.toDomain())
        assertFalse(row.copy(pendingUpload = false).toDomain().pendingUpload)
    }
}
