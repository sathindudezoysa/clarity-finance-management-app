package com.example.clarity.domain.usecase.goal

import com.example.clarity.domain.model.Account
import com.example.clarity.domain.model.ConversionSnapshot
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.model.FinancialGoal
import com.example.clarity.domain.model.GoalError
import com.example.clarity.domain.model.GoalException
import com.example.clarity.domain.model.Transaction
import com.example.clarity.domain.model.TransactionError
import com.example.clarity.domain.model.TransactionException
import com.example.clarity.domain.model.TransactionTags
import com.example.clarity.domain.model.TransactionType
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.CurrencyPreferenceRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.usecase.account.DeleteAccountUseCase
import com.example.clarity.domain.usecase.transaction.AddTransactionUseCase
import com.example.clarity.domain.usecase.transaction.PreviewConversionUseCase
import com.example.clarity.domain.util.GoalPolicy
import com.example.clarity.domain.util.GoalProgressPolicy
import com.example.clarity.domain.util.MoneyUtils
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import javax.inject.Inject

data class FinishGoalResult(
    val goal: FinancialGoal,
    val destinationAccount: Account,
    val payoutTransaction: Transaction,
    val conversionSnapshot: ConversionSnapshot
)

class FinishGoalUseCase @Inject constructor(
    private val getGoal: GetGoalUseCase,
    private val observeGoalContributions: ObserveGoalContributionsUseCase,
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val addTransaction: AddTransactionUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val previewConversion: PreviewConversionUseCase,
    private val rates: ExchangeRateRepository,
    private val currencyPreferenceRepository: CurrencyPreferenceRepository
) {
    suspend operator fun invoke(
        goalId: String,
        destinationAccountId: String,
        dateEpochMillis: Long = System.currentTimeMillis()
    ): Result<FinishGoalResult> {
        try {
            val cleanGoalId = goalId.trim()
            val cleanDestinationId = destinationAccountId.trim()
            if (!GoalPolicy.validId(cleanGoalId)) {
                return Result.failure(GoalException(GoalError.NOT_FOUND, "Financial goal not found."))
            }
            if (cleanDestinationId.isEmpty() || !GoalPolicy.validId(cleanDestinationId)) {
                return Result.failure(
                    TransactionException(TransactionError.ACCOUNT_REQUIRED, "Select a destination account to receive your goal savings.")
                )
            }

            val goal = getGoal(cleanGoalId).getOrNull()
                ?: return Result.failure(GoalException(GoalError.NOT_FOUND, "Financial goal not found."))

            val txResource = transactionRepository.observeTransactions()
                .first { it !is Resource.Loading }
            val activeTransactions = txResource.data.orEmpty()
            val payoutTxId = payoutTransactionId(goal.id)
            if (activeTransactions.any { it.id == payoutTxId }) {
                return Result.failure(
                    IllegalStateException("This goal has already been finished and closed.")
                )
            }

            val txById = activeTransactions.associateBy { it.id }
            val contributionsSnapshot = observeGoalContributions().first()
            val goalContributions = contributionsSnapshot.contributions.filter {
                it.goalId == goal.id &&
                    it.amountMinor > 0L &&
                    it.currency.equals(goal.currency, ignoreCase = true) &&
                    txById[it.id] != null
            }
            val summary = GoalProgressPolicy.evaluateGoalProgress(goal, goalContributions)
            if (!summary.isCompleted || summary.savedMinor <= BigInteger.ZERO) {
                return Result.failure(
                    IllegalStateException("Only goals that have reached 100% can be finished and closed.")
                )
            }

            val payoutSourceMinorBig = summary.savedMinor
                .min(BigInteger.valueOf(goal.targetMinor.coerceAtLeast(1L)))
            val savedMinorLong = runCatching { payoutSourceMinorBig.longValueExact() }
                .getOrElse { goal.targetMinor.coerceAtLeast(1L) }

            val accountsResource = accountRepository.observeAccounts()
                .first { it !is Resource.Loading }
            val observedAccounts = accountsResource.data.orEmpty()

            val destinationAccount = accountRepository.getAccount(cleanDestinationId).getOrNull()
                ?: observedAccounts.firstOrNull { it.id == cleanDestinationId }
                ?: return Result.failure(
                    TransactionException(TransactionError.ACCOUNT_NOT_FOUND, "Destination account not found.")
                )

            if (destinationAccount.isGoalAccount) {
                return Result.failure(
                    IllegalArgumentException("Select a bank account or Cash Wallet to receive the goal funds.")
                )
            }

            val sourceCurrency = goal.currency.uppercase()
            val destinationCurrency = destinationAccount.currency.uppercase()

            val snapshot = if (sourceCurrency == destinationCurrency) {
                ConversionSnapshot.identity(savedMinorLong)
            } else {
                try {
                    previewConversion(savedMinorLong, sourceCurrency, destinationCurrency)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    val cachedQuote = runCatching { rates.cached(sourceCurrency, destinationCurrency) }.getOrNull()
                    if (cachedQuote != null) {
                        CurrencyConversion.snapshot(savedMinorLong, cachedQuote)
                    } else {
                        return Result.failure(
                            IllegalStateException(
                                "Transfer blocked: Unable to convert $sourceCurrency to $destinationCurrency because there is no network service. Please connect to the internet and try again."
                            )
                        )
                    }
                }
            }

            val destinationAmountMinor = snapshot.amountMinor
            val preferredCurrency = runCatching { currencyPreferenceRepository.current() }
                .getOrDefault(destinationCurrency)
            if (!destinationCurrency.equals(preferredCurrency, ignoreCase = true)) {
                runCatching {
                    rates.cached(destinationCurrency, preferredCurrency)
                        ?: rates.latest(destinationCurrency, preferredCurrency)
                }
            }

            val payoutNote = buildPayoutNote(
                goal = goal,
                sourceCurrency = sourceCurrency,
                destinationCurrency = destinationCurrency,
                sourceAmountMinor = savedMinorLong,
                rate = snapshot.rate
            )

            val payoutTx = Transaction(
                id = payoutTxId,
                type = TransactionType.INCOME,
                amountMinor = destinationAmountMinor,
                category = TransactionTags.MONEY_TRANSFER,
                note = payoutNote,
                dateEpochMillis = dateEpochMillis,
                accountId = destinationAccount.id,
                currency = destinationCurrency,
                targetCurrency = preferredCurrency
            )

            val addResult = addTransaction(payoutTx)
            if (addResult.isFailure) {
                return Result.failure(
                    addResult.exceptionOrNull()
                        ?: IllegalStateException("Unable to transfer goal funds to ${destinationAccount.displayLabel}.")
                )
            }

            val goalAccountId = Account.goalAccountId(goal.id)
            val deleteResult = deleteAccount(goalAccountId, allowGoalAccount = true)
            if (deleteResult.isFailure) {
                runCatching { transactionRepository.deleteTransaction(payoutTx.id) }
                return Result.failure(
                    deleteResult.exceptionOrNull()
                        ?: IllegalStateException("Unable to close and delete the goal account.")
                )
            }

            return Result.success(
                FinishGoalResult(
                    goal = goal,
                    destinationAccount = destinationAccount,
                    payoutTransaction = payoutTx,
                    conversionSnapshot = snapshot
                )
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
    }

    private fun buildPayoutNote(
        goal: FinancialGoal,
        sourceCurrency: String,
        destinationCurrency: String,
        sourceAmountMinor: Long,
        rate: String
    ): String {
        return if (sourceCurrency.equals(destinationCurrency, ignoreCase = true)) {
            "Transfer from Goal: ${goal.name} (Goal Completed)"
        } else {
            val formattedSource = "$sourceCurrency ${MoneyUtils.formatMinor(sourceAmountMinor, sourceCurrency)}"
            "Transfer from Goal: ${goal.name} ($formattedSource @ $rate · Goal Completed)"
        }
    }

    companion object {
        fun payoutTransactionId(goalId: String): String = "goal_payout_$goalId"
    }
}
