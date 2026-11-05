package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.GoalsSnapshot
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.GoalFundingRepository
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributeToGoalUseCaseTest {

    private val userId = "user-1"
    private val usdGoal = FinancialGoal(
        id = "goal-usd",
        userId = userId,
        name = "Laptop",
        targetMinor = 200_000L,
        currency = "USD",
        targetDateEpochDay = 25_000L
    )
    private val lkrAccount = Account(
        id = "acc-lkr",
        userId = userId,
        name = "LKR Checking",
        bankName = "Commercial Bank",
        currency = "LKR"
    )
    private val usdAccount = Account(
        id = "acc-usd",
        userId = userId,
        name = "USD Savings",
        bankName = "HSBC",
        currency = "USD"
    )
    private val emptyUsdAccount = Account(
        id = "acc-usd-empty",
        userId = userId,
        name = "Empty USD",
        bankName = "HSBC",
        currency = "USD"
    )

    @Test
    fun `succeeds when selected account matches goal currency and has sufficient balance`() = runBlocking {
        var recordedSourceAccountId: String? = null
        val useCase = buildUseCase(
            goals = mapOf(usdGoal.id to usdGoal),
            accounts = listOf(lkrAccount, usdAccount, emptyUsdAccount),
            transactions = listOf(
                Transaction(
                    id = "inc-usd",
                    userId = userId,
                    type = TransactionType.INCOME,
                    amountMinor = 50_000L,
                    accountId = usdAccount.id,
                    currency = "USD",
                    targetCurrency = "LKR",
                    conversion = null,
                    pendingConversion = true
                )
            ),
            onContribute = { goalId, amountMinor, sourceAccountId, id ->
                recordedSourceAccountId = sourceAccountId
                GoalContribution(
                    id = id,
                    userId = userId,
                    goalId = goalId,
                    amountMinor = amountMinor,
                    currency = "USD"
                )
            }
        )

        val result = useCase(
            goalId = usdGoal.id,
            amountText = "150.00",
            sourceAccountId = usdAccount.id,
            id = "contrib-1"
        )

        assertTrue(result.isSuccess)
        assertEquals(usdAccount.id, recordedSourceAccountId)
        assertEquals(15_000L, result.getOrThrow().amountMinor)
    }

    @Test
    fun `rejects blank source account, mismatched currency account, and unfunded account`() = runBlocking {
        val useCase = buildUseCase(
            goals = mapOf(usdGoal.id to usdGoal),
            accounts = listOf(lkrAccount, usdAccount, emptyUsdAccount),
            transactions = listOf(
                Transaction(
                    id = "inc-lkr",
                    userId = userId,
                    type = TransactionType.INCOME,
                    amountMinor = 900_000L,
                    accountId = lkrAccount.id,
                    currency = "LKR",
                    targetCurrency = "LKR"
                )
            )
        )

        val missingAcc = useCase(usdGoal.id, "10.00", "")
        assertEquals(
            GoalContributionError.ACCOUNT_REQUIRED,
            (missingAcc.exceptionOrNull() as? GoalContributionException)?.error
        )

        val wrongCurrency = useCase(usdGoal.id, "10.00", lkrAccount.id)
        assertEquals(
            GoalContributionError.CURRENCY_MISMATCH,
            (wrongCurrency.exceptionOrNull() as? GoalContributionException)?.error
        )

        val unfunded = useCase(usdGoal.id, "10.00", emptyUsdAccount.id)
        assertEquals(
            GoalContributionError.INSUFFICIENT_FUNDS,
            (unfunded.exceptionOrNull() as? GoalContributionException)?.error
        )
    }

    private fun buildUseCase(
        goals: Map<String, FinancialGoal>,
        accounts: List<Account>,
        transactions: List<Transaction>,
        onContribute: (String, Long, String, String) -> GoalContribution = { goalId, amountMinor, _, id ->
            GoalContribution(id = id, userId = userId, goalId = goalId, amountMinor = amountMinor, currency = "USD")
        }
    ): ContributeToGoalUseCase {
        val goalRepo = object : GoalRepository {
            override fun observeGoals(): Flow<GoalsSnapshot> =
                flowOf(GoalsSnapshot(userId, goals.values.toList()))
            override suspend fun getGoal(id: String): Result<FinancialGoal?> =
                Result.success(goals[id])
            override suspend fun create(goal: FinancialGoal): Result<FinancialGoal> = error("Unused")
            override suspend fun edit(goal: FinancialGoal): Result<FinancialGoal> = error("Unused")
            override suspend fun retrySync(): Result<Unit> = Result.success(Unit)
        }
        val accountRepo = object : AccountRepository {
            override fun observeAccounts(): Flow<Resource<List<Account>>> =
                flowOf(Resource.Success(accounts))
            override suspend fun createAccount(account: Account): Result<Unit> = error("Unused")
            override suspend fun updateAccount(account: Account): Result<Unit> = error("Unused")
            override suspend fun setDefaultAccount(id: String): Result<Unit> = error("Unused")
            override suspend fun getAccount(id: String): Result<Account?> =
                Result.success(accounts.firstOrNull { it.id == id })
            override suspend fun getDefaultAccount(): Result<Account?> =
                Result.success(accounts.firstOrNull { it.isDefault })
        }
        val txRepo = object : TransactionRepository {
            override fun observeTransactions(): Flow<Resource<List<Transaction>>> =
                flowOf(Resource.Success(transactions))
            override fun observeTransaction(id: String): Flow<Resource<Transaction?>> = error("Unused")
            override suspend fun addTransaction(t: Transaction): Result<Unit> = error("Unused")
            override suspend fun updateTransaction(t: Transaction): Result<Unit> = error("Unused")
            override suspend fun deleteTransaction(id: String): Result<Unit> = error("Unused")
            override suspend fun retryConversion(id: String): Result<Unit> = error("Unused")
        }
        val fundingRepo = object : GoalFundingRepository {
            override suspend fun contribute(
                goalId: String,
                amountMinor: Long,
                sourceAccountId: String,
                id: String
            ): Result<GoalContribution> = Result.success(onContribute(goalId, amountMinor, sourceAccountId, id))
        }
        return ContributeToGoalUseCase(goalRepo, accountRepo, txRepo, fundingRepo)
    }
}
