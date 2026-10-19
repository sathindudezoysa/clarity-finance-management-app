package com.example.clarity

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.presentation.income.IncomeContent
import com.example.clarity.presentation.income.IncomeState
import com.example.clarity.presentation.theme.ClarityTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class IncomeSourceUiTest {
    @get:Rule val compose = createComposeRule()
    private val accounts = listOf(Account(id = "bank", userId = "owner", name = "Daily", bankName = "Bank"))
    @Test fun multipleSourcesShowMonthlyPromptOnlyWhenUnconfirmed() {
        val paid = IncomeSource("salary", "owner", "Salary", 10000, "bank", true, lastConfirmedMonth = "2026-10")
        val freelance = paid.copy(id = "freelance", name = "Freelance", recurring = false)
        val period = androidx.compose.runtime.mutableStateOf(YearMonth.of(2026, 10))
        compose.setContent { ClarityTheme {
            IncomeContent(IncomeState(accounts = accounts, sources = listOf(paid, freelance), loading = false, period = period.value),
                period.value.toString(), {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Salary").assertIsDisplayed()
        compose.onNodeWithTag("income-list").performScrollToNode(hasText("Freelance"))
        compose.onNodeWithText("Freelance").assertIsDisplayed()
        compose.onNodeWithTag("income-list").performScrollToNode(hasText("Confirm this month"))
        compose.onNodeWithText("Confirm this month").assertIsNotEnabled()
        compose.runOnIdle { period.value = YearMonth.of(2026, 11) }
        compose.onNodeWithTag("income-list").performScrollToNode(hasText("Confirm this month"))
        compose.onNodeWithText("Confirm this month").assertIsEnabled()
        compose.onNodeWithTag("income-list").performScrollToNode(hasText("Record income received"))
        compose.onNodeWithText("Record income received").assertIsEnabled()
    }
    @Test fun dueSourceOpensReviewWithItsOwnIdentity() {
        val salary = IncomeSource("salary", "owner", "Salary", 10000, "bank", true)
        var reviewed: String? = null
        compose.setContent { ClarityTheme { IncomeContent(IncomeState(accounts = accounts, sources = listOf(salary), loading = false,
            period = YearMonth.of(2026, 10)), "October 2026", {}, { reviewed = it.id }, {}, {}, {}) } }
        compose.onNodeWithText("Confirm this month").performClick()
        assertEquals("salary", reviewed)
    }
}
