package com.example.clarity.domain.usecase.account

import com.example.clarity.domain.model.AccountError
import com.example.clarity.domain.model.AccountException
import com.example.clarity.domain.model.CurrencyConversion
import com.example.clarity.domain.repository.AccountRepository
import com.example.clarity.domain.repository.ExchangeRateRepository
import com.example.clarity.domain.repository.TransactionRepository
import com.example.clarity.domain.util.AccountBalancePolicy
import com.example.clarity.domain.util.Resource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class DeleteAccountUseCase @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val rates: ExchangeRateRepository
) {
    suspend operator fun invoke(
        accountId: String,
        allowGoalAccount: Boolean = false
    ): Result<Unit> {
        try {
            val cleanId = accountId.trim()
            if (cleanId.isEmpty() || cleanId.length > 128 || cleanId.contains('/')) {
                return Result.failure(AccountException(AccountError.NOT_FOUND, "Account not found."))
            }

            val accountsResource = accountRepository.observeAccounts()
                .first { it !is Resource.Loading }
            val account = accountRepository.getAccount(cleanId).getOrNull()
                ?: accountsResource.data.orEmpty().firstOrNull { it.id == cleanId }
            if (account == null) {
                return if (allowGoalAccount) {
                    Result.success(Unit)
                } else {
                    Result.failure(AccountException(AccountError.NOT_FOUND, "Account not found."))
                }
            }

            if (account.isCashWallet || (!allowGoalAccount && account.isGoalAccount)) {
                return Result.failure(IllegalStateException("This system account cannot be deleted."))
            }

            val txResource = transactionRepository.observeTransactions()
                .first { it !is Resource.Loading }
            val activeTransactions = txResource.data.orEmpty()

            val rateCache = mutableMapOf<String, BigDecimal?>()
            suspend fun resolveRate(source: String, target: String): BigDecimal? {
                if (source.equals(target, ignoreCase = true)) return BigDecimal.ONE
                val key = "$source->$target"
                if (rateCache.containsKey(key)) {
                    return rateCache[key]
                }
                val quote = try {
                    rates.cached(source, target) ?: rates.latest(source, target)
                } catch (_: Exception) {
                    null
                }
                val resolved = quote?.let {
                    try {
                        CurrencyConversion.rate(it.rate)
                    } catch (_: Exception) {
                        null
                    }
                }
                rateCache[key] = resolved
                return resolved
            }

            val balanceMinor = AccountBalancePolicy.calculateBalanceMinor(
                account = account,
                transactions = activeTransactions,
                resolveRate = ::resolveRate
            )
            if (balanceMinor != BigInteger.ZERO) {
                return Result.failure(
                    IllegalStateException("Only accounts with a 0 balance can be deleted.")
                )
            }

            return accountRepository.deleteAccount(cleanId)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
    }
}
