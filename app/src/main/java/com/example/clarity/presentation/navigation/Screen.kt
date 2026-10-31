package com.example.clarity.presentation.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Budgets : Screen("budgets?budgetId={budgetId}") {
        fun createRoute(id: String? = null) = "budgets" + (id?.let { "?budgetId=${Uri.encode(it)}" } ?: "")
    }
    object BudgetEdit : Screen("budgets/edit?budgetId={budgetId}") {
        fun createRoute(id: String? = null) = if (id == null) "budgets/edit"
            else "budgets/edit?budgetId=${Uri.encode(id)}"
    }
    object Main : Screen("main_screen")
    object CashWallet : Screen("cash_wallet")
    object Register : Screen("register_screen")
    object Login : Screen("login_screen")
    object TransactionList : Screen("transactions")
    object Settings : Screen("settings")
    object AccountList : Screen("accounts")
    object Goals : Screen("goals")
    object GoalEdit : Screen("goals/edit?goalId={goalId}") {
        fun createRoute(goalId: String? = null) = if (goalId == null) "goals/edit"
            else "goals/edit?goalId=${Uri.encode(goalId)}"
    }
    object AccountEdit : Screen("accounts/edit?id={id}") {
        fun createRoute(id: String? = null) = if (id == null) "accounts/edit"
            else "accounts/edit?id=${Uri.encode(id)}"
    }
    object TransactionDetail : Screen("transactions/{$TRANSACTION_ID_ARGUMENT}") {
        fun createRoute(id: String) = "transactions/${Uri.encode(id)}"
    }
    object TransactionEdit : Screen("transactions/edit?$TRANSACTION_ID_ARGUMENT={$TRANSACTION_ID_ARGUMENT}&type={type}") {
        fun createRoute(id: String? = null, type: String? = null): String {
            val params = mutableListOf<String>()
            if (id != null) params.add("$TRANSACTION_ID_ARGUMENT=${Uri.encode(id)}")
            if (type != null) params.add("type=${Uri.encode(type)}")
            return if (params.isEmpty()) "transactions/edit" else "transactions/edit?${params.joinToString("&")}"
        }
    }
    object CategoryTagCreate : Screen("transactions/categories/create?type={type}") {
        fun createRoute(type: String = "EXPENSE") = "transactions/categories/create?type=${Uri.encode(type)}"
    }
}
