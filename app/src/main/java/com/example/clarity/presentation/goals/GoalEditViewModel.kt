package com.example.clarity.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.usecase.goal.CreateGoalUseCase
import com.example.clarity.domain.usecase.goal.GetGoalUseCase
import com.example.clarity.domain.usecase.goal.UpdateGoalUseCase
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.MoneyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val createGoal: CreateGoalUseCase,
    private val updateGoal: UpdateGoalUseCase,
    private val getGoal: GetGoalUseCase,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository,
    private val clock: Clock
) : ViewModel() {

    private val goalIdArg: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }
    private val generatedCreateId: String = UUID.randomUUID().toString()
    private var loadedGoal: FinancialGoal? = null

    private val _uiState = MutableStateFlow(
        GoalEditState(
            goalId = goalIdArg,
            isEditMode = goalIdArg != null
        )
    )
    val uiState: StateFlow<GoalEditState> = _uiState.asStateFlow()

    init {
        loadInitialState()
    }

    fun loadInitialState() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, fatalError = null, formError = null) }
            try {
                val today = GoalPolicy.todayEpochDay(clock)
                val existingId = goalIdArg
                if (existingId == null) {
                    val currency = currencyPreferenceRepository.current()
                    val defaultTargetDay = today + 30L
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEditMode = false,
                            currency = currency,
                            targetDateEpochDay = defaultTargetDay,
                            selectedDateUtcMillis = epochDayToUtcMillis(defaultTargetDay),
                            targetDateFormatted = formatGoalEpochDay(defaultTargetDay)
                        )
                    }
                } else {
                    val result = getGoal(existingId)
                    val goal = result.getOrElse { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                fatalError = goalErrorMessage(error, GoalMessages.OPEN_ERROR)
                            )
                        }
                        return@launch
                    }
                    if (goal == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                fatalError = GoalMessages.NOT_FOUND
                            )
                        }
                        return@launch
                    }
                    loadedGoal = goal
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isEditMode = true,
                            currency = goal.currency,
                            name = goal.name,
                            description = goal.description,
                            targetAmount = MoneyUtils.formatMinor(goal.targetMinor, goal.currency),
                            targetDateEpochDay = goal.targetDateEpochDay,
                            selectedDateUtcMillis = epochDayToUtcMillis(goal.targetDateEpochDay),
                            targetDateFormatted = formatGoalEpochDay(goal.targetDateEpochDay)
                        )
                    }
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fatalError = goalErrorMessage(error, GoalMessages.OPEN_ERROR)
                    )
                }
            }
        }
    }

    fun onNameChanged(value: String) {
        _uiState.update {
            it.copy(
                name = value,
                nameError = null,
                formError = null
            )
        }
    }

    fun onDescriptionChanged(value: String) {
        _uiState.update {
            it.copy(
                description = value,
                descriptionError = null,
                formError = null
            )
        }
    }

    fun onTargetAmountChanged(value: String) {
        _uiState.update {
            it.copy(
                targetAmount = value,
                targetAmountError = null,
                formError = null
            )
        }
    }

    fun onTargetDateSelected(utcMillis: Long?) {
        val millis = utcMillis ?: return
        val epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
        _uiState.update {
            it.copy(
                targetDateEpochDay = epochDay,
                selectedDateUtcMillis = epochDayToUtcMillis(epochDay),
                targetDateFormatted = formatGoalEpochDay(epochDay),
                targetDateError = null,
                formError = null
            )
        }
    }

    fun save() {
        val current = _uiState.value
        if (current.isLoading || current.isSaving || current.isSaved || current.fatalError != null) return

        val trimmedName = current.name.trim()
        val trimmedDescription = current.description.trim()
        var nameError: String? = null
        var descriptionError: String? = null
        var targetError: String? = null
        var dateError: String? = null

        if (trimmedName.isEmpty()) {
            nameError = GoalMessages.NAME_BLANK
        } else if (trimmedName.length > GoalPolicy.MAX_NAME_LENGTH || trimmedName.any { it.isISOControl() }) {
            nameError = GoalMessages.NAME_TOO_LONG
        }

        if (trimmedDescription.length > GoalPolicy.MAX_DESCRIPTION_LENGTH) {
            descriptionError = GoalMessages.DESCRIPTION_TOO_LONG
        }

        val parsedTargetMinor = GoalPolicy.parseAmountMinor(current.targetAmount, current.currency)
            .getOrElse {
                targetError = GoalMessages.INVALID_AMOUNT
                null
            }
        if (parsedTargetMinor != null && parsedTargetMinor <= 0L) {
            targetError = GoalMessages.TARGET_NON_POSITIVE
        }

        val today = GoalPolicy.todayEpochDay(clock)
        val existing = loadedGoal
        if (!GoalPolicy.validEpochDay(current.targetDateEpochDay)) {
            dateError = GoalMessages.INVALID_DATE
        } else if (!current.isEditMode && current.targetDateEpochDay <= today) {
            dateError = GoalMessages.DATE_NOT_IN_FUTURE
        } else if (current.isEditMode && existing != null &&
            current.targetDateEpochDay != existing.targetDateEpochDay &&
            current.targetDateEpochDay <= today
        ) {
            dateError = GoalMessages.DATE_NOT_IN_FUTURE
        }

        if (nameError != null || descriptionError != null || targetError != null || dateError != null) {
            _uiState.update {
                it.copy(
                    nameError = nameError,
                    descriptionError = descriptionError,
                    targetAmountError = targetError,
                    targetDateError = dateError
                )
            }
            return
        }

        val targetMinor = parsedTargetMinor ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    nameError = null,
                    descriptionError = null,
                    targetAmountError = null,
                    targetDateError = null,
                    formError = null
                )
            }
            val result = if (current.isEditMode) {
                val base = existing ?: run {
                    _uiState.update {
                        it.copy(isSaving = false, fatalError = GoalMessages.NOT_FOUND)
                    }
                    return@launch
                }
                updateGoal(
                    base.copy(
                        name = trimmedName,
                        description = trimmedDescription,
                        targetMinor = targetMinor,
                        targetDateEpochDay = current.targetDateEpochDay
                    )
                )
            } else {
                createGoal(
                    FinancialGoal(
                        id = generatedCreateId,
                        name = trimmedName,
                        description = trimmedDescription,
                        targetMinor = targetMinor,
                        currency = current.currency,
                        targetDateEpochDay = current.targetDateEpochDay
                    )
                )
            }

            result.fold(
                onSuccess = {
                    _uiState.update { it.copy(isSaving = false, isSaved = true) }
                },
                onFailure = { error ->
                    applySaveFailure(error)
                }
            )
        }
    }

    private fun applySaveFailure(error: Throwable) {
        val message = goalErrorMessage(error, GoalMessages.SAVE_ERROR)
        val goalEx = error as? GoalException
        _uiState.update { state ->
            when (goalEx?.error) {
                GoalError.NAME_BLANK, GoalError.NAME_TOO_LONG ->
                    state.copy(isSaving = false, nameError = message)
                GoalError.DESCRIPTION_TOO_LONG ->
                    state.copy(isSaving = false, descriptionError = message)
                GoalError.INVALID_AMOUNT, GoalError.TARGET_NON_POSITIVE ->
                    state.copy(isSaving = false, targetAmountError = message)
                GoalError.INVALID_DATE, GoalError.DATE_NOT_IN_FUTURE ->
                    state.copy(isSaving = false, targetDateError = message)
                GoalError.NOT_FOUND ->
                    state.copy(isSaving = false, fatalError = message)
                else ->
                    state.copy(isSaving = false, formError = message)
            }
        }
    }

    private fun epochDayToUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
