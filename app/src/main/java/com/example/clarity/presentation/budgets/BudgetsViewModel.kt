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
    val tags: List<String> = TransactionTags.defaultExpenseCategories, val currency: String = "LKR", val editor: BudgetEditor? = null,
    val busy: Boolean = false, val error: String? = null, val currentMonth: String = "", val currentYear: String = "")

internal fun availableBudgetCategories(
    tags: List<String>,
    overview: BudgetOverview,
    period: BudgetPeriod
): List<String> {
    val existingKeys = overview.statuses
        .filter { it.budget.period == period }
        .map { BudgetPolicy.categoryKey(it.budget.category) }
        .toSet()
    return tags.filter { BudgetPolicy.categoryKey(it) !in existingKeys }
}

internal fun defaultBudgetCategory(
    tags: List<String>,
    overview: BudgetOverview,
    period: BudgetPeriod
): String {
    val available = availableBudgetCategories(tags, overview, period)
    return matchBudgetCategoryTag(available, "Food")
        ?: available.firstOrNull()
        ?: ""
}

internal fun matchBudgetCategoryTag(tags: List<String>, rawCategory: String): String? =
    tags.firstOrNull { TransactionTags.key(it) == TransactionTags.key(rawCategory) }

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    observe: ObserveBudgetsUseCase, tags: GetTransactionTagsUseCase, preference: ObserveCurrencyPreferenceUseCase,
    private val saveBudget: SaveBudgetUseCase, private val deleteBudget: DeleteBudgetUseCase,
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
                val nextEditor = when {
                    changedOwner -> null
                    old.editor?.budget == null -> old.editor?.let { ed ->
                        val available = availableBudgetCategories(old.tags, overview, ed.period)
                        if (ed.category.isBlank() || matchBudgetCategoryTag(available, ed.category) == null) {
                            ed.copy(category = defaultBudgetCategory(old.tags, overview, ed.period))
                        } else {
                            ed
                        }
                    }
                    else -> old.editor
                }
                old.copy(loading = overview.loading, overview = overview,
                    currentMonth = BudgetPolicy.currentMonth(clock),
                    currentYear = TransactionCalendar.currentYear(clock),
                    editor = nextEditor, error = if (changedOwner) null else old.error)
            } }
        }
        viewModelScope.launch {
            tags(TransactionType.EXPENSE).collect { value ->
                val resolvedTags = value.ifEmpty { TransactionTags.defaultExpenseCategories }
                mutable.update { old ->
                    val updatedEditor = old.editor?.let { ed ->
                        if (ed.budget == null) {
                            val available = availableBudgetCategories(resolvedTags, old.overview, ed.period)
                            if (ed.category.isBlank() || matchBudgetCategoryTag(available, ed.category) == null) {
                                ed.copy(category = defaultBudgetCategory(resolvedTags, old.overview, ed.period))
                            } else {
                                ed
                            }
                        } else {
                            ed
                        }
                    }
                    old.copy(tags = resolvedTags, editor = updatedEditor)
                }
            }
        }
        viewModelScope.launch { preference().collect { value -> mutable.update { it.copy(currency = value.currency) } } }
    }
    fun edit(budget: Budget? = null) {
        val owner = state.value.overview.ownerId ?: return
        val period = budget?.period ?: BudgetPeriod.MONTHLY
        mutable.update {
            val initialCategory = budget?.category ?: defaultBudgetCategory(it.tags, it.overview, period)
            it.copy(
                editor = BudgetEditor(
                    budget = budget,
                    category = initialCategory,
                    month = budget?.month ?: BudgetPolicy.currentPeriodKey(clock, period),
                    amount = budget?.let { b -> BigDecimal.valueOf(b.limitMinor, Currencies.digits(b.currency)).toPlainString() } ?: "",
                    currency = budget?.currency ?: it.currency,
                    owner = owner,
                    period = period
                ),
                error = null
            )
        }
    }
    fun changePeriod(value: BudgetPeriod) {
        mutable.update { state ->
            val editor = state.editor ?: return@update state
            if (editor.budget != null || editor.period == value) return@update state
            val available = availableBudgetCategories(state.tags, state.overview, value)
            val nextCategory = matchBudgetCategoryTag(available, editor.category)
                ?: defaultBudgetCategory(state.tags, state.overview, value)
            state.copy(
                editor = editor.copy(
                    period = value,
                    month = BudgetPolicy.currentPeriodKey(clock, value),
                    category = nextCategory
                ),
                error = null
            )
        }
    }
    fun changeCategory(value: String) {
        mutable.update { state ->
            val editor = state.editor ?: return@update state
            if (editor.budget != null) return@update state
            val available = availableBudgetCategories(state.tags, state.overview, editor.period)
            val matched = matchBudgetCategoryTag(available, value) ?: return@update state
            state.copy(editor = editor.copy(category = matched), error = null)
        }
    }
    fun changeMonth(value: String) { mutable.update { it.copy(editor = it.editor?.copy(month = value), error = null) } }
    fun changeAmount(value: String) { mutable.update { it.copy(editor = it.editor?.copy(amount = value), error = null) } }
    fun closeEditor() { mutable.update { it.copy(editor = null, error = null) } }
    fun save() {
        val currentState = state.value
        val editor = currentState.editor ?: return
        val budget = runCatching {
            val category = if (editor.budget != null) {
                TransactionTags.normalize(editor.budget.category)
            } else {
                val available = availableBudgetCategories(currentState.tags, currentState.overview, editor.period)
                val matched = requireNotNull(matchBudgetCategoryTag(available, editor.category))
                TransactionTags.normalize(matched)
            }
            val month = editor.budget?.month ?: BudgetPolicy.currentPeriodKey(clock, editor.period)
            val amount = BigDecimal(editor.amount).movePointRight(Currencies.digits(editor.currency)).longValueExact()
            require(amount > 0)
            val id = editor.budget?.id ?: BudgetPolicy.id(month, category)
            val categoryKey = BudgetPolicy.categoryKey(category)
            // Existing budgets, including disabled ones, are edited with their recorded currency.
            require(
                editor.budget != null || currentState.overview.statuses.none {
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
    fun delete(id: String) = action(BudgetMessages.DELETE_ERROR) { deleteBudget(id).getOrThrow() }
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
