package com.example.clarity.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.goal.ContributeToGoalUseCase
import com.example.clarity.domain.usecase.goal.GetGoalUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalContributionsUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalsUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.GoalFundingAvailability
import com.example.clarity.domain.util.GoalFundingLimitFactor
import com.example.clarity.domain.util.GoalFundingPolicy
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
import java.math.BigInteger
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGoal: GetGoalUseCase,
    private val observeGoals: ObserveGoalsUseCase,
    private val observeGoalContributions: ObserveGoalContributionsUseCase,
    private val contributeToGoal: ContributeToGoalUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val getTransactions: GetTransactionsUseCase,
    private val observeCurrency: ObserveCurrencyPreferenceUseCase,
    private val clock: Clock
) : ViewModel() {

    private val goalIdArg: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }
    private var observeJob: Job? = null
    private var currentGoal: FinancialGoal? = null
    private var currentSavedMinor: BigInteger = BigInteger.ZERO
    private var latestAvailability: GoalFundingAvailability? = null
    private var pendingContributionId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(
        GoalProgressState(goalId = goalIdArg)
    )
    val uiState: StateFlow<GoalProgressState> = _uiState.asStateFlow()

    init {
        loadAndObserve()
    }

    fun loadAndObserve() {
        val targetId = goalIdArg
        if (targetId == null || !GoalPolicy.validId(targetId)) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    fatalError = GoalMessages.NOT_FOUND
                )
            }
            return
        }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, fatalError = null, formError = null) }
            val initialResult = getGoal(targetId)
            val initialGoal = initialResult.getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fatalError = goalErrorMessage(error, GoalMessages.OPEN_ERROR)
                    )
                }
                return@launch
            }
            if (initialGoal == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fatalError = GoalMessages.NOT_FOUND
                    )
                }
                return@launch
            }
            currentGoal = initialGoal

            combine(
                observeGoals(),
                observeGoalContributions(),
                getAccounts().catch { emit(Resource.Success(emptyList())) },
                getTransactions().catch { emit(Resource.Success(emptyList())) },
                observeCurrency()
            ) { goalsSnapshot, contributionsSnapshot, accountsRes, txRes, pref ->
                CombinedProgressInput(
                    goalsSnapshot = goalsSnapshot,
                    contributionsSnapshot = contributionsSnapshot,
                    accounts = accountsRes.data.orEmpty(),
                    accountsLoading = accountsRes is Resource.Loading,
                    transactions = txRes.data.orEmpty(),
                    preferredCurrency = pref.currency
                )
            }
                .catch { error ->
                    if (error is CancellationException) throw error
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            fatalError = goalErrorMessage(error, GoalMessages.OPEN_ERROR)
                        )
                    }
                }
                .collect { input ->
                    val goal = input.goalsSnapshot.goals.firstOrNull { it.id == targetId } ?: currentGoal
                    if (goal == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                fatalError = GoalMessages.NOT_FOUND
                            )
                        }
                        return@collect
                    }
                    currentGoal = goal
                    val goalContributions = input.contributionsSnapshot.contributions
                        .filter { it.goalId == goal.id && it.amountMinor > 0L && it.currency == goal.currency }
                        .sortedWith(
                            compareByDescending<GoalContribution> { it.dateEpochDay }
                                .thenByDescending { it.createdAt }
                                .thenBy { it.id }
                        )
                    val availability = GoalFundingPolicy.evaluate(
                        transactions = input.transactions,
                        accounts = input.accounts,
                        currency = goal.currency
                    )
                    latestAvailability = availability
                    applyGoalAndContributions(
                        goal = goal,
                        goalContributions = goalContributions,
                        availability = availability,
                        accountsLoading = input.accountsLoading,
                        preferredCurrency = input.preferredCurrency
                    )
                }
        }
    }

    private fun applyGoalAndContributions(
        goal: FinancialGoal,
        goalContributions: List<GoalContribution>,
        availability: GoalFundingAvailability,
        accountsLoading: Boolean,
        preferredCurrency: String
    ) {
        val today = GoalPolicy.todayEpochDay(clock)
        val summary = GoalProgressPolicy.evaluateGoalProgress(goal, goalContributions)
        currentSavedMinor = summary.savedMinor
        val overdue = goal.isOverdue(today)
        val daysLeft = goal.daysRemaining(today)
        val daysLabel = if (overdue) "Overdue" else "$daysLeft days left"
        val percentLabel = if (summary.isCompleted) {
            "100% — Goal Reached"
        } else {
            "${summary.displayPercent}% saved"
        }
        val milestonesUi = summary.milestones.map { m ->
            GoalMilestoneUiModel(
                percent = m.threshold.percent,
                title = m.threshold.title,
                requiredFormatted = formatGoalMoney(m.requiredMinor, goal.currency),
                motivationText = m.threshold.motivationText,
                isReached = m.isReached
            )
        }
        val contributionsUi = goalContributions.map { c ->
            GoalContributionItemUiModel(
                id = c.id,
                amountFormatted = formatGoalMoney(c.amountMinor, c.currency),
                dateFormatted = formatGoalEpochDay(c.dateEpochDay),
                isSyncing = c.pendingSync
            )
        }

        val availableFormatted = formatGoalMoney(availability.availableToContributeMinor, goal.currency)
        val cashFormatted = formatGoalMoney(
            availability.cashBalanceMinor.coerceAtLeast(BigInteger.ZERO),
            goal.currency
        )
        val netWorthFormatted = formatGoalMoney(
            availability.netWorthMinor.coerceAtLeast(BigInteger.ZERO),
            goal.currency
        )
        val limitLabel = when (availability.limitingFactor) {
            GoalFundingLimitFactor.CASH_WALLET ->
                "Limited by Cash Wallet ($cashFormatted; Net Worth: $netWorthFormatted)"
            GoalFundingLimitFactor.NET_WORTH ->
                "Limited by Net Worth ($netWorthFormatted; Cash Wallet: $cashFormatted)"
            GoalFundingLimitFactor.BOTH ->
                "Limited by Cash Wallet and Net Worth ($cashFormatted)"
        }

        val fundingError = when {
            accountsLoading -> null
            availability.cashWallet == null -> GoalMessages.CASH_WALLET_REQUIRED
            !goal.currency.equals(availability.cashWallet.currency, ignoreCase = true) ||
                !goal.currency.equals(preferredCurrency, ignoreCase = true) ->
                GoalMessages.CURRENCY_MISMATCH
            else -> null
        }

        _uiState.update { old ->
            val (revalidatedError, isValid) = validateAmountInput(
                rawInput = old.amountInput,
                goal = goal,
                availability = availability
            )
            old.copy(
                isLoading = false,
                fatalError = null,
                name = goal.name,
                description = goal.description,
                currency = goal.currency,
                savedFormatted = formatGoalMoney(summary.savedMinor, goal.currency),
                targetFormatted = formatGoalMoney(goal.targetMinor, goal.currency),
                targetDateFormatted = formatGoalEpochDay(goal.targetDateEpochDay),
                daysRemainingLabel = daysLabel,
                isOverdue = overdue,
                progressFraction = summary.progressFraction,
                percentLabel = percentLabel,
                isCompleted = summary.isCompleted,
                availableToContributeFormatted = availableFormatted,
                availabilityLimitLabel = limitLabel,
                fundingError = fundingError,
                amountError = if (old.amountInput.isNotBlank()) revalidatedError else old.amountError,
                isAmountValid = isValid,
                milestones = milestonesUi,
                contributions = contributionsUi
            )
        }
    }

    private fun validateAmountInput(
        rawInput: String,
        goal: FinancialGoal?,
        availability: GoalFundingAvailability?
    ): Pair<String?, Boolean> {
        if (rawInput.isBlank()) return null to false
        val currency = goal?.currency ?: _uiState.value.currency
        val parsedMinor = GoalPolicy.parseAmountMinor(rawInput, currency).getOrElse {
            return GoalMessages.INVALID_AMOUNT to false
        }
        if (parsedMinor <= 0L) {
            return GoalMessages.CONTRIBUTION_AMOUNT_POSITIVE to false
        }
        if (availability != null && !availability.canAfford(parsedMinor)) {
            return GoalMessages.INSUFFICIENT_FUNDS to false
        }
        return null to true
    }

    fun onAmountChanged(value: String) {
        val (amountErr, isValid) = validateAmountInput(
            rawInput = value,
            goal = currentGoal,
            availability = latestAvailability
        )
        _uiState.update {
            it.copy(
                amountInput = value,
                amountError = amountErr,
                isAmountValid = isValid,
                formError = null
            )
        }
    }

    fun addContribution() {
        val state = _uiState.value
        val goal = currentGoal ?: return
        if (state.isLoading || state.isSaving || state.fatalError != null) return

        if (state.fundingError != null) {
            _uiState.update { it.copy(formError = state.fundingError) }
            return
        }

        val (validationError, isValid) = validateAmountInput(
            rawInput = state.amountInput,
            goal = goal,
            availability = latestAvailability
        )
        if (!isValid || validationError != null) {
            _uiState.update {
                it.copy(
                    amountError = validationError ?: GoalMessages.INVALID_AMOUNT,
                    isAmountValid = false
                )
            }
            return
        }

        val previousSaved = currentSavedMinor
        val attemptId = pendingContributionId

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSaving = true,
                    amountError = null,
                    formError = null,
                    celebrationTitle = null,
                    celebrationMessage = null
                )
            }
            val result = contributeToGoal(
                goalId = goal.id,
                amountText = state.amountInput,
                id = attemptId
            )
            result.fold(
                onSuccess = { savedContribution ->
                    pendingContributionId = UUID.randomUUID().toString()
                    val newSaved = previousSaved.add(BigInteger.valueOf(savedContribution.amountMinor))
                    val crossed = GoalProgressPolicy.newlyCrossedMilestone(
                        targetMinor = goal.targetMinor,
                        previousSavedMinor = previousSaved,
                        newSavedMinor = newSaved
                    )
                    val addedFormatted = formatGoalMoney(savedContribution.amountMinor, goal.currency)
                    val title = crossed?.title ?: "Progress Added"
                    val message = crossed?.motivationText
                        ?: "Added $addedFormatted toward ${goal.name}. Keep going!"
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            amountInput = "",
                            amountError = null,
                            isAmountValid = false,
                            formError = null,
                            celebrationTitle = title,
                            celebrationMessage = message
                        )
                    }
                },
                onFailure = { error ->
                    val msg = goalErrorMessage(error, GoalMessages.CONTRIBUTION_SAVE_ERROR)
                    val contribEx = error as? GoalContributionException
                    _uiState.update { s ->
                        when (contribEx?.error) {
                            GoalContributionError.INVALID_AMOUNT,
                            GoalContributionError.AMOUNT_NON_POSITIVE,
                            GoalContributionError.INSUFFICIENT_FUNDS ->
                                s.copy(isSaving = false, amountError = msg, isAmountValid = false)
                            GoalContributionError.GOAL_NOT_FOUND ->
                                s.copy(isSaving = false, fatalError = msg)
                            else ->
                                s.copy(isSaving = false, formError = msg)
                        }
                    }
                }
            )
        }
    }

    private data class CombinedProgressInput(
        val goalsSnapshot: com.example.clarity.domain.model.GoalsSnapshot,
        val contributionsSnapshot: com.example.clarity.domain.model.GoalContributionsSnapshot,
        val accounts: List<Account>,
        val accountsLoading: Boolean,
        val transactions: List<Transaction>,
        val preferredCurrency: String
    )
}
