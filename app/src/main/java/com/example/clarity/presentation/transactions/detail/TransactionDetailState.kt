package com.example.clarity.presentation.transactions.detail

import com.example.clarity.domain.model.Transaction

data class TransactionDetailState(
    val isLoading: Boolean = true,
    val transaction: Transaction? = null,
    val error: Int? = null,
    val isDeleteDialogVisible: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false
)
