package com.example.clarity.presentation.settings

enum class SettingsDestination { BACK, BANK_ACCOUNTS }

data class SettingsState(
    val destination: SettingsDestination? = null,
    val currency: String = "LKR",
    val pendingSync: Boolean = false,
    val saving: Boolean = false,
    val error: Boolean = false
)
