package com.example.clarity.presentation.accounts.detail

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.Transaction
import java.math.BigInteger

data class AccountTransactionItemUiModel(
    val transaction: Transaction,
    val resolvedAmountMinor: BigInteger,
    val formattedAmount: String,
    val formattedDate: String,
    val hasReceipt: Boolean = false
)

data class AccountDetailState(
    val accountId: String? = null,
    val isLoading: Boolean = true,
    val account: Account? = null,
    val balanceMinor: BigInteger = BigInteger.ZERO,
    val balanceFormatted: String = "",
    val totalIncomeFormatted: String = "",
    val totalExpenseFormatted: String = "",
    val transactions: List<AccountTransactionItemUiModel> = emptyList(),
    val otherAccountsCount: Int = 0,
    val isMakingDefault: Boolean = false,
    val error: String? = null,
    val actionError: String? = null
)
