package com.example.clarity.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.*
import com.example.clarity.domain.repository.AuthRepository
import com.example.clarity.domain.repository.BudgetOverview
import com.example.clarity.domain.usecase.budget.*
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.domain.util.TransactionCalendar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import javax.inject.Inject

data class BudgetEditor(val budget: Budget? = null, val category: String = "Food", val month: String,
    val amount: String = "", val currency: String, val owner: String, val period: BudgetPeriod = BudgetPeriod.MONTHLY)
data class BudgetsState(val loading: Boolean = true, val overview: BudgetOverview = BudgetOverview(),
    val tags: List<String> = TransactionTags.predefined, val currency: String = "LKR", val editor: BudgetEditor? = null,
    val busy: Boolean = false, val error: String? = null, val currentMonth: String = "", val currentYear: String = "")

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    observe: ObserveBudgetsUseCase, tags: GetTransactionTagsUseCase, preference: ObserveCurrencyPreferenceUseCase,
    private val saveBudget: SaveBudgetUseCase, private val disableBudget: DisableBudgetUseCase,
    private val resolve: ResolveBudgetCurrencyUseCase, private val clock: Clock, private val auth: AuthRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(BudgetsState(
        currentMonth = BudgetPolicy.currentMonth(clock),
        currentYear = TransactionCalendar.currentYear(clock)
    ))
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            observe().catch { error ->
                if (error is CancellationException) throw error
                mutable.update { it.copy(loading = false, error = BudgetMessages.LOAD_ERROR) }
            }.collect { overview -> mutable.update { old ->
                val changedOwner = old.overview.ownerId != overview.ownerId
                old.copy(loading = overview.loading, overview = overview,
                    currentMonth = BudgetPolicy.currentMonth(clock),
                    currentYear = TransactionCalendar.currentYear(clock),
                    editor = if (changedOwner) null else old.editor, error = if (changedOwner) null else old.error)
            } }
        }
        viewModelScope.launch { tags().collect { value -> mutable.update { it.copy(tags = value) } } }
        viewModelScope.launch { preference().collect { value -> mutable.update { it.copy(currency = value.currency) } } }
    }
    fun edit(budget: Budget? = null) {
        val owner = state.value.overview.ownerId ?: return
        val period = budget?.period ?: BudgetPeriod.MONTHLY
        mutable.update { it.copy(editor = BudgetEditor(budget, budget?.category ?: "Food",
            budget?.month ?: BudgetPolicy.currentPeriodKey(clock, period),
            budget?.let { b -> BigDecimal.valueOf(b.limitMinor, Currencies.digits(b.currency)).toPlainString() } ?: "",
            budget?.currency ?: it.currency, owner, period), error = null) }
    }
    fun changePeriod(value: BudgetPeriod) {
        mutable.update { state ->
            val editor = state.editor ?: return@update state
            if (editor.budget != null || editor.period == value) state
            else state.copy(editor = editor.copy(period = value, month = BudgetPolicy.currentPeriodKey(clock, value)), error = null)
        }
    }
    fun changeCategory(value: String) { mutable.update { it.copy(editor = it.editor?.copy(category = value), error = null) } }
    fun changeMonth(value: String) { mutable.update { it.copy(editor = it.editor?.copy(month = value), error = null) } }
    fun changeAmount(value: String) { mutable.update { it.copy(editor = it.editor?.copy(amount = value), error = null) } }
    fun closeEditor() { mutable.update { it.copy(editor = null, error = null) } }
    fun save() {
        val editor = state.value.editor ?: return
        val budget = runCatching {
            val category = TransactionTags.normalize(editor.category)
            val month = editor.budget?.month ?: BudgetPolicy.currentPeriodKey(clock, editor.period)
            val amount = BigDecimal(editor.amount).movePointRight(Currencies.digits(editor.currency)).longValueExact()
            require(amount > 0)
            val id = editor.budget?.id ?: BudgetPolicy.id(month, category)
            val categoryKey = BudgetPolicy.categoryKey(category)
            // Existing budgets, including disabled ones, are edited with their recorded currency.
            require(
                editor.budget != null || state.value.overview.statuses.none {
                    it.budget.id == id ||
                        (it.budget.period == editor.period && BudgetPolicy.categoryKey(it.budget.category) == categoryKey)
                }
            )
            Budget(id, editor.owner, month, category, amount, editor.currency).also { BudgetPolicy.validate(it) }
        }.getOrElse { mutable.update { it.copy(error = BudgetMessages.VALIDATION) }; return }
        action(BudgetMessages.SAVE_ERROR) {
            saveBudget(budget).getOrThrow()
            mutable.update { if (it.editor == editor) it.copy(editor = null) else it }
        }
    }
    fun disable(id: String) = action(BudgetMessages.SAVE_ERROR) { disableBudget(id).getOrThrow() }
    fun retryCurrency(id: String) = action(BudgetMessages.CONVERSION_ERROR) { resolve(id).getOrThrow() }
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
            } finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
