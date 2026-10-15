package com.example.clarity.data.mapper

import com.example.clarity.data.local.entity.*
import com.example.clarity.domain.model.*
import java.math.BigInteger
import com.example.clarity.data.local.WalletBankTotals
import com.example.clarity.data.local.WalletMoneyParts
import com.example.clarity.data.local.WalletActivityRow
import com.example.clarity.data.local.WalletSnapshotRow

fun WalletBankTotals.toWalletAccount(): WalletBankAccount = with(account) {
    WalletBankAccount(id, name, BigInteger.valueOf(openingBalanceMinor) - WalletMoneyParts(high, low).value(),
        bankId, lastFour, accountType, currency, isDefault, isActive, needsBankReview, openingBalanceMinor)
}

fun WalletActivityRow.toWalletActivity(): WalletActivity {
    require(amountMinor != Long.MIN_VALUE) { "Activity amount is outside the supported money range" }
    return WalletActivity(id, title.ifBlank { "Balance adjustment" }, kotlin.math.abs(amountMinor),
        amountMinor >= 0, dateEpochMillis, transactionId, kind, bankAccountId)
}

fun WalletTopUpOptionEntity.toWalletOption() = WalletTopUpOption(bankAccountId, enabled, thresholdMinor, amountMinor, automaticExecution)

fun List<WalletSnapshotRow>.toCashWallet(uid: String): CashWallet {
    val balance = single { it.section == "BALANCE" }
    return CashWallet(
        balanceMinor = WalletMoneyParts(balance.balanceHigh, balance.balanceLow).value(),
        accounts = mapNotNull { it.account?.toWalletAccount() },
        activity = filter { it.section == "ACTIVITY" }.map { requireNotNull(it.activity).toWalletActivity() },
        topUpHistory = filter { it.section == "HISTORY" }.map { requireNotNull(it.activity).toWalletActivity() },
        topUpOption = singleOrNull { it.section == "OPTION" }?.option?.toWalletOption(),
        ownerId = uid
    )
}
