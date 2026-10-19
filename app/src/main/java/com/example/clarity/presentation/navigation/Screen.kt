package com.example.clarity.presentation.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object IncomeSources : Screen("income_sources")
    object Main : Screen("main_screen")
    object CashWallet : Screen("cash_wallet")
    object BankSelection : Screen("wallet/banks?accountId={accountId}") {
        fun createRoute(accountId: String? = null) = "wallet/banks" + (accountId?.let { "?accountId=${Uri.encode(it)}" } ?: "")
    }
    object WalletBankAccount : Screen("wallet/accounts/edit?bankId={bankId}&accountId={accountId}") {
        fun createRoute(bankId: String = "", accountId: String? = null) =
            "wallet/accounts/edit?bankId=${Uri.encode(bankId)}" + (accountId?.let { "&accountId=${Uri.encode(it)}" } ?: "")
    }
    object Register : Screen("register_screen")
    object Login : Screen("login_screen")
    object TransactionList : Screen("transactions")
    object Settings : Screen("settings")
    object AccountList : Screen("accounts")
    object AccountEdit : Screen("accounts/edit?id={id}") {
        fun createRoute(id: String? = null) = if (id == null) "accounts/edit"
            else "accounts/edit?id=${Uri.encode(id)}"
    }
    object TransactionDetail : Screen("transactions/{$TRANSACTION_ID_ARGUMENT}") {
        fun createRoute(id: String) = "transactions/${Uri.encode(id)}"
    }
    object TransactionEdit : Screen("transactions/edit?$TRANSACTION_ID_ARGUMENT={$TRANSACTION_ID_ARGUMENT}") {
        fun createRoute(id: String? = null) = if (id == null) "transactions/edit"
            else "transactions/edit?$TRANSACTION_ID_ARGUMENT=${Uri.encode(id)}"
    }
}
