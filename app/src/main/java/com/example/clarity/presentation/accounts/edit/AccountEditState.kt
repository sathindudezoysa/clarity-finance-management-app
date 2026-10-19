package com.example.clarity.presentation.accounts.edit

data class AccountEditState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val canSave: Boolean = true,
    val name: String = "",
    val bankName: String = "",
    val accountType: String = "",
    val currency: String = "",
    val isDefault: Boolean = false,
    val isDefaultLocked: Boolean = false,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false,
    val nameError: String? = null,
    val currencyError: String? = null,
    val error: String? = null
)
