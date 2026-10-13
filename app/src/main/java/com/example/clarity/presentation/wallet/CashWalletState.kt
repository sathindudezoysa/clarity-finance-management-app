package com.example.clarity.presentation.wallet

import com.example.clarity.domain.model.CashWallet

enum class WalletAction { EXPENSE, INCOME, TOP_UP, AUTOMATIC }

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
    val showBankForm: Boolean = false,
    val bankName: String = "",
    val bankOpeningBalance: String = "0.00",
    val error: String? = null,
    val message: String? = null
)
