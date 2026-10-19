package com.example.clarity.presentation.wallet

import com.example.clarity.domain.model.Account

enum class WalletActionMode {
    TOP_UP,
    EXPENSE,
    INCOME
}

data class CashWalletState(
    val currency: String = "LKR",
    val balance: String = "0.00",
    val cashAccount: Account? = null,
    val sourceAccounts: List<Account> = emptyList(),
    val selectedSourceAccountId: String = "",
    val mode: WalletActionMode = WalletActionMode.TOP_UP,
    val amount: String = "",
    val isSaving: Boolean = false
)
