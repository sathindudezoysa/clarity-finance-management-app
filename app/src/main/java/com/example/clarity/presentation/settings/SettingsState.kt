package com.example.clarity.presentation.settings

enum class SettingsDestination { BACK, BANK_ACCOUNTS, LOGIN }

data class SettingsState(
    val destination: SettingsDestination? = null,
    val uid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val isVerified: Boolean = false,
    val tierLabel: String? = null,
    val streakLabel: String? = null,
    val currency: String = "LKR",
    val pendingSync: Boolean = false,
    val saving: Boolean = false,
    val error: Boolean = false,
    val defaultAccountName: String? = null,
    val cashThreshold: String? = null,
    val cloudSyncSchedule: String? = null,
    val biometricEnabled: Boolean? = null,
    val twoFactorStatus: String? = null,
    val appearanceMode: String? = null,
    val isUpdatingProfile: Boolean = false,
    val profileSaved: Boolean = false,
    val profileError: String? = null
)
