package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.*
import com.example.clarity.domain.model.*
import java.math.BigInteger

fun WalletTopUpOptionEntity.toWalletOption() = WalletTopUpOption(bankAccountId, enabled, thresholdMinor, amountMinor)

fun walletSnapshot(
    accounts: List<WalletBankAccountEntity>, topUps: List<WalletTopUpEntity>,
    cash: List<TransactionEntity>, option: WalletTopUpOptionEntity?
): CashWallet {
    val transferred = topUps.fold(BigInteger.ZERO) { total, entry -> total + BigInteger.valueOf(entry.amountMinor) }
    val cashBalance = cash.fold(transferred) { total, entry ->
        val amount = BigInteger.valueOf(entry.amountMinor)
        if (entry.type == TransactionType.INCOME.name) total + amount else total - amount
    }
    val banks = accounts.map { bank ->
        val withdrawals = topUps.filter { it.bankAccountId == bank.id }
            .fold(BigInteger.ZERO) { sum, entry -> sum + BigInteger.valueOf(entry.amountMinor) }
        WalletBankAccount(bank.id, bank.name, BigInteger.valueOf(bank.openingBalanceMinor) - withdrawals)
    }
    val activity = topUps.map { entry ->
        val bankName = accounts.firstOrNull { it.id == entry.bankAccountId }?.name ?: "Bank account"
        WalletActivity(entry.id, "${if (entry.automatic) "Automatic top-up" else "Top-up"} from $bankName", entry.amountMinor, true, entry.dateEpochMillis)
    } + cash.map { entry ->
        WalletActivity(entry.id, entry.category, entry.amountMinor, entry.type == TransactionType.INCOME.name, entry.dateEpochMillis, entry.id)
    }
    return CashWallet(cashBalance, banks, activity.sortedByDescending { it.dateEpochMillis }, option?.toWalletOption())
}
