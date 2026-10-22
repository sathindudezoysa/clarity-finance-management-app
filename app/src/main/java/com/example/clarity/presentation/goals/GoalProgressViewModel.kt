package com.example.clarity.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.GoalProgressOperation
import com.example.clarity.domain.model.GoalProgressOperationType
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.goal.GetGoalUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalsUseCase
import com.example.clarity.domain.usecase.goal.UpdateGoalProgressUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalProgressViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getGoal: GetGoalUseCase,
    private val observeGoals: ObserveGoalsUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val getTransactions: GetTransactionsUseCase,
    private val updateGoalProgress: UpdateGoalProgressUseCase
) : ViewModel() {

    private val goalId: String? = savedStateHandle["goalId"]
    val operationId: String = savedStateHandle.get<String>(KEY_OPERATION_ID)
        ?: UUID.randomUUID().toString().also { savedStateHandle[KEY_OPERATION_ID] = it }

    private var loadedGoal: FinancialGoal? = null
    private var activeOwnerId: String? = null
    private var latestAccounts: List<Account> = emptyList()
    private var latestTransactions: List<Transaction> = emptyList()
    private var latestOperations: List<GoalProgressOperation> = emptyList()
    private var accountsLoadedOnce: Boolean = false

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
            selectedAccountId = savedStateHandle[KEY_SELECTED_ACCOUNT_ID] ?: "",
            isSuccess = savedStateHandle[KEY_SAVED] ?: false
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        load()
        observeReactiveSources()
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
                    if (activeOwnerId == null && goal.userId.isNotEmpty()) {
                        activeOwnerId = goal.userId
                    }
                    _uiState.update { state ->
                        recomputeDerivedState(
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

    private fun observeReactiveSources() {
        viewModelScope.launch {
            combine(
                observeGoals(),
                getAccounts().catch { emit(Resource.Success(emptyList())) },
                getTransactions().catch { emit(Resource.Success(emptyList())) }
            ) { goalsSnapshot, accountsResource, txResource ->
                Triple(goalsSnapshot, accountsResource, txResource)
            }.catch { error ->
                if (error is CancellationException) throw error
            }.collect { (goalsSnapshot, accountsResource, txResource) ->
                val ownerChanged = activeOwnerId != null && goalsSnapshot.ownerId != activeOwnerId
                activeOwnerId = goalsSnapshot.ownerId

                if (accountsResource !is Resource.Loading) {
                    accountsLoadedOnce = true
                }
                latestAccounts = accountsResource.data.orEmpty()
                    .filter { goalsSnapshot.ownerId == null || it.userId == goalsSnapshot.ownerId }
                latestTransactions = txResource.data.orEmpty()
                latestOperations = goalsSnapshot.operations

                val targetId = goalId
                if (!targetId.isNullOrBlank()) {
                    goalsSnapshot.goals.firstOrNull { it.id == targetId }?.let { updatedGoal ->
                        loadedGoal = updatedGoal
                    }
                }

                _uiState.update { current ->
                    val goal = loadedGoal
                    val base = if (ownerChanged) {
                        savedStateHandle[KEY_SELECTED_ACCOUNT_ID] = ""
                        current.copy(
                            selectedAccountId = "",
                            accountError = null,
                            amountError = null,
                            error = if (goalsSnapshot.ownerId == null) GoalMessages.SIGN_IN else null
                        )
                    } else if (goal != null) {
                        current.copy(
                            goalId = goal.id,
                            goalName = goal.name,
                            currency = goal.currency,
                            targetFormatted = formatGoalMoney(goal.targetMinor, goal.currency),
                            currentSavedFormatted = formatGoalMoney(goal.savedMinor, goal.currency),
                            remainingFormatted = formatGoalMoney(goal.remainingMinor, goal.currency),
                            progressPercent = goal.progressPercent,
                            progressFraction = goal.progressFraction
                        )
                    } else {
                        current
                    }
                    recomputeDerivedState(base)
                }
            }
        }
    }

    fun onModeChange(mode: GoalProgressOperationType) = change { state ->
        state.copy(
            mode = mode,
            accountError = null,
            amountError = null,
            error = null
        )
    }

    fun onAmountChange(value: String) = change { state ->
        state.copy(
            amount = value,
            amountError = null,
            error = null
        )
    }

    fun onSelectAccount(accountId: String) = change { state ->
        val option = state.accounts.firstOrNull { it.id == accountId }
        if (option == null || !option.isEligible) {
            state
        } else {
            state.copy(
                selectedAccountId = accountId,
                accountError = null,
                error = null
            )
        }
    }

    private fun change(transform: (GoalProgressState) -> GoalProgressState) {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return
        _uiState.update { current ->
            recomputeDerivedState(transform(current))
        }
        persistForm()
    }

    private fun recomputeDerivedState(state: GoalProgressState): GoalProgressState {
        val goal = loadedGoal ?: return state
        val accountOptions = latestAccounts.map { account ->
            val hasConsistentCurrency = GoalPolicy.hasConsistentCurrencyUnits(
                accountId = account.id,
                accountCurrency = account.currency,
                transactions = latestTransactions
            )
            val availableMinor = if (hasConsistentCurrency) {
                GoalPolicy.availableBalanceForAccount(
                    accountId = account.id,
                    transactions = latestTransactions,
                    operations = latestOperations
                )
            } else {
                BigInteger.ZERO
            }
            val formattedAvailable = formatGoalMoneyBig(availableMinor, account.currency)
            val disabledReason = when {
                account.currency != goal.currency ->
                    "Currency mismatch (${account.currency}, goal uses ${goal.currency})"
                !hasConsistentCurrency ->
                    "Contains unconverted or cross-currency transactions"
                availableMinor <= BigInteger.ZERO ->
                    "No available balance"
                else -> null
            }
            GoalAccountOptionUi(
                id = account.id,
                displayLabel = account.displayLabel,
                currency = account.currency,
                availableBalanceMinor = availableMinor,
                availableBalanceFormatted = formattedAvailable,
                isEligible = disabledReason == null,
                disabledReason = disabledReason
            )
        }

        val hasEligibleAccount = accountOptions.any { it.isEligible }
        val previouslySelectedId = state.selectedAccountId
        val selectedOption = accountOptions.firstOrNull { it.id == previouslySelectedId }
        val accountVanished = accountsLoadedOnce &&
            previouslySelectedId.isNotBlank() &&
            selectedOption == null

        val effectiveSelectedId = when {
            selectedOption != null -> selectedOption.id
            accountVanished -> {
                savedStateHandle[KEY_SELECTED_ACCOUNT_ID] = ""
                ""
            }
            else -> previouslySelectedId
        }
        val effectiveAccountError = when {
            accountVanished -> GoalMessages.ACCOUNT_NOT_FOUND
            selectedOption != null && !selectedOption.isEligible && state.mode == GoalProgressOperationType.ADD ->
                selectedOption.disabledReason
            else -> state.accountError
        }

        val selectedAvailableFormatted = selectedOption
            ?.takeIf { it.isEligible }
            ?.availableBalanceFormatted

        val parsedAmountResult = if (state.amount.isBlank()) {
            null
        } else {
            GoalPolicy.parseAmountMinor(state.amount, goal.currency)
        }
        val parsedAmountMinor = parsedAmountResult?.getOrNull()

        var liveAmountError = state.amountError
        var previewUi: GoalContributionPreviewUi? = null
        var submitEnabled = false

        when (state.mode) {
            GoalProgressOperationType.ADD -> {
                if (parsedAmountMinor != null && parsedAmountMinor > 0L) {
                    val newSavedBig = BigInteger.valueOf(goal.savedMinor).add(BigInteger.valueOf(parsedAmountMinor))
                    if (newSavedBig > BigInteger.valueOf(goal.targetMinor)) {
                        if (liveAmountError == null) {
                            liveAmountError = GoalMessages.exceedsTargetMessage(goal.remainingMinor, goal.currency)
                        }
                    } else if (selectedOption != null && selectedOption.isEligible) {
                        if (BigInteger.valueOf(parsedAmountMinor) > selectedOption.availableBalanceMinor) {
                            if (liveAmountError == null) {
                                liveAmountError = GoalMessages.INSUFFICIENT_ACCOUNT_BALANCE
                            }
                        } else {
                            val domainPreview = GoalPolicy.previewContribution(
                                goal = goal,
                                accountAvailableMinor = selectedOption.availableBalanceMinor,
                                amountMinor = parsedAmountMinor
                            )
                            if (domainPreview != null) {
                                previewUi = GoalContributionPreviewUi(
                                    accountName = selectedOption.displayLabel,
                                    accountBeforeFormatted = formatGoalMoneyBig(domainPreview.accountBeforeMinor, goal.currency),
                                    accountAfterFormatted = formatGoalMoneyBig(domainPreview.accountAfterMinor, goal.currency),
                                    goalBeforeFormatted = formatGoalMoney(domainPreview.goalBeforeMinor, goal.currency),
                                    goalAfterFormatted = formatGoalMoney(domainPreview.goalAfterMinor, goal.currency),
                                    remainingAfterFormatted = formatGoalMoney(domainPreview.remainingAfterMinor, goal.currency)
                                )
                                submitEnabled = true
                            }
                        }
                    }
                }
            }
            GoalProgressOperationType.SET -> {
                if (parsedAmountMinor != null && parsedAmountMinor in 0L..goal.targetMinor) {
                    submitEnabled = true
                }
            }
        }

        return state.copy(
            accounts = accountOptions,
            selectedAccountId = effectiveSelectedId,
            selectedAccountAvailableFormatted = selectedAvailableFormatted,
            hasEligibleAccount = hasEligibleAccount,
            preview = previewUi,
            isSubmitEnabled = submitEnabled,
            accountError = effectiveAccountError,
            amountError = liveAmountError
        )
    }

    private fun persistForm() {
        val state = _uiState.value
        savedStateHandle[KEY_MODE] = state.mode.name
        savedStateHandle[KEY_AMOUNT] = state.amount
        savedStateHandle[KEY_SELECTED_ACCOUNT_ID] = state.selectedAccountId
    }

    fun save() {
        val state = _uiState.value
        if (state.isLoading || state.isSaving || state.isSuccess || !state.canSave) return

        // Validate in exact order:
        // 1. signed in -> 2. goal exists -> 3. account selected -> 4. currency match ->
        // 5. amount > 0 -> 6. amount <= goal remaining -> 7. amount <= account available balance
        val uid = activeOwnerId ?: loadedGoal?.userId?.takeIf { it.isNotBlank() }
        if (uid.isNullOrBlank()) {
            _uiState.update { it.copy(error = GoalMessages.SIGN_IN) }
            return
        }
        val goal = loadedGoal
        if (goal == null) {
            _uiState.update { it.copy(error = GoalMessages.NOT_FOUND) }
            return
        }

        _uiState.update { it.copy(accountError = null, amountError = null, error = null) }

        if (state.mode == GoalProgressOperationType.ADD) {
            val selectedId = state.selectedAccountId.trim()
            if (selectedId.isEmpty()) {
                _uiState.update { it.copy(accountError = GoalMessages.ACCOUNT_REQUIRED) }
                return
            }
            val account = latestAccounts.firstOrNull { it.id == selectedId && it.userId == uid }
            if (account == null) {
                _uiState.update { it.copy(accountError = GoalMessages.ACCOUNT_NOT_FOUND) }
                return
            }
            val hasConsistentUnits = GoalPolicy.hasConsistentCurrencyUnits(
                accountId = account.id,
                accountCurrency = account.currency,
                transactions = latestTransactions
            )
            if (account.currency != goal.currency || !hasConsistentUnits) {
                _uiState.update { it.copy(accountError = GoalMessages.ACCOUNT_CURRENCY_MISMATCH) }
                return
            }
            val amountMinor = GoalPolicy.parseAmountMinor(state.amount, goal.currency).getOrElse { err ->
                val msg = goalErrorMessage(err, GoalMessages.INVALID_AMOUNT)
                _uiState.update { it.copy(amountError = msg) }
                return
            }
            val availableBalance = GoalPolicy.availableBalanceForAccount(
                accountId = account.id,
                transactions = latestTransactions,
                operations = latestOperations
            )
            try {
                GoalPolicy.validateContributionWithAccount(
                    uid = uid,
                    goal = goal,
                    sourceAccountId = selectedId,
                    account = account,
                    accountCurrencyUnitsValid = hasConsistentUnits,
                    availableBalanceMinor = availableBalance,
                    amountMinor = amountMinor
                )
            } catch (error: GoalException) {
                applyValidationFailure(error)
                return
            }

            _uiState.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val result = updateGoalProgress.addContribution(
                    goalId = goal.id,
                    operationId = operationId,
                    amountMinor = amountMinor,
                    sourceAccountId = selectedId
                )
                result.onSuccess { updated ->
                    loadedGoal = updated
                    savedStateHandle[KEY_SAVED] = true
                    _uiState.update { it.copy(isSaving = false, isSuccess = true) }
                }.onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    applyValidationFailure(error)
                }
            }
        } else {
            val amountMinor = GoalPolicy.parseAmountMinor(state.amount, goal.currency).getOrElse { err ->
                val msg = goalErrorMessage(err, GoalMessages.INVALID_AMOUNT)
                _uiState.update { it.copy(amountError = msg) }
                return
            }
            _uiState.update { it.copy(isSaving = true) }
            viewModelScope.launch {
                val result = updateGoalProgress.setBalance(
                    goalId = goal.id,
                    operationId = operationId,
                    amountMinor = amountMinor
                )
                result.onSuccess { updated ->
                    loadedGoal = updated
                    savedStateHandle[KEY_SAVED] = true
                    _uiState.update { it.copy(isSaving = false, isSuccess = true) }
                }.onFailure { error ->
                    _uiState.update { it.copy(isSaving = false) }
                    applyValidationFailure(error)
                }
            }
        }
    }

    private fun applyValidationFailure(error: Throwable) {
        val goalEx = error as? GoalException
        val message = goalErrorMessage(error, GoalMessages.PROGRESS_SAVE_ERROR)
        val isAccountFieldError = goalEx?.error in setOf(
            GoalError.ACCOUNT_REQUIRED,
            GoalError.ACCOUNT_NOT_FOUND,
            GoalError.ACCOUNT_CURRENCY_MISMATCH
        )
        val isAmountFieldError = goalEx?.error in setOf(
            GoalError.INVALID_AMOUNT,
            GoalError.CONTRIBUTION_NON_POSITIVE,
            GoalError.SAVED_NEGATIVE,
            GoalError.EXCEEDS_TARGET,
            GoalError.INSUFFICIENT_ACCOUNT_BALANCE
        )
        _uiState.update {
            it.copy(
                accountError = if (isAccountFieldError) message else null,
                amountError = if (isAmountFieldError) message else null,
                error = if (!isAccountFieldError && !isAmountFieldError) message else null
            )
        }
    }
}

private const val KEY_OPERATION_ID = "goal_progress_operation_id"
private const val KEY_MODE = "goal_progress_mode"
private const val KEY_AMOUNT = "goal_progress_amount"
private const val KEY_SELECTED_ACCOUNT_ID = "goal_progress_selected_account_id"
private const val KEY_SAVED = "goal_progress_saved"
