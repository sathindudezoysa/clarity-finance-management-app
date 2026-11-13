package com.example.clarity.presentation.wallet

import com.example.clarity.domain.model.Account

data class CashWalletState(
    val currency: String = "LKR",
    val balance: String = "0.00",
    val cashAccount: Account? = null,
    val sourceAccounts: List<Account> = emptyList(),
    val hasOtherCurrencyAccounts: Boolean = false,
    val selectedSourceAccountId: String = "",
    val amount: String = "",
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
)
