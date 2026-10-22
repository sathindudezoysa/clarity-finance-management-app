package com.example.clarity.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.GoalStatus
import com.example.clarity.domain.repository.GoalRepository
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.goal.ObserveGoalsUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionsUseCase
import com.example.clarity.domain.util.GoalPolicy
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
import javax.inject.Inject

@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val observeGoals: ObserveGoalsUseCase,
    private val getAccounts: GetAccountsUseCase,
    private val getTransactions: GetTransactionsUseCase,
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
            combine(
                observeGoals(),
                getAccounts().catch { emit(Resource.Success(emptyList())) },
                getTransactions().catch { emit(Resource.Success(emptyList())) }
            ) { snapshot, accountsResource, txResource ->
                Triple(snapshot, accountsResource.data.orEmpty(), txResource.data.orEmpty())
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
                .collect { (snapshot, accounts, transactions) ->
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
                    val overdrawnWarnings = accounts
                        .filter { account ->
                            snapshot.ownerId != null &&
                                account.userId == snapshot.ownerId &&
                                GoalPolicy.hasConsistentCurrencyUnits(
                                    accountId = account.id,
                                    accountCurrency = account.currency,
                                    transactions = transactions
                                )
                        }
                        .mapNotNull { account ->
                            val reserved = GoalPolicy.reservedFromAccount(account.id, snapshot.operations)
                            val available = GoalPolicy.availableBalanceForAccount(
                                accountId = account.id,
                                transactions = transactions,
                                operations = snapshot.operations
                            )
                            if (available < BigInteger.ZERO && reserved > BigInteger.ZERO) {
                                "Account \"${account.displayLabel}\" has a negative available balance (${formatGoalMoneyBig(available, account.currency)}) after remote reconciliation. Further debits from this account are blocked until its balance is restored."
                            } else {
                                null
                            }
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
                            overdrawnAccountWarnings = overdrawnWarnings,
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
