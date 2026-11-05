package com.example.clarity

import com.example.clarity.domain.model.Budget
import com.example.clarity.domain.model.BudgetPeriod
import com.example.clarity.domain.model.BudgetPolicy
import com.example.clarity.domain.model.BudgetStatus
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.BudgetOverview
import com.example.clarity.domain.repository.TransactionTagRepository
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.presentation.budgets.BudgetsState
import com.example.clarity.presentation.budgets.defaultBudgetCategory
import com.example.clarity.presentation.budgets.formatBudgetPercentLabel
import com.example.clarity.presentation.budgets.matchBudgetCategoryTag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class BudgetsCategoryTagsTest {

    @Test
    fun expenseCategoryTagsIncludeCreatedCustomTagsAndExcludeIncomeAndSystemTags() = runBlocking {
        val repository = object : TransactionTagRepository {
            override fun observeTags(): Flow<List<String>> = flowOf(emptyList())
            override fun observeTagsByType(type: TransactionType): Flow<List<String>> =
                when (type) {
                    TransactionType.EXPENSE -> flowOf(
                        listOf(
                            "Dining Out",
                            "Fuel",
                            "Salary",
                            TransactionTags.GOAL_CONTRIBUTION,
                            TransactionTags.CASH_TOP_UP
                        )
                    )
                    TransactionType.INCOME -> flowOf(listOf("Dividends"))
                }
        }
        val useCase = GetTransactionTagsUseCase(repository)

        val expenseTags = useCase(TransactionType.EXPENSE).first()

        assertTrue(expenseTags.containsAll(TransactionTags.defaultExpenseCategories))
        assertTrue("Dining Out" in expenseTags)
        assertTrue("Fuel" in expenseTags)
        assertFalse("Salary" in expenseTags)
        assertFalse("Dividends" in expenseTags)
        assertFalse(TransactionTags.GOAL_CONTRIBUTION in expenseTags)
        assertFalse(TransactionTags.CASH_TOP_UP in expenseTags)
    }

    @Test
    fun budgetsStateDefaultsToExpenseCategoryTagsAndMatchesCreatedTagsCaseInsensitively() {
        val initialState = BudgetsState()
        assertEquals(TransactionTags.defaultExpenseCategories, initialState.tags)
        assertFalse("Salary" in initialState.tags)

        val tags = TransactionTags.defaultExpenseCategories + listOf("Dining Out", "Pet Care")
        assertEquals("Dining Out", matchBudgetCategoryTag(tags, " dining out "))
        assertNull(matchBudgetCategoryTag(tags, "Uncreated Tag"))
    }

    @Test
    fun defaultBudgetCategoryPicksNextAvailableCreatedTagWhenFoodAlreadyHasMonthlyBudget() {
        val tags = listOf("General", "Groceries", "Food", "Dining Out")
        val existingFoodBudget = Budget(
            id = BudgetPolicy.id("2026-11", "Food"),
            userId = "owner",
            month = "2026-11",
            category = "Food",
            limitMinor = 500_000L,
            currency = "LKR"
        )
        val overview = BudgetOverview(
            ownerId = "owner",
            statuses = listOf(BudgetStatus(existingFoodBudget, BigInteger.ZERO, 0))
        )

        assertEquals("General", defaultBudgetCategory(tags, overview, BudgetPeriod.MONTHLY))
        assertEquals("Food", defaultBudgetCategory(tags, overview, BudgetPeriod.YEARLY))
    }

    @Test
    fun formatBudgetPercentLabelFormatsCleanPercentagesAcrossThresholds() {
        val baseBudget = Budget(
            id = BudgetPolicy.id("2026-11", "Food"),
            userId = "owner",
            month = "2026-11",
            category = "Food",
            limitMinor = 1_000_000L,
            currency = "LKR"
        )
        assertEquals("0%", formatBudgetPercentLabel(BudgetStatus(baseBudget, BigInteger.ZERO, 0)))
        assertEquals("45%", formatBudgetPercentLabel(BudgetStatus(baseBudget, BigInteger.valueOf(450_000L), 0)))
        assertEquals("85.5%", formatBudgetPercentLabel(BudgetStatus(baseBudget, BigInteger.valueOf(855_000L), 0)))
        assertEquals("125%", formatBudgetPercentLabel(BudgetStatus(baseBudget, BigInteger.valueOf(1_250_000L), 0)))
    }
}
