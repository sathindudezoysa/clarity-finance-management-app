package com.example.clarity.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.BudgetOverview
import com.example.clarity.domain.usecase.budget.*
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import javax.inject.Inject

data class BudgetEditor(
    val budget: Budget? = null,
    val category: String = "",
    val month: String,
    val amount: String = "",
    val currency: String,
    val owner: String,
    val categoryError: String? = null,
    val monthError: String? = null,
    val amountError: String? = null,
    val duplicateError: String? = null
)

data class BudgetsState(
    val loading: Boolean = true,
    val overview: BudgetOverview = BudgetOverview(),
    val tags: List<String> = TransactionTags.predefined,
    val currency: String = "LKR",
    val editor: BudgetEditor? = null,
    val confirmDisable: Budget? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val currentMonth: String = "",
    val selectedMonth: String = "",
    val requestedSelectedId: String? = null,
    val handledSelectedId: String? = null
) {
    val monthView: BudgetMonthView
        get() = BudgetSummary.forMonth(overview.statuses, selectedMonth.ifBlank { currentMonth })
}

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    observe: ObserveBudgetsUseCase,
    tags: GetTransactionTagsUseCase,
    preference: ObserveCurrencyPreferenceUseCase,
    private val saveBudget: SaveBudgetUseCase,
    private val disableBudget: DisableBudgetUseCase,
    private val resolve: ResolveBudgetCurrencyUseCase,
    private val clock: Clock,
    private val auth: AuthRepository
) : ViewModel() {
    private val initialMonth = BudgetPolicy.currentMonth(clock)
    private val mutable = MutableStateFlow(
        BudgetsState(currentMonth = initialMonth, selectedMonth = initialMonth)
    )
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            observe().catch { error ->
                if (error is CancellationException) throw error
                mutable.update { it.copy(loading = false, error = BudgetMessages.LOAD_ERROR) }
            }.collect { overview ->
                mutable.update { old ->
                    val changedOwner = old.overview.ownerId != overview.ownerId
                    val now = BudgetPolicy.currentMonth(clock)
                    val baseSelected = old.selectedMonth.ifBlank { now }
                    val baseHandled = if (changedOwner) null else old.handledSelectedId
                    val resolved = BudgetSummary.resolveSelection(
                        selectedMonth = baseSelected,
                        handledSelectedId = baseHandled,
                        selectedId = old.requestedSelectedId,
                        statuses = overview.statuses
                    )
                    old.copy(
                        loading = overview.loading,
                        overview = overview,
                        currentMonth = now,
                        selectedMonth = resolved.selectedMonth,
                        handledSelectedId = resolved.handledSelectedId,
                        editor = if (changedOwner) null else old.editor,
                        confirmDisable = if (changedOwner) null else old.confirmDisable,
                        error = if (changedOwner) null else old.error,
                        notice = if (changedOwner) null else old.notice
                    )
                }
            }
        }
        viewModelScope.launch { tags().collect { value -> mutable.update { it.copy(tags = value) } } }
        viewModelScope.launch { preference().collect { value -> mutable.update { it.copy(currency = value.currency) } } }
    }

    fun selectMonth(month: String) {
        if (!BudgetSummary.isValidMonth(month)) return
        mutable.update { it.copy(selectedMonth = month, error = null) }
    }

    fun previousMonth() {
        mutable.update {
            val base = it.selectedMonth.ifBlank { BudgetPolicy.currentMonth(clock) }
            it.copy(selectedMonth = BudgetSummary.shiftMonth(base, -1), error = null)
        }
    }

    fun nextMonth() {
        mutable.update {
            val base = it.selectedMonth.ifBlank { BudgetPolicy.currentMonth(clock) }
            it.copy(selectedMonth = BudgetSummary.shiftMonth(base, 1), error = null)
        }
    }

    fun applySelectedId(selectedId: String?) {
        mutable.update { old ->
            val resolved = BudgetSummary.resolveSelection(
                selectedMonth = old.selectedMonth.ifBlank { BudgetPolicy.currentMonth(clock) },
                handledSelectedId = old.handledSelectedId,
                selectedId = selectedId,
                statuses = old.overview.statuses
            )
            old.copy(
                requestedSelectedId = selectedId,
                selectedMonth = resolved.selectedMonth,
                handledSelectedId = resolved.handledSelectedId
            )
        }
    }

    fun edit(budget: Budget? = null) {
        val owner = state.value.overview.ownerId ?: return
        mutable.update {
            val defaultMonth = it.selectedMonth.ifBlank { BudgetPolicy.currentMonth(clock) }
            it.copy(
                editor = BudgetEditor(
                    budget = budget,
                    category = budget?.category ?: "",
                    month = budget?.month ?: defaultMonth,
                    amount = budget?.let { b ->
                        BigDecimal.valueOf(b.limitMinor, Currencies.digits(b.currency)).toPlainString()
                    } ?: "",
                    currency = budget?.currency ?: it.currency,
                    owner = owner
                ),
                error = null
            )
        }
    }

    fun changeCategory(value: String) {
        mutable.update {
            val current = it.editor ?: return@update it
            if (current.budget != null) return@update it
            it.copy(
                editor = current.copy(category = value, categoryError = null, duplicateError = null),
                error = null
            )
        }
    }

    fun changeMonth(value: String) {
        mutable.update {
            val current = it.editor ?: return@update it
            if (current.budget != null) return@update it
            it.copy(
                editor = current.copy(month = value, monthError = null, duplicateError = null),
                error = null
            )
        }
    }

    fun changeAmount(value: String) {
        mutable.update {
            val current = it.editor ?: return@update it
            it.copy(editor = current.copy(amount = value, amountError = null), error = null)
        }
    }

    fun closeEditor() {
        mutable.update { it.copy(editor = null, error = null) }
    }

    fun save() {
        val currentState = state.value
        val editor = currentState.editor ?: return
        val validation = BudgetSummary.validateEditor(
            existing = editor.budget,
            rawCategory = editor.category,
            rawMonth = editor.month,
            rawAmount = editor.amount,
            currency = editor.currency,
            owner = editor.owner,
            statuses = currentState.overview.statuses
        )
        val budget = validation.budget
        if (!validation.isValid || budget == null) {
            mutable.update {
                it.copy(
                    editor = it.editor?.copy(
                        categoryError = validation.categoryError,
                        monthError = validation.monthError,
                        amountError = validation.amountError,
                        duplicateError = validation.duplicateError
                    ),
                    error = validation.firstError ?: BudgetMessages.VALIDATION
                )
            }
            return
        }
        val noticeMessage = when {
            editor.budget == null -> BudgetMessages.SAVED_NOTICE
            !editor.budget.active -> BudgetMessages.REENABLED_NOTICE
            else -> BudgetMessages.UPDATED_NOTICE
        }
        action(BudgetMessages.SAVE_ERROR) {
            saveBudget(budget).getOrThrow()
            mutable.update {
                it.copy(
                    editor = if (it.editor?.owner == editor.owner) null else it.editor,
                    selectedMonth = budget.month,
                    notice = noticeMessage
                )
            }
        }
    }

    fun requestDisable(budget: Budget) {
        mutable.update { it.copy(confirmDisable = budget, error = null) }
    }

    fun dismissDisable() {
        mutable.update { it.copy(confirmDisable = null) }
    }

    fun confirmDisable() {
        val target = state.value.confirmDisable ?: return
        disable(target.id)
    }

    fun disable(id: String) = action(BudgetMessages.SAVE_ERROR) {
        disableBudget(id).getOrThrow()
        mutable.update { it.copy(confirmDisable = null, notice = BudgetMessages.DISABLED_NOTICE) }
    }

    fun retryCurrency(id: String) = action(BudgetMessages.CONVERSION_ERROR) {
        resolve(id).getOrThrow()
        mutable.update { it.copy(notice = BudgetMessages.RESOLVED_NOTICE) }
    }

    fun consumeNotice() {
        mutable.update { it.copy(notice = null) }
    }

    private fun action(errorMessage: String, block: suspend () -> Unit) {
        if (state.value.busy) return
        viewModelScope.launch {
            val owner = state.value.overview.ownerId
            mutable.update { it.copy(busy = true, error = null) }
            try {
                check(auth.currentUser.first()?.uid == owner)
                block()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                mutable.update { if (it.overview.ownerId == owner) it.copy(error = errorMessage) else it }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
