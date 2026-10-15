package com.example.clarity.presentation.settings

enum class SettingsDestination { BACK, BANK_ACCOUNTS }

data class SettingsState(val destination: SettingsDestination? = null)
