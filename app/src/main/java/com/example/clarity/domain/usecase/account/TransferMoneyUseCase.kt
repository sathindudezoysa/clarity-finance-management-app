package com.example.clarity.domain.usecase.account

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.PreviewConversionUseCase
import com.example.clarity.domain.util.AccountBalancePolicy
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject

data class TransferMoneyResult(
    val debitTransaction: Transaction,
    val creditTransaction: Transaction,
    val conversionSnapshot: ConversionSnapshot
)

class TransferMoneyUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val addTransaction: AddTransactionUseCase,
    private val previewConversion: PreviewConversionUseCase,
    private val rates: ExchangeRateRepository,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository
) {
    suspend operator fun invoke(
        sourceAccountId: String,
        destinationAccountId: String,
        amountText: String,
        note: String = "",
        dateEpochMillis: Long = System.currentTimeMillis()
    ): Result<TransferMoneyResult> {
        try {
            val cleanSourceId = sourceAccountId.trim()
            val cleanDestinationId = destinationAccountId.trim()
            if (cleanSourceId.isEmpty() || cleanDestinationId.isEmpty()) {
                return Result.failure(
                    TransactionException(TransactionError.ACCOUNT_REQUIRED, "Select both source and destination accounts.")
                )
            }
            if (cleanSourceId == cleanDestinationId) {
                return Result.failure(
                    IllegalArgumentException("Source and destination accounts must be different.")
                )
            }

            val accountsResource = accountRepository.observeAccounts()
                .first { it !is Resource.Loading }
            val observedAccounts = accountsResource.data.orEmpty()

            val sourceAccount = accountRepository.getAccount(cleanSourceId).getOrNull()
                ?: observedAccounts.firstOrNull { it.id == cleanSourceId }
                ?: return Result.failure(
                    TransactionException(TransactionError.ACCOUNT_NOT_FOUND, "Source account not found.")
                )

            val destinationAccount = accountRepository.getAccount(cleanDestinationId).getOrNull()
                ?: observedAccounts.firstOrNull { it.id == cleanDestinationId }
                ?: return Result.failure(
                    TransactionException(TransactionError.ACCOUNT_NOT_FOUND, "Destination account not found.")
                )

            if (sourceAccount.isGoalAccount || destinationAccount.isGoalAccount) {
                return Result.failure(
                    TransactionException(TransactionError.ACCOUNT_NOT_FOUND, "Goal accounts cannot be used for direct transfers.")
                )
            }

            val sourceCurrency = sourceAccount.currency.uppercase()
            val destinationCurrency = destinationAccount.currency.uppercase()

            val sourceAmountMinor = MoneyUtils.parseMinor(amountText, sourceCurrency).getOrNull()
                ?: return Result.failure(IllegalArgumentException("Enter a valid transfer amount."))
            if (sourceAmountMinor <= 0L) {
                return Result.failure(IllegalArgumentException("Transfer amount must be greater than zero."))
            }

            val cleanNote = note.trim()
            if (cleanNote.length > 1800) {
                return Result.failure(IllegalArgumentException("Note is too long."))
            }

            val txResource = transactionRepository.observeTransactions()
                .first { it !is Resource.Loading }
            val activeTransactions = txResource.data.orEmpty()

            val rateCache = mutableMapOf<String, BigDecimal?>()
            suspend fun resolveRate(source: String, target: String): BigDecimal? {
                if (source.equals(target, ignoreCase = true)) return BigDecimal.ONE
                return rateCache.getOrPut("$source->$target") {
                    val quote = runCatching {
                        rates.cached(source, target) ?: rates.latest(source, target)
                    }.getOrNull()
                    quote?.let { runCatching { CurrencyConversion.rate(it.rate) }.getOrNull() }
                }
            }

            val availableBalanceMinor = AccountBalancePolicy.calculateBalanceMinor(
                account = sourceAccount,
                transactions = activeTransactions,
                resolveRate = ::resolveRate
            )
            if (availableBalanceMinor < BigInteger.valueOf(sourceAmountMinor)) {
                return Result.failure(
                    TransactionException(TransactionError.INSUFFICIENT_BALANCE, "Insufficient balance in ${sourceAccount.name}.")
                )
            }

            val snapshot = if (sourceCurrency == destinationCurrency) {
                ConversionSnapshot.identity(sourceAmountMinor)
            } else {
                previewConversion(sourceAmountMinor, sourceCurrency, destinationCurrency)
            }
            val destinationAmountMinor = snapshot.amountMinor

            // Warm preferred currency conversion cache so local persistence has conversion snapshots when available.
            val preferredCurrency = runCatching { currencyPreferenceRepository.current() }.getOrDefault(sourceCurrency)
            if (!sourceCurrency.equals(preferredCurrency, ignoreCase = true)) {
                runCatching { rates.cached(sourceCurrency, preferredCurrency) ?: rates.latest(sourceCurrency, preferredCurrency) }
            }
            if (!destinationCurrency.equals(preferredCurrency, ignoreCase = true)) {
                runCatching { rates.cached(destinationCurrency, preferredCurrency) ?: rates.latest(destinationCurrency, preferredCurrency) }
            }

            val debitNote = buildDebitNote(
                destinationAccount = destinationAccount,
                sourceCurrency = sourceCurrency,
                destinationCurrency = destinationCurrency,
                destinationAmountMinor = destinationAmountMinor,
                rate = snapshot.rate,
                userNote = cleanNote
            )
            val creditNote = buildCreditNote(
                sourceAccount = sourceAccount,
                sourceCurrency = sourceCurrency,
                destinationCurrency = destinationCurrency,
                sourceAmountMinor = sourceAmountMinor,
                rate = snapshot.rate,
                userNote = cleanNote
            )

            val debitTx = Transaction(
                id = UUID.randomUUID().toString(),
                type = TransactionType.EXPENSE,
                amountMinor = sourceAmountMinor,
                category = TransactionTags.MONEY_TRANSFER,
                note = debitNote,
                dateEpochMillis = dateEpochMillis,
                accountId = sourceAccount.id,
                currency = sourceCurrency,
                targetCurrency = preferredCurrency
            )

            val creditTx = Transaction(
                id = UUID.randomUUID().toString(),
                type = TransactionType.INCOME,
                amountMinor = destinationAmountMinor,
                category = TransactionTags.MONEY_TRANSFER,
                note = creditNote,
                dateEpochMillis = dateEpochMillis,
                accountId = destinationAccount.id,
                currency = destinationCurrency,
                targetCurrency = preferredCurrency
            )

            val debitResult = addTransaction(debitTx)
            if (debitResult.isFailure) {
                return Result.failure(
                    debitResult.exceptionOrNull() ?: IllegalStateException("Unable to debit source account.")
                )
            }

            val creditResult = addTransaction(creditTx)
            if (creditResult.isFailure) {
                runCatching { transactionRepository.deleteTransaction(debitTx.id) }
                return Result.failure(
                    creditResult.exceptionOrNull() ?: IllegalStateException("Unable to credit destination account.")
                )
            }

            return Result.success(
                TransferMoneyResult(
                    debitTransaction = debitTx,
                    creditTransaction = creditTx,
                    conversionSnapshot = snapshot
                )
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
    }

    private fun buildDebitNote(
        destinationAccount: Account,
        sourceCurrency: String,
        destinationCurrency: String,
        destinationAmountMinor: Long,
        rate: String,
        userNote: String
    ): String {
        val base = if (sourceCurrency.equals(destinationCurrency, ignoreCase = true)) {
            "Transfer to ${destinationAccount.displayLabel}"
        } else {
            val formattedDest = "${destinationCurrency} ${MoneyUtils.formatMinor(destinationAmountMinor, destinationCurrency)}"
            "Transfer to ${destinationAccount.displayLabel} ($formattedDest @ $rate)"
        }
        return if (userNote.isBlank()) base else "$base · $userNote"
    }

    private fun buildCreditNote(
        sourceAccount: Account,
        sourceCurrency: String,
        destinationCurrency: String,
        sourceAmountMinor: Long,
        rate: String,
        userNote: String
    ): String {
        val base = if (sourceCurrency.equals(destinationCurrency, ignoreCase = true)) {
            "Transfer from ${sourceAccount.displayLabel}"
        } else {
            val formattedSource = "${sourceCurrency} ${MoneyUtils.formatMinor(sourceAmountMinor, sourceCurrency)}"
            "Transfer from ${sourceAccount.displayLabel} ($formattedSource @ $rate)"
        }
        return if (userNote.isBlank()) base else "$base · $userNote"
    }
}
