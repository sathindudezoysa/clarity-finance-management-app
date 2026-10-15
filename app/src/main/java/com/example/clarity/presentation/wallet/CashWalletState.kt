package com.example.clarity.presentation.wallet

import com.example.clarity.domain.model.CashWallet

enum class WalletAction { EXPENSE, INCOME, TOP_UP, AUTOMATIC, ADJUSTMENT }

data class CashWalletState(
    val wallet: CashWallet = CashWallet(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val action: WalletAction = WalletAction.EXPENSE,
    val amount: String = "",
    val category: String = "",
    val note: String = "",
    val selectedBankId: String = "",
    val automaticEnabled: Boolean = false,
    val threshold: String = "0.00",
    val automaticAmount: String = "",
    val error: String? = null,
    val message: String? = null,
    val showAddCashChoices: Boolean = false,
    val showTransferReview: Boolean = false,
    val reminderVisible: Boolean = false
)
