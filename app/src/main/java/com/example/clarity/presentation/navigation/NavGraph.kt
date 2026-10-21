package com.example.clarity.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.clarity.presentation.accounts.edit.AccountEditScreen
import com.example.clarity.presentation.accounts.list.AccountListScreen
import com.example.clarity.presentation.auth.login.LoginScreen
import com.example.clarity.presentation.auth.register.RegisterScreen
import com.example.clarity.presentation.goals.GoalsScreen
import com.example.clarity.presentation.income.IncomeScreen
import com.example.clarity.presentation.main.MainScreen
import com.example.clarity.presentation.settings.SettingsScreen
import com.example.clarity.presentation.transactions.detail.TransactionDetailScreen
import com.example.clarity.presentation.transactions.edit.TransactionEditScreen
import com.example.clarity.presentation.transactions.list.TransactionListScreen

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
                onNavigateToBudgets = { id ->
                    navController.navigate(Screen.Budgets.createRoute(id)) {
                        popUpTo(Screen.Main.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        if (id == null) {
                            restoreState = true
                        }
                    }
                },
                onNavigateToTransactions = {
                    navController.navigate(Screen.TransactionList.route) {
                        popUpTo(Screen.Main.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToIncome = { navController.navigate(Screen.IncomeSources.route) },
                onNavigateToCashWallet = { navController.navigate(Screen.CashWallet.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                bottomBar = {
                    ClarityBottomNavBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.HOME
                    )
                }
            )
        }
        composable(Screen.Budgets.route, arguments = listOf(navArgument("budgetId") {
            type = NavType.StringType; nullable = true; defaultValue = null
        })) { entry ->
            com.example.clarity.presentation.budgets.BudgetsScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Main.route) { launchSingleTop = true }
                    }
                },
                selectedId = entry.arguments?.getString("budgetId"),
                onOpenEditor = { id ->
                    navController.navigate(Screen.BudgetEdit.createRoute(id)) {
                        launchSingleTop = true
                    }
                },
                bottomBar = {
                    ClarityBottomNavBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.BUDGETS
                    )
                }
            )
        }
        composable(Screen.BudgetEdit.route, arguments = listOf(navArgument("budgetId") {
            type = NavType.StringType; nullable = true; defaultValue = null
        })) { entry ->
            val parentEntry = remember(entry) {
                runCatching { navController.getBackStackEntry(Screen.Budgets.route) }.getOrNull()
            }
            val budgetsViewModel: com.example.clarity.presentation.budgets.BudgetsViewModel =
                if (parentEntry != null) hiltViewModel(parentEntry) else hiltViewModel()
            com.example.clarity.presentation.budgets.BudgetEditScreen(
                budgetId = entry.arguments?.getString("budgetId"),
                onBack = {
                    if (!navController.popBackStack(Screen.Budgets.route, inclusive = false)) {
                        navController.popBackStack()
                    }
                },
                viewModel = budgetsViewModel
            )
        }
        composable(route = Screen.Goals.route) {
            GoalsScreen(
                bottomBar = {
                    ClarityBottomNavBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.GOALS
                    )
                },
                viewModel = hiltViewModel()
            )
        }
        composable(Screen.IncomeSources.route) {
            IncomeScreen(onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(Screen.AccountEdit.createRoute()) },
                onOpenTransaction = { id -> navController.navigate(Screen.TransactionDetail.createRoute(id)) })
        }
        composable(route = Screen.CashWallet.route) {
            com.example.clarity.presentation.wallet.CashWalletScreen(
                onBack = { navController.popBackStack() },
                onAddAccount = { navController.navigate(Screen.AccountEdit.createRoute()) }
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
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Main.route) { launchSingleTop = true }
                    }
                },
                onAdd = { navController.navigate(Screen.AccountEdit.createRoute()) },
                onEdit = { id -> navController.navigate(Screen.AccountEdit.createRoute(id)) },
                bottomBar = {
                    ClarityBottomNavBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.ACCOUNTS
                    )
                },
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
                bottomBar = {
                    ClarityBottomNavBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.TRANSACTIONS
                    )
                },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(navArgument(TRANSACTION_ID_ARGUMENT) { type = NavType.StringType })
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
            arguments = listOf(navArgument(TRANSACTION_ID_ARGUMENT) { type = NavType.StringType; nullable = true; defaultValue = null })
        ) {
            TransactionEditScreen(
                onBack = { navController.popBackStack() },
                onCreateAccount = { navController.navigate(Screen.AccountEdit.createRoute()) },
                viewModel = hiltViewModel()
            )
        }
    }
}
