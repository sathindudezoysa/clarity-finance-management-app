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
    private val clock: Clock
) : ViewModel() {

    private val goalIdArg: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }
    private var observeJob: Job? = null
    private var currentGoal: FinancialGoal? = null
    private var currentSavedMinor: BigInteger = BigInteger.ZERO
    private var latestAccounts: List<Account> = emptyList()
    private var latestTransactions: List<Transaction> = emptyList()
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
                getTransactions().catch { emit(Resource.Success(emptyList())) }
            ) { goalsSnapshot, contributionsSnapshot, accountsRes, txRes ->
                CombinedProgressInput(
                    goalsSnapshot = goalsSnapshot,
                    contributionsSnapshot = contributionsSnapshot,
                    accounts = accountsRes.data.orEmpty(),
                    accountsLoading = accountsRes is Resource.Loading,
                    transactions = txRes.data.orEmpty()
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
                    latestAccounts = input.accounts
                    latestTransactions = input.transactions

                    val goalContributions = input.contributionsSnapshot.contributions
                        .filter { it.goalId == goal.id && it.amountMinor > 0L && it.currency == goal.currency }
                        .sortedWith(
                            compareByDescending<GoalContribution> { it.dateEpochDay }
                                .thenByDescending { it.createdAt }
                                .thenBy { it.id }
                        )

                    val eligibleOptions = GoalFundingPolicy.filterEligibleFundingAccounts(
                        accounts = input.accounts,
                        transactions = input.transactions,
                        goalCurrency = goal.currency
                    )
                    val currentSelectedId = _uiState.value.selectedAccountId
                    val resolvedAccountId = currentSelectedId?.takeIf { id ->
                        eligibleOptions.any { it.account.id == id }
                    }
                        ?: eligibleOptions.firstOrNull { it.account.isDefault }?.account?.id
                        ?: eligibleOptions.firstOrNull()?.account?.id

                    val availability = GoalFundingPolicy.evaluate(
                        transactions = input.transactions,
                        accounts = input.accounts,
                        currency = goal.currency,
                        selectedAccountId = resolvedAccountId
                    )
                    latestAvailability = availability
                    applyGoalAndContributions(
                        goal = goal,
                        goalContributions = goalContributions,
                        availability = availability,
                        accounts = input.accounts,
                        transactions = input.transactions,
                        accountsLoading = input.accountsLoading
                    )
                }
        }
    }

    private fun applyGoalAndContributions(
        goal: FinancialGoal,
        goalContributions: List<GoalContribution>,
        availability: GoalFundingAvailability,
        accounts: List<Account>,
        transactions: List<Transaction>,
        accountsLoading: Boolean
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

        val txById = transactions.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val contributionsUi = goalContributions.map { c ->
            val sourceAccountId = txById[c.id]?.accountId
            val sourceLabel = sourceAccountId?.let { accId ->
                accountsById[accId]?.displayLabel
                    ?: if (accId.startsWith(Account.CASH_WALLET_PREFIX)) "Cash Wallet" else null
            }
            GoalContributionItemUiModel(
                id = c.id,
                amountFormatted = formatGoalMoney(c.amountMinor, c.currency),
                dateFormatted = formatGoalEpochDay(c.dateEpochDay),
                isSyncing = c.pendingSync,
                sourceAccountLabel = sourceLabel
            )
        }

        val fundingAccountsUi = availability.eligibleAccounts.map { option ->
            GoalFundingAccountUiModel(
                id = option.account.id,
                label = option.account.displayLabel,
                currency = option.account.currency,
                balanceFormatted = formatGoalMoney(option.balanceMinor, goal.currency),
                isDefault = option.account.isDefault
            )
        }

        val selectedAccount = availability.selectedAccount
        val availableFormatted = if (selectedAccount != null) {
            formatGoalMoney(availability.availableToContributeMinor, goal.currency)
        } else {
            ""
        }
        val limitLabel = buildAvailabilityLimitLabel(goal.currency, availability)

        val fundingError = when {
            accountsLoading && availability.eligibleAccounts.isEmpty() -> null
            availability.matchingCurrencyAccounts.isEmpty() ->
                GoalMessages.noCurrencyAccounts(goal.currency)
            availability.eligibleAccounts.isEmpty() ->
                GoalMessages.noFundedCurrencyAccounts(goal.currency)
            else -> null
        }

        val goalAccountLabel = "${goal.name} (${goal.currency})"

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
                goalAccountLabel = goalAccountLabel,
                savedFormatted = formatGoalMoney(summary.savedMinor, goal.currency),
                targetFormatted = formatGoalMoney(goal.targetMinor, goal.currency),
                targetDateFormatted = formatGoalEpochDay(goal.targetDateEpochDay),
                daysRemainingLabel = daysLabel,
                isOverdue = overdue,
                progressFraction = summary.progressFraction,
                percentLabel = percentLabel,
                isCompleted = summary.isCompleted,
                fundingAccounts = fundingAccountsUi,
                selectedAccountId = selectedAccount?.id,
                selectedAccountLabel = selectedAccount?.displayLabel,
                accountError = if (selectedAccount != null) null else old.accountError,
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

    private fun buildAvailabilityLimitLabel(
        currency: String,
        availability: GoalFundingAvailability
    ): String {
        val selectedAccount = availability.selectedAccount ?: return ""
        val accountFormatted = formatGoalMoney(
            availability.accountBalanceMinor.coerceAtLeast(BigInteger.ZERO),
            currency
        )
        val netWorthFormatted = formatGoalMoney(
            availability.netWorthMinor.coerceAtLeast(BigInteger.ZERO),
            currency
        )
        return when (availability.limitingFactor) {
            GoalFundingLimitFactor.NET_WORTH ->
                "Limited by $currency Net Worth ($netWorthFormatted; ${selectedAccount.displayLabel}: $accountFormatted)"
            GoalFundingLimitFactor.ACCOUNT_BALANCE,
            GoalFundingLimitFactor.BOTH ->
                "Available in ${selectedAccount.displayLabel}: $accountFormatted"
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

    fun onAccountSelected(accountId: String) {
        val state = _uiState.value
        val goal = currentGoal ?: return
        if (state.isLoading || state.isSaving || state.fatalError != null) return
        val currentAvailability = latestAvailability ?: return
        if (currentAvailability.eligibleAccounts.none { it.account.id == accountId }) return

        val updatedAvailability = GoalFundingPolicy.evaluate(
            transactions = latestTransactions,
            accounts = latestAccounts,
            currency = goal.currency,
            selectedAccountId = accountId
        )
        latestAvailability = updatedAvailability
        val selectedAccount = updatedAvailability.selectedAccount
        val availableFormatted = if (selectedAccount != null) {
            formatGoalMoney(updatedAvailability.availableToContributeMinor, goal.currency)
        } else {
            ""
        }
        val limitLabel = buildAvailabilityLimitLabel(goal.currency, updatedAvailability)
        val (amountErr, isValid) = validateAmountInput(
            rawInput = state.amountInput,
            goal = goal,
            availability = updatedAvailability
        )

        _uiState.update {
            it.copy(
                selectedAccountId = selectedAccount?.id,
                selectedAccountLabel = selectedAccount?.displayLabel,
                accountError = null,
                availableToContributeFormatted = availableFormatted,
                availabilityLimitLabel = limitLabel,
                amountError = if (it.amountInput.isNotBlank()) amountErr else null,
                isAmountValid = isValid,
                formError = null
            )
        }
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

        val sourceAccountId = state.selectedAccountId
        if (sourceAccountId.isNullOrBlank()) {
            _uiState.update { it.copy(accountError = GoalMessages.ACCOUNT_REQUIRED) }
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
                    accountError = null,
                    amountError = null,
                    formError = null,
                    celebrationTitle = null,
                    celebrationMessage = null
                )
            }
            val result = contributeToGoal(
                goalId = goal.id,
                amountText = state.amountInput,
                sourceAccountId = sourceAccountId,
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
                            accountError = null,
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
                            GoalContributionError.ACCOUNT_REQUIRED,
                            GoalContributionError.ACCOUNT_NOT_FOUND,
                            GoalContributionError.CURRENCY_MISMATCH ->
                                s.copy(isSaving = false, accountError = msg)
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
        val transactions: List<Transaction>
    )
}
