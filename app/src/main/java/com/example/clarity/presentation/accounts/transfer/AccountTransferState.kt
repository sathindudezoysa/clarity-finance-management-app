package com.example.clarity.presentation.accounts.transfer

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.Currencies
import java.math.BigInteger

data class AccountTransferState(
    val isLoading: Boolean = true,
    val accounts: List<Account> = emptyList(),
    val accountBalancesMinor: Map<String, BigInteger> = emptyMap(),
    val accountBalancesFormatted: Map<String, String> = emptyMap(),
    val sourceAccountId: String? = null,
    val destinationAccountId: String? = null,
    val amountInput: String = "",
    val noteInput: String = "",
    val preview: ConversionSnapshot? = null,
    val previewLoading: Boolean = false,
    val previewError: Boolean = false,
    val amountError: String? = null,
    val accountError: String? = null,
    val noteError: String? = null,
    val error: String? = null,
    val isSaving: Boolean = false,
    val isSuccess: Boolean = false
) {
    val sourceAccount: Account?
        get() = accounts.firstOrNull { it.id == sourceAccountId }

    val destinationAccount: Account?
        get() = accounts.firstOrNull { it.id == destinationAccountId }

    val sourceCurrency: String
        get() = sourceAccount?.currency?.takeIf { it.isNotBlank() }?.uppercase()
            ?: Currencies.supported.first()

    val destinationCurrency: String
        get() = destinationAccount?.currency?.takeIf { it.isNotBlank() }?.uppercase()
            ?: sourceCurrency

    val isCrossCurrency: Boolean
        get() = sourceAccount != null &&
            destinationAccount != null &&
            !sourceCurrency.equals(destinationCurrency, ignoreCase = true)

    val availableSourceBalanceMinor: BigInteger
        get() = sourceAccountId?.let { accountBalancesMinor[it] } ?: BigInteger.ZERO

    val availableSourceBalanceFormatted: String
        get() = sourceAccountId?.let { accountBalancesFormatted[it] } ?: ""

    val destinationBalanceFormatted: String
        get() = destinationAccountId?.let { accountBalancesFormatted[it] } ?: ""

    val canSubmit: Boolean
        get() = !isLoading &&
            !isSaving &&
            !isSuccess &&
            sourceAccount != null &&
            destinationAccount != null &&
            sourceAccountId != destinationAccountId &&
            !previewLoading &&
            (!isCrossCurrency || (preview != null && !previewError))
}
