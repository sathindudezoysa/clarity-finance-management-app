package com.example.clarity.presentation.navigation

import androidx.compose.runtime.Composable
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
            MainScreen(onNavigateToTransactions = { navController.navigate(Screen.TransactionList.route) })
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
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Screen.TransactionEdit.createRoute(id)) },
                viewModel = hiltViewModel()
            )
        }
        composable(
            route = Screen.TransactionEdit.route,
            arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) {
            TransactionEditScreen(onBack = { navController.popBackStack() }, viewModel = hiltViewModel())
        }
    }
}
