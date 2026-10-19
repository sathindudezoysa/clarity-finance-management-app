package com.example.clarity.presentation.main

data class MainState(
    val isHidden: Boolean = false,
    val currency: String = "LKR",
    val netWorth: String = "0.00",
    val income: String = "+0.00",
    val incomeSubtitle: String = "No income sources",
    val expenses: String = "-0.00",
    val expenseSubtitle: String = "0 transactions",
    val cashBalance: String = "0.00"
)
