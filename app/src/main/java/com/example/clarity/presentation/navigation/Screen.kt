package com.example.clarity.presentation.navigation

import android.net.Uri

sealed class Screen(val route: String) {
    object Main : Screen("main_screen")
    object Register : Screen("register_screen")
    object Login : Screen("login_screen")
    object TransactionList : Screen("transactions")
    object TransactionDetail : Screen("transactions/{id}") {
        fun createRoute(id: String) = "transactions/${Uri.encode(id)}"
    }
    object TransactionEdit : Screen("transactions/edit?id={id}") {
        fun createRoute(id: String? = null) = if (id == null) "transactions/edit"
            else "transactions/edit?id=${Uri.encode(id)}"
    }
}
