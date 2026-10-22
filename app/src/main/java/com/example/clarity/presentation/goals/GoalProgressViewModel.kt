package com.example.clarity.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.usecase.goal.GetGoalUseCase
import com.example.clarity.domain.usecase.goal.UpdateGoalProgressUseCase
import com.example.clarity.domain.util.GoalPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalProgressViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getGoal: GetGoalUseCase,
    private val updateGoalProgress: UpdateGoalProgressUseCase
) : ViewModel() {

    private val goalId: String? = savedStateHandle["goalId"]
    val operationId: String = savedStateHandle.get<String>(KEY_OPERATION_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_OPERATION_ID] = it }

    private var loadedGoal: FinancialGoal? = null

    private val initialMode = savedStateHandle.get<String>(KEY_MODE)
        ?.let { runCatching { GoalProgressOperationType.valueOf(it) }.getOrNull() }
        ?: GoalProgressOperationType.ADD

    private val _uiState = MutableStateFlow(
        GoalProgressState(
            isLoading = true,
            canSave = false,
            goalId = goalId.orEmpty(),
            mode = initialMode,
            amount = savedStateHandle[KEY_AMOUNT] ?: "",
            isSuccess = savedStateHandle[KEY_SAVED] ?: false
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val targetGoalId = goalId
            if (targetGoalId.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        canSave = false,
                        error = GoalMessages.NOT_FOUND
                    )
                }
                return@launch
            }
            val result = getGoal(targetGoalId)
            result.onSuccess { goal ->
                loadedGoal = goal
                if (goal == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            canSave = false,
                            error = GoalMessages.NOT_FOUND
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            canSave = true,
                            goalId = goal.id,
                            goalName = goal.name,
                            currency = goal.currency,
                            targetFormatted = formatGoalMoney(goal.targetMinor, goal.currency),
                            currentSavedFormatted = formatGoalMoney(goal.savedMinor, goal.currency),
                            remainingFormatted = formatGoalMoney(goal.remainingMinor, goal.currency),
                            progressPercent = goal.progressPercent,
                            progressFraction = goal.progressFraction
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        canSave = false,
                        error = goalErrorMessage(error, GoalMessages.OPEN_ERROR)
                    )
                }
            }
        }
    }

    fun onModeChange(mode: GoalProgressOperationType) = change {
        it.copy(mode = mode, amountError = null, error = null)
    }

    fun onAmountChange(value: String) = change {
        it.copy(amount = value, amountError = null, error = null)
    }

    private fun change(transform: (GoalProgressState) -> GoalProgressState) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return
        _uiState.update(transform)
        persistForm()
    }

    private fun persistForm() {
        val state = _uiState.value
        savedStateHandle[KEY_MODE] = state.mode.name
        savedStateHandle[KEY_AMOUNT] = state.amount
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return
        val goal = loadedGoal ?: return

        _uiState.update { it.copy(amountError = null, error = null) }

        val amountMinor = GoalPolicy.parseAmountMinor(state.amount, goal.currency).getOrElse { err ->
            val msg = goalErrorMessage(err, GoalMessages.INVALID_AMOUNT)
            _uiState.update { it.copy(amountError = msg) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = updateGoalProgress(
                goalId = goal.id,
                operationId = operationId,
                type = state.mode,
                amountMinor = amountMinor
            )
            result.onSuccess { updated ->
                loadedGoal = updated
                savedStateHandle[KEY_SAVED] = true
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { error ->
                val goalEx = error as? GoalException
                val message = goalErrorMessage(error, GoalMessages.PROGRESS_SAVE_ERROR)
                val isFieldError = goalEx?.error in setOf(
                    GoalError.INVALID_AMOUNT,
                    GoalError.CONTRIBUTION_NON_POSITIVE,
                    GoalError.SAVED_NEGATIVE,
                    GoalError.EXCEEDS_TARGET
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        amountError = if (isFieldError) message else null,
                        error = if (isFieldError) null else message
                    )
                }
            }
        }
    }
}

private const val KEY_OPERATION_ID = "goal_progress_operation_id"
private const val KEY_MODE = "goal_progress_mode"
private const val KEY_AMOUNT = "goal_progress_amount"
private const val KEY_SAVED = "goal_progress_saved"

