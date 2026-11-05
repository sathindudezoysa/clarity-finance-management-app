package com.example.clarity.domain.util

import com.example.clarity.domain.model.Account

object CashTopUpPolicy {

    fun eligibleSourceAccounts(
        accounts: List<Account>,
        defaultCurrency: String
    ): List<Account> {
        if (defaultCurrency.isBlank()) return emptyList()
        return accounts.filter { account ->
            canTopUpFrom(account, defaultCurrency)
        }
    }

    fun canTopUpFrom(
        account: Account?,
        defaultCurrency: String
    ): Boolean {
        if (account == null || defaultCurrency.isBlank()) return false
        return !account.isCashWallet &&
            !account.isGoalAccount &&
            account.currency.equals(defaultCurrency.trim(), ignoreCase = true)
    }

    fun resolveSelectedSourceAccountId(
        currentSelectedId: String,
        eligibleAccounts: List<Account>
    ): String {
        return currentSelectedId.takeIf { id -> eligibleAccounts.any { it.id == id } }
            ?: eligibleAccounts.firstOrNull { it.isDefault }?.id
            ?: eligibleAccounts.firstOrNull()?.id.orEmpty()
    }
}
