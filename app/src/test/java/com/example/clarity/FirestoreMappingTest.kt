package com.example.clarity

import com.example.clarity.data.mapper.mapFirestoreDocuments
import com.example.clarity.data.mapper.toAccount
import com.example.clarity.data.mapper.toFirestoreMap
import com.example.clarity.data.mapper.toTransaction
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Transaction
import org.junit.Assert.*
import org.junit.Test

class FirestoreMappingTest {
    private val account = Account("account", "user-a", "Daily", "Bank", isDefault = true,
        createdAt = 1, updatedAt = 10)
    private val transaction = Transaction("transaction", "user-a", amountMinor = 1250,
        dateEpochMillis = 100, createdAt = 1, updatedAt = 10, accountId = "account")

    @Test
    fun intLongAndDoubleFieldsMapToTheSameAccountsAndTransactions() {
        val conversions: List<(Long) -> Number> = listOf({ it.toInt() }, { it }, { it.toDouble() })
        for (convert in conversions) {
            val accountData = account.toFirestoreMap().mapValues { (_, value) ->
                if (value is Long) convert(value) else value
            }
            val transactionData = transaction.toFirestoreMap().mapValues { (_, value) ->
                if (value is Long) convert(value) else value
            }
            assertEquals(account, accountData.toAccount(account.id))
            assertEquals(transaction, transactionData.toTransaction(transaction.id))
        }
        val mixed = transaction.toFirestoreMap() + mapOf("amountMinor" to 1250.0, "createdAt" to 1)
        assertEquals(transaction, mixed.toTransaction(transaction.id))
        val largest = account.copy(updatedAt = Long.MAX_VALUE)
        assertEquals(largest, largest.toFirestoreMap().toAccount(largest.id))
    }

    @Test
    fun missingRequiredFieldsAndWrongTypesAreRejectedWithoutThrowing() {
        for (field in account.toFirestoreMap().keys - "accountType") {
            assertNull("Missing account field $field", account.toFirestoreMap().minus(field).toAccount(account.id))
        }
        for (field in transaction.toFirestoreMap().keys - "accountId") {
            assertNull("Missing transaction field $field", transaction.toFirestoreMap().minus(field).toTransaction(transaction.id))
        }
        for ((field, value) in listOf("name" to 42, "isDefault" to "true", "accountType" to false)) {
            assertNull((account.toFirestoreMap() + (field to value)).toAccount(account.id))
        }
        for ((field, value) in listOf("type" to "INVALID", "note" to false, "accountId" to 42)) {
            assertNull((transaction.toFirestoreMap() + (field to value)).toTransaction(transaction.id))
        }
    }

    @Test
    fun malformedAndNonFiniteNumbersAreRejectedWithoutThrowing() {
        for (value in listOf(null, "10", true, Double.NaN, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.MAX_VALUE, Long.MAX_VALUE.toDouble())) {
            for (field in listOf("createdAt", "updatedAt")) {
                assertNull((account.toFirestoreMap() + (field to value)).toAccount(account.id))
            }
            for (field in listOf("amountMinor", "dateEpochMillis", "createdAt", "updatedAt")) {
                assertNull((transaction.toFirestoreMap() + (field to value)).toTransaction(transaction.id))
            }
        }
    }

    @Test
    fun badAccountDocumentsAreWarnedAndSkippedWhileGoodDocumentsStillMap() {
        val next = account.copy(id = "next", name = "Savings", isDefault = false)
        val documents = listOf(
            account.id to account.toFirestoreMap(),
            "bad" to mapOf("id" to "bad", "createdAt" to "wrong"),
            "missing" to null,
            next.id to next.toFirestoreMap()
        )
        val warnings = mutableListOf<String>()
        val parsed = documents.mapFirestoreDocuments({ data, id -> data.toAccount(id) }, { warnings.add(it) })
        assertEquals(listOf(account, next), parsed)
        assertEquals(listOf("bad", "missing"), warnings)
    }

    @Test
    fun badTransactionDocumentsAreWarnedAndSkippedWhileGoodDocumentsStillMap() {
        val next = transaction.copy(id = "next")
        val documents = listOf(
            transaction.id to transaction.toFirestoreMap(),
            "bad" to (transaction.copy(id = "bad").toFirestoreMap() + ("amountMinor" to "wrong")),
            "missing" to null,
            next.id to next.toFirestoreMap()
        )
        val warnings = mutableListOf<String>()
        val parsed = documents.mapFirestoreDocuments({ data, id -> data.toTransaction(id) }, { warnings.add(it) })
        assertEquals(listOf(transaction, next), parsed)
        assertEquals(listOf("bad", "missing"), warnings)
    }
}
