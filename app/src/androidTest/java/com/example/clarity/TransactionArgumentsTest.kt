package com.example.clarity

import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.clarity.presentation.navigation.TRANSACTION_ID_ARGUMENT
import com.example.clarity.presentation.navigation.transactionIdArgument
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionArgumentsTest {
    @Test fun sharedArgumentSafelyHandlesMissingMalformedAndRestoredValues() {
        assertNull(SavedStateHandle().transactionIdArgument())
        for (value in listOf(42, true, "", " ", "bad/id", "x".repeat(129))) {
            assertNull(SavedStateHandle(mapOf(TRANSACTION_ID_ARGUMENT to value)).transactionIdArgument())
        }
        assertEquals("legacy.transaction-id", SavedStateHandle(mapOf(TRANSACTION_ID_ARGUMENT to "legacy.transaction-id")).transactionIdArgument())
    }
}
