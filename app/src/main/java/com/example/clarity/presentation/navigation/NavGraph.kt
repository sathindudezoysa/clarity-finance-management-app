package com.example.clarity.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.clarity.presentation.accounts.edit.AccountEditScreen
import com.example.clarity.presentation.accounts.list.AccountListScreen
import com.example.clarity.presentation.auth.login.LoginScreen
import com.example.clarity.presentation.auth.register.RegisterScreen
import com.example.clarity.presentation.goals.GoalEditScreen
import com.example.clarity.presentation.goals.GoalsScreen
import com.example.clarity.presentation.main.MainScreen
import com.example.clarity.presentation.settings.SettingsScreen
import com.example.clarity.presentation.transactions.detail.TransactionDetailScreen
import com.example.clarity.presentation.transactions.edit.TransactionEditScreen
import com.example.clarity.presentation.transactions.list.TransactionListScreen
import com.example.clarity.presentation.transactions.tags.CategoryTagCreateScreen

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
                        launchSingleTop = true
                    }
                },
                onAddTransaction = {
                    navController.navigate(Screen.TransactionEdit.createRoute())
                },
                onOpenTransaction = { id ->
                    navController.navigate(Screen.TransactionDetail.createRoute(id))
                },
                onNavigateToIncome = {
                    navController.navigate(Screen.TransactionEdit.createRoute(type = "INCOME")) {
                        launchSingleTop = true
                    }
                },
                onNavigateToCashWallet = { navController.navigate(Screen.CashWallet.route) },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                topBar = {
                    ClarityTopBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.HOME
                    )
                },
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
                onNavigateToProfile = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                topBar = {
                    ClarityTopBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.BUDGETS
                    )
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
                onNavigateToProfile = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                topBar = {
                    ClarityTopBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.GOALS
                    )
                },
                bottomBar = {
                    ClarityBottomNavBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.GOALS
                    )
                },
                viewModel = hiltViewModel(),
                onAddGoal = {
                    navController.navigate(Screen.GoalEdit.createRoute()) {
                        launchSingleTop = true
                    }
                },
                onEditGoal = { goalId ->
                    navController.navigate(Screen.GoalEdit.createRoute(goalId)) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Screen.GoalEdit.route,
            arguments = listOf(navArgument("goalId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) {
            GoalEditScreen(
                onBack = { navController.popBackStack() },
                viewModel = hiltViewModel()
            )
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
                onSignOut = {
                    Log.d("LogoutDebug", "3. NavGraph onSignOut, navigating to Login")
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
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
                onNavigateToProfile = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                topBar = {
                    ClarityTopBar(
                        navController = navController,
                        currentDestination = BottomNavDestination.ACCOUNTS
                    )
                },
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
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Main.route) {
                            launchSingleTop = true
                        }
                    }
                },
                onAdd = { navController.navigate(Screen.TransactionEdit.createRoute()) },
                onOpen = { id -> navController.navigate(Screen.TransactionDetail.createRoute(id)) },
                onNavigateToProfile = {
                    navController.navigate(Screen.Settings.route) {
                        launchSingleTop = true
                    }
                },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(navArgument(TRANSACTION_ID_ARGUMENT) { type = NavType.StringType })
        ) { entry ->
            val createdCategoryName by entry.savedStateHandle
                .getStateFlow<String?>("created_category_name", null)
                .collectAsStateWithLifecycle()
            val createdCategoryType by entry.savedStateHandle
                .getStateFlow<String?>("created_category_type", null)
                .collectAsStateWithLifecycle()

            TransactionDetailScreen(
                onBack = {
                    if (!navController.popBackStack()) navController.navigate(Screen.TransactionList.route) {
                        launchSingleTop = true
                    }
                },
                onCreateAccount = { navController.navigate(Screen.AccountEdit.createRoute()) },
                onOpenCreateCategory = { type ->
                    navController.navigate(Screen.CategoryTagCreate.createRoute(type.name)) {
                        launchSingleTop = true
                    }
                },
                createdCategoryName = createdCategoryName,
                createdCategoryType = createdCategoryType,
                onConsumedCreatedCategory = {
                    entry.savedStateHandle["created_category_name"] = null
                    entry.savedStateHandle["created_category_type"] = null
                },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.TransactionEdit.route,
            arguments = listOf(
                navArgument(TRANSACTION_ID_ARGUMENT) { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { entry ->
            val createdCategoryName by entry.savedStateHandle
                .getStateFlow<String?>("created_category_name", null)
                .collectAsStateWithLifecycle()
            val createdCategoryType by entry.savedStateHandle
                .getStateFlow<String?>("created_category_type", null)
                .collectAsStateWithLifecycle()

            TransactionEditScreen(
                onBack = { navController.popBackStack() },
                onCreateAccount = { navController.navigate(Screen.AccountEdit.createRoute()) },
                onOpenCreateCategory = { type ->
                    navController.navigate(Screen.CategoryTagCreate.createRoute(type.name)) {
                        launchSingleTop = true
                    }
                },
                createdCategoryName = createdCategoryName,
                createdCategoryType = createdCategoryType,
                onConsumedCreatedCategory = {
                    entry.savedStateHandle["created_category_name"] = null
                    entry.savedStateHandle["created_category_type"] = null
                },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.CategoryTagCreate.route,
            arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = "EXPENSE" })
        ) {
            CategoryTagCreateScreen(
                onBack = { navController.popBackStack() },
                onTagCreated = { createdName, createdType ->
                    navController.previousBackStackEntry?.savedStateHandle?.apply {
                        set("created_category_name", createdName)
                        set("created_category_type", createdType.name)
                    }
                    navController.popBackStack()
                },
                viewModel = hiltViewModel()
            )
        }
    }
}
