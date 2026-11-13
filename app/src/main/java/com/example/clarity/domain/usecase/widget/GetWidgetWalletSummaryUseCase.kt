package com.example.clarity.domain.usecase.widget

import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.model.WidgetWalletSummary
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.util.MoneyUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger
import javax.inject.Inject

/** Cash wallet total in the preferred currency, using stored conversion snapshots only (no network). */
class GetWidgetWalletSummaryUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val currencyPreferences: CurrencyPreferenceRepository
) {
    suspend operator fun invoke(): WidgetWalletSummary {
        val currency = currencyPreferences.current()
        val cashAccountId = withTimeoutOrNull(5_000L) {
            accounts.observeAccounts().first { it.data != null }.data
        }?.firstOrNull { it.isCashWallet }?.id
        val all = withTimeoutOrNull(5_000L) {
            transactions.observeTransactions().first { it.data != null }.data
        }.orEmpty()

        var balance = BigInteger.ZERO
        all.filter { it.isCash || (cashAccountId != null && it.accountId == cashAccountId) }.forEach { tx ->
            val minor = when {
                tx.currency == currency -> tx.amountMinor
                tx.targetCurrency == currency && tx.conversion != null -> tx.conversion.amountMinor
                else -> return@forEach
            }
            balance += if (tx.type == TransactionType.INCOME) BigInteger.valueOf(minor) else BigInteger.valueOf(minor).negate()
        }
        return WidgetWalletSummary(currency, MoneyUtils.formatMinor(balance, currency))
    }
}
