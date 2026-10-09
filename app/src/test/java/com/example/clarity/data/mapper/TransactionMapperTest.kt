package com.example.clarity.data.mapper

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

class TransactionMapperTest {
    private val transaction = Transaction(
        id = "id", userId = "owner", type = TransactionType.INCOME, amountMinor = Long.MAX_VALUE,
        category = "Salary", note = "Monthly", dateEpochMillis = 1000L, createdAt = 500L, updatedAt = 800L
    )
    @Test fun entityAndRemoteMapRoundTripPreserveEveryField() {
        assertEquals(transaction, transaction.toEntity().toDomain())
        val map = transaction.toFirestoreMap()
        assertEquals(9, map.size)
        assertTrue(map["amountMinor"] is Long)
        assertEquals(transaction, map.toTransaction(transaction.id))
    }
    @Test fun rejectsMalformedDocumentData() {
        val valid = transaction.toFirestoreMap()
        assertNull(valid.toTransaction("different-id"))
        valid.keys.forEach { missing -> assertNull((valid - missing).toTransaction(transaction.id)) }
        val invalidFields = mapOf<String, Any>(
            "amountMinor" to 0L, "type" to "UNKNOWN", "userId" to "", "note" to "x".repeat(2001),
            "category" to "", "createdAt" to -1L, "updatedAt" to 1L, "dateEpochMillis" to "1000"
        )
        invalidFields.forEach { (key, value) -> assertNull("Invalid $key", (valid + (key to value)).toTransaction(transaction.id)) }
        assertNull((valid + ("amountMinor" to "100")).toTransaction(transaction.id))
        assertNull((valid + ("amountMinor" to 100)).toTransaction(transaction.id))
    }
    @Test fun outboxMetadataIsNotSentToFirestore() {
        val entity = transaction.toEntity().copy(pendingSync = true, isDeleted = true)
        val fields = entity.toDomain().toFirestoreMap()
        assertFalse(fields.containsKey("pendingSync"))
        assertFalse(fields.containsKey("isDeleted"))
    }
}
