package com.example.clarity.presentation.main

data class MainState(
    val isLoading: Boolean = false,
    val title: String = "",
    val description: String = "",
    val error: String? = null
)
