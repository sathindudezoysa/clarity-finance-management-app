package com.example.clarity.presentation.goals

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalContribution
import com.example.clarity.domain.model.GoalContributionError
import com.example.clarity.domain.model.GoalContributionException
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.goal.ContributeToGoalUseCase
import com.example.clarity.domain.usecase.goal.FinishGoalUseCase
import com.example.clarity.domain.usecase.goal.GetGoalUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalContributionsUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalsUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.usecase.transaction.PreviewConversionUseCase
import com.example.clarity.domain.util.AccountBalancePolicy
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
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GoalProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getGoal: GetGoalUseCase,
    private val observeGoals: ObserveGoalsUseCase,
    private val observeGoalContributions: ObserveGoalContributionsUseCase,
    private val contributeToGoal: ContributeToGoalUseCase,
    private val finishGoalUseCase: FinishGoalUseCase,
    private val previewConversion: PreviewConversionUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val getTransactions: GetTransactionsUseCase,
    private val clock: Clock
) : ViewModel() {

    private val goalIdArg: String? = savedStateHandle.get<String>("goalId")?.takeIf { it.isNotBlank() }
    private var observeJob: Job? = null
    private var payoutPreviewJob: Job? = null
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

                    val txIds = input.transactions.mapTo(hashSetOf()) { it.id }
                    val goalContributions = input.contributionsSnapshot.contributions
                        .filter {
                            it.goalId == goal.id &&
                                it.amountMinor > 0L &&
                                it.currency == goal.currency &&
                                it.id in txIds
                        }
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
        val payoutSourceMinor = summary.savedMinor
            .min(BigInteger.valueOf(goal.targetMinor.coerceAtLeast(0L)))
        currentSavedMinor = summary.savedMinor

        val txById = transactions.associateBy { it.id }
        val accountsById = accounts.associateBy { it.id }
        val payoutTx = txById[FinishGoalUseCase.payoutTransactionId(goal.id)]
        val isClosed = payoutTx != null
        val isCompleted = summary.isCompleted || isClosed

        val overdue = goal.isOverdue(today) && !isCompleted
        val daysLeft = goal.daysRemaining(today)
        val daysLabel = when {
            isClosed -> "Goal completed & closed"
            isCompleted -> "Goal reached — Ready to finish"
            overdue -> "Overdue"
            else -> "$daysLeft days left"
        }
        val percentLabel = when {
            isClosed -> "100% — Completed & Closed"
            isCompleted -> "100% — Goal Reached (Locked)"
            else -> "${summary.displayPercent}% saved"
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

        val visiblePayoutAccounts = GoalFundingPolicy.filterVisibleAccounts(accounts)
        val payoutAccountsUi = visiblePayoutAccounts.map { account ->
            val balMinor = AccountBalancePolicy.calculateBalanceMinorSync(account, transactions)
            GoalFundingAccountUiModel(
                id = account.id,
                label = account.displayLabel,
                currency = account.currency,
                balanceFormatted = AccountBalancePolicy.formatAccountBalance(account, balMinor),
                isDefault = account.isDefault
            )
        }

        val currentPayoutId = _uiState.value.selectedPayoutAccountId
        val selectedPayoutAccount = visiblePayoutAccounts.firstOrNull { it.id == currentPayoutId }
            ?: visiblePayoutAccounts.firstOrNull { it.isDefault && it.currency.equals(goal.currency, ignoreCase = true) }
            ?: visiblePayoutAccounts.firstOrNull { it.currency.equals(goal.currency, ignoreCase = true) }
            ?: visiblePayoutAccounts.firstOrNull { it.isDefault }
            ?: visiblePayoutAccounts.firstOrNull()

        val selectedPayoutCurrency = selectedPayoutAccount?.currency ?: goal.currency
        val isCrossCurrencyPayout = selectedPayoutAccount != null &&
            !selectedPayoutCurrency.equals(goal.currency, ignoreCase = true)

        val closedPayoutFormatted = payoutTx?.let { formatGoalMoney(it.amountMinor, it.currency) }
        val closedDestinationAccountLabel = payoutTx?.accountId?.let { accId ->
            accountsById[accId]?.displayLabel
                ?: if (accId.startsWith(Account.CASH_WALLET_PREFIX)) "Cash Wallet" else "Bank Account"
        }
        val closedDateFormatted = payoutTx?.dateEpochMillis?.let { millis ->
            val epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
            formatGoalEpochDay(epochDay)
        }

        val selectedAccount = availability.selectedAccount
        val availableFormatted = if (selectedAccount != null) {
            formatGoalMoney(availability.availableToContributeMinor, goal.currency)
        } else {
            ""
        }
        val limitLabel = buildAvailabilityLimitLabel(goal.currency, availability)

        val fundingError = when {
            isCompleted || isClosed -> null
            accountsLoading && availability.eligibleAccounts.isEmpty() -> null
            availability.matchingCurrencyAccounts.isEmpty() ->
                GoalMessages.noCurrencyAccounts(goal.currency)
            availability.eligibleAccounts.isEmpty() ->
                GoalMessages.noFundedCurrencyAccounts(goal.currency)
            else -> null
        }

        val goalAccountLabel = "${goal.name} (${goal.currency})"

        val previousPayoutId = _uiState.value.selectedPayoutAccountId
        val previousPayoutCurrency = _uiState.value.selectedPayoutAccountCurrency

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
                savedFormatted = formatGoalMoney(
                    if (isCompleted) payoutSourceMinor else summary.savedMinor,
                    goal.currency
                ),
                targetFormatted = formatGoalMoney(goal.targetMinor, goal.currency),
                targetDateFormatted = formatGoalEpochDay(goal.targetDateEpochDay),
                daysRemainingLabel = daysLabel,
                isOverdue = overdue,
                progressFraction = summary.progressFraction,
                percentLabel = percentLabel,
                isCompleted = isCompleted,
                isClosed = isClosed,
                closedPayoutFormatted = closedPayoutFormatted,
                closedDestinationAccountLabel = closedDestinationAccountLabel,
                closedDateFormatted = closedDateFormatted,
                payoutAccounts = payoutAccountsUi,
                selectedPayoutAccountId = selectedPayoutAccount?.id,
                selectedPayoutAccountLabel = selectedPayoutAccount?.displayLabel,
                selectedPayoutAccountCurrency = selectedPayoutCurrency,
                isCrossCurrencyPayout = isCrossCurrencyPayout,
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

        if (isCompleted && !isClosed && selectedPayoutAccount != null) {
            val needsRefresh = previousPayoutId != selectedPayoutAccount.id ||
                !previousPayoutCurrency.equals(selectedPayoutCurrency, ignoreCase = true) ||
                (isCrossCurrencyPayout && _uiState.value.payoutPreview == null && !_uiState.value.payoutPreviewLoading)
            if (needsRefresh) {
                refreshPayoutPreview(goal, payoutSourceMinor, selectedPayoutCurrency)
            }
        }
    }

    private fun refreshPayoutPreview(
        goal: FinancialGoal,
        savedMinor: BigInteger,
        destinationCurrency: String
    ) {
        payoutPreviewJob?.cancel()
        val sourceCurrency = goal.currency.uppercase()
        val targetCurrency = destinationCurrency.uppercase()
        val boundedSavedMinor = savedMinor.min(BigInteger.valueOf(goal.targetMinor.coerceAtLeast(0L)))
        val amountMinorLong = runCatching { boundedSavedMinor.longValueExact() }
            .getOrElse { goal.targetMinor.coerceAtLeast(0L) }
        if (amountMinorLong <= 0L) return

        if (sourceCurrency == targetCurrency) {
            val identity = ConversionSnapshot.identity(amountMinorLong)
            _uiState.update {
                it.copy(
                    isCrossCurrencyPayout = false,
                    payoutPreview = identity,
                    payoutPreviewFormatted = formatGoalMoney(amountMinorLong, targetCurrency),
                    payoutPreviewLoading = false,
                    payoutPreviewError = false
                )
            }
            return
        }

        payoutPreviewJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCrossCurrencyPayout = true,
                    payoutPreview = null,
                    payoutPreviewFormatted = null,
                    payoutPreviewLoading = true,
                    payoutPreviewError = false,
                    formError = null
                )
            }
            try {
                val snapshot = previewConversion(amountMinorLong, sourceCurrency, targetCurrency)
                _uiState.update {
                    it.copy(
                        payoutPreview = snapshot,
                        payoutPreviewFormatted = formatGoalMoney(snapshot.amountMinor, targetCurrency),
                        payoutPreviewLoading = false,
                        payoutPreviewError = false
                    )
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        payoutPreview = null,
                        payoutPreviewFormatted = null,
                        payoutPreviewLoading = false,
                        payoutPreviewError = true,
                        formError = "Transfer blocked: Unable to convert $sourceCurrency to $targetCurrency because there is no network service. Please connect to the internet and try again."
                    )
                }
            }
        }
    }

    fun onPayoutAccountSelected(accountId: String) {
        val state = _uiState.value
        val goal = currentGoal ?: return
        if (state.isLoading || state.isFinishing || state.isClosed) return
        val account = latestAccounts.firstOrNull { it.id == accountId && !it.isGoalAccount } ?: return
        val crossCurrency = !account.currency.equals(goal.currency, ignoreCase = true)
        _uiState.update {
            it.copy(
                selectedPayoutAccountId = account.id,
                selectedPayoutAccountLabel = account.displayLabel,
                selectedPayoutAccountCurrency = account.currency,
                payoutAccountError = null,
                isCrossCurrencyPayout = crossCurrency,
                formError = null
            )
        }
        refreshPayoutPreview(goal, currentSavedMinor, account.currency)
    }

    fun retryPayoutPreview() {
        val goal = currentGoal ?: return
        val targetCurrency = _uiState.value.selectedPayoutAccountCurrency
        refreshPayoutPreview(goal, currentSavedMinor, targetCurrency)
    }

    fun finishGoal() {
        val state = _uiState.value
        val goal = currentGoal ?: return
        if (state.isLoading || state.isFinishing || state.isClosed || !state.isCompleted) return

        val destinationAccountId = state.selectedPayoutAccountId
        if (destinationAccountId.isNullOrBlank()) {
            _uiState.update {
                it.copy(payoutAccountError = "Select a destination account to receive your goal funds.")
            }
            return
        }

        if (state.isCrossCurrencyPayout && (state.payoutPreviewError || state.payoutPreview == null)) {
            _uiState.update {
                it.copy(
                    formError = "Transfer blocked: Unable to convert ${goal.currency} to ${state.selectedPayoutAccountCurrency} because there is no network service. Please connect to the internet and try again."
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isFinishing = true,
                    payoutAccountError = null,
                    formError = null,
                    celebrationTitle = null,
                    celebrationMessage = null
                )
            }
            val result = finishGoalUseCase(
                goalId = goal.id,
                destinationAccountId = destinationAccountId
            )
            result.fold(
                onSuccess = { finished ->
                    val payoutFormatted = formatGoalMoney(
                        finished.payoutTransaction.amountMinor,
                        finished.payoutTransaction.currency
                    )
                    val closedDate = formatGoalEpochDay(GoalPolicy.todayEpochDay(clock))
                    _uiState.update {
                        it.copy(
                            isFinishing = false,
                            isClosed = true,
                            closedPayoutFormatted = payoutFormatted,
                            closedDestinationAccountLabel = finished.destinationAccount.displayLabel,
                            closedDateFormatted = closedDate,
                            formError = null,
                            celebrationTitle = "Goal Completed & Closed",
                            celebrationMessage = "Transferred $payoutFormatted to ${finished.destinationAccount.displayLabel} and closed the goal account."
                        )
                    }
                },
                onFailure = { error ->
                    val message = error.message?.takeIf { it.isNotBlank() }
                        ?: "Could not finish and close this goal. Please try again."
                    _uiState.update {
                        it.copy(
                            isFinishing = false,
                            formError = message
                        )
                    }
                }
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
        if (goal != null && goal.targetMinor > 0L) {
            val remainingMinor = BigInteger.valueOf(goal.targetMinor)
                .subtract(currentSavedMinor)
                .coerceAtLeast(BigInteger.ZERO)
            if (BigInteger.valueOf(parsedMinor) > remainingMinor) {
                val remainingFormatted = formatGoalMoney(remainingMinor, currency)
                return "Amount cannot exceed remaining goal target ($remainingFormatted)." to false
            }
        }
        return null to true
    }

    fun onAccountSelected(accountId: String) {
        val state = _uiState.value
        val goal = currentGoal ?: return
        if (state.isLoading || state.isSaving || state.isCompleted || state.isClosed || state.fatalError != null) return
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
        if (_uiState.value.isCompleted || _uiState.value.isClosed) return
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
        if (state.isLoading || state.isSaving || state.isCompleted || state.isClosed || state.fatalError != null) return

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
