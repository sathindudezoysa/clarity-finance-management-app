package com.example.clarity.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.repository.GoalContributionRepository
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalContributionsUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalsUseCase
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.GoalProgressPolicy
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
                observeCurrency()
            ) { goalsSnapshot, contributionsSnapshot, currencyPref ->
                Triple(goalsSnapshot, contributionsSnapshot, currencyPref.currency)
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
                .collect { (snapshot, contributionsSnapshot, preferredCurrency) ->
                    val today = GoalPolicy.todayEpochDay(clock)
                    val contributions = if (snapshot.ownerId != null && contributionsSnapshot.ownerId == snapshot.ownerId) {
                        contributionsSnapshot.contributions
                    } else {
                        emptyList()
                    }
                    latestGoals = snapshot.goals
                    latestContributions = contributions
                    latestPreferredCurrency = preferredCurrency
                    lastEvaluatedEpochDay = today
                    val cards = snapshot.goals.map { it.toCardUiModel(today, contributions) }
                    val totals = calculateMonthlyRateTotals(snapshot.goals, today)
                    val aggregate = computeAggregateSummary(
                        goals = snapshot.goals,
                        contributions = contributions,
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

    private suspend fun computeAggregateSummary(
        goals: List<FinancialGoal>,
        contributions: List<GoalContribution>,
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
            return rateCache.getOrPut(source) {
                val quote = runCatching {
                    exchangeRateRepository.cached(source, summaryCurrency)
                        ?: exchangeRateRepository.latest(source, summaryCurrency)
                }.getOrNull()
                quote?.let { runCatching { CurrencyConversion.rate(it.rate) }.getOrNull() }
            }
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

        for (goal in goals) {
            val goalCurrency = if (goal.currency in Currencies.supported) {
                goal.currency
            } else {
                Currencies.supported.first()
            }
            val progress = GoalProgressPolicy.evaluateGoalProgress(goal, contributions)
            val rateMinor = goal.requiredMonthlySavingsMinor(todayEpochDay) ?: 0L
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
        val preferredCurrency = latestPreferredCurrency
        viewModelScope.launch {
            val cards = currentGoals.map { it.toCardUiModel(today, currentContributions) }
            val totals = calculateMonthlyRateTotals(currentGoals, today)
            val aggregate = computeAggregateSummary(
                goals = currentGoals,
                contributions = currentContributions,
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
}
