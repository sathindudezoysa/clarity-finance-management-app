package com.example.clarity.presentation.transactions.edit

import com.example.clarity.domain.model.Transaction

data class TransactionEditState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val transaction: Transaction? = null,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val error: Int? = null,
    val amountError: Int? = null,
    val categoryError: Int? = null,
    val noteError: Int? = null
)
