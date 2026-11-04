package com.example.clarity.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.repository.GoalContributionRepository
import com.example.clarity.domain.repository.GoalRepository
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
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val observeGoals: ObserveGoalsUseCase,
    private val observeGoalContributions: ObserveGoalContributionsUseCase,
    private val goalRepository: GoalRepository,
    private val goalContributionRepository: GoalContributionRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsState())
    val uiState: StateFlow<GoalsState> = _uiState.asStateFlow()
    private var observeJob: Job? = null
    private var latestGoals: List<FinancialGoal> = emptyList()
    private var latestContributions: List<GoalContribution> = emptyList()
    private var lastEvaluatedEpochDay: Long? = null

    init {
        startObserving()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            combine(observeGoals(), observeGoalContributions()) { goalsSnapshot, contributionsSnapshot ->
                goalsSnapshot to contributionsSnapshot
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
                .collect { (snapshot, contributionsSnapshot) ->
                    val today = GoalPolicy.todayEpochDay(clock)
                    val contributions = if (snapshot.ownerId != null && contributionsSnapshot.ownerId == snapshot.ownerId) {
                        contributionsSnapshot.contributions
                    } else {
                        emptyList()
                    }
                    latestGoals = snapshot.goals
                    latestContributions = contributions
                    lastEvaluatedEpochDay = today
                    val cards = snapshot.goals.map { it.toCardUiModel(today, contributions) }
                    val totals = calculateMonthlyRateTotals(snapshot.goals, today)
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
                            activeGoalCount = activeCount,
                            streakSummary = streak
                        )
                    }
                }
        }
    }

    fun refreshForCurrentDate() {
        val today = GoalPolicy.todayEpochDay(clock)
        val previousDay = lastEvaluatedEpochDay ?: return
        if (previousDay == today) return
        lastEvaluatedEpochDay = today
        val currentGoals = latestGoals
        val currentContributions = latestContributions
        val cards = currentGoals.map { it.toCardUiModel(today, currentContributions) }
        val totals = calculateMonthlyRateTotals(currentGoals, today)
        val activeCount = cards.count { it.isActive }
        val streak = GoalProgressPolicy.calculateStreak(currentContributions, today)
        _uiState.update { state ->
            state.copy(
                goals = cards,
                totalMonthlyRates = totals,
                activeGoalCount = activeCount,
                streakSummary = streak
            )
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
