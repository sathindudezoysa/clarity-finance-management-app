package com.example.clarity.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.usecase.goal.CreateGoalUseCase
import com.example.clarity.domain.usecase.goal.GetGoalUseCase
import com.example.clarity.domain.usecase.goal.UpdateGoalUseCase
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.TransactionCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalEditViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getGoal: GetGoalUseCase,
    private val createGoal: CreateGoalUseCase,
    private val updateGoal: UpdateGoalUseCase,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository,
    private val clock: Clock
) : ViewModel() {

    private val goalId: String? = savedStateHandle["goalId"]
    private val draftId: String = savedStateHandle.get<String>(KEY_DRAFT_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_DRAFT_ID] = it }

    private val defaultEpochDay: Long = GoalPolicy.todayEpochDay(clock) + 30L
    private val initialEpochDay: Long = savedStateHandle.get<Long>(KEY_TARGET_EPOCH_DAY) ?: defaultEpochDay
    private val initialCurrency: String = savedStateHandle.get<String>(KEY_CURRENCY) ?: Currencies.supported.first()

    private var loadedGoal: FinancialGoal? = null

    private val _uiState = MutableStateFlow(
        GoalEditState(
            isEditMode = goalId != null,
            isLoading = true,
            canSave = false,
            name = savedStateHandle[KEY_NAME] ?: "",
            targetAmount = savedStateHandle[KEY_TARGET_AMOUNT] ?: "",
            savedAmount = savedStateHandle[KEY_SAVED_AMOUNT] ?: "0",
            description = savedStateHandle[KEY_DESCRIPTION] ?: "",
            currency = initialCurrency,
            targetDateEpochDay = initialEpochDay,
            selectedDateUtcMillis = epochDayToUtcMillis(initialEpochDay),
            formattedTargetDate = formatGoalEpochDay(initialEpochDay),
            currentSavedFormatted = formatGoalMoney(0L, initialCurrency),
            isSuccess = savedStateHandle[KEY_SAVED] ?: false
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val lookupId = goalId ?: draftId
            val result = getGoal(lookupId)
            result.onSuccess { existing ->
                loadedGoal = existing
                val hasFormDraft = savedStateHandle.get<Boolean>(KEY_FORM_LOADED) == true
                if (goalId == null) {
                    val committed = existing != null || _uiState.value.isSuccess
                    savedStateHandle[KEY_SAVED] = committed
                    val preferredCurrency = if (hasFormDraft) {
                        _uiState.value.currency
                    } else {
                        runCatching { currencyPreferenceRepository.current() }
                            .getOrElse { Currencies.supported.first() }
                    }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            canSave = true,
                            currency = preferredCurrency,
                            currentSavedFormatted = formatGoalMoney(0L, preferredCurrency),
                            isSuccess = committed
                        )
                    }
                    persistForm()
                } else if (existing == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            canSave = false,
                            error = GoalMessages.NOT_FOUND
                        )
                    }
                } else {
                    val epochDay = if (hasFormDraft) _uiState.value.targetDateEpochDay else existing.targetDateEpochDay
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            canSave = true,
                            name = if (hasFormDraft) state.name else existing.name,
                            targetAmount = if (hasFormDraft) {
                                state.targetAmount
                            } else {
                                MoneyUtils.formatMinor(existing.targetMinor, existing.currency)
                            },
                            savedAmount = MoneyUtils.formatMinor(existing.savedMinor, existing.currency),
                            description = if (hasFormDraft) state.description else existing.description,
                            currency = existing.currency,
                            targetDateEpochDay = epochDay,
                            selectedDateUtcMillis = epochDayToUtcMillis(epochDay),
                            formattedTargetDate = formatGoalEpochDay(epochDay),
                            currentSavedFormatted = formatGoalMoney(existing.savedMinor, existing.currency)
                        )
                    }
                    persistForm()
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

    fun onNameChange(value: String) = change {
        it.copy(name = value, nameError = null, error = null)
    }

    fun onTargetAmountChange(value: String) = change {
        it.copy(targetAmount = value, targetAmountError = null, error = null)
    }

    fun onSavedAmountChange(value: String) = change {
        if (it.isEditMode) it else it.copy(savedAmount = value, savedAmountError = null, error = null)
    }

    fun onDescriptionChange(value: String) = change {
        it.copy(description = value, descriptionError = null, error = null)
    }

    fun onTargetDateSelected(selectedUtcMillis: Long) = change {
        val epochDay = TransactionCalendar.dateAt(selectedUtcMillis, ZoneOffset.UTC).toEpochDay()
        it.copy(
            targetDateEpochDay = epochDay,
            selectedDateUtcMillis = epochDayToUtcMillis(epochDay),
            formattedTargetDate = formatGoalEpochDay(epochDay),
            targetDateError = null,
            error = null
        )
    }

    private fun change(transform: (GoalEditState) -> GoalEditState) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return
        _uiState.update(transform)
        persistForm()
    }

    private fun persistForm() {
        val state = _uiState.value
        savedStateHandle[KEY_NAME] = state.name
        savedStateHandle[KEY_TARGET_AMOUNT] = state.targetAmount
        savedStateHandle[KEY_SAVED_AMOUNT] = state.savedAmount
        savedStateHandle[KEY_DESCRIPTION] = state.description
        savedStateHandle[KEY_TARGET_EPOCH_DAY] = state.targetDateEpochDay
        savedStateHandle[KEY_CURRENCY] = state.currency
        savedStateHandle[KEY_FORM_LOADED] = true
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return

        _uiState.update {
            it.copy(
                nameError = null,
                targetAmountError = null,
                savedAmountError = null,
                targetDateError = null,
                descriptionError = null,
                error = null
            )
        }

        val targetResult = GoalPolicy.parseAmountMinor(state.targetAmount, state.currency)
        val targetMinor = targetResult.getOrElse { err ->
            applyErrorToFields(err)
            return
        }

        val savedMinor = if (state.isEditMode) {
            loadedGoal?.savedMinor ?: 0L
        } else {
            val savedText = state.savedAmount.trim().ifEmpty { "0" }
            GoalPolicy.parseAmountMinor(savedText, state.currency).getOrElse {
                _uiState.update { s -> s.copy(savedAmountError = GoalMessages.INVALID_AMOUNT) }
                return
            }
        }

        val base = loadedGoal ?: FinancialGoal(
            id = draftId,
            name = state.name,
            description = state.description,
            targetMinor = targetMinor,
            savedMinor = savedMinor,
            currency = state.currency,
            targetDateEpochDay = state.targetDateEpochDay
        )

        val candidate = base.copy(
            name = state.name,
            description = state.description,
            targetMinor = targetMinor,
            savedMinor = savedMinor,
            currency = state.currency,
            targetDateEpochDay = state.targetDateEpochDay
        )

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = if (goalId == null) createGoal(candidate) else updateGoal(candidate)
            result.onSuccess {
                savedStateHandle[KEY_SAVED] = true
                _uiState.update { it.copy(isSaving = false, isSuccess = true) }
            }.onFailure { err ->
                _uiState.update { it.copy(isSaving = false) }
                applyErrorToFields(err)
            }
        }
    }

    private fun applyErrorToFields(error: Throwable) {
        val goalError = (error as? GoalException)?.error
        val message = goalErrorMessage(error, GoalMessages.SAVE_ERROR)
        _uiState.update { state ->
            when (goalError) {
                GoalError.NAME_BLANK, GoalError.NAME_TOO_LONG ->
                    state.copy(nameError = message)
                GoalError.DESCRIPTION_TOO_LONG ->
                    state.copy(descriptionError = message)
                GoalError.INVALID_AMOUNT, GoalError.TARGET_NON_POSITIVE, GoalError.TARGET_BELOW_SAVED ->
                    state.copy(targetAmountError = message)
                GoalError.SAVED_NEGATIVE, GoalError.SAVED_EXCEEDS_TARGET ->
                    state.copy(savedAmountError = message)
                GoalError.INVALID_DATE, GoalError.DATE_NOT_IN_FUTURE ->
                    state.copy(targetDateError = message)
                else -> state.copy(error = message)
            }
        }
    }

    private fun epochDayToUtcMillis(epochDay: Long): Long =
        LocalDate.ofEpochDay(epochDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private const val KEY_NAME = "goal_edit_name"
private const val KEY_TARGET_AMOUNT = "goal_edit_target_amount"
private const val KEY_SAVED_AMOUNT = "goal_edit_saved_amount"
private const val KEY_DESCRIPTION = "goal_edit_description"
private const val KEY_TARGET_EPOCH_DAY = "goal_edit_target_epoch_day"
private const val KEY_CURRENCY = "goal_edit_currency"
private const val KEY_FORM_LOADED = "goal_edit_form_loaded"
private const val KEY_SAVED = "goal_edit_saved"
private const val KEY_DRAFT_ID = "goal_edit_draft_id"

