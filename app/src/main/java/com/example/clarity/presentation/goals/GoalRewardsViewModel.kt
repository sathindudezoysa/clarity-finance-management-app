package com.example.clarity.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class GoalRewardsViewModel @Inject constructor(
    private val observeGoals: ObserveGoalsUseCase,
    private val observeGoalContributions: ObserveGoalContributionsUseCase,
    private val goalRepository: GoalRepository,
    private val goalContributionRepository: GoalContributionRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalRewardsState())
    val uiState: StateFlow<GoalRewardsState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init {
        startObserving()
    }

    fun startObserving() {
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
                .collect { (goalsSnapshot, contributionsSnapshot) ->
                    val today = GoalPolicy.todayEpochDay(clock)
                    val contributions = if (goalsSnapshot.ownerId != null && contributionsSnapshot.ownerId == goalsSnapshot.ownerId) {
                        contributionsSnapshot.contributions
                    } else {
                        emptyList()
                    }
                    val streak = GoalProgressPolicy.calculateStreak(contributions, today)
                    val badges = GoalProgressPolicy.deriveBadges(
                        goals = goalsSnapshot.goals,
                        contributions = contributions,
                        todayEpochDay = today
                    )
                    _uiState.update {
                        GoalRewardsState(
                            isLoading = false,
                            ownerId = goalsSnapshot.ownerId,
                            error = if (goalsSnapshot.ownerId == null) GoalMessages.SIGN_IN else null,
                            streakSummary = streak,
                            badges = badges,
                            earnedCount = badges.count { b -> b.isEarned }
                        )
                    }
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

