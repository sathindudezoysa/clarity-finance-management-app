package com.example.clarity.presentation.navigation

sealed class Screen(val route: String) {
    object Main : Screen("main_screen")
}
