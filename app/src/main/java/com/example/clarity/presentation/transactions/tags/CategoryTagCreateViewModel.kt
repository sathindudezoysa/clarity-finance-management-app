package com.example.clarity.presentation.transactions.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.usecase.transaction.AddTransactionTagUseCase
import com.example.clarity.domain.usecase.transaction.GetTransactionTagsUseCase
import com.example.clarity.presentation.transactions.TransactionMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoryTagCreateState(
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val expenseTags: List<String> = TransactionTags.defaultExpenseCategories,
    val incomeTags: List<String> = TransactionTags.defaultIncomeCategories,
    val isSaving: Boolean = false,
    val error: String? = null,
    val savedCategoryName: String? = null,
    val savedCategoryType: TransactionType? = null
) {
    val activeTags: List<String>
        get() = if (selectedType == TransactionType.INCOME) incomeTags else expenseTags

    val customTags: List<String>
        get() = activeTags.filterNot { TransactionTags.isDefault(selectedType, it) }

    val defaultTags: List<String>
        get() = TransactionTags.defaultsFor(selectedType)
}

@HiltViewModel
class CategoryTagCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTransactionTags: GetTransactionTagsUseCase,
    private val addTransactionTag: AddTransactionTagUseCase
) : ViewModel() {

    private val initialType = savedStateHandle.get<String>("type")
        ?.let { runCatching { TransactionType.valueOf(it.uppercase()) }.getOrNull() }
        ?: TransactionType.EXPENSE

    private val _uiState = MutableStateFlow(CategoryTagCreateState(selectedType = initialType))
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getTransactionTags(TransactionType.EXPENSE).collect { tags ->
                _uiState.update { it.copy(expenseTags = tags) }
            }
        }
        viewModelScope.launch {
            getTransactionTags(TransactionType.INCOME).collect { tags ->
                _uiState.update { it.copy(incomeTags = tags) }
            }
        }
    }

    fun onTypeSelected(type: TransactionType) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(selectedType = type, error = null) }
    }

    fun onClearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun saveCategoryTag(rawName: String) {
        val currentState = _uiState.value
        if (currentState.isSaving || currentState.savedCategoryName != null) return

        val cleanName = rawName.trim()
        if (cleanName.isEmpty()) {
            _uiState.update { it.copy(error = "Category tag name cannot be empty.") }
            return
        }
        if (cleanName.length > 100) {
            _uiState.update { it.copy(error = TransactionMessages.TAG_LONG) }
            return
        }

        val targetType = currentState.selectedType
        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            addTransactionTag(cleanName, targetType)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedCategoryName = cleanName,
                            savedCategoryType = targetType
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            error = "Could not save category tag. Please try again."
                        )
                    }
                }
        }
    }
}
