package com.example.clarity.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.usecase.goal.ObserveGoalsUseCase
import com.example.clarity.domain.util.GoalPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val observeGoals: ObserveGoalsUseCase,
    private val goalRepository: GoalRepository,
    private val clock: Clock
) : ViewModel() {

    private val _uiState = MutableStateFlow(GoalsState())
    val uiState: StateFlow<GoalsState> = _uiState.asStateFlow()
    private var observeJob: Job? = null

    init {
        startObserving()
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            observeGoals()
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            error = goalErrorMessage(error, GoalMessages.LOAD_ERROR)
                        )
                    }
                }
                .collect { snapshot ->
                    val today = GoalPolicy.todayEpochDay(clock)
                    val goalsById = snapshot.goals.associateBy { it.id }
                    val visibleGoals = snapshot.goals.filter { it.status != GoalStatus.ARCHIVED }
                    val activeCards = visibleGoals
                        .filter { it.status == GoalStatus.ACTIVE }
                        .map { it.toCardUiModel(today, snapshot.isGoalSyncing(it)) }
                    val completedCards = visibleGoals
                        .filter { it.status == GoalStatus.COMPLETED }
                        .map { it.toCardUiModel(today, snapshot.isGoalSyncing(it)) }
                    val rejectedBanners = snapshot.rejectedOperations.map { op ->
                        RejectedGoalOperationUi(
                            id = op.id,
                            message = GoalMessages.rejectedOperationMessage(op, goalsById[op.goalId])
                        )
                    }

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
                            activeGoals = activeCards,
                            completedGoals = completedCards,
                            rejectedOperations = rejectedBanners,
                            error = nextError
                        )
                    }
                }
        }
    }

    fun retry() {
        startObserving()
        viewModelScope.launch {
            val result = goalRepository.retrySync()
            result.onFailure { error ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        error = goalErrorMessage(error, GoalMessages.LOAD_ERROR)
                    )
                }
            }
        }
    }

    fun dismissRejectedOperation(operationId: String) {
        viewModelScope.launch {
            goalRepository.dismissRejectedOperation(operationId)
        }
    }
}
