package com.example.clarity.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.clarity.presentation.auth.register.RegisterScreen
import com.example.clarity.presentation.main.MainScreen

@Composable
fun SetupNavGraph(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Register.route
    ) {
        composable(route = Screen.Register.route) {
            RegisterScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.Register.route) { inclusive = true }
                    }
                }
            )
        }
        composable(route = Screen.Main.route) {
            MainScreen()
        }
    }
}
