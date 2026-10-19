package com.example.clarity.presentation.income

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.R
import com.example.clarity.domain.model.IncomeError
import com.example.clarity.domain.model.IncomeException
import com.example.clarity.domain.model.IncomeSource
import com.example.clarity.domain.usecase.account.GetAccountsUseCase
import com.example.clarity.domain.usecase.income.ArchiveIncomeSourceUseCase
import com.example.clarity.domain.usecase.income.ObserveIncomeSourcesUseCase
import com.example.clarity.domain.usecase.income.RecordIncomeSourceUseCase
import com.example.clarity.domain.usecase.income.SaveIncomeSourceUseCase
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import com.example.clarity.domain.util.isSignInRequired
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class IncomeViewModel @Inject constructor(
    observe: ObserveIncomeSourcesUseCase, accounts: GetAccountsUseCase,
    private val save: SaveIncomeSourceUseCase, private val archive: ArchiveIncomeSourceUseCase,
    private val record: RecordIncomeSourceUseCase, private val clock: Clock
) : ViewModel() {
    private val state = MutableStateFlow(IncomeState())
    val uiState = state.asStateFlow()
    private var boundary: Job? = null
    init {
        refreshPeriod()
        viewModelScope.launch {
            observe().catch { error ->
                if (error is CancellationException) throw error
                state.update { it.copy(loading = false, error = R.string.income_load_error) }
            }.collect { data -> state.update { old ->
                val sources = data.sources.filter { it.active }
                if (old.ownerId != data.ownerId) IncomeState(ownerId = data.ownerId, sources = sources,
                    accounts = old.accounts.filter { it.userId == data.ownerId }, period = old.period, loading = false,
                    error = if (data.ownerId == null) R.string.income_sign_in else null)
                else old.copy(sources = sources, loading = false)
            } }
        }
        viewModelScope.launch {
            accounts().catch { error ->
                if (error is CancellationException) throw error
                state.update { it.copy(error = R.string.income_account_error) }
            }.collect { result ->
                state.update { old ->
                    val rows = result.data?.filter { it.currency == "LKR" } ?: if (result is Resource.Loading) old.accounts else emptyList()
                    val draft = old.draft?.let { if (it.accountId.isBlank()) it.copy(accountId = rows.firstOrNull { a -> a.isDefault }?.id ?: rows.firstOrNull()?.id.orEmpty()) else it }
                    old.copy(accounts = rows, draft = draft, error = if (result is Resource.Error && result.cause.isSignInRequired()) R.string.income_sign_in else old.error)
                }
            }
        }
    }
    /** Refresh on resume/timezone changes; schedule the boundary, without periodic polling. */
    fun refreshPeriod() {
        boundary?.cancel()
        boundary = viewModelScope.launch {
            while (true) {
                val now = Instant.ofEpochMilli(clock.millis()).atZone(ZoneId.systemDefault())
                val period = YearMonth.from(now)
                state.update { it.copy(period = period) }
                val next = period.plusMonths(1).atDay(1).atStartOfDay(now.zone).toInstant().toEpochMilli()
                delay((next - clock.millis()).coerceAtLeast(1000))
            }
        }
    }
    fun edit(source: IncomeSource? = null) {
        if (state.value.busy) return
        val account = state.value.accounts.firstOrNull { it.isDefault } ?: state.value.accounts.firstOrNull()
        val item = source ?: IncomeSource(name = "", amountMinor = 0, accountId = account?.id.orEmpty(), recurring = true)
        state.update { it.copy(draft = IncomeDraft(item, item.name, if (item.amountMinor > 0) MoneyUtils.formatMinor(item.amountMinor) else "", item.accountId, item.recurring), error = null) }
    }
    fun name(value: String) = draft { it.copy(name = value.take(100)) }
    fun amount(value: String) = draft { it.copy(amount = value.take(24)) }
    fun account(value: String) = draft { it.copy(accountId = value) }
    fun recurring(value: Boolean) = draft { it.copy(recurring = value) }
    private fun draft(change: (IncomeDraft) -> IncomeDraft) { if (!state.value.busy) state.update { it.copy(draft = it.draft?.let(change), error = null) } }
    fun dismiss() { if (!state.value.busy) state.update { it.copy(draft = null, confirmation = null, confirmationMonth = null, error = null) } }
    fun saveDraft() = action {
        val draft = checkNotNull(state.value.draft)
        val amount = MoneyUtils.parseMinor(draft.amount).getOrThrow()
        save(draft.source.copy(name = draft.name.trim(), amountMinor = amount, accountId = draft.accountId, recurring = draft.recurring)).getOrThrow()
        state.update { it.copy(draft = null) }
    }
    fun stop(id: String) = action { archive(id).getOrThrow() }
    fun review(source: IncomeSource) { if (!state.value.busy) state.update { it.copy(confirmation = source, confirmationMonth = if (source.recurring) it.period.toString() else null, error = null) } }
    fun confirm() = action {
        val current = checkNotNull(state.value.confirmation)
        val id = record(current.id, state.value.confirmationMonth, current.updatedAt).getOrThrow()
        state.update { it.copy(confirmation = null, confirmationMonth = null, lastTransactionId = id) }
    }
    private fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (error: Exception) {
                if (error is CancellationException) throw error
                val message = when ((error as? IncomeException)?.error) {
                    IncomeError.SIGN_IN -> R.string.income_sign_in
                    IncomeError.ACCOUNT -> R.string.income_account_error
                    IncomeError.ALREADY_CONFIRMED -> R.string.income_already_confirmed
                    IncomeError.PERIOD_CHANGED, IncomeError.SOURCE_CHANGED -> R.string.income_changed
                    else -> R.string.income_save_error
                }
                state.update { it.copy(error = message, confirmation = null, confirmationMonth = null) }
            } finally { state.update { it.copy(busy = false) } }
        }
    }
}
