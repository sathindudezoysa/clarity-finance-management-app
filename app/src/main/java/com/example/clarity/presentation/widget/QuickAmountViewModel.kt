package com.example.clarity.presentation.widget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.Currencies
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.WidgetQuickEntryRepository
import com.example.clarity.domain.usecase.currency.ObserveCurrencyPreferenceUseCase
import com.example.clarity.domain.usecase.widget.RecordWidgetTransactionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickAmountViewModel @Inject constructor(
    private val record: RecordWidgetTransactionUseCase,
    private val quickEntry: WidgetQuickEntryRepository,
    private val observeCurrency: ObserveCurrencyPreferenceUseCase,
    private val widgetRefresher: QuickEntryWidgetRefresher,
    private val observeUser: com.example.clarity.domain.usecase.auth.ObserveCurrentUserUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickAmountState())
    val uiState: StateFlow<QuickAmountState> = _uiState.asStateFlow()
    private var started = false

    fun start(type: TransactionType, category: String) {
        if (started) return
        started = true
        // Prefill with the last amount remembered for this category.
        _uiState.value = QuickAmountState(
            type = type,
            category = category,
            amount = quickEntry.lastAmount(type, category).orEmpty()
        )
        viewModelScope.launch {
            if (observeUser().first() == null) {
                _uiState.update { it.copy(signedOut = true) }
                return@launch
            }
            val currency = runCatching { observeCurrency().first().currency }.getOrDefault("")
            _uiState.update { it.copy(currency = currency) }
        }
    }

    fun onAmountChange(raw: String) {
        val digits = runCatching { Currencies.digits(_uiState.value.currency) }.getOrDefault(2)
        val cleaned = raw.replace(',', '.').filter { it.isDigit() || it == '.' }
        val valid = cleaned.count { it == '.' } <= 1 &&
            cleaned.substringAfter('.', "").length <= digits &&
            !(digits == 0 && cleaned.contains('.')) &&
            cleaned.length <= 12
        if (valid) _uiState.update { it.copy(amount = cleaned, error = null) }
    }

    fun submit() {
        val state = _uiState.value
        if (state.isSaving) return
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = record(state.type, state.category, state.amount)
            if (result.isSuccess) widgetRefresher.refreshNow()
            _uiState.update {
                if (result.isSuccess) it.copy(isSaving = false, isSaved = true)
                else it.copy(isSaving = false, error = result.exceptionOrNull()?.message ?: "Could not save")
            }
        }
    }
}
