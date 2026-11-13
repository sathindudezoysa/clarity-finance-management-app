package com.example.clarity.domain.usecase.widget

import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.WidgetQuickEntryRepository
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.util.MoneyUtils
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Records a cash wallet transaction entered from the home screen widget. */
class RecordWidgetTransactionUseCase @Inject constructor(
    private val addTransaction: AddTransactionUseCase,
    private val accounts: AccountRepository,
    private val currencyPreferences: CurrencyPreferenceRepository,
    private val quickEntry: WidgetQuickEntryRepository
) {
    suspend operator fun invoke(type: TransactionType, category: String, amount: String): Result<Unit> {
        val currency = currencyPreferences.current()
        val amountMinor = MoneyUtils.parseMinor(amount, currency).getOrElse {
            return Result.failure(IllegalArgumentException("Invalid amount"))
        }
        if (amountMinor <= 0L) return Result.failure(IllegalArgumentException("Enter an amount"))

        val cash = accounts.observeAccounts().first { it.data != null }.data
            ?.firstOrNull { it.isCashWallet }
            ?: return Result.failure(TransactionException(TransactionError.ACCOUNT_NOT_FOUND, "Open Clarity to set up your wallet"))

        val result = addTransaction(
            Transaction(
                type = type,
                amountMinor = amountMinor,
                category = category,
                accountId = cash.id,
                currency = currency
            )
        )
        if (result.isSuccess) quickEntry.saveLastAmount(type, category, MoneyUtils.formatMinor(amountMinor, currency))
        return result
    }
}
