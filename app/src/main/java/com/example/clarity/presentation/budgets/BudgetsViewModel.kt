package com.example.clarity.presentation.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
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

data class BudgetEditor(val budget: Budget? = null, val category: String = "Food", val month: String,
    val amount: String = "", val currency: String, val owner: String)
data class BudgetsState(val loading: Boolean = true, val overview: BudgetOverview = BudgetOverview(),
    val tags: List<String> = TransactionTags.predefined, val currency: String = "LKR", val editor: BudgetEditor? = null,
    val busy: Boolean = false, val error: Int? = null, val currentMonth: String = "")

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    observe: ObserveBudgetsUseCase, tags: GetTransactionTagsUseCase, preference: ObserveCurrencyPreferenceUseCase,
    private val saveBudget: SaveBudgetUseCase, private val disableBudget: DisableBudgetUseCase,
    private val resolve: ResolveBudgetCurrencyUseCase, private val clock: Clock, private val auth: AuthRepository
) : ViewModel() {
    private val mutable = MutableStateFlow(BudgetsState(currentMonth = BudgetPolicy.currentMonth(clock)))
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            observe().catch { error ->
                if (error is CancellationException) throw error
                mutable.update { it.copy(loading = false, error = R.string.budget_load_error) }
            }.collect { overview -> mutable.update { old ->
                val changedOwner = old.overview.ownerId != overview.ownerId
                old.copy(loading = overview.loading, overview = overview, currentMonth = BudgetPolicy.currentMonth(clock),
                    editor = if (changedOwner) null else old.editor, error = if (changedOwner) null else old.error)
            } }
        }
        viewModelScope.launch { tags().collect { value -> mutable.update { it.copy(tags = value) } } }
        viewModelScope.launch { preference().collect { value -> mutable.update { it.copy(currency = value.currency) } } }
    }
    fun edit(budget: Budget? = null) {
        val owner = state.value.overview.ownerId ?: return
        mutable.update { it.copy(editor = BudgetEditor(budget, budget?.category ?: "Food", budget?.month ?: BudgetPolicy.currentMonth(clock),
            budget?.let { b -> BigDecimal.valueOf(b.limitMinor, Currencies.digits(b.currency)).toPlainString() } ?: "",
            budget?.currency ?: it.currency, owner), error = null) }
    }
    fun changeCategory(value: String) { mutable.update { it.copy(editor = it.editor?.copy(category = value), error = null) } }
    fun changeMonth(value: String) { mutable.update { it.copy(editor = it.editor?.copy(month = value), error = null) } }
    fun changeAmount(value: String) { mutable.update { it.copy(editor = it.editor?.copy(amount = value), error = null) } }
    fun closeEditor() { mutable.update { it.copy(editor = null, error = null) } }
    fun save() {
        val editor = state.value.editor ?: return
        val budget = runCatching {
            val category = TransactionTags.normalize(editor.category)
            val amount = BigDecimal(editor.amount).movePointRight(Currencies.digits(editor.currency)).longValueExact()
            require(amount > 0)
            val id = BudgetPolicy.id(editor.month, category)
            // Existing budgets, including disabled ones, are edited with their recorded currency.
            require(editor.budget != null || state.value.overview.statuses.none { it.budget.id == id })
            Budget(id, editor.owner, editor.month, category, amount, editor.currency).also { BudgetPolicy.validate(it) }
        }.getOrElse { mutable.update { it.copy(error = R.string.budget_validation) }; return }
        action(R.string.budget_save_error) {
            saveBudget(budget).getOrThrow()
            mutable.update { if (it.editor == editor) it.copy(editor = null) else it }
        }
    }
    fun disable(id: String) = action(R.string.budget_save_error) { disableBudget(id).getOrThrow() }
    fun retryCurrency(id: String) = action(R.string.budget_conversion_error) { resolve(id).getOrThrow() }
    private fun action(errorMessage: Int, block: suspend () -> Unit) {
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
