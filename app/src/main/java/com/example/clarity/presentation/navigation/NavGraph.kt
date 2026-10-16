package com.example.clarity.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.clarity.presentation.transactions.list.TransactionListScreen
import com.example.clarity.presentation.transactions.detail.TransactionDetailScreen
import com.example.clarity.presentation.transactions.edit.TransactionEditScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.clarity.presentation.auth.register.RegisterScreen
import com.example.clarity.presentation.main.MainScreen
import com.example.clarity.presentation.accounts.list.AccountListScreen
import com.example.clarity.presentation.accounts.edit.AccountEditScreen
import com.example.clarity.presentation.settings.SettingsScreen

import com.example.clarity.presentation.auth.login.LoginScreen

@Composable
fun SetupNavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(route = Screen.Login.route) {
            LoginScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                }
            )
        }
        composable(route = Screen.Register.route) {
            RegisterScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }
        composable(route = Screen.Main.route) {
            MainScreen(
                onNavigateToTransactions = { navController.navigate(Screen.TransactionList.route) },
                onNavigateToCashWallet = { navController.navigate(Screen.CashWallet.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(route = Screen.CashWallet.route) {
            com.example.clarity.presentation.wallet.CashWalletScreen(
                onBack = { navController.popBackStack() },
                onOpenTransaction = { id -> navController.navigate(Screen.TransactionDetail.createRoute(id)) },
                onRegisterBank = { navController.navigate(Screen.BankSelection.createRoute()) },
                onManageBank = { id -> navController.navigate(Screen.WalletBankAccount.createRoute(accountId = id)) }
            )
        }
        composable(Screen.BankSelection.route,
            arguments = listOf(navArgument("accountId") { type = NavType.StringType; nullable = true; defaultValue = null })) { entry ->
            com.example.clarity.presentation.wallet.BankSelectionScreen(
                onBack = { navController.popBackStack() },
                onSelect = { bankId ->
                    val previous = navController.previousBackStackEntry
                    if (previous?.destination?.route == Screen.WalletBankAccount.route) {
                        previous.savedStateHandle["selectedBank"] = bankId
                        navController.popBackStack()
                    } else navController.navigate(Screen.WalletBankAccount.createRoute(bankId, entry.arguments?.getString("accountId")))
                }
            )
        }
        composable(Screen.WalletBankAccount.route, arguments = listOf(
            navArgument("bankId") { type = NavType.StringType; defaultValue = "" },
            navArgument("accountId") { type = NavType.StringType; nullable = true; defaultValue = null }
        )) { entry ->
            val selectedBank by entry.savedStateHandle.getStateFlow<String?>("selectedBank", null).collectAsStateWithLifecycle()
            com.example.clarity.presentation.wallet.BankAccountScreen(
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack(Screen.CashWallet.route, false) },
                onChangeBank = { id -> navController.navigate(Screen.BankSelection.createRoute(id)) },
                selectedBankId = selectedBank
            )
        }
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onBankAccounts = { navController.navigate(Screen.AccountList.route) },
                viewModel = hiltViewModel()
            )
        }
        composable(route = Screen.AccountList.route) {
            AccountListScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Screen.AccountEdit.createRoute()) },
                onEdit = { id -> navController.navigate(Screen.AccountEdit.createRoute(id)) },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.AccountEdit.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) {
            AccountEditScreen(onBack = { navController.popBackStack() }, viewModel = hiltViewModel())
        }
        composable(route = Screen.TransactionList.route) {
            TransactionListScreen(
                onBack = { navController.popBackStack() },
                onAdd = { navController.navigate(Screen.TransactionEdit.createRoute()) },
                onOpen = { id -> navController.navigate(Screen.TransactionDetail.createRoute(id)) },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) {
            TransactionDetailScreen(
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Screen.TransactionList.route) {
                        launchSingleTop = true
                    }
                },
                onEdit = { id -> navController.navigate(Screen.TransactionEdit.createRoute(id)) },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.TransactionEdit.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) {
            TransactionEditScreen(
                onBack = { navController.popBackStack() },
                onCreateAccount = { navController.navigate(Screen.AccountEdit.createRoute()) },
                viewModel = hiltViewModel()
            )
        }
    }
}
