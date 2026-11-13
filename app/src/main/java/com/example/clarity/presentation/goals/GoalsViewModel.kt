package com.example.clarity.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.repository.GoalContributionRepository
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.goal.FinishGoalUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalContributionsUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalsUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.GoalProgressPolicy
import com.example.clarity.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val observeGoals: ObserveGoalsUseCase,
    private val observeGoalContributions: ObserveGoalContributionsUseCase,
    private val observeCurrency: ObserveCurrencyPreferenceUseCase,
    private val getTransactions: GetTransactionsUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val exchangeRateRepository: ExchangeRateRepository,
    private val goalRepository: GoalRepository,
    private val goalContributionRepository: GoalContributionRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsState())
    val uiState: StateFlow<GoalsState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var latestGoals: List<FinancialGoal> = emptyList()
    private var latestContributions: List<GoalContribution> = emptyList()
    private var latestTransactions: List<Transaction> = emptyList()
    private var latestAccounts: List<Account> = emptyList()
    private var latestPreferredCurrency: String = Currencies.supported.first()
    private var lastEvaluatedEpochDay: Long? = null

    init {
        startObserving()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            combine(
                observeGoals(),
                observeGoalContributions(),
                observeCurrency(),
                getTransactions().catch { emit(Resource.Success(emptyList())) },
                getAccounts().catch { emit(Resource.Success(emptyList())) }
            ) { goalsSnapshot, contributionsSnapshot, currencyPref, txRes, accountsRes ->
                CombinedGoalsInput(
                    goalsSnapshot = goalsSnapshot,
                    contributionsSnapshot = contributionsSnapshot,
                    preferredCurrency = currencyPref.currency,
                    transactions = txRes.data.orEmpty(),
                    accounts = accountsRes.data.orEmpty()
                )
            }
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = goalErrorMessage(error, GoalMessages.LOAD_ERROR)
                        )
                    }
                }
                .collect { input ->
                    val snapshot = input.goalsSnapshot
                    val contributionsSnapshot = input.contributionsSnapshot
                    val preferredCurrency = input.preferredCurrency
                    val today = GoalPolicy.todayEpochDay(clock)
                    val txIds = input.transactions.mapTo(hashSetOf()) { it.id }
                    val contributions = if (snapshot.ownerId != null && contributionsSnapshot.ownerId == snapshot.ownerId) {
                        contributionsSnapshot.contributions.filter { it.id in txIds }
                    } else {
                        emptyList()
                    }
                    latestGoals = snapshot.goals
                    latestContributions = contributions
                    latestTransactions = input.transactions
                    latestAccounts = input.accounts
                    latestPreferredCurrency = preferredCurrency
                    lastEvaluatedEpochDay = today

                    val cards = buildGoalCards(
                        goals = snapshot.goals,
                        contributions = contributions,
                        transactions = input.transactions,
                        accounts = input.accounts,
                        today = today
                    )
                    val openGoals = snapshot.goals.filter { goal ->
                        val card = cards.firstOrNull { it.id == goal.id }
                        card?.isCompleted != true && card?.isClosed != true
                    }
                    val totals = calculateMonthlyRateTotals(openGoals, today)
                    val aggregate = computeAggregateSummary(
                        goals = snapshot.goals,
                        contributions = contributions,
                        transactions = input.transactions,
                        todayEpochDay = today,
                        preferredCurrency = preferredCurrency
                    )
                    val activeCount = cards.count { it.isActive }
                    val streak = GoalProgressPolicy.calculateStreak(contributions, today)
                    _uiState.update { old ->
                        val ownerChanged = old.ownerId != snapshot.ownerId
                        val nextError = when {
                            snapshot.ownerId == null -> GoalMessages.SIGN_IN
                            ownerChanged -> null
                            else -> old.error
                        }
                        GoalsState(
                            isLoading = false,
                            ownerId = snapshot.ownerId,
                            goals = cards,
                            error = nextError,
                            totalMonthlyRates = totals,
                            aggregateSummary = aggregate,
                            activeGoalCount = activeCount,
                            streakSummary = streak
                        )
                    }
                }
        }
    }

    private fun buildGoalCards(
        goals: List<FinancialGoal>,
        contributions: List<GoalContribution>,
        transactions: List<Transaction>,
        accounts: List<Account>,
        today: Long
    ): List<GoalCardUiModel> {
        val txById = transactions.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        return goals.map { goal ->
            val payoutTx = txById[FinishGoalUseCase.payoutTransactionId(goal.id)]
            val isClosed = payoutTx != null
            val closedSummaryLabel = payoutTx?.let { tx ->
                val accountLabel = tx.accountId?.let { accId ->
                    accountsById[accId]?.displayLabel
                        ?: if (accId.startsWith(Account.CASH_WALLET_PREFIX)) "Cash Wallet" else "Bank Account"
                } ?: "Bank Account"
                "Transferred ${formatGoalMoney(tx.amountMinor, tx.currency)} to $accountLabel • Goal closed"
            }
            goal.toCardUiModel(
                todayEpochDay = today,
                contributions = contributions,
                isClosed = isClosed,
                closedSummaryLabel = closedSummaryLabel
            )
        }
    }

    private suspend fun computeAggregateSummary(
        goals: List<FinancialGoal>,
        contributions: List<GoalContribution>,
        transactions: List<Transaction>,
        todayEpochDay: Long,
        preferredCurrency: String
    ): GoalsAggregateSummaryUiModel {
        val safePreferred = if (preferredCurrency in Currencies.supported) {
            preferredCurrency
        } else {
            Currencies.supported.first()
        }
        if (goals.isEmpty()) {
            return buildGoalsAggregateSummary(
                currency = safePreferred,
                monthlyRateMinor = BigInteger.ZERO,
                savedMinor = BigInteger.ZERO,
                targetMinor = BigInteger.ZERO
            )
        }

        val distinctCurrencies = goals.map {
            if (it.currency in Currencies.supported) it.currency else Currencies.supported.first()
        }.distinct()

        val summaryCurrency = if (distinctCurrencies.size == 1) {
            distinctCurrencies.first()
        } else {
            safePreferred
        }

        val rateCache = mutableMapOf<String, BigDecimal?>()
        suspend fun resolveRate(source: String): BigDecimal? {
            if (source == summaryCurrency) return BigDecimal.ONE
            if (rateCache.containsKey(source)) {
                return rateCache[source]
            }
            val quote = try {
                exchangeRateRepository.cached(source, summaryCurrency)
                    ?: exchangeRateRepository.latest(source, summaryCurrency)
            } catch (_: Exception) {
                null
            }
            val resolved = quote?.let {
                try {
                    CurrencyConversion.rate(it.rate)
                } catch (_: Exception) {
                    null
                }
            }
            rateCache[source] = resolved
            return resolved
        }

        suspend fun convertMinor(amountMinor: BigInteger, sourceCurrency: String): BigInteger {
            if (amountMinor <= BigInteger.ZERO || sourceCurrency == summaryCurrency) return amountMinor
            val rate = resolveRate(sourceCurrency) ?: return BigInteger.ZERO
            return BigDecimal(amountMinor, Currencies.digits(sourceCurrency))
                .multiply(rate)
                .movePointRight(Currencies.digits(summaryCurrency))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger()
        }

        var totalRateMinor = BigInteger.ZERO
        var totalSavedMinor = BigInteger.ZERO
        var totalTargetMinor = BigInteger.ZERO
        val txIds = transactions.mapTo(hashSetOf()) { it.id }

        for (goal in goals) {
            val goalCurrency = if (goal.currency in Currencies.supported) {
                goal.currency
            } else {
                Currencies.supported.first()
            }
            val progress = GoalProgressPolicy.evaluateGoalProgress(goal, contributions)
            val isClosed = FinishGoalUseCase.payoutTransactionId(goal.id) in txIds
            val rateMinor = if (progress.isCompleted || isClosed) {
                0L
            } else {
                goal.requiredMonthlySavingsMinor(todayEpochDay) ?: 0L
            }
            totalRateMinor += convertMinor(BigInteger.valueOf(rateMinor), goalCurrency)
            totalSavedMinor += convertMinor(progress.savedMinor, goalCurrency)
            totalTargetMinor += convertMinor(BigInteger.valueOf(goal.targetMinor.coerceAtLeast(0L)), goalCurrency)
        }

        return buildGoalsAggregateSummary(
            currency = summaryCurrency,
            monthlyRateMinor = totalRateMinor,
            savedMinor = totalSavedMinor,
            targetMinor = totalTargetMinor
        )
    }

    fun refreshForCurrentDate() {
        val today = GoalPolicy.todayEpochDay(clock)
        val previousDay = lastEvaluatedEpochDay ?: return
        if (previousDay == today) return
        lastEvaluatedEpochDay = today
        val currentGoals = latestGoals
        val currentContributions = latestContributions
        val currentTransactions = latestTransactions
        val currentAccounts = latestAccounts
        val preferredCurrency = latestPreferredCurrency
        viewModelScope.launch {
            val cards = buildGoalCards(
                goals = currentGoals,
                contributions = currentContributions,
                transactions = currentTransactions,
                accounts = currentAccounts,
                today = today
            )
            val openGoals = currentGoals.filter { goal ->
                val card = cards.firstOrNull { it.id == goal.id }
                card?.isCompleted != true && card?.isClosed != true
            }
            val totals = calculateMonthlyRateTotals(openGoals, today)
            val aggregate = computeAggregateSummary(
                goals = currentGoals,
                contributions = currentContributions,
                transactions = currentTransactions,
                todayEpochDay = today,
                preferredCurrency = preferredCurrency
            )
            val activeCount = cards.count { it.isActive }
            val streak = GoalProgressPolicy.calculateStreak(currentContributions, today)
            _uiState.update { state ->
                state.copy(
                    goals = cards,
                    totalMonthlyRates = totals,
                    aggregateSummary = aggregate,
                    activeGoalCount = activeCount,
                    streakSummary = streak
                )
            }
        }
    }

    fun retry() {
        startObserving()
        viewModelScope.launch {
            val goalResult = goalRepository.retrySync()
            val contributionResult = goalContributionRepository.retrySync()
            val error = goalResult.exceptionOrNull() ?: contributionResult.exceptionOrNull()
            if (error != null) {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = goalErrorMessage(error, GoalMessages.LOAD_ERROR)
                    )
                }
            }
        }
    }

    private data class CombinedGoalsInput(
        val goalsSnapshot: com.example.clarity.domain.model.GoalsSnapshot,
        val contributionsSnapshot: com.example.clarity.domain.model.GoalContributionsSnapshot,
        val preferredCurrency: String,
        val transactions: List<Transaction>,
        val accounts: List<Account>
    )
}
