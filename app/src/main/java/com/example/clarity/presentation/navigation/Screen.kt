package com.example.clarity.presentation.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main_screen")
    object Register : Screen("register_screen")
    object Login : Screen("login_screen")
    object Setup : Screen("setup_screen")
    object Profile : Screen("profile_screen")
}
