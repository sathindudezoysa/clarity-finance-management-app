package com.example.clarity

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.clarity.domain.model.TransactionProof
import com.example.clarity.presentation.transactions.proofs.TransactionProofContent
import com.example.clarity.presentation.transactions.proofs.TransactionProofState
import com.example.clarity.presentation.theme.ClarityTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionProofUiTest {
    @get:Rule val compose = createComposeRule()
    private val proof = TransactionProof("proof", "transaction", "Receipt.pdf", "application/pdf", 100, 1)

    @Test fun unverifiedProofHasNoVerificationIcon() {
        compose.setContent { ClarityTheme { TransactionProofContent(TransactionProofState(listOf(proof), false), 1250, true, {}, {}, { _, _ -> }, {}) } }
        compose.onAllNodesWithContentDescription("Receipt amount verified by you").assertCountEquals(0)
        compose.onNodeWithText("Receipt.pdf").assertIsDisplayed()
    }
    @Test fun verifiedProofShowsAccessibleIconAndChangedAmountHidesIt() {
        val amount = androidx.compose.runtime.mutableStateOf(1250L)
        compose.setContent { ClarityTheme { TransactionProofContent(TransactionProofState(listOf(proof.copy(verifiedAmountMinor = 1250)), false), amount.value, true, {}, {}, { _, _ -> }, {}) } }
        compose.onAllNodesWithContentDescription("Receipt amount verified by you").assertCountEquals(2)
        compose.runOnIdle { amount.value = 1500 }
        compose.onAllNodesWithContentDescription("Receipt amount verified by you").assertCountEquals(0)
    }
}
